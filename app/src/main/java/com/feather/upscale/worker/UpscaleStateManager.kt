package com.feather.upscale.worker

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trạng thái của quá trình Upscale (Single Image hoặc Batch ZIP/CBZ/MOBI).
 */
sealed class UpscaleState {
    data object Idle : UpscaleState()

    data class Queued(val totalPages: Int, val totalTiles: Int) : UpscaleState()

    data class Processing(
        val currentPage: Int = 1,
        val totalPages: Int = 1,
        val completedTiles: Int = 0,
        val totalTiles: Int = 1,
        val currentTileSize: Int = 256,
        val isLowRam: Boolean = false,
        val progressFraction: Float = 0f,
        val speedTilesPerSec: Float = 0f,
        val estimatedRemainingSec: Long = 0L,
        val statusMessage: String = "",
    ) : UpscaleState()

    data class Paused(
        val currentPage: Int = 1,
        val totalPages: Int = 1,
        val completedTiles: Int = 0,
        val totalTiles: Int = 1,
        val currentTileSize: Int = 256,
    ) : UpscaleState()

    data class Completed(
        val totalPages: Int = 1,
        val totalDurationMs: Long = 0L,
        val outputPath: String? = null,
        val outputDirectory: String? = null,
        val outputFileName: String = "",
        val outputResolution: String = "",
        val outputFileSize: String = "",
        val isNewFile: Boolean = true,
        val isVerified: Boolean = true,
    ) : UpscaleState()

    data class Error(
        val message: String,
        val isOom: Boolean = false,
    ) : UpscaleState()

    data object Cancelled : UpscaleState()
}

/**
 * Singleton quản lý trạng thái và luồng điều khiển Pause / Resume / Cancel
 * giữa WorkManager và giao diện Compose UI, kèm luồng ảnh preview thời gian thực.
 */
object UpscaleStateManager {

    private val _state = MutableStateFlow<UpscaleState>(UpscaleState.Idle)
    val state: StateFlow<UpscaleState> = _state.asStateFlow()

    private val _runtimePreview = MutableStateFlow<Bitmap?>(null)
    val runtimePreview: StateFlow<Bitmap?> = _runtimePreview.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isCancelled = MutableStateFlow(false)
    val isCancelled: StateFlow<Boolean> = _isCancelled.asStateFlow()

    fun updateState(newState: UpscaleState) {
        _state.value = newState
        if (newState is UpscaleState.Paused) {
            _isPaused.value = true
        } else if (newState is UpscaleState.Processing) {
            _isPaused.value = false
        } else if (newState is UpscaleState.Cancelled) {
            _isCancelled.value = true
            _isPaused.value = false
            _runtimePreview.value = null
        } else if (newState is UpscaleState.Idle || newState is UpscaleState.Completed) {
            _isPaused.value = false
            _isCancelled.value = false
        }
    }

    fun updateRuntimePreview(bitmap: Bitmap?) {
        _runtimePreview.value = bitmap
    }

    fun pause() {
        _isPaused.value = true
        val current = _state.value
        if (current is UpscaleState.Processing) {
            _state.value = UpscaleState.Paused(
                currentPage = current.currentPage,
                totalPages = current.totalPages,
                completedTiles = current.completedTiles,
                totalTiles = current.totalTiles,
                currentTileSize = current.currentTileSize
            )
        }
    }

    fun resume() {
        _isPaused.value = false
        val current = _state.value
        if (current is UpscaleState.Paused) {
            _state.value = UpscaleState.Processing(
                currentPage = current.currentPage,
                totalPages = current.totalPages,
                completedTiles = current.completedTiles,
                totalTiles = current.totalTiles,
                currentTileSize = current.currentTileSize,
                statusMessage = "Đang tiếp tục xử lý..."
            )
        }
    }

    fun cancel() {
        _isCancelled.value = true
        _isPaused.value = false
        _runtimePreview.value = null
        _state.value = UpscaleState.Cancelled
    }

    fun reset() {
        _isPaused.value = false
        _isCancelled.value = false
        _runtimePreview.value = null
        _state.value = UpscaleState.Idle
    }
}
