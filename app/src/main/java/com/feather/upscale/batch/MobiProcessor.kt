package com.feather.upscale.batch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.feather.upscale.TileProcessor
import com.feather.upscale.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bộ xử lý tệp truyện định dạng MOBI / PRC (Palm Database Format).
 *
 * Thuật toán & Tối ưu hóa chuẩn đọc Ebook:
 * 1. Phân tích trực tiếp bảng Record Table của Palm Database Header mà không nạp toàn bộ file vào RAM ($O(1)$ RAM).
 * 2. Phát hiện chính xác các bản ghi chứa dữ liệu hình ảnh (JPEG, PNG, GIF, WebP) qua Magic Bytes.
 * 3. Upscale từng trang truyện bằng [TileProcessor] theo chuẩn 4K UHD ([TileProcessor.MAX_COMIC_PAGE_DIMENSION] = 3840px).
 * 4. Đóng gói tập truyện đã upscale vào tệp truyện CBZ siêu phân giải chuẩn quốc tế, không gây crash cho các app đọc truyện khác.
 */
class MobiProcessor(
    private val tileProcessor: TileProcessor,
    private val hapticHelper: HapticHelper? = null,
) {
    data class MobiProgress(
        val currentPage: Int,
        val totalPages: Int,
        val pageName: String,
        val currentTile: Int,
        val totalTiles: Int,
        val currentTileSize: Int,
    )

    data class ImageRecord(
        val recordIndex: Int,
        val offset: Long,
        val length: Int,
        val extension: String,
    )

    companion object {
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val GIF_MAGIC = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte())
        private val RIFF_MAGIC = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())

        /**
         * Quét và trích xuất danh sách offset của tất cả các bản ghi ảnh trong file MOBI / PRC.
         */
        internal fun scanImageRecords(raf: RandomAccessFile): List<ImageRecord> {
            val fileLength = raf.length()
            if (fileLength < 78) return emptyList()

            // Đọc số lượng record tại offset 76 (2 bytes big-endian unsigned short)
            raf.seek(76)
            val numRecords = raf.readUnsignedShort()
            if (numRecords <= 0 || fileLength < 78 + numRecords * 8L) return emptyList()

            val recordOffsets = LongArray(numRecords)
            for (i in 0 until numRecords) {
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                val attrAndId = raf.readInt() // 4 bytes attributes & unique ID
                recordOffsets[i] = offset
            }

            val imageRecords = mutableListOf<ImageRecord>()
            val headerBuffer = ByteArray(12)

            for (i in 0 until numRecords) {
                val startOffset = recordOffsets[i]
                val endOffset = if (i + 1 < numRecords) recordOffsets[i + 1] else fileLength
                val recordLen = (endOffset - startOffset).toInt()

                if (recordLen < 16) continue // Quá nhỏ để chứa 1 ảnh truyện

                raf.seek(startOffset)
                val bytesRead = raf.read(headerBuffer, 0, minOf(headerBuffer.size, recordLen))
                if (bytesRead < 4) continue

                val ext = detectImageExtension(headerBuffer)
                if (ext != null) {
                    imageRecords.add(
                        ImageRecord(
                            recordIndex = i,
                            offset = startOffset,
                            length = recordLen,
                            extension = ext
                        )
                    )
                }
            }

            return imageRecords
        }

        private fun detectImageExtension(header: ByteArray): String? {
            if (header.size >= 3 &&
                header[0] == JPEG_MAGIC[0] &&
                header[1] == JPEG_MAGIC[1] &&
                header[2] == JPEG_MAGIC[2]
            ) {
                return "jpg"
            }
            if (header.size >= 4 &&
                header[0] == PNG_MAGIC[0] &&
                header[1] == PNG_MAGIC[1] &&
                header[2] == PNG_MAGIC[2] &&
                header[3] == PNG_MAGIC[3]
            ) {
                return "png"
            }
            if (header.size >= 4 &&
                header[0] == GIF_MAGIC[0] &&
                header[1] == GIF_MAGIC[1] &&
                header[2] == GIF_MAGIC[2] &&
                header[3] == GIF_MAGIC[3]
            ) {
                return "gif"
            }
            if (header.size >= 12 &&
                header[0] == RIFF_MAGIC[0] &&
                header[1] == RIFF_MAGIC[1] &&
                header[2] == RIFF_MAGIC[2] &&
                header[3] == RIFF_MAGIC[3] &&
                header[8] == 'W'.code.toByte() &&
                header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() &&
                header[11] == 'P'.code.toByte()
            ) {
                return "webp"
            }
            return null
        }
    }

    /**
     * Xử lý toàn bộ tệp truyện MOBI / PRC:
     * - Trích xuất từng bản ghi ảnh
     * - Upscale từng ảnh qua [TileProcessor] với chuẩn 4K UHD
     * - Đóng gói vào tệp đầu ra CBZ siêu phân giải
     */
    suspend fun processMobi(
        inputFile: File,
        outputFile: File,
        onProgress: (MobiProgress) -> Unit,
        onPreviewUpdate: ((Bitmap, Bitmap) -> Unit)? = null,
        isPaused: () -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        val raf = RandomAccessFile(inputFile, "r")
        val imageRecords = try {
            scanImageRecords(raf)
        } catch (e: Exception) {
            raf.close()
            throw IllegalArgumentException("Không thể phân tích cấu trúc Palm MOBI: ${e.message}", e)
        }

        if (imageRecords.isEmpty()) {
            raf.close()
            throw IllegalArgumentException("Không tìm thấy trang ảnh nào trong tệp MOBI / PRC: ${inputFile.name}")
        }

        val totalPages = imageRecords.size
        val tempOutputFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        try {
            ZipOutputStream(FileOutputStream(tempOutputFile)).use { zos ->
                zos.setMethod(ZipOutputStream.DEFLATED)
                zos.setLevel(Deflater.DEFAULT_COMPRESSION)

                for ((pageIdx, record) in imageRecords.withIndex()) {
                    if (isCancelled()) {
                        throw CancellationException("Tiến trình upscale MOBI bị hủy bởi người dùng")
                    }
                    while (isPaused()) {
                        if (isCancelled()) throw CancellationException("Tiến trình upscale MOBI bị hủy khi tạm dừng")
                        delay(100)
                    }

                    val pageNumber = pageIdx + 1
                    val pageName = String.format("page_%04d.jpg", pageNumber)

                    // Đọc trực tiếp byte ảnh từ file mà không giữ các trang khác trong RAM
                    val imageBytes = ByteArray(record.length)
                    raf.seek(record.offset)
                    raf.readFully(imageBytes)

                    val originalBitmap = decodeSampledBitmap(imageBytes, maxDimension = 4096)
                    if (originalBitmap == null) {
                        val entry = ZipEntry(pageName)
                        zos.putNextEntry(entry)
                        zos.write(imageBytes)
                        zos.closeEntry()
                        continue
                    }

                    var origPageCopy: Bitmap? = null
                    try {
                        origPageCopy = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Throwable) {}

                    // Upscale trang truyện bằng TileProcessor (giới hạn 4K an toàn cho app đọc truyện)
                    val upscaledBitmap = tileProcessor.process(
                        bitmap = originalBitmap,
                        maxSafeDimension = TileProcessor.MAX_COMIC_PAGE_DIMENSION,
                        onProgress = { currentTile, totalTiles ->
                            onProgress(
                                MobiProgress(
                                    currentPage = pageNumber,
                                    totalPages = totalPages,
                                    pageName = pageName,
                                    currentTile = currentTile,
                                    totalTiles = totalTiles,
                                    currentTileSize = tileProcessor.tileSize
                                )
                            )
                        },
                        onPreviewUpdate = null,
                        isPaused = isPaused,
                        isCancelled = isCancelled
                    )

                    originalBitmap.recycle()

                    if (origPageCopy != null) {
                        try {
                            val upscaledCopy = upscaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            onPreviewUpdate?.invoke(upscaledCopy, origPageCopy)
                        } catch (_: Throwable) {}
                    }

                    // Ghi trang ảnh đã upscale vào tệp nén CBZ dưới chuẩn JPEG 92
                    val entry = ZipEntry(pageName)
                    zos.putNextEntry(entry)
                    val baos = ByteArrayOutputStream()
                    upscaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                    upscaledBitmap.recycle()

                    val upscaledBytes = baos.toByteArray()
                    zos.write(upscaledBytes)
                    zos.closeEntry()

                    hapticHelper?.vibratePageComplete()
                    System.gc()
                }
                zos.finish()
            }

            if (outputFile.exists()) outputFile.delete()
            tempOutputFile.renameTo(outputFile)
            hapticHelper?.vibrateBatchComplete()

        } catch (e: Throwable) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            throw e
        } finally {
            try {
                raf.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Giải mã an toàn không vượt quá giới hạn heap.
     */
    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }
}
