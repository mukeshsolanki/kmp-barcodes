package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType

/**
 * Builds the exact zxing hint map for [format]; null [options] means that format's defaults.
 * Wrong options class throws.
 */
internal fun hintsFor(format: BarcodeFormat, options: EncodeOptions?): Map<EncodeHintType, Any?> = when (format) {
    BarcodeFormat.QrCode -> qrHints(options.requireOptions<QrOptions>(format) ?: QrOptions())
    BarcodeFormat.DataMatrix -> dataMatrixHints(
        options.requireOptions<DataMatrixOptions>(format) ?: DataMatrixOptions(),
    )
    BarcodeFormat.Aztec -> aztecHints(options.requireOptions<AztecOptions>(format) ?: AztecOptions())
    BarcodeFormat.Pdf417 -> pdf417Hints(options.requireOptions<Pdf417Options>(format) ?: Pdf417Options())
    BarcodeFormat.Code128 -> code128Hints(options.requireOptions<Code128Options>(format) ?: Code128Options())
    else -> marginHint(options.requireOptions<LinearOptions>(format)?.quietZone)
}

/** Returns this as [T], or throws [IllegalArgumentException] when it is a different options class. */
private inline fun <reified T : EncodeOptions> EncodeOptions?.requireOptions(format: BarcodeFormat): T? {
    require(this == null || this is T) { "${this?.let { it::class.simpleName }} cannot configure $format" }
    return this as T?
}

/** QR hints: ERROR_CORRECTION and MARGIN always, CHARACTER_SET and QR_MASK_PATTERN only when set. */
private fun qrHints(options: QrOptions): Map<EncodeHintType, Any?> = buildMap {
    put(EncodeHintType.ERROR_CORRECTION, options.errorCorrection.toZxing())
    put(EncodeHintType.MARGIN, options.quietZone)
    putCharset(options.charset)
    options.maskPattern?.let { put(EncodeHintType.QR_MASK_PATTERN, it) }
}

/** Data Matrix hints: DATA_MATRIX_SHAPE only. */
private fun dataMatrixHints(options: DataMatrixOptions): Map<EncodeHintType, Any?> =
    mapOf(EncodeHintType.DATA_MATRIX_SHAPE to options.shape.toZxing())

/** Aztec hints: ERROR_CORRECTION percent and AZTEC_LAYERS always, CHARACTER_SET when set. */
private fun aztecHints(options: AztecOptions): Map<EncodeHintType, Any?> = buildMap {
    put(EncodeHintType.ERROR_CORRECTION, options.errorCorrectionPercent)
    put(EncodeHintType.AZTEC_LAYERS, options.layers)
    putCharset(options.charset)
}

/** PDF417 hints: ERROR_CORRECTION, MARGIN, PDF417_COMPACT, PDF417_COMPACTION always, CHARACTER_SET when set. */
private fun pdf417Hints(options: Pdf417Options): Map<EncodeHintType, Any?> = buildMap {
    put(EncodeHintType.ERROR_CORRECTION, options.errorCorrectionLevel)
    put(EncodeHintType.MARGIN, options.quietZone)
    put(EncodeHintType.PDF417_COMPACT, options.compact)
    put(EncodeHintType.PDF417_COMPACTION, options.compaction.toZxing())
    putCharset(options.charset)
}

/** Code 128 hints: CODE128_COMPACT always, FORCE_CODE_SET and MARGIN when set. */
private fun code128Hints(options: Code128Options): Map<EncodeHintType, Any?> = buildMap {
    put(EncodeHintType.CODE128_COMPACT, options.compact)
    options.forceCodeSet?.let { put(EncodeHintType.FORCE_CODE_SET, it.name) }
    putAll(marginHint(options.quietZone))
}

/** MARGIN only when [quietZone] is set. */
private fun marginHint(quietZone: Int?): Map<EncodeHintType, Any?> =
    quietZone?.let { mapOf(EncodeHintType.MARGIN to it) }.orEmpty()

/** Adds CHARACTER_SET when [charset] is set. */
private fun MutableMap<EncodeHintType, Any?>.putCharset(charset: BarcodeCharset?) {
    charset?.let { put(EncodeHintType.CHARACTER_SET, it.zxingName) }
}
