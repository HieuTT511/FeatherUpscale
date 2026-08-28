package com.feather.upscale

import android.content.Context
import java.io.File

/**
 * Wrapper JNI cho NCNN Real-ESRGAN (Vulkan backend).
 *
 * - Load library "featherup" (xem app/src/main/cpp/CMakeLists.txt).
 * - Model đặt trong assets: models/realesrgan-x4.param + .bin
 *   -> copy sang filesDir/models ở lần chạy đầu (NCNN load từ file path).
 * - Hỗ trợ cờ FP16 (Half-precision) giúp giảm 50% VRAM GPU.
 */
object NcnnUpscaler {

    const val MODEL_PARAM_ASSET = "models/realesrgan-x4.param"
    const val MODEL_BIN_ASSET = "models/realesrgan-x4.bin"

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

    external fun nativeHasNcnn(): Boolean

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
     * Copy model từ assets sang filesDir/models (idempotent) và trả về cặp path.
     * Gọi hàm này trước khi dùng native inference thật.
     */
    fun ensureModelFiles(context: Context): Pair<File, File> {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val paramFile = File(dir, "realesrgan-x4.param")
        val binFile = File(dir, "realesrgan-x4.bin")

        if (!initialized) {
            copyAssetIfNeeded(context, MODEL_PARAM_ASSET, paramFile)
            copyAssetIfNeeded(context, MODEL_BIN_ASSET, binFile)
            initialized = true
        }
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
     * Trả về null nếu native không khả dụng / lỗi OOM native.
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
                ?: fallbackUpscale(pixels, w, h, scale)
        } catch (_: OutOfMemoryError) {
            null // caller sẽ retry tile nhỏ hơn
        } catch (_: Throwable) {
            fallbackUpscale(pixels, w, h, scale)
        }
    }

    /** Fallback nearest/bilinear pure-Kotlin khi chạy không có native JNI */
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
