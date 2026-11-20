package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Zoom clamping, torch and camera publication on [ScannerState]. */
class ScannerStateTest {
    /** A fresh state with default policies. */
    private fun newState() = ScannerState(null, AutoZoom(), dedupeGapMillis = 1000, busyCameraTimeoutMillis = 2000)

    /** Before a camera binds, maxZoom is 1 so zoom stays at 1. */
    @Test
    fun zoomIsPinnedToOneBeforeBind() {
        val state = newState()
        state.zoom = 3f
        assertEquals(1f, state.maxZoom)
        assertEquals(1f, state.zoom)
    }

    /** Zoom clamps to 1..maxZoom once bound. */
    @Test
    fun zoomClampsToBoundRange() {
        val state = newState()
        state.onCameraBound(maxZoom = 5f, torchAvailable = false, cameras = emptyList())
        state.zoom = 0.2f
        assertEquals(1f, state.zoom)
        state.zoom = 9f
        assertEquals(5f, state.zoom)
        state.zoom = 2.5f
        assertEquals(2.5f, state.zoom)
    }

    /** Rebinding to a camera with a lower max re-clamps the current zoom. */
    @Test
    fun rebindReclampsZoom() {
        val state = newState()
        state.onCameraBound(maxZoom = 8f, torchAvailable = false, cameras = emptyList())
        state.zoom = 6f
        state.onCameraBound(maxZoom = 4f, torchAvailable = false, cameras = emptyList())
        assertEquals(4f, state.zoom)
    }

    /** A reported max below 1 is treated as 1. */
    @Test
    fun maxZoomNeverBelowOne() {
        val state = newState()
        state.onCameraBound(maxZoom = 0.5f, torchAvailable = false, cameras = emptyList())
        assertEquals(1f, state.maxZoom)
    }

    /** Torch is off by default, follows writes, and availability comes from the camera. */
    @Test
    fun torchStateAndAvailability() {
        val state = newState()
        assertFalse(state.torchEnabled)
        assertFalse(state.torchAvailable)
        state.torchEnabled = true
        assertTrue(state.torchEnabled)
        val cameras = listOf(CameraInfo("0", CameraFacing.Back), CameraInfo("1", CameraFacing.Front))
        state.onCameraBound(maxZoom = 2f, torchAvailable = true, cameras = cameras)
        assertTrue(state.torchAvailable)
        assertEquals(cameras, state.availableCameras)
    }

    /** Only a true to false transition of paused resets zoom and bumps the resume generation. */
    @Test
    fun resumeResetsZoomOnlyOnTransition() {
        val state = newState()
        state.onCameraBound(maxZoom = 8f, torchAvailable = false, cameras = emptyList())
        state.zoom = 3f
        state.paused = false
        assertEquals(3f, state.zoom)
        assertEquals(0, state.resumeGeneration)
        state.paused = true
        assertEquals(3f, state.zoom)
        state.paused = false
        assertEquals(1f, state.zoom)
        assertEquals(1, state.resumeGeneration)
    }
}
