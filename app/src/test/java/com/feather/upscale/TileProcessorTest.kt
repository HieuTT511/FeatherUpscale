package com.feather.upscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test thuần JVM cho logic Seamless Merging, OOM guard, tỉ lệ 8X và chia tile của TileProcessor.
 */
class TileProcessorTest {

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun tile(x: Int, y: Int, w: Int, h: Int, color: Int) =
        TileProcessor.Tile(x, y, w, h, IntArray(w * h) { color })

    @Test
    fun `stitch single tile khong lam sai pixel`() {
        val red = rgb(255, 0, 0)
        val out = TileProcessor.stitchTiles(listOf(tile(0, 0, 4, 4, red)), 4, 4, scale = 1)
        assertEquals(16, out.size)
        out.forEach { assertEquals(red, it) }
    }

    @Test
    fun `stitch hai tiles co overlap tao seamless normalized blending khong bi dut gtx`() {
        val red = rgb(255, 0, 0)
        val blue = rgb(0, 0, 255)
        // Tile 1: [0..15], Tile 2: [8..23] -> Overlap [8..15], tổng width 24
        val out = TileProcessor.stitchTiles(
            listOf(tile(0, 0, 16, 4, red), tile(8, 0, 16, 4, blue)), 24, 4, scale = 1
        )

        // Vùng độc quyền của Tile 1 (x=0..7): đỏ nguyên bản
        assertEquals(red, out[0])
        assertEquals(red, out[7])

        // Vùng độc quyền của Tile 2 (x=16..23): xanh nguyên bản
        assertEquals(blue, out[16])
        assertEquals(blue, out[23])

        // Vùng overlap [8..15]: chuyển màu mượt mà giữa đỏ và xanh
        val mid = out[12]
        val r = (mid shr 16) and 0xFF
        val b = mid and 0xFF
        assertTrue("Màu đỏ phải còn trong vùng blend: $r", r in 1..254)
        assertTrue("Màu xanh phải xuất hiện trong vùng blend: $b", b in 1..254)
    }

    @Test
    fun `calculatePixelWeight bang 1 o mep anh va tieng ve 0 o mep trong`() {
        // Mép ngoài bên trái
        val wLeft = TileProcessor.calculatePixelWeight(
            tx = 0, ty = 32, tileW = 64, tileH = 64, overlapOut = 16,
            isLeftEdge = true, isTopEdge = false, isRightEdge = false, isBottomEdge = false
        )
        assertEquals(1f, wLeft, 1e-6f)

        // Mép trong giáp tile khác
        val wInterior = TileProcessor.calculatePixelWeight(
            tx = 0, ty = 32, tileW = 64, tileH = 64, overlapOut = 16,
            isLeftEdge = false, isTopEdge = false, isRightEdge = false, isBottomEdge = false
        )
        assertTrue("Mép trong phải có trọng số nhỏ để hòa trộn mượt: $wInterior", wInterior < 0.2f)
    }

    @Test
    fun `TileProcessor 8X tu dong chon tile 128px de bao ve an toan bo nho RAM`() {
        val processor8x = TileProcessor(scale = 8, forcedLowRam = false)
        assertEquals(128, processor8x.tileSize)
        val tiles8x = processor8x.computeTiles(500, 500)
        assertTrue("Tỉ lệ 8x chia tile an toàn 128px", tiles8x.isNotEmpty())
    }

    @Test
    fun `computeTiles bao phu toan bo anh goc khong bo sot pixel`() {
        val processor = TileProcessor(forcedLowRam = false)
        val w = 300
        val h = 400
        val tiles = processor.computeTiles(w, h)

        val coveredMap = Array(h) { BooleanArray(w) }
        for (tile in tiles) {
            for (y in tile.y until tile.y + tile.h) {
                for (x in tile.x until tile.x + tile.w) {
                    coveredMap[y][x] = true
                }
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                assertTrue("Pixel ($x, $y) phải được bao phủ bởi ít nhất 1 tile", coveredMap[y][x])
            }
        }
    }

    @Test
    fun `OOM guard low-ram device tu dong chon tile 128 va co the ha xuong 64`() {
        val lowRamProcessor = TileProcessor(forcedLowRam = true)
        assertEquals(128, lowRamProcessor.tileSize)

        // Giả lập hạ tile size khi phát hiện OOM
        lowRamProcessor.tileSize = 64
        assertEquals(64, lowRamProcessor.tileSize)

        val tiles64 = lowRamProcessor.computeTiles(200, 200)
        assertTrue("Tile size 64px tạo nhiều tiles hơn để tiết kiệm RAM", tiles64.size > 1)
    }

    @Test
    fun `fallback upscaler cua NcnnUpscaler hoat dong dung kich thuoc cho 8x`() {
        val w = 4
        val h = 4
        val scale = 8
        val input = ByteArray(w * h * 4) { it.toByte() }
        val output = NcnnUpscaler.fallbackUpscale(input, w, h, scale)

        assertEquals((w * scale) * (h * scale) * 4, output.size)
    }
}
