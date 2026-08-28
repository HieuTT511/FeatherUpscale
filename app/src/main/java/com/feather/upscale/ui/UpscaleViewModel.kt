package com.feather.upscale.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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

    private val _isBatchZip = MutableStateFlow(false)
    val isBatchZip: StateFlow<Boolean> = _isBatchZip.asStateFlow()

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
        // 1. Lắng nghe runtime preview thời gian thực khi đang render từng tile
        viewModelScope.launch {
            UpscaleStateManager.runtimePreview.collect { previewBmp ->
                if (previewBmp != null) {
                    _afterBitmap.value = previewBmp
                }
            }
        }

        // 2. Lắng nghe trạng thái hoàn tất để nạp ảnh kết quả sắc nét hoàn chỉnh
        viewModelScope.launch {
            UpscaleStateManager.state.collect { state ->
                if (state is UpscaleState.Completed) {
                    val outPath = state.outputPath
                    if (outPath != null && File(outPath).exists() && !_isBatchZip.value) {
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

    fun onImageSelected(uri: Uri) {
        _selectedUri.value = uri
        _isBatchZip.value = false
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

    fun onZipSelected(uri: Uri) {
        _selectedUri.value = uri
        _isBatchZip.value = true
        _afterBitmap.value = null
        UpscaleStateManager.reset()

        viewModelScope.launch(Dispatchers.IO) {
            val name = queryFileName(uri) ?: "comic_${System.currentTimeMillis()}.cbz"
            _selectedFileName.value = name
            _beforeBitmap.value = createMangaThumbnailPlaceholder()

            val detected = if (name.contains("color", true) || name.contains("manhwa", true) || name.contains("webtoon", true)) {
                "Manga Màu"
            } else {
                "Manga B&W"
            }
            _selectedPreset.value = detected
            _isAutoDetected.value = true
        }
    }

    fun startUpscale() {
        val uri = _selectedUri.value ?: return
        val isZip = _isBatchZip.value

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

                // Copy file từ URI vào cache file bằng buffer 64KB an toàn
                val cacheFile = File(appContext.cacheDir, if (isZip) "input_batch.cbz" else "input_image.png")
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

                val inputData = workDataOf(
                    UpscaleWorker.KEY_MODE to if (isZip) UpscaleWorker.MODE_BATCH_ARCHIVE else UpscaleWorker.MODE_SINGLE_IMAGE,
                    UpscaleWorker.KEY_INPUT_PATH to cacheFile.absolutePath,
                    UpscaleWorker.KEY_SCALE to _scale.value,
                    UpscaleWorker.KEY_USE_FP16 to _useFp16.value,
                    UpscaleWorker.KEY_FORCE_LOW_RAM to _forceLowRam.value
                )

                val workRequest = OneTimeWorkRequestBuilder<UpscaleWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "FeatherUpscaleWork",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Throwable) {
                UpscaleStateManager.updateState(
                    UpscaleState.Error("Lỗi khởi tạo: ${e.localizedMessage ?: e.message}")
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
        WorkManager.getInstance(appContext).cancelUniqueWork("FeatherUpscaleWork")
    }

    fun reset() {
        UpscaleStateManager.reset()
        _afterBitmap.value = null
    }

    /**
     * Hủy tiến trình upscale, xóa các tệp tạm thời trong cache và bảo toàn 100% file gốc ban đầu.
     */
    fun cancelAndCleanup(onComplete: () -> Unit) {
        cancelUpscale()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(appContext.cacheDir, "input_image.png").delete()
                File(appContext.cacheDir, "input_batch.cbz").delete()
            } catch (_: Throwable) {}
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Lưu trạng thái và file tạm vào thư mục UpScale_Drafts do app tự tạo để tiếp tục sau này.
     */
    fun saveDraftAndExit(onComplete: () -> Unit) {
        cancelUpscale()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val draftsDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "UpScale_Drafts").apply { mkdirs() }
                val isZip = _isBatchZip.value
                val sourceCacheFile = File(appContext.cacheDir, if (isZip) "input_batch.cbz" else "input_image.png")
                val draftFileName = _selectedFileName.value ?: if (isZip) "draft_batch.cbz" else "draft_image.png"

                if (sourceCacheFile.exists()) {
                    val targetDraftFile = File(draftsDir, draftFileName)
                    sourceCacheFile.copyTo(targetDraftFile, overwrite = true)
                }

                // Lưu metadata cấu hình draft
                val metaFile = File(draftsDir, "draft_metadata.txt")
                metaFile.writeText(
                    "fileName=$draftFileName\n" +
                    "isBatchZip=$isZip\n" +
                    "preset=${_selectedPreset.value}\n" +
                    "scale=${_scale.value}\n" +
                    "useFp16=${_useFp16.value}\n" +
                    "forceLowRam=${_forceLowRam.value}\n" +
                    "timestamp=${System.currentTimeMillis()}"
                )
            } catch (_: Throwable) {}
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Tự động nhận diện Preset từ màu sắc, độ bão hòa (saturation) và tỉ lệ kích thước.
     * Hoạt động an toàn 100%, không bao giờ crash.
     */
    internal fun detectPresetFromBitmap(bitmap: Bitmap?): String {
        if (bitmap == null) return "Manga Màu"
        try {
            val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return "Manga Màu"
            } else {
                bitmap
            }
            val w = safe.width
            val h = safe.height
            val stepX = maxOf(1, w / 32)
            val stepY = maxOf(1, h / 32)

            var totalSampled = 0
            var monochromeCount = 0
            var totalSat = 0f

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val color = safe.getPixel(x, y)
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF

                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val sat = if (max == 0) 0f else (max - min).toFloat() / max
                    totalSat += sat

                    val diffRG = kotlin.math.abs(r - g)
                    val diffGB = kotlin.math.abs(g - b)
                    val diffRB = kotlin.math.abs(r - b)
                    if (diffRG <= 15 && diffGB <= 15 && diffRB <= 15) {
                        monochromeCount++
                    }
                    totalSampled++
                }
            }

            if (totalSampled == 0) return "Manga Màu"

            val monoRatio = monochromeCount.toFloat() / totalSampled
            val avgSat = totalSat / totalSampled

            return when {
                monoRatio >= 0.82f || avgSat < 0.12f -> "Manga B&W"
                avgSat >= 0.35f || (w >= 1000 && h >= 1000 && (w.toFloat() / h in 0.65f..1.5f)) -> "Cover Poster"
                else -> "Manga Màu"
            }
        } catch (_: Throwable) {
            return "Manga Màu"
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
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

    private fun createMangaThumbnailPlaceholder(): Bitmap {
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawColor(android.graphics.Color.LTGRAY)
        canvas.drawText("CBZ / ZIP Comic Book", 200f, 300f, paint)
        return bmp
    }
}
