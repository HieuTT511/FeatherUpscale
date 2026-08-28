package com.feather.upscale.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.feather.upscale.video.VideoProcessor
import com.feather.upscale.worker.UpscaleState
import com.feather.upscale.worker.UpscaleStateManager
import com.feather.upscale.worker.UpscaleWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class UpscaleViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private val _customOutputDir = MutableStateFlow<String?>(null)
    val customOutputDir: StateFlow<String?> = _customOutputDir.asStateFlow()

    private val _isBatchZip = MutableStateFlow(false)
    val isBatchZip: StateFlow<Boolean> = _isBatchZip.asStateFlow()

    private val _isVideo = MutableStateFlow(false)
    val isVideo: StateFlow<Boolean> = _isVideo.asStateFlow()

    private val _beforeBitmap = MutableStateFlow<Bitmap?>(null)
    val beforeBitmap: StateFlow<Bitmap?> = _beforeBitmap.asStateFlow()

    private val _afterBitmap = MutableStateFlow<Bitmap?>(null)
    val afterBitmap: StateFlow<Bitmap?> = _afterBitmap.asStateFlow()

    private val _selectedPreset = MutableStateFlow("Manga Màu")
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()

    private val _isAutoDetected = MutableStateFlow(false)
    val isAutoDetected: StateFlow<Boolean> = _isAutoDetected.asStateFlow()

    private val _scale = MutableStateFlow(4)
    val scale: StateFlow<Int> = _scale.asStateFlow()

    private val _useFp16 = MutableStateFlow(true)
    val useFp16: StateFlow<Boolean> = _useFp16.asStateFlow()

    private val isDeviceLowRam = run {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice ?: false
    }

    private val _forceLowRam = MutableStateFlow(isDeviceLowRam)
    val forceLowRam: StateFlow<Boolean> = _forceLowRam.asStateFlow()

    val upscaleState: StateFlow<UpscaleState> = UpscaleStateManager.state

    init {
        // 1. Lắng nghe runtime preview thời gian thực khi đang render từng frame / tile
        viewModelScope.launch {
            UpscaleStateManager.runtimePreview.collect { previewBmp ->
                if (previewBmp != null) {
                    _afterBitmap.value = previewBmp
                }
            }
        }

        // 2. Lắng nghe trạng thái hoàn tất để nạp ảnh kết quả
        viewModelScope.launch {
            UpscaleStateManager.state.collect { state ->
                if (state is UpscaleState.Completed) {
                    val outPath = state.outputPath
                    if (outPath != null && File(outPath).exists() && !_isBatchZip.value && !_isVideo.value) {
                        try {
                            _afterBitmap.value = decodeSampledPreviewFromFile(outPath, 2400)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun setScale(newScale: Int) {
        _scale.value = newScale
    }

    fun setUseFp16(enabled: Boolean) {
        _useFp16.value = enabled
    }

    fun setForceLowRam(enabled: Boolean) {
        _forceLowRam.value = enabled
    }

    fun setPreset(preset: String) {
        _selectedPreset.value = preset
        _isAutoDetected.value = false
    }

    fun setCustomOutputDir(path: String?) {
        _customOutputDir.value = path
    }

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _isBatchZip.value = false
        _isVideo.value = false
        _afterBitmap.value = null
        UpscaleStateManager.reset()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = queryFileName(uri) ?: "image_${System.currentTimeMillis()}.png"
                _selectedFileName.value = name

                // Load thumbnail an toàn cho Preview Slider
                val sampled = decodeSampledPreviewFromUri(uri, 1200)
                _beforeBitmap.value = sampled

                // Tự động phân tích ảnh và nhận diện Preset thông minh
                val detected = detectPresetFromBitmap(sampled)
                _selectedPreset.value = detected
                _isAutoDetected.value = true
            } catch (e: Throwable) {
                UpscaleStateManager.updateState(UpscaleState.Error("Không thể nạp ảnh xem trước: ${e.message}"))
            }
        }
    }

    fun onComicSelected(uri: Uri) {
        _selectedUri.value = uri
        _isBatchZip.value = true
        _isVideo.value = false
        _afterBitmap.value = null
        UpscaleStateManager.reset()

        viewModelScope.launch(Dispatchers.IO) {
            val name = queryFileName(uri) ?: "comic_${System.currentTimeMillis()}.cbz"
            _selectedFileName.value = name
            _beforeBitmap.value = createMangaThumbnailPlaceholder(name)

            val detected = if (name.contains("color", true) || name.contains("manhwa", true) || name.contains("webtoon", true)) {
                "Manga Màu"
            } else {
                "Manga B&W"
            }
            _selectedPreset.value = detected
            _isAutoDetected.value = true
        }
    }

    fun onVideoSelected(uri: Uri) {
        _selectedUri.value = uri
        _isBatchZip.value = false
        _isVideo.value = true
        _afterBitmap.value = null
        UpscaleStateManager.reset()

        viewModelScope.launch(Dispatchers.IO) {
            val name = queryFileName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            _selectedFileName.value = name

            val frame = extractVideoThumbnail(uri)
            _beforeBitmap.value = frame ?: createVideoThumbnailPlaceholder(name)

            _selectedPreset.value = "Anime Video"
            _isAutoDetected.value = true
        }
    }

    fun startUpscale() {
        val uri = _selectedUri.value ?: return
        val isZip = _isBatchZip.value
        val isVid = _isVideo.value

        viewModelScope.launch(Dispatchers.IO) {
            try {
                UpscaleStateManager.reset()
                UpscaleStateManager.updateState(
                    UpscaleState.Processing(
                        currentPage = 1,
                        totalPages = 1,
                        completedTiles = 0,
                        totalTiles = 1,
                        currentTileSize = if (_forceLowRam.value) 128 else 256,
                        isLowRam = _forceLowRam.value,
                        statusMessage = "Đang chuẩn bị file xử lý..."
                    )
                )

                val fileName = _selectedFileName.value ?: "input"
                val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else (if (isVid) "mp4" else if (isZip) "cbz" else "png")
                val cacheFile = File(appContext.cacheDir, "input_job.$ext")

                // Copy file từ URI vào cache file bằng buffer 64KB an toàn
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedInputStream(input).use { bis ->
                        FileOutputStream(cacheFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                val buffer = ByteArray(64 * 1024)
                                var read = bis.read(buffer)
                                while (read != -1) {
                                    bos.write(buffer, 0, read)
                                    read = bis.read(buffer)
                                }
                            }
                        }
                    }
                }

                val mode = when {
                    isVid || VideoProcessor.isVideoFile(ext) -> UpscaleWorker.MODE_VIDEO
                    ext == "mobi" || ext == "prc" -> UpscaleWorker.MODE_MOBI_ARCHIVE
                    isZip || ext == "cbz" || ext == "zip" -> UpscaleWorker.MODE_BATCH_ARCHIVE
                    else -> UpscaleWorker.MODE_SINGLE_IMAGE
                }

                val inputData = workDataOf(
                    UpscaleWorker.KEY_MODE to mode,
                    UpscaleWorker.KEY_INPUT_PATH to cacheFile.absolutePath,
                    UpscaleWorker.KEY_ORIGINAL_NAME to (_selectedFileName.value ?: (if (isVid) "video.mp4" else "image.png")),
                    UpscaleWorker.KEY_CUSTOM_OUTPUT_DIR to _customOutputDir.value,
                    UpscaleWorker.KEY_SCALE to _scale.value,
                    UpscaleWorker.KEY_USE_FP16 to _useFp16.value,
                    UpscaleWorker.KEY_FORCE_LOW_RAM to _forceLowRam.value
                )

                val upscaleWorkRequest = OneTimeWorkRequestBuilder<UpscaleWorker>()
                    .setInputData(inputData)
                    .addTag("FEATHER_UPSCALE_JOB")
                    .build()

                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "FEATHER_UPSCALE_WORK",
                    ExistingWorkPolicy.REPLACE,
                    upscaleWorkRequest
                )

            } catch (e: Throwable) {
                UpscaleStateManager.updateState(
                    UpscaleState.Error(
                        message = "Không thể khởi động tác vụ: ${e.message}",
                        isOom = e.message?.contains("Out of memory", true) == true
                    )
                )
            }
        }
    }

    fun pauseUpscale() {
        UpscaleStateManager.pause()
    }

    fun resumeUpscale() {
        UpscaleStateManager.resume()
    }

    fun cancelUpscale() {
        UpscaleStateManager.cancel()
        WorkManager.getInstance(appContext).cancelUniqueWork("FEATHER_UPSCALE_WORK")
    }

    fun clearState() {
        _selectedUri.value = null
        _selectedFileName.value = null
        _beforeBitmap.value = null
        _afterBitmap.value = null
        _isBatchZip.value = false
        _isVideo.value = false
        UpscaleStateManager.reset()
    }

    fun reset() {
        clearState()
    }

    fun cancelAndCleanup(onFinish: (() -> Unit)? = null) {
        cancelUpscale()
        clearState()
        onFinish?.invoke()
    }

    fun saveDraftAndExit(onFinish: (() -> Unit)? = null) {
        pauseUpscale()
        onFinish?.invoke()
    }

    internal fun detectPresetFromBitmap(bitmap: Bitmap?): String {
        if (bitmap == null) return "Manga Màu"
        val sampleW = minOf(bitmap.width, 100)
        val sampleH = minOf(bitmap.height, 100)
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled != bitmap) scaled.recycle()

        var grayCount = 0
        var totalSat = 0f
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            if (Math.abs(r - g) <= 12 && Math.abs(g - b) <= 12 && Math.abs(r - b) <= 12) {
                grayCount++
            }

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            totalSat += hsv[1]
        }

        val totalPixels = pixels.size.toFloat()
        val grayRatio = grayCount / totalPixels
        val avgSat = totalSat / totalPixels

        return when {
            grayRatio >= 0.82f || avgSat < 0.12f -> "Manga B&W"
            avgSat >= 0.35f || (bitmap.width >= 1000 && bitmap.height >= 1000) -> "Cover Poster"
            else -> "Manga Màu"
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractVideoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun decodeSampledPreviewFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun decodeSampledPreviewFromFile(filePath: String, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }

    private fun createMangaThumbnailPlaceholder(name: String): Bitmap {
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawColor(android.graphics.Color.LTGRAY)
        val label = if (name.endsWith(".mobi", true) || name.endsWith(".prc", true)) "MOBI / PRC E-Book" else "CBZ / ZIP Comic Book"
        canvas.drawText(label, 200f, 300f, paint)
        return bmp
    }

    private fun createVideoThumbnailPlaceholder(name: String): Bitmap {
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawColor(android.graphics.Color.DKGRAY)
        canvas.drawText("Video: $name", 320f, 180f, paint)
        return bmp
    }
}
