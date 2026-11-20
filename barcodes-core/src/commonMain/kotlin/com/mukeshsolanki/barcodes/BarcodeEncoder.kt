package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.MultiFormatWriter
import com.mukeshsolanki.barcodes.internal.zxing.WriterException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/** A read-only grid of barcode cells; `true` means dark. */
public class BarcodeMatrix internal constructor(
    /** The wrapped zxing matrix. */
    internal val bitMatrix: BitMatrix,
) {
    /** Width in cells. */
    public val width: Int get() = bitMatrix.width

    /** Height in cells. */
    public val height: Int get() = bitMatrix.height

    /** Whether the cell at ([x], [y]) is dark. */
    public operator fun get(x: Int, y: Int): Boolean = bitMatrix.get(x, y)
}

/** Encodes text into barcode matrices with zxing-identical output. */
public object BarcodeEncoder {
    /**
     * Same semantics as zxing MultiFormatWriter.encode: width/height 0 = minimum size;
     * larger sizes are scaled and centred exactly as zxing does.
     */
    @Suppress("SwallowedException")
    public fun encode(
        content: String,
        format: BarcodeFormat,
        width: Int = 0,
        height: Int = 0,
        options: EncodeOptions? = null,
    ): BarcodeMatrix {
        val hints = hintsFor(format, options)
        val matrix = try {
            MultiFormatWriter().encode(content, format.toZxing(), width, height, hints)
        } catch (e: WriterException) {
            throw IllegalArgumentException("Content cannot be encoded as $format")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid content or size for $format")
        }
        return BarcodeMatrix(matrix)
    }
}
