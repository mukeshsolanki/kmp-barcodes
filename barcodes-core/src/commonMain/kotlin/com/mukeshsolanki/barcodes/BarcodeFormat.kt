package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat as ZxingFormat

/** Barcode symbologies supported for both encoding and decoding. */
public enum class BarcodeFormat {
    /** QR Code (2D). */
    QrCode,

    /** Data Matrix (2D). */
    DataMatrix,

    /** Aztec (2D). */
    Aztec,

    /** PDF417 (stacked linear). */
    Pdf417,

    /** UPC-A (1D). */
    UpcA,

    /** UPC-E (1D). */
    UpcE,

    /** EAN-8 (1D). */
    Ean8,

    /** EAN-13 (1D). */
    Ean13,

    /** Code 128 (1D). */
    Code128,

    /** Code 93 (1D). */
    Code93,

    /** Code 39 (1D). */
    Code39,

    /** Codabar (1D). */
    Codabar,

    /** Interleaved 2 of 5 (1D). */
    Itf,
}

/** Every supported format; the default for decoding. */
public val AllFormats: Set<BarcodeFormat> = BarcodeFormat.entries.toSet()

/** The one-dimensional formats, which benefit from an upright rotation of camera frames. */
internal val LinearFormats: Set<BarcodeFormat> =
    AllFormats - setOf(BarcodeFormat.QrCode, BarcodeFormat.DataMatrix, BarcodeFormat.Aztec, BarcodeFormat.Pdf417)

/** Maps this public format to the internal zxing format. */
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

/** Maps an internal zxing format back to the public one, or null for formats this library does not expose. */
internal fun ZxingFormat.toPublic(): BarcodeFormat? = BarcodeFormat.entries.firstOrNull { it.toZxing() == this }
