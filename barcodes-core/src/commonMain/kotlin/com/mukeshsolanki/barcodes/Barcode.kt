package com.mukeshsolanki.barcodes

/**
 * One decoded barcode: its [text], the raw codeword bytes when the format exposes them, its [format],
 * and corner/finder [bounds].
 */
public class Barcode(
    /** Decoded text. */
    public val text: String,
    /** Raw bytes, or null when the format does not expose them. */
    public val rawBytes: ByteArray?,
    /** Format the code was read as. */
    public val format: BarcodeFormat,
    /** Detector points (finder patterns or corners) in source image pixels. */
    public val bounds: List<BarcodePoint>,
) {
    /** Value equality; [rawBytes] are compared by content. */
    override fun equals(other: Any?): Boolean = this === other ||
        other is Barcode &&
        text == other.text &&
        format == other.format &&
        rawBytes.contentEquals(other.rawBytes) &&
        bounds == other.bounds

    /** Hash consistent with [equals]. */
    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return 31 * result + bounds.hashCode()
    }

    /** Prints only the format and text length so payloads never leak into logs. */
    override fun toString(): String = "Barcode(format=$format, length=${text.length})"
}

/** A point in source image pixels. */
public data class BarcodePoint(
    /** Horizontal pixel coordinate. */
    val x: Float,
    /** Vertical pixel coordinate. */
    val y: Float,
)

/**
 * Still-image decode options: downscale the longest side to [maxDimension] (null = never)
 * and use zxing TRY_HARDER when [tryHarder].
 */
public data class ScanOptions(
    /** Longest side after downscaling, or null to keep the original size. */
    val maxDimension: Int? = 500,
    /** Spend more time looking for a code. */
    val tryHarder: Boolean = true,
)

/** Marks low-level APIs shared between kmp-barcodes modules. */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "Internal kmp-barcodes API; may change without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalBarcodesApi

/** Outcome of decoding one camera luminance frame. */
@InternalBarcodesApi
public sealed interface LuminanceResult {
    /** A code was decoded. */
    public data class Found(
        /** The decoded code. */
        val barcode: Barcode,
    ) : LuminanceResult

    /** QR requested, decode failed, but finder patterns were found with this estimated [moduleSize] in pixels. */
    public data class QrDetected(
        /** Estimated module size in pixels. */
        val moduleSize: Float,
    ) : LuminanceResult

    /** Nothing was found. */
    public data object None : LuminanceResult
}
