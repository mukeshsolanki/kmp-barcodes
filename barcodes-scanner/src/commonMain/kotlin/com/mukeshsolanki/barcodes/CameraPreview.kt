package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform camera + preview. Calls [onFrame] on a background thread, applies [ScannerState.torchEnabled] and
 * [ScannerState.zoom] live, calls [ScannerState.onCameraBound], and releases the camera on dispose and lifecycle stop.
 */
@Composable
internal expect fun CameraPreview(
    state: ScannerState,
    onFrame: (Frame) -> Unit,
    onError: (ScannerError) -> Unit,
    modifier: Modifier,
)
