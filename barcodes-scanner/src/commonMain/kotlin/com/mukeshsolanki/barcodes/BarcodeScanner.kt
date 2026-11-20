@file:OptIn(InternalBarcodesApi::class)

package com.mukeshsolanki.barcodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlin.time.TimeSource

/**
 * Live camera scanner that draws only the preview. [onResult] gets the first decoded barcode on the main thread,
 * once per new value (see [ScannerState.dedupeGapMillis]). Camera permission must already be granted.
 */
@Composable
public fun BarcodeScanner(
    state: ScannerState,
    formats: Set<BarcodeFormat>,
    onResult: (Barcode) -> Unit,
    modifier: Modifier = Modifier,
    onError: (ScannerError) -> Unit = {},
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val results = remember { Channel<Barcode>(Channel.UNLIMITED) }
    val pipeline = remember(state, formats) {
        ScanPipeline(state, formats, monotonicMillis()) { decodeFrame(it, formats) }
    }
    val onFrame: (Frame) -> Unit = remember(pipeline, results) {
        { frame -> pipeline.onFrame(frame)?.let(results::trySend) }
    }
    val onCameraError: (ScannerError) -> Unit = remember { { currentOnError(it) } }
    LaunchedEffect(results) {
        for (barcode in results) currentOnResult(barcode)
    }
    CameraPreview(state, onFrame, onCameraError, modifier)
}

/** Decodes one camera frame with the core luminance decoder. */
private fun decodeFrame(frame: Frame, formats: Set<BarcodeFormat>): LuminanceResult =
    BarcodeDecoder.decodeLuminance(
        frame.luminance,
        frame.rowStride,
        frame.width,
        frame.height,
        frame.rotationDegrees,
        formats,
    )

/** A monotonic millisecond clock starting at 0 now. */
private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
