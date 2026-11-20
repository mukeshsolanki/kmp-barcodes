@file:OptIn(InternalBarcodesApi::class)

package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Dedupe, pause/resume and auto-zoom behaviour of [ScanPipeline] with a fake clock and decoder. */
class ScanPipelineTest {
    /** Holding the same code steady delivers it once. */
    @Test
    fun steadyCodeIsDeliveredOnce() {
        val h = Harness()
        val delivered = (0L..3000L step 100).mapNotNull { h.feed(it, found("A")) }
        assertEquals(listOf("A"), delivered.map { it.text })
    }

    /** A different code is delivered immediately. */
    @Test
    fun differentCodeIsDelivered() {
        val h = Harness()
        assertNotNull(h.feed(0, found("A")))
        assertEquals("B", h.feed(100, found("B"))?.text)
        assertEquals("A", h.feed(200, found("A"))?.text)
    }

    /** No decode for longer than the gap lets the same code through again. */
    @Test
    fun gapLongerThanDedupeRedelivers() {
        val h = Harness()
        assertNotNull(h.feed(0, found("A")))
        assertNull(h.feed(500, LuminanceResult.None))
        assertNull(h.feed(900, found("A")))
        assertNull(h.feed(1500, LuminanceResult.None))
        assertEquals("A", h.feed(1901, found("A"))?.text)
    }

    /** Paused frames never reach the decoder. */
    @Test
    fun pausedFramesSkipDecode() {
        val h = Harness()
        h.state.paused = true
        assertNull(h.feed(0, found("A")))
        assertNull(h.feed(100, found("A")))
        assertEquals(0, h.decodeCalls)
    }

    /** Pause then resume while aiming at the same code delivers it again and resets zoom to 1x. */
    @Test
    fun resumeRedeliversAndResetsZoom() {
        val h = Harness()
        assertNotNull(h.feed(0, found("A")))
        assertNull(h.feed(100, found("A")))
        h.state.zoom = 3f
        h.state.paused = true
        assertNull(h.feed(200, found("A")))
        h.state.paused = false
        assertEquals(1f, h.state.zoom)
        assertEquals("A", h.feed(300, found("A"))?.text)
        assertNull(h.feed(400, found("A")))
    }

    /** Small QR modules step zoom by 1.5x, honouring the cooldown and the maxRatio cap. */
    @Test
    fun autoZoomStepsWithCooldownAndMaxRatio() {
        val h = Harness()
        h.feed(0, small)
        assertEquals(1.5f, h.state.zoom, TOLERANCE)
        h.feed(100, small)
        assertEquals(1.5f, h.state.zoom, TOLERANCE)
        h.feed(500, small)
        assertEquals(2.25f, h.state.zoom, TOLERANCE)
        h.feed(1000, small)
        assertEquals(3.375f, h.state.zoom, TOLERANCE)
        h.feed(1500, small)
        assertEquals(4f, h.state.zoom, TOLERANCE)
        h.feed(2000, small)
        assertEquals(4f, h.state.zoom, TOLERANCE)
    }

    /** The device max zoom caps auto-zoom below maxRatio. */
    @Test
    fun autoZoomRespectsDeviceMaxZoom() {
        val h = Harness(maxZoom = 2f)
        h.feed(0, small)
        h.feed(500, small)
        h.feed(1000, small)
        assertEquals(2f, h.state.zoom, TOLERANCE)
    }

    /** Modules at or above the threshold never zoom. */
    @Test
    fun largeModulesDoNotZoom() {
        val h = Harness()
        h.feed(0, LuminanceResult.QrDetected(3f))
        assertEquals(1f, h.state.zoom)
    }

    /** Disabled auto-zoom never changes zoom. */
    @Test
    fun disabledAutoZoomNeverZooms() {
        val h = Harness(autoZoom = AutoZoom(enabled = false))
        (0L..3000L step 500).forEach { h.feed(it, small) }
        assertEquals(1f, h.state.zoom)
    }

    /** Found results pass the decoded barcode through unchanged. */
    @Test
    fun deliversTheDecodedInstance() {
        val h = Harness()
        val barcode = Barcode("X", null, BarcodeFormat.QrCode, emptyList())
        assertSame(barcode, h.feed(0, LuminanceResult.Found(barcode)))
    }

    /** A pipeline over a real [ScannerState] with a fake clock and a scripted decoder. */
    private class Harness(autoZoom: AutoZoom = AutoZoom(), maxZoom: Float = 8f) {
        /** Fake clock value in milliseconds. */
        var now = 0L

        /** Result the fake decoder returns next. */
        var next: LuminanceResult = LuminanceResult.None

        /** Number of decoder invocations. */
        var decodeCalls = 0

        /** State under test, bound to a fake camera. */
        val state = ScannerState(null, autoZoom, dedupeGapMillis = 1000, busyCameraTimeoutMillis = 2000).apply {
            onCameraBound(maxZoom = maxZoom, torchAvailable = true, cameras = emptyList())
        }

        /** Pipeline under test. */
        val pipeline = ScanPipeline(state, setOf(BarcodeFormat.QrCode), { now }) {
            decodeCalls++
            next
        }

        /** Feeds one frame at time [at] that decodes to [result]. */
        fun feed(at: Long, result: LuminanceResult): Barcode? {
            now = at
            next = result
            return pipeline.onFrame(frame)
        }
    }

    /** Fixtures. */
    private companion object {
        /** Float comparison tolerance. */
        const val TOLERANCE = 1e-4f

        /** A dummy 1x1 frame; the fake decoder ignores it. */
        val frame = Frame(ByteArray(1), 1, 1, 1, 0)

        /** A QR seen with modules below the default 3 px threshold. */
        val small = LuminanceResult.QrDetected(2f)

        /** A found QR barcode with [text]. */
        fun found(text: String) = LuminanceResult.Found(Barcode(text, null, BarcodeFormat.QrCode, emptyList()))
    }
}
