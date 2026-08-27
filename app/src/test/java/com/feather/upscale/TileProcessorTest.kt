package com.feather.upscale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test thuần JVM cho logic stitch/feather, OOM guard và chia tile của TileProcessor.
 */
class TileProcessorTest {

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    private fun tile(x: Int, y: Int, w: Int, h: Int, color: Int) =
        TileProcessor.Tile(x, y, w, h, IntArray(w * h) { color })

    @Test
    fun `stitch single tile khong lam sai pixel`() {
        val red = rgb(255, 0, 0)
        val out = TileProcessor.stitchTiles(listOf(tile(0, 0, 4, 4, red)), 4, 4)
        assertEquals(16, out.size)
        out.forEach { assertEquals(red, it) }
    }

    @Test
    fun `stitch hai tiles co overlap tao linear feather blend muot ma`() {
        val red = rgb(255, 0, 0)
        val blue = rgb(0, 0, 255)
        // Tile 1: [0..15], Tile 2: [8..23] -> Overlap [8..15], tổng width 24
        val out = TileProcessor.stitchTiles(
            listOf(tile(0, 0, 16, 4, red), tile(8, 0, 16, 4, blue)), 24, 4
        )

        // Vùng độc quyền của Tile 1 (x=0..7): đỏ nguyên bản
        assertEquals(red, out[0])
        assertEquals(red, out[7])

        // Vùng độc quyền của Tile 2 (x=16..23): xanh nguyên bản
        assertEquals(blue, out[16])
        assertEquals(blue, out[23])

        // Vùng overlap [8..15]: được blend giữa đỏ và xanh
        val mid = out[12] // x=12 -> offset = (12 - 8) = 4 -> alpha = 4/16 = 0.25
        val r = (mid shr 16) and 0xFF
        val b = mid and 0xFF
        assertTrue("Màu đỏ phải còn trong vùng blend: $r", r in 1..254)
        assertTrue("Màu xanh phải xuất hiện trong vùng blend: $b", b in 1..254)
    }

    @Test
    fun `featherFactor bang 1 khi xa mep, nho hon 1 tai mep`() {
        // Điểm nội địa (xa cả 2 mép hơn OVERLAP=16)
        assertEquals(1f, TileProcessor.featherFactor(32, 0, 63), 1e-6f)
        assertTrue(TileProcessor.featherFactor(0, 0, 15) < 1f)
        assertTrue(TileProcessor.featherFactor(15, 0, 15) < 1f)
        assertEquals(0f, TileProcessor.featherFactor(-5, 0, 15), 1e-6f)
    }

    @Test
    fun `feather tang dan theo overlap`() {
        val f0 = TileProcessor.featherFactor(0, 0, 31)
        val f4 = TileProcessor.featherFactor(4, 0, 31)
        val f8 = TileProcessor.featherFactor(8, 0, 31)
        assertTrue(f0 < f4 && f4 < f8 && f8 <= 1f)
    }

    @Test
    fun `blendArgb t=1 tra ve src, t=0 giu dst`() {
        val dst = rgb(10, 20, 30)
        val src = rgb(200, 210, 220)
        assertEquals(src, TileProcessor.blendArgb(dst, src, 1f))
        assertEquals(dst, TileProcessor.blendArgb(dst, src, 0f))
        val mid = TileProcessor.blendArgb(dst, src, 0.5f)
        assertEquals(105, (mid shr 16) and 0xFF) // (10+200)/2
    }

    @Test
    fun `computeTiles chia dung toa do va kich thuoc cho ca 256px va 128px`() {
        val processor256 = TileProcessor(forcedLowRam = false)
        assertEquals(256, processor256.tileSize)
        val tiles256 = processor256.computeTiles(500, 500)
        // Với w=500, step=256-16=240 -> x=0, 240, 480 (3 bước mỗi chiều -> 9 tiles)
        assertEquals(9, tiles256.size)

        val processor128 = TileProcessor(forcedLowRam = true)
        assertEquals(128, processor128.tileSize)
        assertTrue(processor128.isLowRam)
        val tiles128 = processor128.computeTiles(500, 500)
        // Với w=500, step=128-16=112 -> x=0, 112, 224, 336, 448 (5 bước -> 25 tiles)
        assertEquals(25, tiles128.size)
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
        assertTrue("Tile size 64px tạo nhiều tiles hơn để tiết kiệm RAM", tiles64.size > 4)
    }

    @Test
    fun `fallback upscaler cua NcnnUpscaler hoat dong dung kich thuoc`() {
        val w = 4
        val h = 4
        val scale = 2
        val input = ByteArray(w * h * 4) { it.toByte() }
        val output = NcnnUpscaler.fallbackUpscale(input, w, h, scale)

        assertEquals((w * scale) * (h * scale) * 4, output.size)
    }
}
