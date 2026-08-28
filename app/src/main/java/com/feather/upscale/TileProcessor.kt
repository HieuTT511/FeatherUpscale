package com.feather.upscale

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.BitSet
import kotlin.coroutines.cancellation.CancellationException

/**
 * Chia Bitmap thành tiles (mặc định 256px, overlap 16px), upscale từng tile,
 * rồi ghép lại với linear feather blending tại vùng overlap.
 *
 * OOM guard cho máy 4GB RAM & file lớn (lên tới 1GB):
 * - Giới hạn kích thước output tối đa an toàn (MAX_OUTPUT_DIMENSION = 4096px / 4K UHD) để tránh mảng IntArray 1.5GB tràn heap.
 * - Hỗ trợ và tự động chuyển đổi Hardware Bitmap sang Software Bitmap để tránh crash `getPixels()`.
 * - Single-tile memory footprint: blend trực tiếp vào master buffer thay vì cache toàn bộ tiles.
 * - Check available memory trước mỗi tile.
 * - OutOfMemoryError -> tự retry với tile nhỏ hơn (128 -> 64).
 * - Hỗ trợ Pause / Resume / Cancel linh hoạt.
 */
class TileProcessor(
    private val context: Context? = null,
    val scale: Int = 4,
    private val tileUpscaler: TileUpscaler = NcnnTileUpscalerAdapter(),
    forcedLowRam: Boolean? = null,
    val useFp16: Boolean = true,
) {
    interface TileUpscaler {
        /** bytes RGBA của tile đầu vào -> RGBA đã upscale; null nếu lỗi. */
        fun upscale(pixels: ByteArray, w: Int, h: Int, scale: Int, useFp16: Boolean = true): ByteArray?
    }

    /** Adapter gọi [NcnnUpscaler] thật. */
    class NcnnTileUpscalerAdapter : TileUpscaler {
        override fun upscale(pixels: ByteArray, w: Int, h: Int, scale: Int, useFp16: Boolean): ByteArray? =
            NcnnUpscaler.upscaleTile(pixels, w, h, scale, useFp16)
    }

    val isLowRam: Boolean = forcedLowRam ?: isLowRamDevice()

    var tileSize: Int = if (isLowRam) LOW_RAM_TILE_SIZE else DEFAULT_TILE_SIZE
        internal set

    companion object {
        const val DEFAULT_TILE_SIZE = 256
        const val LOW_RAM_TILE_SIZE = 128
        const val MIN_TILE_SIZE = 64
        const val OVERLAP = 16
        const val MAX_OUTPUT_DIMENSION = 4096 // 4K UHD chuẩn an toàn tuyệt đối cho Android Canvas / GPU textures
        private const val MIN_FREE_BYTES_PER_TILE = 32L * 1024L * 1024L // ~32MB

        /**
         * Blend 1 tile đã upscale vào mảng output tổng thể.
         * Giúp tiết kiệm RAM: không cần giữ tất cả các tile trong bộ nhớ cùng lúc.
         */
        internal fun blendTileIntoOutput(
            output: IntArray,
            covered: BitSet,
            tile: Tile,
            outW: Int,
            outH: Int,
        ) {
            for (ty in 0 until tile.h) {
                val gy = tile.y + ty
                if (gy >= outH) break
                for (tx in 0 until tile.w) {
                    val gx = tile.x + tx
                    if (gx >= outW) continue
                    val srcColor = tile.pixels[ty * tile.w + tx]
                    val dstIdx = gy * outW + gx

                    if (!covered.get(dstIdx)) {
                        output[dstIdx] = srcColor // first writer: ghi nguyên pixel
                        covered.set(dstIdx)
                        continue
                    }

                    // Feather theo vị trí trong overlap band
                    val fx = featherFactor(
                        gx, tile.x, tile.x + tile.w - 1,
                        skipLeftEdge = tile.x == 0,
                        skipRightEdge = tile.x + tile.w >= outW
                    )
                    val fy = featherFactor(
                        gy, tile.y, tile.y + tile.h - 1,
                        skipLeftEdge = tile.y == 0,
                        skipRightEdge = tile.y + tile.h >= outH
                    )
                    val alpha = minOf(fx, fy)

                    if (alpha >= 1f) {
                        output[dstIdx] = srcColor
                        continue
                    }
                    if (alpha <= 0f) {
                        continue
                    }
                    output[dstIdx] = blendArgb(output[dstIdx], srcColor, alpha)
                }
            }
        }

        /** Ghép danh sách tiles (đã upscale) — thuần Kotlin (không android.*) để unit-test được. */
        internal fun stitchTiles(tiles: List<Tile>, outW: Int, outH: Int): IntArray {
            val output = IntArray(outW * outH)
            val covered = BitSet(outW * outH)
            for (tile in tiles) {
                blendTileIntoOutput(output, covered, tile, outW, outH)
            }
            return output
        }

        /** Linear blend ARGB int thuần Kotlin (bitwise, không android.graphics.Color). */
        internal fun blendArgb(dst: Int, src: Int, t: Float): Int {
            fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF)
            fun mix(a: Int, b: Int) = (a + Math.round((b - a) * t)).coerceIn(0, 255)
            return (mix(ch(dst, 16), ch(src, 16)) shl 16) or
                    (mix(ch(dst, 8), ch(src, 8)) shl 8) or
                    mix(ch(dst, 0), ch(src, 0))
        }

        /** Linear feather qua OVERLAP px từ mỗi mép; 1 = lấy nguyên pixel nguồn.
         *  skip*Edge=true bỏ feather ở mép đó (mép trùng viền ảnh). */
        internal fun featherFactor(
            globalPos: Int, start: Int, end: Int,
            skipLeftEdge: Boolean = false, skipRightEdge: Boolean = false,
        ): Float {
            val offsetFromStart = globalPos - start
            val offsetFromEnd = end - globalPos
            val fStart = if (skipLeftEdge) 1f else minOf(1f, offsetFromStart / OVERLAP.toFloat())
            val fEnd = if (skipRightEdge) 1f else minOf(1f, (offsetFromEnd + 1) / OVERLAP.toFloat())
            return fStart.coerceIn(0f, 1f).coerceAtMost(fEnd.coerceIn(0f, 1f))
        }
    }

    /** Tính tile specs (tọa độ gốc, không scale) từ W x H. */
    fun computeTiles(w: Int, h: Int, customTileSize: Int = tileSize): List<TileSpec> {
        val step = (customTileSize - OVERLAP).coerceAtLeast(1)
        val xs = generateRange(w, step)
        val ys = generateRange(h, step)
        return ys.flatMap { y ->
            xs.map { x ->
                TileSpec(x, y, minOf(customTileSize, w - x), minOf(customTileSize, h - y))
            }
        }
    }

    private fun generateRange(size: Int, step: Int): List<Int> {
        val positions = mutableListOf<Int>()
        var pos = 0
        while (pos < size) {
            positions += pos
            pos += step
        }
        return positions
    }

    /**
     * Xử lý toàn ảnh: extract -> upscale -> blend trực tiếp. Suspend trên Dispatchers.Default.
     * Tự động shrink tile size và retry nếu gặp OutOfMemoryError.
     */
    suspend fun process(
        bitmap: Bitmap,
        onProgress: ((completedTiles: Int, totalTiles: Int) -> Unit)? = null,
        isPaused: () -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ): Bitmap = withContext(Dispatchers.Default) {
        // Tối ưu kích thước đầu vào nếu kích thước đầu ra vượt quá 4K an toàn
        val safeInputBitmap = prepareSafeBitmap(bitmap)
        val shouldRecycleSafe = safeInputBitmap !== bitmap

        var currentTileSize = tileSize
        try {
            while (true) {
                try {
                    return@withContext processWithTileSize(
                        safeInputBitmap, currentTileSize, onProgress, isPaused, isCancelled
                    )
                } catch (e: OutOfMemoryError) {
                    if (currentTileSize > MIN_TILE_SIZE) {
                        currentTileSize /= 2
                        tileSize = currentTileSize
                        System.gc()
                    } else {
                        throw IllegalStateException("OOM khi upscale ảnh dù đã hạ tile size xuống $MIN_TILE_SIZE px", e)
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        } finally {
            if (shouldRecycleSafe) {
                safeInputBitmap.recycle()
            }
        }
    }

    /**
     * Thu nhỏ nhẹ ảnh đầu vào nếu kích thước sau upscale vượt quá 4K (4096px)
     * và chuyển đổi Hardware Bitmap sang Software Bitmap để tránh crash getPixels.
     */
    private fun prepareSafeBitmap(input: Bitmap): Bitmap {
        val softwareBitmap = if (input.config == Bitmap.Config.HARDWARE || (!input.isMutable && input.config != Bitmap.Config.ARGB_8888)) {
            input.copy(Bitmap.Config.ARGB_8888, false) ?: input
        } else {
            input
        }

        val targetOutW = softwareBitmap.width * scale
        val targetOutH = softwareBitmap.height * scale
        if (targetOutW <= MAX_OUTPUT_DIMENSION && targetOutH <= MAX_OUTPUT_DIMENSION) {
            return softwareBitmap
        }

        val scaleRatio = minOf(
            MAX_OUTPUT_DIMENSION.toFloat() / targetOutW,
            MAX_OUTPUT_DIMENSION.toFloat() / targetOutH
        )

        val newW = (softwareBitmap.width * scaleRatio).toInt().coerceAtLeast(64)
        val newH = (softwareBitmap.height * scaleRatio).toInt().coerceAtLeast(64)

        return Bitmap.createScaledBitmap(softwareBitmap, newW, newH, true)
    }

    private suspend fun processWithTileSize(
        bitmap: Bitmap,
        currentTileSize: Int,
        onProgress: ((completedTiles: Int, totalTiles: Int) -> Unit)?,
        isPaused: () -> Boolean,
        isCancelled: () -> Boolean,
    ): Bitmap {
        val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }

        val w = safeBitmap.width
        val h = safeBitmap.height
        val outW = w * scale
        val outH = h * scale

        val srcPixels = IntArray(w * h).also { safeBitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val outputPixels = IntArray(outW * outH)
        val covered = BitSet(outW * outH)

        val tiles = computeTiles(w, h, currentTileSize)
        val totalTiles = tiles.size

        for ((index, spec) in tiles.withIndex()) {
            if (isCancelled()) {
                throw CancellationException("Upscale bị hủy bởi người dùng")
            }
            while (isPaused()) {
                if (isCancelled()) throw CancellationException("Upscale bị hủy trong khi tạm dừng")
                delay(100)
            }

            ensureMemoryAvailable(currentTileSize)
            val tileW = spec.w
            val tileH = spec.h
            val srcRGBA = ByteArray(tileW * tileH * 4)

            // ARGB int -> RGBA byte[]
            for (i in 0 until tileW * tileH) {
                val c = srcPixels[(spec.y + i / tileW) * w + spec.x + i % tileW]
                srcRGBA[i * 4] = Color.red(c).toByte()
                srcRGBA[i * 4 + 1] = Color.green(c).toByte()
                srcRGBA[i * 4 + 2] = Color.blue(c).toByte()
                srcRGBA[i * 4 + 3] = Color.alpha(c).toByte()
            }

            val outRGBA = tileUpscaler.upscale(srcRGBA, tileW, tileH, scale, useFp16)
                ?: throw OutOfMemoryError("Upscaler trả về null cho tile ${spec.x},${spec.y}")

            // RGBA byte[] -> IntArray màu tile
            val ow = tileW * scale
            val oh = tileH * scale
            val tileColors = IntArray(ow * oh)
            for (i in tileColors.indices) {
                val base = i * 4
                tileColors[i] = Color.rgb(
                    outRGBA[base].toInt() and 0xFF,
                    outRGBA[base + 1].toInt() and 0xFF,
                    outRGBA[base + 2].toInt() and 0xFF
                )
            }

            // Blend trực tiếp vào output master buffer
            val tile = Tile(spec.x * scale, spec.y * scale, ow, oh, tileColors)
            blendTileIntoOutput(outputPixels, covered, tile, outW, outH)

            onProgress?.invoke(index + 1, totalTiles)
        }

        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, outW, 0, 0, outW, outH)
        }.also {
            outputPixels.fill(0)
            System.gc()
        }
    }

    private fun isLowRamDevice(): Boolean {
        if (context == null) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice ?: false
    }

    private fun ensureMemoryAvailable(checkTileSize: Int) {
        if (context == null) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val needed = (checkTileSize * checkTileSize * scale * scale * 4L).coerceAtLeast(MIN_FREE_BYTES_PER_TILE)
        if (memInfo.availMem - memInfo.threshold < needed) {
            System.gc()
            val again = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            if (again.availMem - again.threshold < needed / 2) {
                throw OutOfMemoryError("Bộ nhớ không đủ: ${again.availMem} bytes trống, cần $needed bytes")
            }
        }
    }

    data class TileSpec(val x: Int, val y: Int, val w: Int, val h: Int)

    /** Tile đã upscale trong hệ toạ độ output. */
    internal data class Tile(val x: Int, val y: Int, val w: Int, val h: Int, val pixels: IntArray) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = x xor y xor w xor h
    }
}
