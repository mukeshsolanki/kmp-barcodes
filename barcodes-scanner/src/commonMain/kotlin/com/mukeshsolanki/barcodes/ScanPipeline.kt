@file:OptIn(InternalBarcodesApi::class)

package com.mukeshsolanki.barcodes

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.math.min

/** One camera luminance (Y) plane. The buffer may be reused by the camera after [ScanPipeline.onFrame] returns. */
internal class Frame(
    /** Y bytes, row `y` starting at `y * rowStride`. */
    val luminance: ByteArray,
    /** Bytes between the starts of consecutive rows. */
    val rowStride: Int,
    /** Frame width in pixels. */
    val width: Int,
    /** Frame height in pixels. */
    val height: Int,
    /** Clockwise rotation that makes the frame upright. */
    val rotationDegrees: Int,
)

/** Platform-free scan logic for a single analysis thread: paused-drop, decode, dedupe with gap, auto-zoom stepping. */
internal class ScanPipeline(
    /** Shared scanner state: pause, zoom and policies. */
    private val state: ScannerState,
    /** Requested formats; auto-zoom only runs when QR is among them. */
    private val formats: Set<BarcodeFormat>,
    /** Monotonic clock in milliseconds. */
    private val nowMillis: () -> Long,
    /** Decodes one frame. */
    private val decode: (Frame) -> LuminanceResult,
) {
    /** Text of the last delivered barcode, or null after a resume. */
    private var lastDeliveredText: String? = null

    /** Time of the last successful decode, or null if none since the last resume. */
    private var lastDecodeAt: Long? = null

    /** Time of the last auto-zoom step, or null if none yet. */
    private var lastZoomStepAt: Long? = null

    /** The [ScannerState.resumeGeneration] already handled. */
    private var seenResumeGeneration: Int = state.resumeGeneration

    /** Processes [frame]; returns a barcode to deliver (already deduped) or null. */
    fun onFrame(frame: Frame): Barcode? {
        if (state.paused) return null
        consumeResume()
        return when (val result = decode(frame)) {
            is LuminanceResult.Found -> deliverIfNew(result.barcode)
            is LuminanceResult.QrDetected -> {
                stepZoom(result.moduleSize)
                null
            }
            LuminanceResult.None -> null
        }
    }

    /** Clears dedupe memory once per resume. */
    private fun consumeResume() {
        val generation = state.resumeGeneration
        if (generation == seenResumeGeneration) return
        seenResumeGeneration = generation
        lastDeliveredText = null
        lastDecodeAt = null
    }

    /** Returns [barcode] if its text is new or the dedupe gap has passed, and records the decode time. */
    private fun deliverIfNew(barcode: Barcode): Barcode? {
        val now = nowMillis()
        val previous = lastDecodeAt
        lastDecodeAt = now
        val fresh = barcode.text != lastDeliveredText || previous == null || now - previous >= state.dedupeGapMillis
        if (!fresh) return null
        lastDeliveredText = barcode.text
        return barcode
    }

    /** Zooms in one step when a QR with modules smaller than the threshold is seen, respecting cooldown and caps. */
    private fun stepZoom(moduleSize: Float) {
        val policy = state.autoZoom
        if (!policy.enabled || BarcodeFormat.QrCode !in formats || moduleSize >= policy.minModuleSizePx) return
        val now = nowMillis()
        if (isCoolingDown(now, policy.cooldownMillis)) return
        val current = state.zoom
        val target = min(current * policy.step, min(policy.maxRatio, state.maxZoom))
        if (target > current) {
            lastZoomStepAt = now
            Snapshot.withMutableSnapshot { state.zoom = target }
        }
    }

    /** Whether the last zoom step at [lastZoomStepAt] is less than [cooldownMillis] before [now]. */
    private fun isCoolingDown(now: Long, cooldownMillis: Long): Boolean {
        val last = lastZoomStepAt
        return last != null && now - last < cooldownMillis
    }
}
