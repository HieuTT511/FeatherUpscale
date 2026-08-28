package com.feather.upscale

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bộ xử lý chia mảnh (Tiling Engine) siêu phân giải không đường viền (Seamless Merging).
 *
 * Thuật toán cải tiến đạt chuẩn Seamless Super-Resolution (Quy tắc 7):
 * 1. Overlap Padding: Mỗi tile được mở rộng thêm biên padding P = 16px (input) để triệt tiêu hiện tượng méo biên (boundary distortion) của mạng nơ-ron tích chập (CNN).
 * 2. Normalized Weight Blending (Hòa trộn trọng số chuẩn hóa):
 *    - Sử dụng hàm trọng số hình thang/Raised-Cosine tại các dải chồng lấn.
 *    - Chuẩn hóa bằng tổng trọng số: Output(x,y) = Sum(W_k * C_k) / Sum(W_k).
 *    - Loại bỏ 100% các ô vuông, đường kẻ phân chia hay vết ghép mảnh, cho bức ảnh đầu ra liền mạch và hoàn hảo tuyệt đối.
 * 3. OOM Guard: Tự động điều chỉnh kích thước tile phù hợp với dung lượng RAM máy.
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
        const val OVERLAP = 16 // Overlap in input space (16px input -> 64px output at 4x)
        const val MAX_OUTPUT_DIMENSION = 4096 // 4K UHD chuẩn an toàn tuyệt đối cho Android Canvas / GPU textures
        private const val MIN_FREE_BYTES_PER_TILE = 32L * 1024L * 1024L // ~32MB

        /**
         * Tính trọng số hòa trộn cho 1 pixel bên trong tile tại tọa độ (tx, ty).
         * Trọng số tiệm cận 0 ở mép trong (tiếp giáp tile khác) và bằng 1 ở vùng độc quyền.
         */
        internal fun calculatePixelWeight(
            tx: Int,
            ty: Int,
            tileW: Int,
            tileH: Int,
            overlapOut: Int,
            isLeftEdge: Boolean,
            isTopEdge: Boolean,
            isRightEdge: Boolean,
            isBottomEdge: Boolean,
        ): Float {
            val wx = when {
                isLeftEdge && tx < overlapOut -> 1f
                isRightEdge && tx >= tileW - overlapOut -> 1f
                tx < overlapOut -> (tx + 1f) / (overlapOut + 1f)
                tx >= tileW - overlapOut -> (tileW - tx).toFloat() / (overlapOut + 1f)
                else -> 1f
            }.coerceIn(0.001f, 1f)

            val wy = when {
                isTopEdge && ty < overlapOut -> 1f
                isBottomEdge && ty >= tileH - overlapOut -> 1f
                ty < overlapOut -> (ty + 1f) / (overlapOut + 1f)
                ty >= tileH - overlapOut -> (tileH - ty).toFloat() / (overlapOut + 1f)
                else -> 1f
            }.coerceIn(0.001f, 1f)

            return wx * wy
        }

        /**
         * Blend 1 tile đã upscale vào bộ tích lũy màu và trọng số (Normalized Accumulators).
         */
        internal fun blendTileIntoAccumulators(
            rAcc: FloatArray,
            gAcc: FloatArray,
            bAcc: FloatArray,
            wAcc: FloatArray,
            tile: Tile,
            outW: Int,
            outH: Int,
            overlapOut: Int,
            isLeftEdge: Boolean,
            isTopEdge: Boolean,
            isRightEdge: Boolean,
            isBottomEdge: Boolean,
        ) {
            for (ty in 0 until tile.h) {
                val gy = tile.y + ty
                if (gy >= outH) break
                val tileRowOffset = ty * tile.w
                val globalRowOffset = gy * outW

                for (tx in 0 until tile.w) {
                    val gx = tile.x + tx
                    if (gx >= outW) continue
                    val dstIdx = globalRowOffset + gx

                    val weight = calculatePixelWeight(
                        tx, ty, tile.w, tile.h, overlapOut,
                        isLeftEdge, isTopEdge, isRightEdge, isBottomEdge
                    )

                    val color = tile.pixels[tileRowOffset + tx]
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF

                    rAcc[dstIdx] += r * weight
                    gAcc[dstIdx] += g * weight
                    bAcc[dstIdx] += b * weight
                    wAcc[dstIdx] += weight
                }
            }
        }

        /**
         * Chuẩn hóa bộ tích lũy để tạo mảng pixel cuối cùng liền mạch 100%, không còn đường viền hay ô vuông.
         */
        internal fun finalizePixels(
            rAcc: FloatArray,
            gAcc: FloatArray,
            bAcc: FloatArray,
            wAcc: FloatArray,
            outputPixels: IntArray,
        ) {
            for (i in outputPixels.indices) {
                val totalWeight = wAcc[i]
                if (totalWeight > 0f) {
                    val inv = 1f / totalWeight
                    val r = (rAcc[i] * inv).toInt().coerceIn(0, 255)
                    val g = (gAcc[i] * inv).toInt().coerceIn(0, 255)
                    val b = (bAcc[i] * inv).toInt().coerceIn(0, 255)
                    outputPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        /** Ghép danh sách tiles (đã upscale) — thuần Kotlin để unit-test được. */
        internal fun stitchTiles(tiles: List<Tile>, outW: Int, outH: Int, scale: Int = 4): IntArray {
            val rAcc = FloatArray(outW * outH)
            val gAcc = FloatArray(outW * outH)
            val bAcc = FloatArray(outW * outH)
            val wAcc = FloatArray(outW * outH)
            val overlapOut = OVERLAP * scale

            for (tile in tiles) {
                blendTileIntoAccumulators(
                    rAcc = rAcc,
                    gAcc = gAcc,
                    bAcc = bAcc,
                    wAcc = wAcc,
                    tile = tile,
                    outW = outW,
                    outH = outH,
                    overlapOut = overlapOut,
                    isLeftEdge = tile.x == 0,
                    isTopEdge = tile.y == 0,
                    isRightEdge = tile.x + tile.w >= outW,
                    isBottomEdge = tile.y + tile.h >= outH
                )
            }

            val output = IntArray(outW * outH)
            finalizePixels(rAcc, gAcc, bAcc, wAcc, output)
            return output
        }

        /** Tạo preview bitmap kích thước nhẹ phục vụ hiển thị thời gian thực */
        internal fun createPreviewFromPixels(
            pixels: IntArray,
            srcW: Int,
            srcH: Int,
            targetW: Int,
            targetH: Int
        ): Bitmap {
            val previewPixels = IntArray(targetW * targetH)
            val scaleX = srcW.toFloat() / targetW
            val scaleY = srcH.toFloat() / targetH
            for (y in 0 until targetH) {
                val sy = (y * scaleY).toInt().coerceIn(0, srcH - 1)
                val srcRow = sy * srcW
                val dstRow = y * targetW
                for (x in 0 until targetW) {
                    val sx = (x * scaleX).toInt().coerceIn(0, srcW - 1)
                    previewPixels[dstRow + x] = pixels[srcRow + sx]
                }
            }
            return Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).apply {
                setPixels(previewPixels, 0, targetW, 0, 0, targetW, targetH)
            }
        }
    }

    /** Tính tile specs (tọa độ gốc, không scale) từ W x H với overlap chuẩn. */
    fun computeTiles(w: Int, h: Int, customTileSize: Int = tileSize): List<TileSpec> {
        if (w <= customTileSize && h <= customTileSize) {
            return listOf(TileSpec(0, 0, w, h))
        }

        val step = (customTileSize - OVERLAP).coerceAtLeast(1)
        val xs = generateRange(w, step, customTileSize)
        val ys = generateRange(h, step, customTileSize)
        return ys.flatMap { y ->
            xs.map { x ->
                TileSpec(x, y, minOf(customTileSize, w - x), minOf(customTileSize, h - y))
            }
        }
    }

    private fun generateRange(size: Int, step: Int, customTileSize: Int): List<Int> {
        val positions = mutableListOf<Int>()
        var pos = 0
        while (pos < size) {
            positions += pos
            if (pos + customTileSize >= size) break
            pos += step
        }
        return positions
    }

    /**
     * Xử lý toàn ảnh: extract -> upscale -> seamless normalized blending.
     * Suspend trên Dispatchers.Default.
     */
    suspend fun process(
        bitmap: Bitmap,
        onProgress: ((completedTiles: Int, totalTiles: Int) -> Unit)? = null,
        onPreviewUpdate: ((Bitmap) -> Unit)? = null,
        isPaused: () -> Boolean = { false },
        isCancelled: () -> Boolean = { false },
    ): Bitmap = withContext(Dispatchers.Default) {
        val safeInputBitmap = prepareSafeBitmap(bitmap)
        val shouldRecycleSafe = safeInputBitmap !== bitmap

        var currentTileSize = tileSize
        try {
            while (true) {
                try {
                    return@withContext processWithTileSize(
                        safeInputBitmap, currentTileSize, onProgress, onPreviewUpdate, isPaused, isCancelled
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
        onPreviewUpdate: ((Bitmap) -> Unit)?,
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
        val overlapOut = OVERLAP * scale

        val srcPixels = IntArray(w * h).also { safeBitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val rAcc = FloatArray(outW * outH)
        val gAcc = FloatArray(outW * outH)
        val bAcc = FloatArray(outW * outH)
        val wAcc = FloatArray(outW * outH)

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

            val tile = Tile(spec.x * scale, spec.y * scale, ow, oh, tileColors)
            blendTileIntoAccumulators(
                rAcc = rAcc,
                gAcc = gAcc,
                bAcc = bAcc,
                wAcc = wAcc,
                tile = tile,
                outW = outW,
                outH = outH,
                overlapOut = overlapOut,
                isLeftEdge = spec.x == 0,
                isTopEdge = spec.y == 0,
                isRightEdge = spec.x + spec.w >= w,
                isBottomEdge = spec.y + spec.h >= h
            )

            onProgress?.invoke(index + 1, totalTiles)

            // Cập nhật ảnh Preview thời gian thực (Live Runtime Upscale Preview)
            if (onPreviewUpdate != null && (index % 2 == 0 || index == totalTiles - 1)) {
                try {
                    val previewW = minOf(outW, 1080)
                    val previewH = minOf(outH, 1080)
                    val currentPreviewPixels = IntArray(outW * outH)
                    finalizePixels(rAcc, gAcc, bAcc, wAcc, currentPreviewPixels)
                    val previewBmp = createPreviewFromPixels(currentPreviewPixels, outW, outH, previewW, previewH)
                    onPreviewUpdate.invoke(previewBmp)
                } catch (_: Throwable) {}
            }
        }

        val outputPixels = IntArray(outW * outH)
        finalizePixels(rAcc, gAcc, bAcc, wAcc, outputPixels)

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
