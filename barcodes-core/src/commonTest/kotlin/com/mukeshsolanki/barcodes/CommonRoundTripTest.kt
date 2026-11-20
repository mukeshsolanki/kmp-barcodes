package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** ARGB pixel buffer produced from a [BarcodeMatrix] for decoder tests. */
internal class TestRaster(
    /** Row-major ARGB pixels. */
    val pixels: IntArray,
    /** Width in pixels. */
    val width: Int,
    /** Height in pixels. */
    val height: Int,
)

/** Opaque black ARGB value used for dark modules. */
internal const val TEST_DARK: Int = -0x1000000

/** Opaque white ARGB value used for light modules and padding. */
internal const val TEST_LIGHT: Int = -0x1

/** Formats rendered as a single row of bars rather than a 2D grid. */
internal val TEST_LINEAR_FORMATS: Set<BarcodeFormat> = BarcodeFormat.entries.toSet() -
    setOf(BarcodeFormat.QrCode, BarcodeFormat.DataMatrix, BarcodeFormat.Aztec, BarcodeFormat.Pdf417)

/** One readable sample payload per format; each decodes back to exactly the same text. */
internal val roundTripSamples: Map<BarcodeFormat, String> = mapOf(
    BarcodeFormat.QrCode to "HomeNet-5G\nS3cr3t-Pa55word!\n9f8e7d6c5b4a39281706f5e4d3c2b1a0\nap-south-1\nprod",
    BarcodeFormat.DataMatrix to "Hello DataMatrix 12345",
    BarcodeFormat.Aztec to "Hello Aztec 2026",
    BarcodeFormat.Pdf417 to "PDF417 round trip 0123456789",
    BarcodeFormat.UpcA to "485963095124",
    BarcodeFormat.UpcE to "05096893",
    BarcodeFormat.Ean8 to "96385074",
    BarcodeFormat.Ean13 to "5901234123457",
    BarcodeFormat.Code128 to "Code128-Test 42",
    BarcodeFormat.Code93 to "CODE93 TEST",
    BarcodeFormat.Code39 to "CODE39 TEST",
    BarcodeFormat.Codabar to "40156789",
    BarcodeFormat.Itf to "00123456789012",
)

/** Encodes [content] at minimum size, giving linear formats a bar height so every row is scannable. */
internal fun encodeForRoundTrip(content: String, format: BarcodeFormat): BarcodeMatrix =
    BarcodeEncoder.encode(content, format, 0, if (format in TEST_LINEAR_FORMATS) LINEAR_BAR_HEIGHT else 0)

/** Bar height in modules used when encoding linear formats for round trips. */
private const val LINEAR_BAR_HEIGHT = 30

/** Expands each cell to a [scale]x[scale] block and pads the result with [quietZone] cells of white. */
internal fun BarcodeMatrix.rasterizeForTest(scale: Int, quietZone: Int): TestRaster {
    val outWidth = (width + 2 * quietZone) * scale
    val outHeight = (height + 2 * quietZone) * scale
    val pixels = IntArray(outWidth * outHeight) { index ->
        val cellX = index % outWidth / scale - quietZone
        val cellY = index / outWidth / scale - quietZone
        if (isDarkCell(cellX, cellY)) TEST_DARK else TEST_LIGHT
    }
    return TestRaster(pixels, outWidth, outHeight)
}

/** True when ([x], [y]) is inside the matrix and set. */
private fun BarcodeMatrix.isDarkCell(x: Int, y: Int): Boolean =
    x in 0 until width && y in 0 until height && this[x, y]

/** Common (Android host + iOS simulator) round trip: our encoder output must decode back through our decoder. */
@OptIn(InternalBarcodesApi::class)
class CommonRoundTripTest {

    /** Formats exercised on every platform; the JVM-only RoundTripTest covers the rest. */
    private val commonFormats = listOf(
        BarcodeFormat.QrCode,
        BarcodeFormat.DataMatrix,
        BarcodeFormat.Aztec,
        BarcodeFormat.Pdf417,
        BarcodeFormat.Code128,
        BarcodeFormat.Ean13,
    )

    /** Each common format decodes back to its original text and reports the right format. */
    @Test
    fun encodedMatrixDecodesBackToSameText() {
        commonFormats.forEach { format ->
            val content = roundTripSamples.getValue(format)
            val raster = encodeForRoundTrip(content, format).rasterizeForTest(scale = 4, quietZone = 10)
            val barcode = BarcodeDecoder.decodeArgb(
                raster.pixels,
                raster.width,
                raster.height,
                setOf(format),
                ScanOptions(maxDimension = null, tryHarder = true),
            )
            assertNotNull(barcode, "$format: our decoder returned null")
            assertTrue(content == barcode.text, "$format: decoded text differs")
            assertEquals(format, barcode.format, "$format: decoded format differs")
        }
    }
}
