package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.SymbolShapeHint
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder.Compaction
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies [hintsFor] produces exactly the zxing hints these options map to. */
class EncodeOptionsMappingTest {
    /** Default QR options: EC L and margin 4 only, no charset or mask. */
    @Test
    fun qrDefaults() {
        val expected = mapOf<EncodeHintType, Any?>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 4,
        )
        assertEquals(expected, hintsFor(BarcodeFormat.QrCode, QrOptions()))
        assertEquals(expected, hintsFor(BarcodeFormat.QrCode, null))
    }

    /** Fully specified QR options. */
    @Test
    fun qrAllSet() {
        assertEquals(
            mapOf<EncodeHintType, Any?>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.QR_MASK_PATTERN to 3,
            ),
            hintsFor(BarcodeFormat.QrCode, QrOptions(ErrorCorrection.H, BarcodeCharset.Utf8, 3, 1)),
        )
    }

    /** Charset names match zxing's expectations. */
    @Test
    fun charsetNames() {
        val names = BarcodeCharset.entries.map {
            hintsFor(BarcodeFormat.QrCode, QrOptions(charset = it))[EncodeHintType.CHARACTER_SET]
        }
        assertEquals(listOf("UTF-8", "ISO-8859-1", "US-ASCII", "Shift_JIS"), names)
    }

    /** Data Matrix shapes map to SymbolShapeHint. */
    @Test
    fun dataMatrix() {
        assertEquals(
            mapOf<EncodeHintType, Any?>(EncodeHintType.DATA_MATRIX_SHAPE to SymbolShapeHint.FORCE_NONE),
            hintsFor(BarcodeFormat.DataMatrix, null),
        )
        assertEquals(
            SymbolShapeHint.FORCE_SQUARE,
            hintsFor(BarcodeFormat.DataMatrix, DataMatrixOptions(DataMatrixShape.Square))[EncodeHintType.DATA_MATRIX_SHAPE],
        )
        assertEquals(
            mapOf<EncodeHintType, Any?>(EncodeHintType.DATA_MATRIX_SHAPE to SymbolShapeHint.FORCE_RECTANGLE),
            hintsFor(BarcodeFormat.DataMatrix, DataMatrixOptions(DataMatrixShape.Rectangle)),
        )
    }

    /** Aztec percent and layers are always passed. */
    @Test
    fun aztec() {
        assertEquals(
            mapOf<EncodeHintType, Any?>(EncodeHintType.ERROR_CORRECTION to 33, EncodeHintType.AZTEC_LAYERS to 0),
            hintsFor(BarcodeFormat.Aztec, null),
        )
        assertEquals(
            mapOf<EncodeHintType, Any?>(EncodeHintType.ERROR_CORRECTION to 50, EncodeHintType.AZTEC_LAYERS to -2),
            hintsFor(BarcodeFormat.Aztec, AztecOptions(50, -2)),
        )
    }

    /** PDF417 always passes EC, margin, compact and compaction. */
    @Test
    fun pdf417() {
        assertEquals(
            mapOf<EncodeHintType, Any?>(
                EncodeHintType.ERROR_CORRECTION to 2,
                EncodeHintType.MARGIN to 30,
                EncodeHintType.PDF417_COMPACT to false,
                EncodeHintType.PDF417_COMPACTION to Compaction.AUTO,
            ),
            hintsFor(BarcodeFormat.Pdf417, null),
        )
        val compactions = Pdf417Compaction.entries.map {
            hintsFor(BarcodeFormat.Pdf417, Pdf417Options(compaction = it))[EncodeHintType.PDF417_COMPACTION]
        }
        assertEquals(listOf(Compaction.AUTO, Compaction.TEXT, Compaction.BYTE, Compaction.NUMERIC), compactions)
    }

    /** Code 128 passes compact always, code set and margin only when set. */
    @Test
    fun code128() {
        assertEquals(
            mapOf<EncodeHintType, Any?>(EncodeHintType.CODE128_COMPACT to false),
            hintsFor(BarcodeFormat.Code128, null),
        )
        assertEquals(
            mapOf<EncodeHintType, Any?>(
                EncodeHintType.CODE128_COMPACT to true,
                EncodeHintType.FORCE_CODE_SET to "C",
                EncodeHintType.MARGIN to 5,
            ),
            hintsFor(BarcodeFormat.Code128, Code128Options(true, Code128CodeSet.C, 5)),
        )
    }

    /** Linear formats pass MARGIN only when a quiet zone is given. */
    @Test
    fun linear() {
        val linear = AllFormats - setOf(
            BarcodeFormat.QrCode,
            BarcodeFormat.DataMatrix,
            BarcodeFormat.Aztec,
            BarcodeFormat.Pdf417,
            BarcodeFormat.Code128,
        )
        linear.forEach {
            assertEquals(emptyMap(), hintsFor(it, null))
            assertEquals(emptyMap(), hintsFor(it, LinearOptions()))
            assertEquals(mapOf<EncodeHintType, Any?>(EncodeHintType.MARGIN to 7), hintsFor(it, LinearOptions(7)))
        }
    }

    /** Options of the wrong class are rejected. */
    @Test
    fun wrongOptionsClassThrows() {
        assertFailsWith<IllegalArgumentException> { hintsFor(BarcodeFormat.QrCode, LinearOptions()) }
        assertFailsWith<IllegalArgumentException> { hintsFor(BarcodeFormat.Ean13, QrOptions()) }
        assertFailsWith<IllegalArgumentException> { hintsFor(BarcodeFormat.Code128, LinearOptions()) }
        assertFailsWith<IllegalArgumentException> {
            BarcodeEncoder.encode("x", BarcodeFormat.Aztec, options = Pdf417Options())
        }
    }
}
