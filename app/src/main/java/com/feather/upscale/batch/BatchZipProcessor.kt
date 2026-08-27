package com.feather.upscale.batch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.feather.upscale.TileProcessor
import com.feather.upscale.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Xử lý hàng loạt tập tin truyện tranh định dạng ZIP / CBZ.
 *
 * Tính năng chính:
 * 1. Tự động nhận diện và trích xuất các trang truyện (PNG, JPG, WEBP).
 * 2. Natural Sorting (sắp xếp thứ tự tự nhiên) để các trang (p1, p2, p10) luôn theo đúng mạch đọc truyện.
 * 3. Upscale từng trang bằng [TileProcessor] với OOM Guard cho thiết bị 4GB RAM.
 * 4. Hỗ trợ Checkpoint để tiếp tục (Resume) từ trang đang dở khi bị ngắt.
 * 5. Nén lại thành file ZIP / CBZ đầu ra chất lượng cao.
 */
class BatchZipProcessor(
    private val tileProcessor: TileProcessor,
    private val hapticHelper: HapticHelper? = null,
) {

    data class PageEntry(
        val entryName: String,
        val originalIndex: Int,
        val byteSize: Long,
    )

    data class BatchProgress(
        val currentPage: Int,
        val totalPages: Int,
        val currentTile: Int,
        val totalTiles: Int,
        val currentTileSize: Int,
        val pageName: String,
    )

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /**
         * Natural sort comparator: so sánh tên file có chứa số tự nhiên
         * Ví dụ: page_2.jpg < page_10.jpg (thay vì so sánh chuỗi thông thường page_10 < page_2).
         */
        val naturalSortComparator: Comparator<String> = Comparator { str1, str2 ->
            val s1 = str1.lowercase()
            val s2 = str2.lowercase()
            var i1 = 0
            var i2 = 0
            while (i1 < s1.length && i2 < s2.length) {
                val c1 = s1[i1]
                val c2 = s2[i2]
                if (c1.isDigit() && c2.isDigit()) {
                    var end1 = i1
                    while (end1 < s1.length && s1[end1].isDigit()) end1++
                    var end2 = i2
                    while (end2 < s2.length && s2[end2].isDigit()) end2++

                    val numStr1 = s1.substring(i1, end1)
                    val numStr2 = s2.substring(i2, end2)

                    val val1 = numStr1.toLongOrNull()
                    val val2 = numStr2.toLongOrNull()

                    val cmp = if (val1 != null && val2 != null) {
                        val1.compareTo(val2)
                    } else {
                        numStr1.compareTo(numStr2)
                    }

                    if (cmp != 0) return@Comparator cmp
                    i1 = end1
                    i2 = end2
                } else {
                    if (c1 != c2) return@Comparator c1.compareTo(c2)
                    i1++
                    i2++
                }
            }
            s1.length.compareTo(s2.length)
        }

        /**
         * Liệt kê và sắp xếp các trang ảnh hợp lệ trong file ZIP/CBZ.
         */
        fun listComicPages(zipFile: File): List<PageEntry> {
            val list = mutableListOf<PageEntry>()
            var index = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && isImageFile(name)) {
                        list.add(PageEntry(name, index++, entry.size))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return list.sortedWith { a, b -> naturalSortComparator.compare(a.entryName, b.entryName) }
        }

        fun isImageFile(path: String): Boolean {
            if (path.contains("__MACOSX") || path.startsWith(".")) return false
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS
        }
    }

    /**
     * Xử lý toàn bộ file ZIP / CBZ với hỗ trợ checkpoint, pause, cancel và live progress.
     */
    suspend fun processArchive(
        inputFile: File,
        outputFile: File,
        startFromPageIndex: Int = 0,
        onProgress: ((BatchProgress) -> Unit)? = null,
        isPaused: () -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ): File = withContext(Dispatchers.IO) {
        val pages = listComicPages(inputFile)
        if (pages.isEmpty()) {
            throw IllegalArgumentException("Không tìm thấy trang ảnh nào trong tập tin ${inputFile.name}")
        }

        val totalPages = pages.size
        val tempOutputDir = File(inputFile.parentFile, ".feather_temp_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            for (i in startFromPageIndex until totalPages) {
                if (isCancelled()) throw CancellationException("Đã hủy xử lý batch ZIP")

                val page = pages[i]
                val pageNumber = i + 1

                // 1. Trích xuất trang ảnh từ ZIP
                val imageBytes = extractEntryBytes(inputFile, page.entryName)
                val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IllegalStateException("Không thể giải mã ảnh: ${page.entryName}")

                // 2. Upscale trang ảnh qua TileProcessor
                val upscaledBitmap = tileProcessor.process(
                    bitmap = originalBitmap,
                    onProgress = { currentTile, totalTiles ->
                        onProgress?.invoke(
                            BatchProgress(
                                currentPage = pageNumber,
                                totalPages = totalPages,
                                currentTile = currentTile,
                                totalTiles = totalTiles,
                                currentTileSize = tileProcessor.tileSize,
                                pageName = page.entryName
                            )
                        )
                    },
                    isPaused = isPaused,
                    isCancelled = isCancelled
                )

                originalBitmap.recycle()

                // 3. Lưu ảnh đã upscale vào thư mục tạm
                val ext = page.entryName.substringAfterLast('.', "jpg").lowercase()
                val pageOutFile = File(tempOutputDir, String.format("page_%04d.%s", pageNumber, ext))
                FileOutputStream(pageOutFile).use { fos ->
                    if (ext == "png") {
                        upscaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    } else if (ext == "webp") {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            upscaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, fos)
                        } else {
                            @Suppress("DEPRECATION")
                            upscaledBitmap.compress(Bitmap.CompressFormat.WEBP, 92, fos)
                        }
                    } else {
                        upscaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    }
                }

                upscaledBitmap.recycle()
                System.gc()

                hapticHelper?.vibratePageComplete()
            }

            // 4. Đóng gói toàn bộ các trang đã upscale vào file ZIP / CBZ đầu ra
            packageZipArchive(tempOutputDir, outputFile)
            hapticHelper?.vibrateBatchComplete()

            return@withContext outputFile
        } finally {
            // Dọn dẹp thư mục tạm
            tempOutputDir.deleteRecursively()
        }
    }

    private fun extractEntryBytes(zipFile: File, targetName: String): ByteArray {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == targetName) {
                    return zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw NoSuchElementException("Không tìm thấy entry $targetName trong file $zipFile")
    }

    /**
     * Nén thư mục ảnh thành tập tin ZIP / CBZ hoàn chỉnh.
     */
    internal fun packageZipArchive(sourceDir: File, outputZip: File) {
        val files = sourceDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
            for (file in files) {
                if (file.isFile) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}

