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

    fun setScale(newScale: Int) {
        _scale.value = newScale
    }

    fun setUseFp16(enabled: Boolean) {
        _useFp16.value = enabled
    }

    fun setForceLowRam(enabled: Boolean) {
        _forceLowRam.value = enabled
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

                // Load thumbnail an toàn cho Preview Slider (tránh OOM khi chọn ảnh khổng lồ 1GB)
                _beforeBitmap.value = decodeSampledPreviewFromUri(uri, 1200)
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
        }
    }

    fun startUpscale() {
        val uri = _selectedUri.value ?: return
        val isZip = _isBatchZip.value

        viewModelScope.launch(Dispatchers.IO) {
            try {
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
