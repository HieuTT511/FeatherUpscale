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
import com.feather.upscale.NcnnUpscaler
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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

    private val _mobileModel = MutableStateFlow(NcnnUpscaler.MODEL_ANIME_6B)
    val mobileModel: StateFlow<String> = _mobileModel.asStateFlow()

    private val _quantizationMode = MutableStateFlow(NcnnUpscaler.PRECISION_FP16)
    val quantizationMode: StateFlow<Int> = _quantizationMode.asStateFlow()

    private val _rawWidth = MutableStateFlow<Int?>(null)
    val rawWidth: StateFlow<Int?> = _rawWidth.asStateFlow()

    private val _rawHeight = MutableStateFlow<Int?>(null)
    val rawHeight: StateFlow<Int?> = _rawHeight.asStateFlow()

    private val _inputResolution = MutableStateFlow<String?>(null)
    val inputResolution: StateFlow<String?> = _inputResolution.asStateFlow()

    private val _targetResolution = MutableStateFlow<String?>(null)
    val targetResolution: StateFlow<String?> = _targetResolution.asStateFlow()

    private val _inputMetadataInfo = MutableStateFlow<String?>(null)
    val inputMetadataInfo: StateFlow<String?> = _inputMetadataInfo.asStateFlow()

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

        viewModelScope.launch {
            UpscaleStateManager.runtimeBeforePreview.collect { beforeBmp ->
                if (beforeBmp != null) {
                    _beforeBitmap.value = beforeBmp
                }
            }
        }

        // 2. Lắng nghe trạng thái hoàn tất để nạp ảnh kết quả
        viewModelScope.launch {
            UpscaleStateManager.state.collect { state ->
                if (state is UpscaleState.Completed) {
                    val outPath = state.outputPath
                    if (outPath != null && File(outPath).exists() && !_isBatchZip.value) {
                        try {
                            if (_isVideo.value || outPath.endsWith(".mp4", true) || outPath.endsWith(".mkv", true)) {
                                _afterBitmap.value = extractVideoThumbnailFromFile(outPath)
                            } else {
                                _afterBitmap.value = decodeSampledPreviewFromFile(outPath, 2400)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun updateTargetResolution() {
        val w = _rawWidth.value
        val h = _rawHeight.value
        val s = _scale.value
        if (w != null && h != null && w > 0 && h > 0) {
            val tw = w * s
            val th = h * s
            val badge = when {
                tw >= 7680 || th >= 7680 -> "8K Ultra-HD"
                tw >= 3840 || th >= 3840 -> "4K UHD"
                tw >= 1920 || th >= 1920 -> "Full HD"
                else -> "HD"
            }
            _targetResolution.value = "$tw x $th px ($badge • ${s}X)"
        } else {
            _targetResolution.value = null
        }
    }

    fun setMobileModel(model: String) {
        _mobileModel.value = model
    }

    fun setQuantizationMode(precision: Int) {
        _quantizationMode.value = precision
        _useFp16.value = (precision == NcnnUpscaler.PRECISION_FP16)
    }

    fun setScale(newScale: Int) {
        _scale.value = newScale
        updateTargetResolution()
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

                // 1. Nhận diện kích thước và độ phân giải thật của ảnh đầu vào
                val bounds = decodeBoundsFromUri(uri)
                if (bounds != null) {
                    _rawWidth.value = bounds.first
                    _rawHeight.value = bounds.second
                    _inputResolution.value = "${bounds.first} x ${bounds.second} px"
                    val resQuality = when {
                        bounds.first >= 3840 || bounds.second >= 3840 -> "4K UHD"
                        bounds.first >= 1920 || bounds.second >= 1920 -> "Full HD"
                        bounds.first >= 1280 || bounds.second >= 1280 -> "HD 720p"
                        else -> "SD"
                    }
                    _inputMetadataInfo.value = "Ảnh gốc • $resQuality"
                } else {
                    _rawWidth.value = null
                    _rawHeight.value = null
                    _inputResolution.value = null
                    _inputMetadataInfo.value = "Ảnh gốc"
                }

                updateTargetResolution()

                // 2. Load thumbnail an toàn cho Preview Slider
                val sampled = decodeSampledPreviewFromUri(uri, 1200)
                _beforeBitmap.value = sampled

                // 3. Tự động phân tích ảnh và nhận diện Preset thông minh
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

            // 1. Trích xuất trang bìa thật và nhận diện độ phân giải thật của tập truyện
            val details = extractComicFirstPageAndDetails(uri)
            val coverBmp = details.first
            val bounds = details.second
            val pageCount = details.third

            if (bounds != null) {
                _rawWidth.value = bounds.first
                _rawHeight.value = bounds.second
                _inputResolution.value = "${bounds.first} x ${bounds.second} px (Trang bìa)"
            } else {
                _rawWidth.value = 1200
                _rawHeight.value = 1800
                _inputResolution.value = "Chuẩn Manga"
            }

            _inputMetadataInfo.value = "Tập truyện • $pageCount trang • CBZ/ZIP"
            updateTargetResolution()

            _beforeBitmap.value = coverBmp ?: createMangaThumbnailPlaceholder(name)

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

            val videoInfo = extractVideoDetails(uri)
            val frame = videoInfo.first
            val bounds = videoInfo.second

            if (bounds != null) {
                _rawWidth.value = bounds.first
                _rawHeight.value = bounds.second
                _inputResolution.value = "${bounds.first} x ${bounds.second} px"
                val quality = when {
                    bounds.first >= 3840 || bounds.second >= 3840 -> "4K UHD"
                    bounds.first >= 1920 || bounds.second >= 1920 -> "Full HD"
                    bounds.first >= 1280 || bounds.second >= 1280 -> "HD 720p"
                    else -> "SD"
                }
                _inputMetadataInfo.value = "Video AI • $quality • 30 FPS"
            } else {
                _rawWidth.value = 1280
                _rawHeight.value = 720
                _inputResolution.value = "1280 x 720 px"
                _inputMetadataInfo.value = "Video AI"
            }

            updateTargetResolution()

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
                    UpscaleWorker.KEY_FORCE_LOW_RAM to _forceLowRam.value,
                    UpscaleWorker.KEY_MODEL_NAME to _mobileModel.value
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
        if (bitmap == null) return "Anime & Cartoons"
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
            avgSat < 0.25f && (bitmap.width < 800 || bitmap.height < 800) -> "Fix Pixelation"
            avgSat >= 0.35f && (bitmap.width >= 1200 || bitmap.height >= 1200) -> "Face Recovery"
            else -> "Anime & Cartoons"
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
            val raw = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (raw != null) {
                val swBmp = raw.copy(Bitmap.Config.ARGB_8888, false)
                if (swBmp != raw) raw.recycle()
                swBmp
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun extractVideoThumbnailFromFile(filePath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val raw = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (raw != null) {
                val swBmp = raw.copy(Bitmap.Config.ARGB_8888, false)
                if (swBmp != raw) raw.recycle()
                swBmp
            } else null
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

    private fun decodeBoundsFromUri(uri: Uri): Pair<Int, Int>? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                Pair(boundsOptions.outWidth, boundsOptions.outHeight)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractComicFirstPageAndDetails(uri: Uri): Triple<Bitmap?, Pair<Int, Int>?, Int> {
        var pageCount = 0
        var firstPageBytes: ByteArray? = null
        val imgExts = setOf("jpg", "jpeg", "png", "webp", "bmp")

        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.lowercase()
                            val ext = name.substringAfterLast('.', "")
                            if (ext in imgExts && !name.contains("__macosx") && !name.startsWith(".")) {
                                pageCount++
                                if (firstPageBytes == null) {
                                    val bos = ByteArrayOutputStream()
                                    val buffer = ByteArray(32 * 1024)
                                    var read = zis.read(buffer)
                                    while (read != -1) {
                                        bos.write(buffer, 0, read)
                                        read = zis.read(buffer)
                                    }
                                    firstPageBytes = bos.toByteArray()
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Throwable) {}

        if (firstPageBytes != null) {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(firstPageBytes, 0, firstPageBytes.size, boundsOptions)
            val bounds = if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                Pair(boundsOptions.outWidth, boundsOptions.outHeight)
            } else null

            val maxDim = maxOf(boundsOptions.outWidth.coerceAtLeast(1), boundsOptions.outHeight.coerceAtLeast(1))
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= 1200) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(firstPageBytes, 0, firstPageBytes.size, decodeOptions)
            return Triple(bmp, bounds, pageCount.coerceAtLeast(1))
        }

        return Triple(null, null, pageCount.coerceAtLeast(1))
    }

    private fun extractVideoDetails(uri: Uri): Pair<Bitmap?, Pair<Int, Int>?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val bounds = if (w != null && h != null && w > 0 && h > 0) Pair(w, h) else null

            val raw = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val swBmp = if (raw != null) {
                val copy = raw.copy(Bitmap.Config.ARGB_8888, false)
                if (copy != raw) raw.recycle()
                copy
            } else null

            Pair(swBmp, bounds)
        } catch (_: Throwable) {
            Pair(null, null)
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
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
