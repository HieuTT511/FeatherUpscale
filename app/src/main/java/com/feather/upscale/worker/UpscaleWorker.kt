package com.feather.upscale.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.feather.upscale.TileProcessor
import com.feather.upscale.batch.BatchZipProcessor
import com.feather.upscale.batch.MobiProcessor
import com.feather.upscale.notification.UpscaleNotificationManager
import com.feather.upscale.util.HapticHelper
import com.feather.upscale.util.StorageHelper
import com.feather.upscale.video.VideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class UpscaleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_ORIGINAL_NAME = "original_name"
        const val KEY_CUSTOM_OUTPUT_DIR = "custom_output_dir"
        const val KEY_MODE = "mode"
        const val KEY_SCALE = "scale"
        const val KEY_USE_FP16 = "use_fp16"
        const val KEY_FORCE_LOW_RAM = "force_low_ram"
        const val KEY_MODEL_NAME = "model_name"

        const val MODE_SINGLE_IMAGE = "single_image"
        const val MODE_BATCH_ARCHIVE = "batch_archive"
        const val MODE_MOBI_ARCHIVE = "mobi_archive"
        const val MODE_VIDEO = "video"
    }

    private val notificationManager = UpscaleNotificationManager(applicationContext)
    private val hapticHelper = HapticHelper(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputPath = inputData.getString(KEY_INPUT_PATH) ?: return@withContext Result.failure()
        val originalName = inputData.getString(KEY_ORIGINAL_NAME) ?: File(inputPath).name
        val customOutputDir = inputData.getString(KEY_CUSTOM_OUTPUT_DIR)
        val mode = inputData.getString(KEY_MODE) ?: MODE_SINGLE_IMAGE
        val scale = inputData.getInt(KEY_SCALE, 4)
        val useFp16 = inputData.getBoolean(KEY_USE_FP16, true)
        val forceLowRam = inputData.getBoolean(KEY_FORCE_LOW_RAM, false)
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "realesrgan-x4plus-anime"

        val startTime = System.currentTimeMillis()

        try {
            // Khởi động Foreground Notification an toàn
            try {
                val initialForegroundInfo = notificationManager.createForegroundInfo(
                    pageIndex = 1,
                    totalPages = 1,
                    tileIndex = 0,
                    totalTiles = 1,
                    isPaused = false,
                    tileSize = if (forceLowRam || scale >= 8) 128 else 256
                )
                setForeground(initialForegroundInfo)
            } catch (_: Throwable) {
                // Tiếp tục xử lý background bình thường nếu quyền notification bị từ chối
            }

            val tileProcessor = TileProcessor(
                context = applicationContext,
                scale = scale,
                forcedLowRam = forceLowRam,
                useFp16 = useFp16,
                modelName = modelName
            )

            val baseName = if (originalName.contains('.')) originalName.substringBeforeLast('.') else originalName

            if (mode == MODE_VIDEO || VideoProcessor.isVideoFile(originalName)) {
                // ==========================================
                // 1. ON-DEVICE VIDEO SUPER-RESOLUTION MODE
                // ==========================================
                val inputFile = File(inputPath)
                val tempOutputFile = File(applicationContext.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                val videoProcessor = VideoProcessor(
                    context = applicationContext,
                    tileProcessor = tileProcessor,
                    hapticHelper = hapticHelper
                )

                UpscaleStateManager.updateState(
                    UpscaleState.Processing(
                        currentPage = 1,
                        totalPages = 1,
                        completedTiles = 0,
                        totalTiles = 1,
                        currentTileSize = tileProcessor.tileSize,
                        isLowRam = tileProcessor.isLowRam,
                        statusMessage = "Đang khởi tạo bộ giải mã video AI..."
                    )
                )

                videoProcessor.processVideo(
                    inputFile = inputFile,
                    outputFile = tempOutputFile,
                    onProgress = { progress ->
                        UpscaleStateManager.updateState(
                            UpscaleState.Processing(
                                currentPage = progress.currentFrame,
                                totalPages = progress.totalFrames,
                                completedTiles = progress.currentFrame,
                                totalTiles = progress.totalFrames,
                                currentTileSize = tileProcessor.tileSize,
                                isLowRam = tileProcessor.isLowRam,
                                progressFraction = progress.progressFraction,
                                statusMessage = progress.statusMessage
                            )
                        )

                        notificationManager.updateProgress(
                            pageIndex = progress.currentFrame,
                            totalPages = progress.totalFrames,
                            tileIndex = progress.currentFrame,
                            totalTiles = progress.totalFrames,
                            isPaused = UpscaleStateManager.isPaused.value,
                            tileSize = tileProcessor.tileSize
                        )
                    },
                    onPreviewUpdate = { upscaledBmp, origBmp ->
                        UpscaleStateManager.updateRuntimePreview(upscaledBmp, origBmp)
                    },
                    isPaused = { UpscaleStateManager.isPaused.value },
                    isCancelled = { UpscaleStateManager.isCancelled.value }
                )

                val outTarget = StorageHelper.createOutputFileStream(
                    context = applicationContext,
                    customOutputDirUriStr = customOutputDir,
                    fileName = "${baseName}_Upscale_${scale}x.mp4",
                    mimeType = "video/mp4",
                    isComicOrMobi = false
                )

                tempOutputFile.inputStream().use { input ->
                    outTarget.outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                val fileSizeStr = formatFileSize(tempOutputFile.length())
                tempOutputFile.delete()

                StorageHelper.scanMediaFile(applicationContext, outTarget, "video/mp4")

                val duration = System.currentTimeMillis() - startTime

                UpscaleStateManager.updateState(
                    UpscaleState.Completed(
                        outputPath = outTarget.absolutePath,
                        outputDirectory = outTarget.displayDirectory,
                        totalDurationMs = duration,
                        outputFileName = "${baseName}_Upscale_${scale}x.mp4",
                        outputFileSize = fileSizeStr,
                        outputResolution = "Video Super-Resolution (${scale}X)",
                        isVerified = true
                    )
                )

                notificationManager.showCompleted(
                    outputFileName = "${baseName}_Upscale_${scale}x.mp4",
                    durationMs = duration
                )

            } else if (mode == MODE_BATCH_ARCHIVE || mode == MODE_MOBI_ARCHIVE) {
                // ==========================================
                // 2. COMIC CBZ / MOBI BATCH ARCHIVE MODE
                // ==========================================
                val inputFile = File(inputPath)
                val isMobi = mode == MODE_MOBI_ARCHIVE ||
                        originalName.endsWith(".mobi", true) ||
                        originalName.endsWith(".prc", true)

                val tempOutputFile = File(applicationContext.cacheDir, "temp_upscaled_${System.currentTimeMillis()}.cbz")

                if (isMobi) {
                    val mobiProcessor = MobiProcessor(
                        tileProcessor = tileProcessor,
                        hapticHelper = hapticHelper
                    )

                    UpscaleStateManager.updateState(
                        UpscaleState.Processing(
                            currentPage = 1,
                            totalPages = 1,
                            completedTiles = 0,
                            totalTiles = 1,
                            currentTileSize = tileProcessor.tileSize,
                            isLowRam = tileProcessor.isLowRam,
                            statusMessage = "Đang giải mã sách truyện MOBI / PRC..."
                        )
                    )

                    mobiProcessor.processMobi(
                        inputFile = inputFile,
                        outputFile = tempOutputFile,
                        onProgress = { progress ->
                            val percent = if (progress.totalPages > 0) {
                                val pageFraction = 1f / progress.totalPages
                                val tileFraction = if (progress.totalTiles > 0) progress.currentTile.toFloat() / progress.totalTiles else 0f
                                (progress.currentPage - 1) * pageFraction + tileFraction * pageFraction
                            } else 0f

                            UpscaleStateManager.updateState(
                                UpscaleState.Processing(
                                    currentPage = progress.currentPage,
                                    totalPages = progress.totalPages,
                                    completedTiles = progress.currentTile,
                                    totalTiles = progress.totalTiles,
                                    currentTileSize = progress.currentTileSize,
                                    isLowRam = tileProcessor.isLowRam,
                                    progressFraction = percent,
                                    statusMessage = "Trang ${progress.currentPage}/${progress.totalPages} (${progress.pageName})"
                                )
                            )

                            notificationManager.updateProgress(
                                pageIndex = progress.currentPage,
                                totalPages = progress.totalPages,
                                tileIndex = progress.currentTile,
                                totalTiles = progress.totalTiles,
                                isPaused = UpscaleStateManager.isPaused.value,
                                tileSize = progress.currentTileSize
                            )
                        },
                        onPreviewUpdate = { upscaledBmp, origBmp ->
                            UpscaleStateManager.updateRuntimePreview(upscaledBmp, origBmp)
                        },
                        isPaused = { UpscaleStateManager.isPaused.value },
                        isCancelled = { UpscaleStateManager.isCancelled.value }
                    )
                } else {
                    // Batch ZIP / CBZ mode
                    val batchProcessor = BatchZipProcessor(
                        tileProcessor = tileProcessor,
                        hapticHelper = hapticHelper
                    )

                    UpscaleStateManager.updateState(
                        UpscaleState.Processing(
                            currentPage = 1,
                            totalPages = 1,
                            completedTiles = 0,
                            totalTiles = 1,
                            currentTileSize = tileProcessor.tileSize,
                            isLowRam = tileProcessor.isLowRam,
                            statusMessage = "Đang quét các trang truyện..."
                        )
                    )

                    batchProcessor.processArchive(
                        inputFile = inputFile,
                        outputFile = tempOutputFile,
                        onProgress = { progress ->
                            val percent = if (progress.totalPages > 0) {
                                val pageFraction = 1f / progress.totalPages
                                val tileFraction = if (progress.totalTiles > 0) progress.currentTile.toFloat() / progress.totalTiles else 0f
                                (progress.currentPage - 1) * pageFraction + tileFraction * pageFraction
                            } else 0f

                            UpscaleStateManager.updateState(
                                UpscaleState.Processing(
                                    currentPage = progress.currentPage,
                                    totalPages = progress.totalPages,
                                    completedTiles = progress.currentTile,
                                    totalTiles = progress.totalTiles,
                                    currentTileSize = progress.currentTileSize,
                                    isLowRam = tileProcessor.isLowRam,
                                    progressFraction = percent,
                                    statusMessage = "Trang ${progress.currentPage}/${progress.totalPages} (${progress.pageName})"
                                )
                            )

                            notificationManager.updateProgress(
                                pageIndex = progress.currentPage,
                                totalPages = progress.totalPages,
                                tileIndex = progress.currentTile,
                                totalTiles = progress.totalTiles,
                                isPaused = UpscaleStateManager.isPaused.value,
                                tileSize = progress.currentTileSize
                            )
                        },
                        onPreviewUpdate = { upscaledBmp, origBmp ->
                            UpscaleStateManager.updateRuntimePreview(upscaledBmp, origBmp)
                        },
                        isPaused = { UpscaleStateManager.isPaused.value },
                        isCancelled = { UpscaleStateManager.isCancelled.value }
                    )
                }

                // Ghi vào thư mục đích (Tùy chỉnh hoặc Mặc định Pictures/Download/UpScale)
                val outTarget = StorageHelper.createOutputFileStream(
                    context = applicationContext,
                    customOutputDirUriStr = customOutputDir,
                    fileName = "${baseName}_Upscale_${scale}x.cbz",
                    mimeType = "application/x-cbz",
                    isComicOrMobi = true
                )

                tempOutputFile.inputStream().use { input ->
                    outTarget.outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                val fileSizeStr = formatFileSize(tempOutputFile.length())
                tempOutputFile.delete()

                StorageHelper.scanMediaFile(applicationContext, outTarget, "application/x-cbz")

                val duration = System.currentTimeMillis() - startTime

                UpscaleStateManager.updateState(
                    UpscaleState.Completed(
                        outputPath = outTarget.absolutePath,
                        outputDirectory = outTarget.displayDirectory,
                        totalDurationMs = duration,
                        outputFileName = "${baseName}_Upscale_${scale}x.cbz",
                        outputFileSize = fileSizeStr,
                        outputResolution = "Tập Truyện CBZ (${scale}X)",
                        isVerified = true
                    )
                )

                notificationManager.showCompleted(
                    outputFileName = "${baseName}_Upscale_${scale}x.cbz",
                    durationMs = duration
                )

            } else {
                // ==========================================
                // 3. ON-DEVICE SINGLE IMAGE MODE
                // ==========================================
                val inputFile = File(inputPath)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val inputBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
                    ?: return@withContext Result.failure()

                UpscaleStateManager.updateState(
                    UpscaleState.Processing(
                        currentPage = 1,
                        totalPages = 1,
                        completedTiles = 0,
                        totalTiles = 1,
                        currentTileSize = tileProcessor.tileSize,
                        isLowRam = tileProcessor.isLowRam,
                        statusMessage = "Bắt đầu On-Device AI (${scale}X)..."
                    )
                )

                val upscaledBitmap = tileProcessor.process(
                    bitmap = inputBitmap,
                    onProgress = { currentTile, totalTiles ->
                        val progressFraction = if (totalTiles > 0) currentTile.toFloat() / totalTiles else 0f
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                        val speed = if (elapsed > 0.5f) currentTile / elapsed else 0f
                        val remaining = if (speed > 0.01f) ((totalTiles - currentTile) / speed).toLong() else 0L
                        UpscaleStateManager.updateState(
                            UpscaleState.Processing(
                                currentPage = 1,
                                totalPages = 1,
                                completedTiles = currentTile,
                                totalTiles = totalTiles,
                                currentTileSize = tileProcessor.tileSize,
                                isLowRam = tileProcessor.isLowRam,
                                progressFraction = progressFraction,
                                speedTilesPerSec = speed,
                                estimatedRemainingSec = remaining,
                                statusMessage = "Tile $currentTile/$totalTiles (${(progressFraction * 100).toInt()}%)"
                            )
                        )

                        notificationManager.updateProgress(
                            pageIndex = 1,
                            totalPages = 1,
                            tileIndex = currentTile,
                            totalTiles = totalTiles,
                            isPaused = UpscaleStateManager.isPaused.value,
                            tileSize = tileProcessor.tileSize
                        )
                    },
                    onPreviewUpdate = { previewBitmap ->
                        UpscaleStateManager.updateRuntimePreview(previewBitmap)
                    },
                    isPaused = { UpscaleStateManager.isPaused.value },
                    isCancelled = { UpscaleStateManager.isCancelled.value }
                )

                // Lưu ảnh vào thư mục đích (Tùy chỉnh hoặc Mặc định)
                val outTarget = StorageHelper.createOutputFileStream(
                    context = applicationContext,
                    customOutputDirUriStr = customOutputDir,
                    fileName = "${baseName}_Upscale_${scale}x.png",
                    mimeType = "image/png",
                    isComicOrMobi = false
                )

                outTarget.outputStream.use { outStream ->
                    upscaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    outStream.flush()
                }

                StorageHelper.scanMediaFile(applicationContext, outTarget, "image/png")

                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = if (outTarget.localFile != null) formatFileSize(outTarget.localFile.length()) else "Đã tối ưu"
                val resolutionStr = "${upscaledBitmap.width} x ${upscaledBitmap.height} px"

                upscaledBitmap.recycle()
                inputBitmap.recycle()

                hapticHelper.vibrateBatchComplete()

                UpscaleStateManager.updateState(
                    UpscaleState.Completed(
                        outputPath = outTarget.absolutePath,
                        outputDirectory = outTarget.displayDirectory,
                        totalDurationMs = duration,
                        outputFileName = "${baseName}_Upscale_${scale}x.png",
                        outputFileSize = fileSizeStr,
                        outputResolution = resolutionStr,
                        isVerified = true
                    )
                )

                notificationManager.showCompleted(
                    outputFileName = "${baseName}_Upscale_${scale}x.png",
                    durationMs = duration
                )
            }

            Result.success()

        } catch (e: Throwable) {
            val errorMsg = e.message ?: "Lỗi xử lý siêu phân giải"
            // Cleanup orphaned temp files
            try {
                applicationContext.cacheDir.listFiles()?.filter {
                    it.name.startsWith("temp_video_") || it.name.startsWith("temp_upscaled_")
                }?.forEach { it.delete() }
            } catch (_: Throwable) {}
            UpscaleStateManager.updateState(UpscaleState.Error(errorMsg))
            notificationManager.dismiss()
            Result.failure()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatter = DecimalFormat("#,##0.#")
        return "${formatter.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
