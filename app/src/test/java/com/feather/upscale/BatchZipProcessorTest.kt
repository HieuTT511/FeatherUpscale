package com.feather.upscale

import com.feather.upscale.batch.BatchZipProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BatchZipProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `naturalSortComparator sap xep dung thu tu so tu nhien trong truyen tranh`() {
        val inputList = listOf(
            "page_10.jpg",
            "page_1.jpg",
            "page_2.jpg",
            "page_20.jpg",
            "page_3.jpg",
            "cover.jpg",
            "page_100.jpg"
        )

        val sorted = inputList.sortedWith(BatchZipProcessor.naturalSortComparator)

        val expected = listOf(
            "cover.jpg",
            "page_1.jpg",
            "page_2.jpg",
            "page_3.jpg",
            "page_10.jpg",
            "page_20.jpg",
            "page_100.jpg"
        )

        assertEquals(expected, sorted)
    }

    @Test
    fun `naturalSortComparator ho tro cac dinh dang chuong va trang phuc tap`() {
        val inputList = listOf(
            "Chapter_2_Page_10.png",
            "Chapter_1_Page_1.png",
            "Chapter_1_Page_2.png",
            "Chapter_1_Page_10.png",
            "Chapter_2_Page_1.png"
        )

        val sorted = inputList.sortedWith(BatchZipProcessor.naturalSortComparator)

        val expected = listOf(
            "Chapter_1_Page_1.png",
            "Chapter_1_Page_2.png",
            "Chapter_1_Page_10.png",
            "Chapter_2_Page_1.png",
            "Chapter_2_Page_10.png"
        )

        assertEquals(expected, sorted)
    }

    @Test
    fun `isImageFile nhan dien dung anh hop le va loai bo file rac`() {
        assertTrue(BatchZipProcessor.isImageFile("manga/chapter1/001.png"))
        assertTrue(BatchZipProcessor.isImageFile("page_01.JPG"))
        assertTrue(BatchZipProcessor.isImageFile("p02.webp"))
        assertTrue(BatchZipProcessor.isImageFile("cover.jpeg"))
        assertTrue(BatchZipProcessor.isImageFile("scan.bmp"))

        // File rác / không phải ảnh
        assertFalse(BatchZipProcessor.isImageFile("__MACOSX/chapter1/._001.png"))
        assertFalse(BatchZipProcessor.isImageFile(".DS_Store"))
        assertFalse(BatchZipProcessor.isImageFile("info.txt"))
        assertFalse(BatchZipProcessor.isImageFile("comic.xml"))
        assertFalse(BatchZipProcessor.isImageFile("folder/"))
    }

    @Test
    fun `listComicPages liet ke dung va sap xep dung tu tap tin zip that`() {
        val zipFile = tempFolder.newFile("test_comic.cbz")

        // Tạo file ZIP giả lập 4 trang truyện (thứ tự cố tình đảo lộn)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val entries = listOf(
                "ch01_10.jpg" to "content10",
                "ch01_1.jpg" to "content1",
                "ch01_2.jpg" to "content2",
                "readme.txt" to "metadata"
            )
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }

        val pages = BatchZipProcessor.listComicPages(zipFile)

        assertEquals(3, pages.size) // Đã loại bỏ readme.txt
        assertEquals("ch01_1.jpg", pages[0].entryName)
        assertEquals("ch01_2.jpg", pages[1].entryName)
        assertEquals("ch01_10.jpg", pages[2].entryName)
    }

    @Test
    fun `packageZipArchive dong goi thu muc thanh tap tin zip hop le`() {
        val srcDir = tempFolder.newFolder("output_pages")
        val page1 = File(srcDir, "page_0001.png").apply { writeText("dummy image data 1") }
        val page2 = File(srcDir, "page_0002.png").apply { writeText("dummy image data 2") }

        val outZip = tempFolder.newFile("final_upscaled.cbz")

        val dummyProcessor = BatchZipProcessor(TileProcessor(forcedLowRam = true))
        dummyProcessor.packageZipArchive(srcDir, outZip)

        assertTrue(outZip.exists())
        assertTrue(outZip.length() > 0)

        // Đọc lại để kiểm tra nội dung
        val extractedPages = BatchZipProcessor.listComicPages(outZip)
        assertEquals(2, extractedPages.size)
        assertEquals("page_0001.png", extractedPages[0].entryName)
        assertEquals("page_0002.png", extractedPages[1].entryName)
    }
}

