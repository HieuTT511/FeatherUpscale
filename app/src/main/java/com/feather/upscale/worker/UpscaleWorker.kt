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

        const val MODE_SINGLE_IMAGE = "single_image"
        const val MODE_BATCH_ARCHIVE = "batch_archive"
        const val MODE_MOBI_ARCHIVE = "mobi_archive"
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
                useFp16 = useFp16
            )

            val baseName = if (originalName.contains('.')) originalName.substringBeforeLast('.') else originalName

            if (mode == MODE_BATCH_ARCHIVE || mode == MODE_MOBI_ARCHIVE) {
                val inputFile = File(inputPath)
                val isMobi = mode == MODE_MOBI_ARCHIVE ||
                        inputFile.extension.equals("mobi", true) ||
                        inputFile.extension.equals("prc", true)

                val tempOutputFile = File(applicationContext.cacheDir, "temp_batch_${System.currentTimeMillis()}.cbz")

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
                            statusMessage = "Đang phân tích cấu trúc truyện MOBI / PRC..."
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
                                    statusMessage = "Trang ${progress.currentPage}/${progress.totalPages} (MOBI)"
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
                        onPreviewUpdate = { previewBitmap ->
                            UpscaleStateManager.updateRuntimePreview(previewBitmap)
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
                        onPreviewUpdate = { previewBitmap ->
                            UpscaleStateManager.updateRuntimePreview(previewBitmap)
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
                // Single Image Mode
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
                        statusMessage = "Bắt đầu upscale AI ${scale}X..."
                    )
                )

                val upscaledBitmap = tileProcessor.process(
                    bitmap = inputBitmap,
                    onProgress = { currentTile, totalTiles ->
                        val progressFraction = if (totalTiles > 0) currentTile.toFloat() / totalTiles else 0f
                        UpscaleStateManager.updateState(
                            UpscaleState.Processing(
                                currentPage = 1,
                                totalPages = 1,
                                completedTiles = currentTile,
                                totalTiles = totalTiles,
                                currentTileSize = tileProcessor.tileSize,
                                isLowRam = tileProcessor.isLowRam,
                                progressFraction = progressFraction,
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

        } catch (e: OutOfMemoryError) {
            UpscaleStateManager.updateState(
                UpscaleState.Error(
                    message = "Tràn bộ nhớ RAM khi upscale $scale X. Vui lòng bật chế độ 'OOM Guard' để bảo vệ máy.",
                    isOom = true
                )
            )
            Result.failure()
        } catch (e: Throwable) {
            val isCancelled = UpscaleStateManager.isCancelled.value
            if (!isCancelled) {
                val isOom = e.message?.contains("Out of memory", true) == true
                UpscaleStateManager.updateState(
                    UpscaleState.Error(
                        message = e.localizedMessage ?: e.message ?: "Lỗi không xác định khi upscale",
                        isOom = isOom
                    )
                )
            }
            Result.failure()
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        return "${format.format(sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
