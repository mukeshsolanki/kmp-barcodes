package com.mukeshsolanki.barcodes

import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.google.zxing.BarcodeFormat as ZxingFormat

/** Raster exports must equal zxing's BitMatrix pixel for pixel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapExportTest {

    /** Payload shaped like a Wi-Fi provisioning code. */
    private val payload = "WIFI:T:WPA;S:Camera-Setup;P:correct horse battery;;"

    /** A 1200 px Android Bitmap matches zxing. */
    @Test
    fun bitmapAt1200MatchesZxing() = assertBitmapMatches(1200)

    /** A 512 px Android Bitmap matches zxing. */
    @Test
    fun bitmapAt512MatchesZxing() = assertBitmapMatches(512)

    /** A 1200 px ImageBitmap matches zxing. */
    @Test
    fun imageBitmapAt1200MatchesZxing() = assertImageBitmapMatches(1200)

    /** A 512 px ImageBitmap matches zxing. */
    @Test
    fun imageBitmapAt512MatchesZxing() = assertImageBitmapMatches(512)

    /** Compares [BarcodeMatrix.toBitmap] at [size] px with zxing's matrix. */
    private fun assertBitmapMatches(size: Int) {
        val bitmap = ourMatrix(size).toBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertPixelsMatch(zxingMatrix(size), bitmap.width, bitmap.height, pixels)
    }

    /** Compares [BarcodeMatrix.toImageBitmap] at [size] px with zxing's matrix. */
    private fun assertImageBitmapMatches(size: Int) {
        val bitmap = ourMatrix(size).toImageBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        assertPixelsMatch(zxingMatrix(size), bitmap.width, bitmap.height, pixels)
    }

    /** Our QR matrix at [size] px with a one-module quiet zone. */
    private fun ourMatrix(size: Int): BarcodeMatrix =
        BarcodeEncoder.encode(payload, BarcodeFormat.QrCode, size, size, QrOptions(quietZone = 1))

    /** zxing's QR matrix at [size] px with the same hints our encoder passes. */
    private fun zxingMatrix(size: Int): BitMatrix = MultiFormatWriter().encode(
        payload,
        ZxingFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1),
    )

    /** Asserts every pixel is black where [expected] is set and white elsewhere. */
    private fun assertPixelsMatch(expected: BitMatrix, width: Int, height: Int, pixels: IntArray) {
        assertEquals(expected.width, width)
        assertEquals(expected.height, height)
        val mismatches = (0 until width * height).filter { index ->
            val want = if (expected[index % width, index / width]) Color.BLACK else Color.WHITE
            pixels[index] != want
        }
        assertEquals(emptyList(), mismatches.take(10), "${mismatches.size} pixels differ from zxing")
    }
}
