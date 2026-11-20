package com.mukeshsolanki.barcodes

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDevicePositionUnspecified
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.position
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreFoundation.CFRetain
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.Foundation.numberWithUnsignedInt
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.posix.memcpy
import kotlin.concurrent.Volatile

/**
 * Owns one AVCaptureSession, its Y-plane video output and preview layer for [CameraPreview].
 * Every session and device mutation runs on a private serial queue; callbacks to Compose state hop to main.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosCamera(
    /** Scanner state that receives bound-camera info and gates frames while paused. */
    private val state: ScannerState,
    /** Receives each copied luminance frame on the video queue. */
    private val onFrame: (Frame) -> Unit,
    /** Receives setup failures on the main thread. */
    private val onError: (ScannerError) -> Unit,
) {
    /** The capture session shared by the preview layer and the video output. */
    private val session = AVCaptureSession()

    /** Serial queue for configuration, start/stop and device locking. */
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create("com.mukeshsolanki.barcodes.session", null)

    /** Serial queue on which sample buffers are delivered. */
    private val videoQueue: dispatch_queue_t = dispatch_queue_create("com.mukeshsolanki.barcodes.video", null)

    /** Strongly held sample buffer delegate; AVFoundation keeps only a weak reference. */
    private val delegate = SampleBufferDelegate(::onSampleBuffer)

    /** Output delivering 420f frames whose plane 0 is the luminance plane. */
    private val output = AVCaptureVideoDataOutput().apply {
        videoSettings = mapOf(pixelFormatKey() to NSNumber.numberWithUnsignedInt(kCVPixelFormatType_420YpCbCr8BiPlanarFullRange))
        alwaysDiscardsLateVideoFrames = true
        setSampleBufferDelegate(delegate, videoQueue)
    }

    /** Fill-centre preview layer hosted by the UIKit view. */
    val previewLayer: AVCaptureVideoPreviewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    /** The currently bound device; only touched on [sessionQueue]. */
    private var device: AVCaptureDevice? = null

    /** Torch state requested by Compose; only touched on [sessionQueue]. */
    private var desiredTorch = false

    /** Zoom ratio requested by Compose; only touched on [sessionQueue]. */
    private var desiredZoom = 1f

    /** Whether the bound device is the front camera; read on the video queue. */
    @Volatile
    private var isFront = false

    /** Latest UI orientation reported by the preview view; read on the video queue. */
    @Volatile
    private var interfaceOrientation: UIInterfaceOrientation = UIInterfaceOrientationPortrait

    /** Reused copy target for the luminance plane; only touched on [videoQueue]. */
    private var luminance = ByteArray(0)

    /** Selects and attaches the camera for [cameraId] (null = back, then front) and publishes it to [state]. */
    fun bind(cameraId: String?) = dispatch_async(sessionQueue) { configureSafely(cameraId) }

    /** Starts the session if a camera is attached, then reapplies torch and zoom. */
    fun start() = dispatch_async(sessionQueue) {
        if (session.inputs.isNotEmpty() && !session.running) {
            session.startRunning()
            applyControls()
        }
    }

    /** Stops the session while keeping its inputs and outputs. */
    fun stop() = dispatch_async(sessionQueue) {
        if (session.running) session.stopRunning()
    }

    /** Stops the session and detaches every input and output. */
    fun release() = dispatch_async(sessionQueue) {
        if (session.running) session.stopRunning()
        session.beginConfiguration()
        detachAll()
        session.commitConfiguration()
        device = null
    }

    /** Records the requested torch and zoom and applies them when the session is running. */
    fun updateControls(torch: Boolean, zoom: Float) = dispatch_async(sessionQueue) {
        desiredTorch = torch
        desiredZoom = zoom
        if (session.running) applyControls()
    }

    /** Records the UI orientation for frame rotation and rotates the preview to match. Main thread only. */
    fun updateOrientation(orientation: UIInterfaceOrientation) {
        interfaceOrientation = orientation
        applyPreviewOrientation()
    }

    /** Runs [configure] and reports any unexpected failure as [ScannerError.BindFailed]. */
    private fun configureSafely(cameraId: String?) {
        try {
            configure(cameraId)
        } catch (e: Exception) {
            fail(ScannerError.BindFailed("Camera setup failed"))
        }
    }

    /** Checks permission, picks a device, attaches it and publishes the bound camera. */
    private fun configure(cameraId: String?) {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != AVAuthorizationStatusAuthorized) {
            return fail(ScannerError.BindFailed("Camera permission not granted"))
        }
        val devices = discoverCameras()
        val selected = selectCamera(devices, cameraId) ?: return fail(ScannerError.NoCamera)
        val input = AVCaptureDeviceInput.deviceInputWithDevice(selected, null) as? AVCaptureDeviceInput
            ?: return fail(ScannerError.BindFailed("Camera input unavailable"))
        if (!attach(input)) return fail(ScannerError.BindFailed("Camera configuration rejected"))
        device = selected
        isFront = selected.position == AVCaptureDevicePositionFront
        publishBound(selected, devices.map { CameraInfo(it.uniqueID, it.facing()) })
    }

    /** Replaces the session's inputs and outputs with [input] and the video output; false if rejected. */
    private fun attach(input: AVCaptureDeviceInput): Boolean {
        session.beginConfiguration()
        detachAll()
        session.sessionPreset = preferredPreset()
        val attached = session.canAddInput(input) && session.canAddOutput(output)
        if (attached) {
            session.addInput(input)
            session.addOutput(output)
        }
        session.commitConfiguration()
        return attached
    }

    /** Removes every input and output from the session. Call inside a configuration block. */
    private fun detachAll() {
        session.inputs.filterIsInstance<AVCaptureInput>().forEach(session::removeInput)
        session.outputs.filterIsInstance<AVCaptureOutput>().forEach(session::removeOutput)
    }

    /** 1280x720 when supported, otherwise the high preset. */
    private fun preferredPreset() =
        if (session.canSetSessionPreset(AVCaptureSessionPreset1280x720)) AVCaptureSessionPreset1280x720 else AVCaptureSessionPresetHigh

    /** Posts the bound device's zoom range, torch support and the camera list to [state] on main. */
    private fun publishBound(selected: AVCaptureDevice, cameras: List<CameraInfo>) {
        val maxZoom = maxZoomFactor(selected).toFloat()
        val torch = selected.hasTorch
        dispatch_async(dispatch_get_main_queue()) {
            state.onCameraBound(maxZoom = maxZoom, torchAvailable = torch, cameras = cameras)
            applyPreviewOrientation()
        }
    }

    /** Applies [desiredTorch] and [desiredZoom] to the bound device under a configuration lock. */
    private fun applyControls() {
        val current = device ?: return
        if (!current.lockForConfiguration(null)) return
        val mode = if (desiredTorch) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        if (current.hasTorch && current.isTorchModeSupported(mode)) current.torchMode = mode
        current.videoZoomFactor = desiredZoom.toDouble().coerceIn(1.0, maxZoomFactor(current))
        current.unlockForConfiguration()
    }

    /** Rotates the preview layer's connection to the latest UI orientation. Main thread only. */
    private fun applyPreviewOrientation() {
        val connection = previewLayer.connection ?: return
        if (connection.supportsVideoOrientation) connection.videoOrientation = videoOrientation(interfaceOrientation)
    }

    /** Reports [error] on the main thread. */
    private fun fail(error: ScannerError) {
        dispatch_async(dispatch_get_main_queue()) { onError(error) }
    }

    /** Copies plane 0 of [sampleBuffer] into [luminance] and forwards it unless the scanner is paused. */
    private fun onSampleBuffer(sampleBuffer: CMSampleBufferRef?) {
        if (state.paused) return
        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
        val frame = copyLuminance(pixelBuffer) ?: return
        onFrame(frame)
    }

    /** Copies the Y plane of [pixelBuffer] into the reused buffer, or null when it has no base address. */
    private fun copyLuminance(pixelBuffer: CVPixelBufferRef): Frame? {
        CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        try {
            val base = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0.convert()) ?: return null
            val rowStride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0.convert()).toInt()
            val width = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0.convert()).toInt()
            val height = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0.convert()).toInt()
            val size = rowStride * height
            if (luminance.size != size) luminance = ByteArray(size)
            luminance.usePinned { memcpy(it.addressOf(0), base, size.convert()) }
            return Frame(luminance, rowStride, width, height, frameRotation())
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    /** Clockwise degrees that turn the sensor-oriented buffer upright for the current UI orientation. */
    private fun frameRotation(): Int {
        val back = when (interfaceOrientation) {
            UIInterfaceOrientationLandscapeRight -> 0
            UIInterfaceOrientationLandscapeLeft -> 180
            UIInterfaceOrientationPortraitUpsideDown -> 270
            else -> 90
        }
        return if (isFront) (360 - back) % 360 else back
    }
}

