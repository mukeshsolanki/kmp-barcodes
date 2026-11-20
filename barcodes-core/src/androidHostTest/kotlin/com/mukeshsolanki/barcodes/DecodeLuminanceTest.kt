package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Camera-plane decoding: tiny-QR detection for auto-zoom, blank planes, and rotated 1D codes. */
@OptIn(InternalBarcodesApi::class)
class DecodeLuminanceTest {

    /** Width of the synthetic camera plane in pixels. */
    private val planeWidth = 240

    /** Height of the synthetic camera plane in pixels. */
    private val planeHeight = 200

    /** Row stride larger than the width, so the padding bytes must be skipped. */
    private val rowStride = 256

    /** Builds a Y plane of [height] rows at [rowStride]; padding bytes are black so ignoring the stride breaks decoding. */
    private fun plane(height: Int, shade: (x: Int, y: Int) -> Int): ByteArray =
        ByteArray(rowStride * height) { index ->
            val x = index % rowStride
            if (x < planeWidth) shade(x, index / rowStride).toByte() else 0
        }

    /** Sharp luminance of a QR whose modules are [moduleSize] px, drawn from the plane's origin offset. */
    private fun BarcodeMatrix.sharpShade(x: Int, y: Int, moduleSize: Float, offset: Int): Int {
        val cellX = ((x - offset) / moduleSize).toInt()
        val cellY = ((y - offset) / moduleSize).toInt()
        val inside = x >= offset && y >= offset && cellX < width && cellY < height
        return if (inside && this[cellX, cellY]) 0 else WHITE
    }

    /** 3x3 box blur of [sharp] at ([x], [y]), simulating a slightly out-of-focus camera. */
    private fun blurred(x: Int, y: Int, sharp: (Int, Int) -> Int): Int =
        (-1..1).sumOf { dy -> (-1..1).sumOf { dx -> sharp(x + dx, y + dy) } } / 9

    /** A pairing QR with ~2.5 px modules, blurred, is either decoded or reported as detected with a small module size. */
    @Test
    fun tinyBlurredQrIsFoundOrDetected() {
        val matrix = BarcodeEncoder.encode(pairingPayload("HomeNet-5G", "S3cr3t-Pa55word!"), BarcodeFormat.QrCode)
        val luminance = plane(planeHeight) { x, y ->
            blurred(x, y) { sx, sy -> matrix.sharpShade(sx, sy, moduleSize = 2.5f, offset = 20) }
        }
        val result = BarcodeDecoder.decodeLuminance(
            luminance, rowStride, planeWidth, planeHeight, 0, setOf(BarcodeFormat.QrCode),
        )
        when (result) {
            is LuminanceResult.Found -> assertEquals(BarcodeFormat.QrCode, result.barcode.format)
            is LuminanceResult.QrDetected -> assertTrue(result.moduleSize in 0f..3f, "module size ${result.moduleSize}")
            LuminanceResult.None -> fail("tiny QR was neither decoded nor detected")
        }
    }

    /** A blank plane yields None for every format. */
    @Test
    fun blankPlaneReturnsNone() {
        val luminance = plane(planeHeight) { _, _ -> WHITE }
        val result = BarcodeDecoder.decodeLuminance(luminance, rowStride, planeWidth, planeHeight, 0, AllFormats)
        assertEquals(LuminanceResult.None, result)
    }

    /** A Code128 drawn with horizontal bars (camera sensor orientation) decodes when rotationDegrees is 90. */
    @Test
    fun verticalCode128DecodesWithRotation() {
        val content = "ROT90-128"
        val bars = BarcodeEncoder.encode(content, BarcodeFormat.Code128)
        val modulePx = 2
        val height = bars.width * modulePx + 2 * VERTICAL_MARGIN
        val luminance = plane(height) { x, y ->
            val cell = (y - VERTICAL_MARGIN) / modulePx
            val onBar = x in 40 until planeWidth - 40 && y >= VERTICAL_MARGIN && cell < bars.width && bars[cell, 0]
            if (onBar) 0 else WHITE
        }
        val result = BarcodeDecoder.decodeLuminance(
            luminance, rowStride, planeWidth, height, 90, setOf(BarcodeFormat.Code128),
        )
        val found = assertIs<LuminanceResult.Found>(result, "vertical Code128 not decoded with rotation 90")
        assertEquals(content, found.barcode.text)
        assertEquals(BarcodeFormat.Code128, found.barcode.format)
    }

    /** Constants for plane synthesis. */
    private companion object {
        /** Full-white luminance value. */
        const val WHITE = 255

        /** White rows above and below the vertical barcode. */
        const val VERTICAL_MARGIN = 20
    }
}
