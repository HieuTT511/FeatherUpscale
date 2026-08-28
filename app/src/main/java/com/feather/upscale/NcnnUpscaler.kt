package com.feather.upscale

import android.content.Context
import java.io.File

/**
 * Wrapper JNI cho NCNN Real-ESRGAN (https://github.com/xinntao/Real-ESRGAN).
 *
 * - Hỗ trợ các model:
 *   + RealESRGAN_x4plus_anime_6B (Tối ưu chuyên dụng cho Manga / Anime / Manhwa)
 *   + RealESRGAN_x4plus (Ảnh chụp chân dung, phong cảnh nghệ thuật)
 *   + realesr-animevideov3 (Siêu tốc độ)
 * - Tăng tốc GPU Vulkan & FP16 Half-Precision (giảm 50% VRAM GPU).
 */
object NcnnUpscaler {

    const val MODEL_ANIME_PARAM_ASSET = "models/realesrgan-x4plus-anime.param"
    const val MODEL_ANIME_BIN_ASSET = "models/realesrgan-x4plus-anime.bin"
    const val MODEL_PHOTO_PARAM_ASSET = "models/realesrgan-x4plus.param"
    const val MODEL_PHOTO_BIN_ASSET = "models/realesrgan-x4plus.bin"

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

    /** true nếu native được build với ncnn (FEATHER_HAS_NCNN). */
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

    /**
     * Upscale một tile RGBA bytes qua JNI.
     * Input: w*h*4 bytes; output: (w*scale)*(h*scale)*4.
     */
    external fun nativeUpscaleTile(
        pixels: ByteArray,
        w: Int,
        h: Int,
        scale: Int,
        useFp16: Boolean = true
    ): ByteArray?

    /**
     * Đảm bảo model files tồn tại trong bộ nhớ trong.
     */
    fun ensureModelFiles(context: Context, isAnime: Boolean = true): Pair<File, File> {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val paramName = if (isAnime) "realesrgan-x4plus-anime.param" else "realesrgan-x4plus.param"
        val binName = if (isAnime) "realesrgan-x4plus-anime.bin" else "realesrgan-x4plus.bin"

        val paramAsset = if (isAnime) MODEL_ANIME_PARAM_ASSET else MODEL_PHOTO_PARAM_ASSET
        val binAsset = if (isAnime) MODEL_ANIME_BIN_ASSET else MODEL_PHOTO_BIN_ASSET

        val paramFile = File(dir, paramName)
        val binFile = File(dir, binName)

        copyAssetIfNeeded(context, paramAsset, paramFile)
        copyAssetIfNeeded(context, binAsset, binFile)

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