/** Forwards each delivered sample buffer to [onSampleBuffer]; dropped buffers are ignored. */
@OptIn(ExperimentalForeignApi::class)
private class SampleBufferDelegate(
    /** Handler invoked on the video queue for every delivered buffer. */
    private val onSampleBuffer: (CMSampleBufferRef?) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    /** Called by AVFoundation for each frame on the video queue. */
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) = onSampleBuffer(didOutputSampleBuffer)
}

/** The CoreVideo pixel-format key bridged to an NSString usable in a Foundation dictionary. */
@OptIn(ExperimentalForeignApi::class)
private fun pixelFormatKey(): Any = requireNotNull(CFBridgingRelease(CFRetain(kCVPixelBufferPixelFormatTypeKey)))

/** Wide-angle back and front cameras, back cameras first. */
private fun discoverCameras(): List<AVCaptureDevice> =
    AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
        mediaType = AVMediaTypeVideo,
        position = AVCaptureDevicePositionUnspecified,
    ).devices
        .filterIsInstance<AVCaptureDevice>()
        .filter { it.position == AVCaptureDevicePositionBack || it.position == AVCaptureDevicePositionFront }
        .sortedBy { it.position != AVCaptureDevicePositionBack }

/** The device matching [cameraId], else the first back camera, else the first front camera. */
private fun selectCamera(devices: List<AVCaptureDevice>, cameraId: String?): AVCaptureDevice? =
    devices.firstOrNull { it.uniqueID == cameraId }
        ?: devices.firstOrNull { it.position == AVCaptureDevicePositionBack }
        ?: devices.firstOrNull { it.position == AVCaptureDevicePositionFront }

/** Maps the device position to the public [CameraFacing]. */
private fun AVCaptureDevice.facing(): CameraFacing =
    if (position == AVCaptureDevicePositionFront) CameraFacing.Front else CameraFacing.Back

/** The largest zoom factor both the active format and the device currently allow. */
private fun maxZoomFactor(device: AVCaptureDevice): Double =
    minOf(device.activeFormat.videoMaxZoomFactor, device.maxAvailableVideoZoomFactor).coerceAtLeast(1.0)

/** Maps a UI orientation to the matching capture orientation; unknown is treated as portrait. */
private fun videoOrientation(orientation: UIInterfaceOrientation): AVCaptureVideoOrientation = when (orientation) {
    UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
    UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
    UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
    else -> AVCaptureVideoOrientationPortrait
}
