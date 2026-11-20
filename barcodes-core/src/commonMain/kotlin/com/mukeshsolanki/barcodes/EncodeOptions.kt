package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.SymbolShapeHint
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder.Compaction
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Character sets an encoder can be told to use for byte-mode content. */
public enum class BarcodeCharset(
    /** The zxing CHARACTER_SET hint name. */
    internal val zxingName: String,
) {
    /** UTF-8. */
    Utf8("UTF-8"),

    /** ISO-8859-1 (zxing's default when no charset is given). */
    Iso8859_1("ISO-8859-1"),

    /** US-ASCII. */
    UsAscii("US-ASCII"),

    /** Shift_JIS. */
    ShiftJis("Shift_JIS"),
}

/** QR error correction level, from lowest (L, ~7%) to highest (H, ~30%). */
public enum class ErrorCorrection {
    /** About 7% recovery. */
    L,

    /** About 15% recovery. */
    M,

    /** About 25% recovery. */
    Q,

    /** About 30% recovery. */
    H,
}

/** Per-format encoder options; each subclass configures exactly one family of formats. */
public sealed interface EncodeOptions

/** QR Code options; [maskPattern] is 0..7 or null for automatic, [quietZone] is in modules. */
public data class QrOptions(
    /** Error correction level. */
    val errorCorrection: ErrorCorrection = ErrorCorrection.L,
    /** Byte-mode charset, or null for zxing's default. */
    val charset: BarcodeCharset? = null,
    /** Forced mask pattern, or null to let the encoder choose. */
    val maskPattern: Int? = null,
    /** Quiet zone width in modules. */
    val quietZone: Int = 4,
) : EncodeOptions

/** Data Matrix symbol shape preference. */
public enum class DataMatrixShape {
    /** Smallest symbol of either shape. */
    Auto,

    /** Square symbols only. */
    Square,

    /** Rectangular symbols only. */
    Rectangle,
}

/** Data Matrix options; content must be ISO-8859-1 (zxing's default encoder rejects anything else). */
public data class DataMatrixOptions(
    /** Shape preference. */
    val shape: DataMatrixShape = DataMatrixShape.Auto,
) : EncodeOptions

/** Aztec options; [layers] 0 picks the minimum size, negative values select compact symbols. */
public data class AztecOptions(
    /** Minimum error correction percentage. */
    val errorCorrectionPercent: Int = 33,
    /** Layer count, or 0 for automatic. */
    val layers: Int = 0,
    /** Charset, or null for the default. */
    val charset: BarcodeCharset? = null,
) : EncodeOptions

/** PDF417 compaction mode. */
public enum class Pdf417Compaction {
    /** Choose per segment. */
    Auto,

    /** Text compaction only. */
    Text,

    /** Byte compaction only. */
    Byte,

    /** Numeric compaction only. */
    Numeric,
}

/** PDF417 options; [errorCorrectionLevel] is 0..8 and [quietZone] is in pixels of the minimum-size matrix. */
public data class Pdf417Options(
    /** Error correction level 0..8. */
    val errorCorrectionLevel: Int = 2,
    /** Compact (truncated) PDF417. */
    val compact: Boolean = false,
    /** Compaction mode. */
    val compaction: Pdf417Compaction = Pdf417Compaction.Auto,
    /** Charset, or null for the default. */
    val charset: BarcodeCharset? = null,
    /** Quiet zone. */
    val quietZone: Int = 30,
) : EncodeOptions

/** Code 128 code sets. */
public enum class Code128CodeSet {
    /** Code set A. */
    A,

    /** Code set B. */
    B,

    /** Code set C. */
    C,
}

/** Code 128 options; [quietZone] null keeps zxing's default margin. */
public data class Code128Options(
    /** Use the compact (shortest) encoder. */
    val compact: Boolean = false,
    /** Force a single code set, or null for automatic. */
    val forceCodeSet: Code128CodeSet? = null,
    /** Quiet zone, or null for zxing's default. */
    val quietZone: Int? = null,
) : EncodeOptions

/** Options for the remaining 1D formats; [quietZone] null keeps zxing's per-format default margin. */
public data class LinearOptions(
    /** Quiet zone, or null for zxing's default. */
    val quietZone: Int? = null,
) : EncodeOptions

/** Maps to zxing's QR error correction level. */
internal fun ErrorCorrection.toZxing(): ErrorCorrectionLevel = ErrorCorrectionLevel.valueOf(name)

/** Maps to zxing's Data Matrix shape hint. */
internal fun DataMatrixShape.toZxing(): SymbolShapeHint = when (this) {
    DataMatrixShape.Auto -> SymbolShapeHint.FORCE_NONE
    DataMatrixShape.Square -> SymbolShapeHint.FORCE_SQUARE
    DataMatrixShape.Rectangle -> SymbolShapeHint.FORCE_RECTANGLE
}

/** Maps to zxing's PDF417 compaction. */
internal fun Pdf417Compaction.toZxing(): Compaction = Compaction.valueOf(name.uppercase())
