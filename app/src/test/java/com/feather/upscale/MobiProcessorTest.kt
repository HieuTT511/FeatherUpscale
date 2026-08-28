package com.feather.upscale

import com.feather.upscale.batch.MobiProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MobiProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `scanImageRecords phan tich dung cac ban ghi anh JPEG va PNG trong file mock Palm MOBI`() {
        val mockMobiFile = tempFolder.newFile("test_comic.mobi")
        val numRecords = 4

        // 1. Tạo mock PDB header (78 bytes) + Record Entry Table (numRecords * 8 bytes)
        val headerSize = 78 + (numRecords * 8)
        val record0Data = "PALMDOC_HEADER_TEXT".toByteArray() // Text record
        val record1Data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10) + ByteArray(100) // JPEG
        val record2Data = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(120) // PNG
        val record3Data = "FLIS_RECORD".toByteArray() // Metadata record

        val offset0 = headerSize.toLong()
        val offset1 = offset0 + record0Data.size
        val offset2 = offset1 + record1Data.size
        val offset3 = offset2 + record2Data.size

        val raf = RandomAccessFile(mockMobiFile, "rw")

        // Ghi 76 bytes tên DB & flags
        val dbHeader = ByteArray(76) { 0 }
        "MyMangaMobi".toByteArray().copyInto(dbHeader, 0)
        raf.write(dbHeader)

        // Ghi numRecords tại offset 76 (2 bytes big-endian)
        raf.writeShort(numRecords)

        // Ghi Record table (8 bytes per record)
        val offsets = listOf(offset0, offset1, offset2, offset3)
        for (off in offsets) {
            raf.writeInt(off.toInt())
            raf.writeInt(0) // attributes + ID
        }

        // Ghi dữ liệu từng record
        raf.write(record0Data)
        raf.write(record1Data)
        raf.write(record2Data)
        raf.write(record3Data)

        // 2. Chạy thuật toán scanImageRecords
        val records = MobiProcessor.scanImageRecords(raf)
        raf.close()

        // 3. Kiểm tra kết quả
        assertEquals("Phải phát hiện chính xác 2 bản ghi ảnh (JPEG & PNG)", 2, records.size)
        assertEquals("Bản ghi ảnh 1 là JPEG", "jpg", records[0].extension)
        assertEquals("Offset bản ghi 1 đúng", offset1, records[0].offset)
        assertEquals("Length bản ghi 1 đúng", record1Data.size, records[0].length)

        assertEquals("Bản ghi ảnh 2 là PNG", "png", records[1].extension)
        assertEquals("Offset bản ghi 2 đúng", offset2, records[1].offset)
        assertEquals("Length bản ghi 2 đúng", record2Data.size, records[1].length)
    }

    @Test
    fun `scanImageRecords tra ve rong khi file qua nho hoac khong chua record nao`() {
        val emptyFile = tempFolder.newFile("empty.mobi")
        val raf = RandomAccessFile(emptyFile, "rw")
        raf.write(ByteArray(30)) // < 78 bytes

        val records = MobiProcessor.scanImageRecords(raf)
        raf.close()

        assertTrue("File rỗng phải trả về danh sách rỗng an toàn", records.isEmpty())
    }
}

