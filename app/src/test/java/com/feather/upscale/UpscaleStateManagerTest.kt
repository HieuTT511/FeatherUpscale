package com.feather.upscale

import com.feather.upscale.worker.UpscaleState
import com.feather.upscale.worker.UpscaleStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpscaleStateManagerTest {

    @Before
    fun setUp() {
        UpscaleStateManager.reset()
    }

    @Test
    fun `initial state is Idle va cac co dieu khien deu false`() {
        assertEquals(UpscaleState.Idle, UpscaleStateManager.state.value)
        assertFalse(UpscaleStateManager.isPaused.value)
        assertFalse(UpscaleStateManager.isCancelled.value)
    }

    @Test
    fun `chuyen sang Processing cap nhat dung thong so va mo co paused`() {
        val processingState = UpscaleState.Processing(
            currentPage = 2,
            totalPages = 10,
            completedTiles = 5,
            totalTiles = 20,
            currentTileSize = 128,
            isLowRam = true,
            progressFraction = 0.25f,
            statusMessage = "Đang xử lý tile 5"
        )
        UpscaleStateManager.updateState(processingState)

        assertEquals(processingState, UpscaleStateManager.state.value)
        assertFalse(UpscaleStateManager.isPaused.value)
        assertFalse(UpscaleStateManager.isCancelled.value)
    }

    @Test
    fun `pause chuyen state sang Paused va bat co isPaused`() {
        UpscaleStateManager.updateState(
            UpscaleState.Processing(
                currentPage = 3,
                totalPages = 5,
                completedTiles = 8,
                totalTiles = 16,
                currentTileSize = 256
            )
        )

        UpscaleStateManager.pause()

        assertTrue(UpscaleStateManager.isPaused.value)
        val currentState = UpscaleStateManager.state.value
        assertTrue(currentState is UpscaleState.Paused)
        val paused = currentState as UpscaleState.Paused
        assertEquals(3, paused.currentPage)
        assertEquals(5, paused.totalPages)
        assertEquals(8, paused.completedTiles)
        assertEquals(16, paused.totalTiles)
    }

    @Test
    fun `resume tu Paused chuyen state tro lai Processing va tat co isPaused`() {
        UpscaleStateManager.updateState(
            UpscaleState.Processing(
                currentPage = 1,
                totalPages = 1,
                completedTiles = 4,
                totalTiles = 10,
                currentTileSize = 128
            )
        )
        UpscaleStateManager.pause()
        assertTrue(UpscaleStateManager.isPaused.value)

        UpscaleStateManager.resume()
        assertFalse(UpscaleStateManager.isPaused.value)
        assertTrue(UpscaleStateManager.state.value is UpscaleState.Processing)
    }

    @Test
    fun `cancel chuyen state sang Cancelled va bat co isCancelled`() {
        UpscaleStateManager.updateState(
            UpscaleState.Processing(
                currentPage = 1,
                totalPages = 1,
                completedTiles = 2,
                totalTiles = 5
            )
        )

        UpscaleStateManager.cancel()

        assertTrue(UpscaleStateManager.isCancelled.value)
        assertEquals(UpscaleState.Cancelled, UpscaleStateManager.state.value)
    }

    @Test
    fun `reset tra ve trang thai ban dau`() {
        UpscaleStateManager.cancel()
        assertTrue(UpscaleStateManager.isCancelled.value)

        UpscaleStateManager.reset()
        assertEquals(UpscaleState.Idle, UpscaleStateManager.state.value)
        assertFalse(UpscaleStateManager.isPaused.value)
        assertFalse(UpscaleStateManager.isCancelled.value)
    }
}

