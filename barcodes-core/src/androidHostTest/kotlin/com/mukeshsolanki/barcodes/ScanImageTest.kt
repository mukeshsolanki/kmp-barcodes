package com.mukeshsolanki.barcodes

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** scanImage never throws on bad input and downscales large images before decoding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(InternalBarcodesApi::class)
class ScanImageTest {

    /** Encodes [raster] as PNG bytes with the JDK's ImageIO. */
    private fun TestRaster.toPng(): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** A plain white image of the given size, with no barcode in it. */
    private fun blankRaster(width: Int, height: Int): TestRaster =
        TestRaster(IntArray(width * height) { TEST_LIGHT }, width, height)

    /** A 2000x2000 device-id QR, far above the default 500 px maxDimension. */
    private fun largeQrRaster(): TestRaster =
        BarcodeEncoder.encode(SAMPLE_DEVICE_ID, BarcodeFormat.QrCode, 2000, 2000).rasterizeForTest(1, 0)

    /** An empty byte array yields null. */
    @Test
    fun emptyBytesReturnNull() = runTest {
        assertNull(scanImage(ByteArray(0)))
    }

    /** Random bytes, and random bytes behind a PNG signature, yield null. */
    @Test
    fun garbageBytesReturnNull() = runTest {
        val garbage = Random(seed = 42).nextBytes(4096)
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + garbage
        assertNull(scanImage(garbage), "random bytes")
        assertNull(scanImage(fakePng), "PNG signature + random bytes")
    }

    /** A valid 1x1 PNG yields null. */
    @Test
    fun onePixelPngReturnsNull() = runTest {
        assertNull(scanImage(blankRaster(1, 1).toPng()))
    }

    /** A valid PNG with no barcode yields null. */
    @Test
    fun blankPngReturnsNull() = runTest {
        assertNull(scanImage(blankRaster(300, 200).toPng()))
    }

    /** A 2000x2000 QR PNG decodes through scanImage with default options (downscaled to 500 px). */
    @Test
    fun largePngDecodesWithDefaultDownscale() = runTest {
        val barcode = scanImage(largeQrRaster().toPng())
        assertNotNull(barcode, "2000x2000 QR did not decode")
        assertTrue(barcode.text == SAMPLE_DEVICE_ID, "decoded text differs")
    }

    /** A 2000x2000 QR decodes through decodeArgb with the default maxDimension of 500. */
    @Test
    fun largeArgbDecodesWithDefaultDownscale() {
        val raster = largeQrRaster()
        val barcode = BarcodeDecoder.decodeArgb(raster.pixels, raster.width, raster.height, AllFormats, ScanOptions())
        assertNotNull(barcode, "2000x2000 QR did not decode")
        assertTrue(barcode.text == SAMPLE_DEVICE_ID, "decoded text differs")
    }
}
