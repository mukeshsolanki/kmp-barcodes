package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIView

/** AVFoundation camera preview that feeds Y-plane frames to [onFrame] and follows [state] torch, zoom and camera. */
@Composable
internal actual fun CameraPreview(
    state: ScannerState,
    onFrame: (Frame) -> Unit,
    onError: (ScannerError) -> Unit,
    modifier: Modifier,
) {
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    val camera = remember(state) {
        IosCamera(state = state, onFrame = { currentOnFrame(it) }, onError = { currentOnError(it) })
    }
    val cameraId = state.cameraId
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(camera, cameraId, lifecycleOwner) {
        camera.bind(cameraId)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> camera.start()
                Lifecycle.Event.ON_STOP -> camera.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            camera.release()
        }
    }
    LaunchedEffect(camera) {
        snapshotFlow { state.torchEnabled to state.zoom }.collect { (torch, zoom) -> camera.updateControls(torch, zoom) }
    }
    UIKitView(factory = { PreviewView(camera) }, modifier = modifier)
}

/** Hosts the camera's preview layer, keeps it sized to the view and reports the UI orientation. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(
    /** Camera whose preview layer is hosted and which receives orientation updates. */
    private val camera: IosCamera,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        layer.addSublayer(camera.previewLayer)
    }

    /** Resizes the preview layer without implicit animation and forwards the current UI orientation. */
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        camera.previewLayer.frame = bounds
        CATransaction.commit()
        camera.updateOrientation(window?.windowScene?.interfaceOrientation ?: UIInterfaceOrientationPortrait)
    }
}
