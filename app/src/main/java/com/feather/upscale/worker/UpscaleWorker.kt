package com.feather.upscale.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.feather.upscale.TileProcessor
import com.feather.upscale.batch.BatchZipProcessor
import com.feather.upscale.notification.UpscaleNotificationManager
import com.feather.upscale.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import kotlin.coroutines.cancellation.CancellationException

/**
 * WorkManager CoroutineWorker chạy quá trình Upscale nền (Single Image / Batch ZIP).
 *
 * - Hỗ trợ Live Preview cập nhật ảnh thời gian thực khi tile đang render.
 * - Lưu file mới rõ ràng (không đè file gốc) vào thư mục Pictures/UpScale hoặc Downloads/UpScale.
 * - Hỗ trợ Foreground Service an toàn với notification thanh tiến độ.
 * - Tương tác hai chiều với [UpscaleStateManager] cho phép Pause / Resume / Cancel.
 */
class UpscaleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODE = "key_mode"
        const val MODE_SINGLE_IMAGE = "mode_single_image"
        const val MODE_BATCH_ARCHIVE = "mode_batch_archive"

        const val KEY_INPUT_PATH = "key_input_path"
        const val KEY_OUTPUT_PATH = "key_output_path"
        const val KEY_SCALE = "key_scale"
        const val KEY_USE_FP16 = "key_use_fp16"
        const val KEY_FORCE_LOW_RAM = "key_force_low_ram"

        const val KEY_PROGRESS_PERCENT = "key_progress_percent"
        const val KEY_RESULT_PATH = "key_result_path"
    }

    private val notificationManager = UpscaleNotificationManager(applicationContext)
    private val hapticHelper = HapticHelper(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mode = inputData.getString(KEY_MODE) ?: MODE_SINGLE_IMAGE
        val inputPath = inputData.getString(KEY_INPUT_PATH)
            ?: return@withContext Result.failure(workDataOf("error" to "Thiếu đường dẫn input"))
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
                    tileSize = if (forceLowRam) 128 else 256
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

            if (mode == MODE_BATCH_ARCHIVE) {
                // Batch ZIP / CBZ mode
                val inputFile = File(inputPath)
                val baseDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(applicationContext.filesDir, "downloads")
                val targetDir = File(baseDir, "UpScale").apply { mkdirs() }
                val outputFile = File(targetDir, "${inputFile.nameWithoutExtension}_upscaled_${scale}x.cbz")

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
                    outputFile = outputFile,
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

                        setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to (percent * 100).toInt()))
                    },
                    isPaused = { isStopped || UpscaleStateManager.isPaused.value },
                    isCancelled = { isStopped || UpscaleStateManager.isCancelled.value }
                )

                val duration = System.currentTimeMillis() - startTime
                val fileSizeFormatted = formatFileSize(outputFile.length())
                UpscaleStateManager.updateState(
                    UpscaleState.Completed(
                        totalPages = 1,
                        totalDurationMs = duration,
                        outputPath = outputFile.absolutePath,
                        outputFileName = outputFile.name,
                        outputResolution = "Batch Comic Book (${scale}X)",
                        outputFileSize = fileSizeFormatted,
                        isNewFile = true
                    )
                )

                return@withContext Result.success(workDataOf(KEY_RESULT_PATH to outputFile.absolutePath))

            } else {
                // Single Image Mode
                val inputFile = File(inputPath)
                val originalBitmap = decodeSafeBitmapFromFile(inputPath)
                    ?: return@withContext Result.failure(workDataOf("error" to "Không giải mã được ảnh $inputPath"))

                val baseDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: File(applicationContext.filesDir, "pictures")
                val targetDir = File(baseDir, "UpScale").apply { mkdirs() }
                val outputFile = File(targetDir, "${inputFile.nameWithoutExtension}_upscaled_${scale}x.png")

                UpscaleStateManager.updateState(
                    UpscaleState.Processing(
                        currentPage = 1,
                        totalPages = 1,
                        completedTiles = 0,
                        totalTiles = 1,
                        currentTileSize = tileProcessor.tileSize,
                        isLowRam = tileProcessor.isLowRam,
                        statusMessage = "Đang chia tile..."
                    )
                )

                val upscaled = tileProcessor.process(
                    bitmap = originalBitmap,
                    onProgress = { currentTile, totalTiles ->
                        val fraction = if (totalTiles > 0) currentTile.toFloat() / totalTiles else 0f
                        UpscaleStateManager.updateState(
                            UpscaleState.Processing(
                                currentPage = 1,
                                totalPages = 1,
                                completedTiles = currentTile,
                                totalTiles = totalTiles,
                                currentTileSize = tileProcessor.tileSize,
                                isLowRam = tileProcessor.isLowRam,
                                progressFraction = fraction,
                                statusMessage = "Tile $currentTile/$totalTiles (${(fraction * 100).toInt()}%)"
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
                        setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to (fraction * 100).toInt()))
                    },
                    onPreviewUpdate = { previewBmp ->
                        // Cập nhật trực tiếp ảnh Preview thời gian thực vào Slider!
                        UpscaleStateManager.updateRuntimePreview(previewBmp)
                    },
                    isPaused = { isStopped || UpscaleStateManager.isPaused.value },
                    isCancelled = { isStopped || UpscaleStateManager.isCancelled.value }
                )

                val outW = upscaled.width
                val outH = upscaled.height

                originalBitmap.recycle()

                // Lưu ảnh đầu ra vào file mới
                FileOutputStream(outputFile).use { fos ->
                    upscaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                upscaled.recycle()

                hapticHelper.vibratePageComplete()

                val duration = System.currentTimeMillis() - startTime
                val fileSizeFormatted = formatFileSize(outputFile.length())
                val resolutionStr = "${outW}x${outH}" + if (outW >= 3840 || outH >= 3840) " (4K UHD)" else ""

                UpscaleStateManager.updateState(
                    UpscaleState.Completed(
                        totalPages = 1,
                        totalDurationMs = duration,
                        outputPath = outputFile.absolutePath,
                        outputFileName = outputFile.name,
                        outputResolution = resolutionStr,
                        outputFileSize = fileSizeFormatted,
                        isNewFile = true
                    )
                )

                return@withContext Result.success(workDataOf(KEY_RESULT_PATH to outputFile.absolutePath))
            }

        } catch (e: CancellationException) {
            UpscaleStateManager.updateState(UpscaleState.Cancelled)
            notificationManager.dismiss()
            return@withContext Result.failure(workDataOf("error" to "Quá trình bị hủy"))
        } catch (e: OutOfMemoryError) {
            hapticHelper.vibrateError()
            UpscaleStateManager.updateState(UpscaleState.Error("Thiếu bộ nhớ RAM (OOM). Hãy bật chế độ Low-RAM 128px.", isOom = true))
            notificationManager.dismiss()
            return@withContext Result.failure(workDataOf("error" to "OutOfMemoryError: ${e.message}"))
        } catch (e: Throwable) {
            hapticHelper.vibrateError()
            UpscaleStateManager.updateState(UpscaleState.Error("Lỗi: ${e.localizedMessage ?: e.message}"))
            notificationManager.dismiss()
            return@withContext Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        } finally {
            notificationManager.dismiss()
        }
    }

    private fun decodeSafeBitmapFromFile(path: String, maxDimension: Int = 4096): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
