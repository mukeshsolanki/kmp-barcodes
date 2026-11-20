package com.mukeshsolanki.barcodes

import com.google.zxing.EncodeHintType
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.google.zxing.pdf417.encoder.Compaction
import kotlin.test.Test

/** Matrix parity of every non-QR format against zxing's MultiFormatWriter, at minimum and scaled sizes. */
class FormatParityTest {

    /** One parity case: our options and the zxing hints these options map to. */
    private class FormatCase(
        /** Name used in failure messages. */
        val tag: String,
        /** Format under test. */
        val format: BarcodeFormat,
        /** Payload to encode. */
        val content: String,
        /** Options passed to our encoder. */
        val options: EncodeOptions?,
        /** Hints passed to zxing. */
        val hints: Map<EncodeHintType, Any>,
    )

    /** Target sizes: zxing's minimum (0x0) and a scaled, non-square box. */
    private val sizes = listOf(0 to 0, 400 to 200)

    /** Quiet zones for linear formats: zxing's default (null), none and a wide one. */
    private val linearMargins = listOf(null, 0, 20)

    /** DataMatrix in every shape, plus the null-options default. */
    private fun dataMatrixCases(): List<FormatCase> {
        val content = "Hello DataMatrix 12345"
        val shapes = mapOf(
            DataMatrixShape.Auto to SymbolShapeHint.FORCE_NONE,
            DataMatrixShape.Square to SymbolShapeHint.FORCE_SQUARE,
            DataMatrixShape.Rectangle to SymbolShapeHint.FORCE_RECTANGLE,
        )
        return listOf(FormatCase("dm-default", BarcodeFormat.DataMatrix, content, null, emptyMap())) +
            shapes.map { (shape, hint) ->
                FormatCase(
                    "dm-$shape",
                    BarcodeFormat.DataMatrix,
                    content,
                    DataMatrixOptions(shape = shape),
                    mapOf(EncodeHintType.DATA_MATRIX_SHAPE to hint),
                )
            }
    }

    /** Aztec defaults, a 50% EC level, and fixed full and compact layer counts. */
    private fun aztecCases(): List<FormatCase> {
        val content = "Hello Aztec 2026"
        val variants = listOf(33 to 0, 50 to 0, 33 to 5, 33 to -4, 23 to -3)
        return listOf(FormatCase("aztec-default", BarcodeFormat.Aztec, content, null, emptyMap())) +
            variants.map { (ec, layers) ->
                FormatCase(
                    "aztec-ec$ec-layers$layers",
                    BarcodeFormat.Aztec,
                    content,
                    AztecOptions(errorCorrectionPercent = ec, layers = layers),
                    mapOf(EncodeHintType.ERROR_CORRECTION to ec, EncodeHintType.AZTEC_LAYERS to layers),
                )
            }
    }

