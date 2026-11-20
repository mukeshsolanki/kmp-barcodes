package com.mukeshsolanki.barcodes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import androidx.camera.core.CameraInfo as XCameraInfo

/** CameraX preview + Y-plane analysis bound to the composition and the lifecycle owner. */
@Composable
internal actual fun CameraPreview(
    state: ScannerState,
    onFrame: (Frame) -> Unit,
    onError: (ScannerError) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { createPreviewView(context) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    val cameraId = state.cameraId
    AndroidView(factory = { previewView }, modifier = modifier)
    LaunchedEffect(context, lifecycleOwner, previewView, cameraId) {
        val session = CameraSession(context, lifecycleOwner, previewView, state) { currentOnFrame(it) }
        session.run { currentOnError(it) }
    }
}

/** Creates a fill-centre preview view that composes correctly inside Compose (COMPATIBLE mode). */
private fun createPreviewView(context: Context): PreviewView =
    PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }

/** One bind of the camera: selection, busy wait, binding, live torch/zoom, and release on cancellation. */
private class CameraSession(
    /** Context used for the provider, permission check and CameraManager. */
    private val context: Context,
    /** Lifecycle the use cases are bound to; stopping it stops the camera. */
    private val lifecycleOwner: LifecycleOwner,
    /** Surface for the preview use case. */
    private val previewView: PreviewView,
    /** Shared scanner state. */
    private val state: ScannerState,
    /** Frame sink, called on the analysis thread. */
    private val onFrame: (Frame) -> Unit,
) {
    /** Binds and runs until cancelled, reporting failures to [onError] on the main thread. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun run(onError: (ScannerError) -> Unit) {
        try {
            bindAndRun(onError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(ScannerError.BindFailed(e::class.simpleName ?: "Exception"))
        }
    }

    /** Picks a camera, waits if it is busy, binds the use cases and applies controls until cancelled. */
    private suspend fun bindAndRun(onError: (ScannerError) -> Unit) {
        if (!hasCameraPermission()) {
            onError(ScannerError.BindFailed(SecurityException::class.simpleName ?: "SecurityException"))
            return
        }
        val provider = ProcessCameraProvider.awaitInstance(context)
        val cameras = provider.availableCameraInfos.mapNotNull(::toCameraInfo)
        val target = chooseCamera(cameras, state.cameraId)
        if (target == null) {
            onError(ScannerError.NoCamera)
            return
        }
        if (target.facing == CameraFacing.Back) awaitCameraAvailable(target.id)
        val executor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(executor, LumaAnalyzer(state, onFrame)) }
        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, selectorFor(target.id), preview, analysis)
            state.onCameraBound(
                maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f,
                torchAvailable = camera.cameraInfo.hasFlashUnit(),
                cameras = cameras,
            )
            applyControls(camera)
        } finally {
            analysis.clearAnalyzer()
            provider.unbind(preview, analysis)
            executor.shutdown()
        }
    }

    /** Whether the app holds the CAMERA permission (checked only; never requested). */
    private fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Waits up to [ScannerState.busyCameraTimeoutMillis] for [cameraId] to be reported available. */
    private suspend fun awaitCameraAvailable(cameraId: String) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val available = CompletableDeferred<Unit>()
        val callback = object : CameraManager.AvailabilityCallback() {
            /** Completes the wait when our camera frees up (also fires immediately if it already is). */
            override fun onCameraAvailable(id: String) {
                if (id == cameraId) available.complete(Unit)
            }
        }
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        try {
            withTimeoutOrNull(state.busyCameraTimeoutMillis) { available.await() }
        } finally {
            manager.unregisterAvailabilityCallback(callback)
        }
    }

    /** Follows [ScannerState.torchEnabled] and [ScannerState.zoom] until cancelled. */
    private suspend fun applyControls(camera: Camera): Unit = coroutineScope {
        launch {
            snapshotFlow { state.torchEnabled }.collect { enabled ->
                if (state.torchAvailable) camera.cameraControl.enableTorch(enabled)
            }
        }
        snapshotFlow { state.zoom }.collect { camera.cameraControl.setZoomRatio(it) }
    }
}

/** Maps a CameraX camera to our [CameraInfo]; lenses that are neither back nor front are skipped. */
@OptIn(ExperimentalCamera2Interop::class)
private fun toCameraInfo(info: XCameraInfo): CameraInfo? {
    val facing = when (info.lensFacing) {
        CameraSelector.LENS_FACING_BACK -> CameraFacing.Back
        CameraSelector.LENS_FACING_FRONT -> CameraFacing.Front
        else -> return null
    }
    return CameraInfo(Camera2CameraInfo.from(info).cameraId, facing)
}

/** The requested camera if present, else the first back camera, else the first front camera. */
private fun chooseCamera(cameras: List<CameraInfo>, requestedId: String?): CameraInfo? =
    cameras.firstOrNull { it.id == requestedId }
        ?: cameras.firstOrNull { it.facing == CameraFacing.Back }
        ?: cameras.firstOrNull { it.facing == CameraFacing.Front }

/** A selector matching exactly the camera with [cameraId]. */
@OptIn(ExperimentalCamera2Interop::class)
private fun selectorFor(cameraId: String): CameraSelector =
    CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId } }
        .build()

/** Copies each Y plane into a reused buffer and hands it to [onFrame]; drops frames while paused. */
private class LumaAnalyzer(
    /** Read for [ScannerState.paused]. */
    private val state: ScannerState,
    /** Frame sink, called synchronously on the analysis thread. */
    private val onFrame: (Frame) -> Unit,
) : ImageAnalysis.Analyzer {
    /** Reused luminance buffer; safe because [onFrame] finishes before the next frame arrives. */
    private var buffer = ByteArray(0)

    /** Builds a [Frame] from [image] unless paused, then closes the image. */
    override fun analyze(image: ImageProxy) {
        image.use { if (!state.paused) onFrame(toFrame(it)) }
    }

    /** Copies the Y plane, compacting it when the pixel stride is not 1. */
    private fun toFrame(image: ImageProxy): Frame {
        val plane = image.planes[0]
        val source = plane.buffer.duplicate().apply { rewind() }
        return if (plane.pixelStride == 1) {
            val bytes = reuse(source.remaining())
            source.get(bytes, 0, bytes.size)
            Frame(bytes, plane.rowStride, image.width, image.height, image.imageInfo.rotationDegrees)
        } else {
            val bytes = reuse(image.width * image.height)
            copyStrided(source, bytes, image.width, image.height, plane.rowStride, plane.pixelStride)
            Frame(bytes, image.width, image.width, image.height, image.imageInfo.rotationDegrees)
        }
    }

    /** Returns [buffer], reallocated when [size] differs. */
    private fun reuse(size: Int): ByteArray {
        if (buffer.size != size) buffer = ByteArray(size)
        return buffer
    }
}

/** Copies a strided plane into a tightly packed [width]-stride array. */
private fun copyStrided(
    source: ByteBuffer,
    target: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
) {
    for (y in 0 until height) {
        for (x in 0 until width) target[y * width + x] = source.get(y * rowStride + x * pixelStride)
    }
}
