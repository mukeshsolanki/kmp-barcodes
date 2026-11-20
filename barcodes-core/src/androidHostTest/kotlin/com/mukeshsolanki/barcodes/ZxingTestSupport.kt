package com.mukeshsolanki.barcodes

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.BarcodeFormat as ZxingFormat

/** Maps our public format to zxing's enum constant. */
internal fun BarcodeFormat.toZxing(): ZxingFormat = when (this) {
    BarcodeFormat.QrCode -> ZxingFormat.QR_CODE
    BarcodeFormat.DataMatrix -> ZxingFormat.DATA_MATRIX
    BarcodeFormat.Aztec -> ZxingFormat.AZTEC
    BarcodeFormat.Pdf417 -> ZxingFormat.PDF_417
    BarcodeFormat.UpcA -> ZxingFormat.UPC_A
    BarcodeFormat.UpcE -> ZxingFormat.UPC_E
    BarcodeFormat.Ean8 -> ZxingFormat.EAN_8
    BarcodeFormat.Ean13 -> ZxingFormat.EAN_13
    BarcodeFormat.Code128 -> ZxingFormat.CODE_128
    BarcodeFormat.Code93 -> ZxingFormat.CODE_93
    BarcodeFormat.Code39 -> ZxingFormat.CODE_39
    BarcodeFormat.Codabar -> ZxingFormat.CODABAR
    BarcodeFormat.Itf -> ZxingFormat.ITF
}

/** The Java charset name passed as zxing's CHARACTER_SET hint. */
internal fun BarcodeCharset.zxingName(): String = when (this) {
    BarcodeCharset.Utf8 -> "UTF-8"
    BarcodeCharset.Iso8859_1 -> "ISO-8859-1"
    BarcodeCharset.UsAscii -> "US-ASCII"
    BarcodeCharset.ShiftJis -> "Shift_JIS"
}

/** Builds the zxing QR hints [options] map to: EC and margin always, charset and mask only when set. */
internal fun qrHints(options: QrOptions): Map<EncodeHintType, Any> = buildMap {
    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(options.errorCorrection.name))
    put(EncodeHintType.MARGIN, options.quietZone)
    options.charset?.let { put(EncodeHintType.CHARACTER_SET, it.zxingName()) }
    options.maskPattern?.let { put(EncodeHintType.QR_MASK_PATTERN, it) }
}

/** Encodes with zxing's MultiFormatWriter, the reference for every parity check. */
internal fun zxingEncode(
    content: String,
    format: BarcodeFormat,
    width: Int,
    height: Int,
    hints: Map<EncodeHintType, Any>,
): BitMatrix = MultiFormatWriter().encode(content, format.toZxing(), width, height, hints)

/**
 * Runs both encoders and returns null on parity, or a description of the difference.
 * When zxing rejects the input ours must throw IllegalArgumentException. Never includes payload text.
 */
internal fun parityFailure(ours: () -> BarcodeMatrix, theirs: () -> BitMatrix): String? {
    val expected = runCatching(theirs)
    val actual = runCatching(ours)
    return when {
        expected.isFailure -> rejectionMismatch(expected.exceptionOrNull(), actual)
        actual.isFailure -> "ours threw ${actual.exceptionOrNull()?.javaClass?.simpleName} but zxing encoded"
        else -> cellMismatch(expected.getOrThrow(), actual.getOrThrow())
    }
}

/** Describes how our result differs when zxing threw [zxingError]; null if ours threw IllegalArgumentException too. */
private fun rejectionMismatch(zxingError: Throwable?, actual: Result<BarcodeMatrix>): String? = when {
    actual.isSuccess -> "zxing threw ${zxingError?.javaClass?.simpleName} but ours encoded"
    actual.exceptionOrNull() is IllegalArgumentException -> null
    else -> "zxing threw ${zxingError?.javaClass?.simpleName}, ours threw ${actual.exceptionOrNull()?.javaClass?.simpleName}"
}

/** Null when [actual] equals [expected] cell for cell, otherwise the size or first differing cell and the diff count. */
internal fun cellMismatch(expected: BitMatrix, actual: BarcodeMatrix): String? {
    if (expected.width != actual.width || expected.height != actual.height) {
        return "size ${actual.width}x${actual.height}, zxing ${expected.width}x${expected.height}"
    }
    val diffs = (0 until expected.height).flatMap { y ->
        (0 until expected.width).filter { x -> expected[x, y] != actual[x, y] }.map { x -> x to y }
    }
    return diffs.firstOrNull()?.let { (x, y) -> "${diffs.size} cells differ, first at ($x,$y)" }
}

/** Collects parity failures and fails the test with all of them at the end. */
internal class ParityReport {
    /** Labelled failure descriptions recorded so far. */
    private val failures = mutableListOf<String>()

    /** Number of comparisons performed. */
    private var checks = 0

    /** Compares one case; [label] must identify the case without payload text. */
    fun check(label: String, ours: () -> BarcodeMatrix, theirs: () -> BitMatrix) {
        checks++
        parityFailure(ours, theirs)?.let { failures += "$label: $it" }
    }

    /** Fails with up to [MAX_LISTED] failure lines when any check failed. */
    fun assertAllPassed() {
        kotlin.test.assertTrue(checks > 0, "no parity checks ran")
        kotlin.test.assertTrue(
            failures.isEmpty(),
            "${failures.size}/$checks parity checks failed:\n" + failures.take(MAX_LISTED).joinToString("\n"),
        )
    }

    /** Companion constants for report formatting. */
    private companion object {
        /** Maximum number of failures printed in the assertion message. */
        const val MAX_LISTED = 40
    }
}

/** Decodes [raster] with zxing's MultiFormatReader restricted to [format], returning null when nothing reads. */
internal fun zxingDecodeText(raster: TestRaster, format: BarcodeFormat): String? = runCatching {
    val source = RGBLuminanceSource(raster.width, raster.height, raster.pixels)
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(format.toZxing()),
        DecodeHintType.TRY_HARDER to true,
    )
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
}.getOrNull()
