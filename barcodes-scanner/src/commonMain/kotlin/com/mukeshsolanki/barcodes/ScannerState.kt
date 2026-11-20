package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.concurrent.Volatile

/** Hoisted, observable scanner controls (torch, zoom, pause, camera choice) plus what the bound camera reports. */
@Stable
public class ScannerState internal constructor(
    cameraId: String?,
    /** Auto-zoom policy used by the scan pipeline. */
    public val autoZoom: AutoZoom,
    /** A code held in view is delivered again only after this long with no code decoded. */
    public val dedupeGapMillis: Long,
    /** Android only: how long to wait for a busy back camera before binding anyway. */
    public val busyCameraTimeoutMillis: Long,
) {
    /** Torch on/off; applied live by the platform camera when [torchAvailable]. */
    public var torchEnabled: Boolean by mutableStateOf(false)

    /** Backing state for [paused]. */
    private var pausedState by mutableStateOf(false)

    /** Backing state for [zoom]. */
    private var zoomState by mutableFloatStateOf(1f)

    /** Bumped on every resume; the pipeline compares it to clear its dedupe memory. Written on the main thread only. */
    @Volatile
    internal var resumeGeneration: Int = 0
        private set

    /** When true, frames are dropped before decoding. Going from true to false clears dedupe and resets zoom to 1x. */
    public var paused: Boolean
        get() = pausedState
        set(value) {
            if (pausedState && !value) onResume()
            pausedState = value
        }

    /** Zoom ratio, clamped to `1f..maxZoom`. Written by the app or by auto-zoom. */
    public var zoom: Float
        get() = zoomState
        set(value) {
            zoomState = value.coerceIn(1f, maxZoom)
        }

    /** Highest zoom ratio the bound camera supports; 1f until a camera is bound. */
    public var maxZoom: Float by mutableFloatStateOf(1f)
        private set

    /** Whether the bound camera has a torch. */
    public var torchAvailable: Boolean by mutableStateOf(false)
        private set

    /** Cameras found on the device, published when a camera binds. */
    public var availableCameras: List<CameraInfo> by mutableStateOf(emptyList())
        private set

    /** Camera to bind; null picks Back first, then Front. Changing it rebinds. */
    public var cameraId: String? by mutableStateOf(cameraId)

    /** Called by the platform camera after binding to publish its capabilities. Re-clamps [zoom]. */
    internal fun onCameraBound(maxZoom: Float, torchAvailable: Boolean, cameras: List<CameraInfo>) {
        this.maxZoom = maxZoom.coerceAtLeast(1f)
        this.torchAvailable = torchAvailable
        availableCameras = cameras
        zoomState = zoomState.coerceIn(1f, this.maxZoom)
    }

    /** Signals the pipeline to forget delivered codes and resets zoom to 1x. */
    private fun onResume() {
        resumeGeneration++
        zoomState = 1f
    }
}

/** Creates and remembers a [ScannerState]. The arguments are initial values read once. */
@Composable
public fun rememberScannerState(
    cameraId: String? = null,
    autoZoom: AutoZoom = AutoZoom(),
    dedupeGapMillis: Long = 1000,
    busyCameraTimeoutMillis: Long = 2000,
): ScannerState = remember { ScannerState(cameraId, autoZoom, dedupeGapMillis, busyCameraTimeoutMillis) }
