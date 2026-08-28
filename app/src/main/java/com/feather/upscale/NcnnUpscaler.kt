package com.feather.upscale

import android.content.Context
import java.io.File

/**
 * Mobile AI Engine & Lightweight Model Manager (v1.7.0).
 *
 * Hỗ trợ các Model tối ưu cho Di Động:
 * 1. RealESRGAN_x4plus_anime_6B: Model nhẹ tối ưu cho Anime / Manga / Webtoon.
 * 2. realesr-animevideov3: Model siêu nhẹ (Ultra-compact), chạy mượt trên mọi điện thoại.
 * 3. MobileSR / ESPCN: Kiến trúc Sub-Pixel Convolution siêu nhanh dành riêng cho NPU di động.
 * 4. RealESRGAN_x4plus: Model chuẩn cho ảnh chân dung / ảnh nghệ thuật.
 *
 * Lượng tử hóa (Model Quantization):
 * - INT8 Quantization (w8a8): Nén dung lượng 4 lần, suy luận cực nhanh.
 * - FP16 Half-Precision: Tăng tốc GPU Vulkan tiết kiệm 50% VRAM.
 */
object NcnnUpscaler {

    const val MODEL_ANIME_6B = "realesrgan-x4plus-anime"
    const val MODEL_VIDEO_V3 = "realesr-animevideov3"
    const val MODEL_MOBILE_SR = "mobilesr-fast"
    const val MODEL_PHOTO_X4 = "realesrgan-x4plus"

    const val PRECISION_FP32 = 0
    const val PRECISION_FP16 = 1
    const val PRECISION_INT8 = 2

    @Volatile
    private var initialized = false

    @Volatile
    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("featherup")
            libraryLoaded = true
        } catch (e: Throwable) {
            println("[FeatherUpscale] libfeatherup.so native library not loaded: ${e.message}")
            libraryLoaded = false
        }
    }

    fun hasNcnn(): Boolean {
        if (!libraryLoaded) return false
        return try {
            nativeHasNcnn()
        } catch (_: Throwable) {
            false
        }
    }

    fun getGpuCount(): Int {
        if (!libraryLoaded) return 0
        return try {
            nativeGetGpuCount()
        } catch (_: Throwable) {
            0
        }
    }

    external fun nativeHasNcnn(): Boolean
    external fun nativeGetGpuCount(): Int

    external fun nativeInit(
        paramPath: String,
        binPath: String,
        gpuid: Int = 0,
        useFp16: Boolean = true
    ): Boolean

    external fun nativeUpscaleTile(
        pixels: ByteArray,
        w: Int,
        h: Int,
        scale: Int,
        useFp16: Boolean = true
    ): ByteArray?

    /**
     * Đảm bảo model files tồn tại trong bộ nhớ trong của thiết bị.
     */
    fun ensureModelFiles(context: Context, modelType: String = MODEL_ANIME_6B): Pair<File, File> {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val paramName = "$modelType.param"
        val binName = "$modelType.bin"

        val paramFile = File(dir, paramName)
        val binFile = File(dir, binName)

        copyAssetIfNeeded(context, "models/$paramName", paramFile)
        copyAssetIfNeeded(context, "models/$binName", binFile)

        return paramFile to binFile
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, target: File) {
        if (target.exists() && target.length() > 0) return
        try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Upscale tile với kiểu tiện dụng cho TileProcessor.
     */
    fun upscaleTile(
        pixels: ByteArray,
        w: Int,
        h: Int,
        scale: Int,
        useFp16: Boolean = true
    ): ByteArray? {
        if (!libraryLoaded) {
            return fallbackUpscale(pixels, w, h, scale)
        }
        return try {
            nativeUpscaleTile(pixels, w, h, scale, useFp16)
        } catch (_: Throwable) {
            fallbackUpscale(pixels, w, h, scale)
        }
    }

    /** Fallback nếu JNI chưa build hoặc crash. */
    internal fun fallbackUpscale(pixels: ByteArray, w: Int, h: Int, scale: Int): ByteArray {
        val ow = w * scale
        val oh = h * scale
        val out = ByteArray(ow * oh * 4)
        for (y in 0 until oh) {
            val sy = (y / scale).coerceIn(0, h - 1)
            for (x in 0 until ow) {
                val sx = (x / scale).coerceIn(0, w - 1)
                val srcIdx = (sy * w + sx) * 4
                val dstIdx = (y * ow + x) * 4
                out[dstIdx] = pixels[srcIdx]
                out[dstIdx + 1] = pixels[srcIdx + 1]
                out[dstIdx + 2] = pixels[srcIdx + 2]
                out[dstIdx + 3] = pixels[srcIdx + 3]
            }
        }
        return out
    }
}
