package com.mukeshsolanki.barcodes

/** Which way a camera lens faces. */
public enum class CameraFacing {
    /** Faces away from the user. */
    Back,

    /** Faces the user. */
    Front,
}

/**
 * A camera the scanner can bind to.
 * @property id platform camera id, usable as [ScannerState.cameraId].
 */
public data class CameraInfo(val id: String, val facing: CameraFacing)

/**
 * Auto-zoom policy: when a QR code is seen but too small to decode, zoom in by [step] up to [maxRatio].
 * @property minModuleSizePx QR modules smaller than this (in frame pixels) trigger a zoom step.
 */
public data class AutoZoom(
    val enabled: Boolean = true,
    val step: Float = 1.5f,
    val maxRatio: Float = 4f,
    val cooldownMillis: Long = 500,
    val minModuleSizePx: Float = 3f,
)

/** Camera failures reported by [BarcodeScanner]. Never carries payload text. */
public sealed interface ScannerError {
    /** The device has no usable back or front camera. */
    public data object NoCamera : ScannerError

    /**
     * The camera could not be bound.
     * @property reason the simple class name of the underlying failure.
     */
    public data class BindFailed(val reason: String) : ScannerError
}