    /** Builds a PDF417 case with all four always-passed hints. */
    private fun pdf417Case(tag: String, content: String, options: Pdf417Options): FormatCase =
        FormatCase(
            tag,
            BarcodeFormat.Pdf417,
            content,
            options,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to options.errorCorrectionLevel,
                EncodeHintType.MARGIN to options.quietZone,
                EncodeHintType.PDF417_COMPACT to options.compact,
                EncodeHintType.PDF417_COMPACTION to Compaction.valueOf(options.compaction.name.uppercase()),
            ),
        )

    /** PDF417 defaults, compact mode, each compaction, every EC level 0..8 and a custom margin. */
    private fun pdf417Cases(): List<FormatCase> {
        val text = "PDF417 TEST 0123456789"
        val digits = "12345678901234567890123456789"
        return listOf(FormatCase("pdf417-default", BarcodeFormat.Pdf417, text, null, emptyMap())) +
            pdf417Case("pdf417-compact", text, Pdf417Options(compact = true)) +
            pdf417Case("pdf417-margin0", text, Pdf417Options(quietZone = 0)) +
            Pdf417Compaction.entries.map { compaction ->
                val content = if (compaction == Pdf417Compaction.Numeric) digits else text
                pdf417Case("pdf417-$compaction", content, Pdf417Options(compaction = compaction))
            } +
            (0..8).map { ec -> pdf417Case("pdf417-ec$ec", text, Pdf417Options(errorCorrectionLevel = ec)) }
    }

    /** Builds a Code128 case: compact always passed, forced code set and margin only when set. */
    private fun code128Case(tag: String, content: String, options: Code128Options): FormatCase =
        FormatCase(
            tag,
            BarcodeFormat.Code128,
            content,
            options,
            buildMap {
                put(EncodeHintType.CODE128_COMPACT, options.compact)
                options.forceCodeSet?.let { put(EncodeHintType.FORCE_CODE_SET, it.name) }
                options.quietZone?.let { put(EncodeHintType.MARGIN, it) }
            },
        )

    /** Code128 defaults, compact, forced A/B/C, each quiet zone, and a forced set that rejects the content. */
    private fun code128Cases(): List<FormatCase> {
        val content = "Code128-Test 42"
        return listOf(FormatCase("code128-default", BarcodeFormat.Code128, content, null, emptyMap())) +
            linearMargins.map { code128Case("code128-margin$it", content, Code128Options(quietZone = it)) } +
            code128Case("code128-compact", content, Code128Options(compact = true)) +
            code128Case("code128-forceA", "CODE128 SET A", Code128Options(forceCodeSet = Code128CodeSet.A)) +
            code128Case("code128-forceB", "Code set b", Code128Options(forceCodeSet = Code128CodeSet.B)) +
            code128Case("code128-forceC", "12345678", Code128Options(forceCodeSet = Code128CodeSet.C)) +
            code128Case("code128-forceC-invalid", "ABC", Code128Options(forceCodeSet = Code128CodeSet.C))
    }

    /** Plain linear formats with default options and each explicit quiet zone. */
    private fun linearCases(): List<FormatCase> {
        val samples = mapOf(
            BarcodeFormat.Code39 to "CODE39 TEST",
            BarcodeFormat.Code93 to "CODE93 TEST",
            BarcodeFormat.Ean8 to "96385074",
            BarcodeFormat.Ean13 to "5901234123457",
            BarcodeFormat.UpcA to "485963095124",
            BarcodeFormat.UpcE to "05096893",
            BarcodeFormat.Itf to "00123456789012",
            BarcodeFormat.Codabar to "A40156789B",
        )
        return samples.flatMap { (format, content) ->
            listOf(FormatCase("$format-default", format, content, null, emptyMap())) +
                linearMargins.map { margin ->
                    FormatCase(
                        "$format-margin$margin",
                        format,
                        content,
                        LinearOptions(quietZone = margin),
                        margin?.let { mapOf(EncodeHintType.MARGIN to it) }.orEmpty(),
                    )
                }
        }
    }

    /** Runs [cases] at every target size against zxing and fails with every mismatch. */
    private fun assertParity(cases: List<FormatCase>) {
        val report = ParityReport()
        cases.forEach { case ->
            sizes.forEach { (width, height) ->
                report.check(
                    "${case.tag} at ${width}x$height",
                    { BarcodeEncoder.encode(case.content, case.format, width, height, case.options) },
                    { zxingEncode(case.content, case.format, width, height, case.hints) },
                )
            }
        }
        report.assertAllPassed()
    }

    /** DataMatrix matches zxing for every shape. */
    @Test
    fun dataMatrixMatchesZxing(): Unit = assertParity(dataMatrixCases())

    /** Aztec matches zxing for default, EC 50 and fixed layers. */
    @Test
    fun aztecMatchesZxing(): Unit = assertParity(aztecCases())

    /** PDF417 matches zxing for default, compact, each compaction and EC 0..8. */
    @Test
    fun pdf417MatchesZxing(): Unit = assertParity(pdf417Cases())

    /** Code128 matches zxing for default, compact and forced code sets. */
    @Test
    fun code128MatchesZxing(): Unit = assertParity(code128Cases())

    /** Code39, Code93, EAN-8/13, UPC-A/E, ITF and Codabar match zxing with and without margins. */
    @Test
    fun linearFormatsMatchZxing(): Unit = assertParity(linearCases())
}
