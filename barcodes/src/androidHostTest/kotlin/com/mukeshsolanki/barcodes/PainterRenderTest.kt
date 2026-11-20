package com.mukeshsolanki.barcodes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Painters render to bitmaps that decode back to their content, and export to valid image files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PainterRenderTest {

    /** Text encoded by every painter in this test. */
    private val text = "https://example.com/kmp-barcodes?id=42"

    /** The default QR style renders and decodes back to the same text. */
    @Test
    fun defaultQrStyleRoundTrips() = runBlocking {
        val bitmap = BarcodePainter(text).toImageBitmap(400, 400)
        assertEquals(text, scanImage(bitmap)?.text)
    }

    /** A logo over cleared modules still decodes at error correction H. */
    @Test
    fun logoWithClearedModulesDecodes() = runBlocking {
        val style = QrStyle(logo = Logo(ColorPainter(Color.Red), size = 0.2f, clearModules = true))
        val bitmap = BarcodePainter(text, options = QrOptions(errorCorrection = ErrorCorrection.H), style = style)
            .toImageBitmap(400, 400)
        assertEquals(text, scanImage(bitmap)?.text)
    }

    /** A circular code with rounded modules and a circular, accurately padded logo still decodes. */
    @Test
    fun circleCodeWithAccurateLogoDecodes() = runBlocking {
        val style = QrStyle(
            darkPixel = PixelShape.RoundCorners(),
            frame = FrameShape.RoundCorners(),
            ball = BallShape.Circle,
            codeShape = CodeShape.Circle,
            logo = Logo(
                painter = ColorPainter(Color.Blue),
                size = 0.2f,
                padding = LogoPadding.Accurate(0.1f),
                shape = LogoShape.Circle,
            ),
        )
        val bitmap = BarcodePainter(text, options = QrOptions(errorCorrection = ErrorCorrection.H), style = style)
            .toImageBitmap(600, 600)
        assertEquals(text, scanImage(bitmap)?.text)
    }

    /** A circular code renders the same pixels every time. */
    @Test
    fun circleCodeIsDeterministic() {
        val style = QrStyle(codeShape = CodeShape.Circle)
        assertTrue(render(style).contentEquals(render(style)))
    }

    /** A linear format with a QR style falls back to the plain renderer and decodes. */
    @Test
    fun code128WithQrStyleDecodes() = runBlocking {
        val bitmap = BarcodePainter("KMP-0042", BarcodeFormat.Code128).toImageBitmap(600, 200)
        assertEquals("KMP-0042", scanImage(bitmap, setOf(BarcodeFormat.Code128))?.text)
    }

    /** A blank bitmap decodes to null instead of throwing. */
    @Test
    fun blankBitmapScansToNull() = runBlocking {
        val blank = SimpleStyle(dark = BarcodeBrush.Solid(Color.White))
        val bitmap = BarcodePainter(text, style = blank).toImageBitmap(64, 64)
        assertNull(scanImage(bitmap))
    }

    /** PNG export starts with the PNG signature. */
    @Test
    fun pngExportHasPngSignature() {
        val bytes = BarcodePainter(text).toByteArray(256, 256, ImageFormat.Png)
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertTrue(bytes.copyOfRange(0, signature.size).contentEquals(signature))
    }

    /** JPEG export starts with the SOI marker. */
    @Test
    fun jpegExportHasSoiMarker() {
        val bytes = BarcodePainter(text).toByteArray(256, 256, ImageFormat.Jpeg, quality = 90)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    /** Renders a painter with [style] to 300x300 ARGB pixels. */
    private fun render(style: QrStyle): IntArray {
        val bitmap = BarcodePainter(text, style = style).toImageBitmap(300, 300)
        return IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
    }
}
