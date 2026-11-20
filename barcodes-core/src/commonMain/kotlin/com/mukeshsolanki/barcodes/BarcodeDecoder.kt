package com.mukeshsolanki.barcodes

import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.InvertedLuminanceSource
import com.mukeshsolanki.barcodes.internal.zxing.LuminanceSource
import com.mukeshsolanki.barcodes.internal.zxing.MultiFormatReader
import com.mukeshsolanki.barcodes.internal.zxing.PlanarYUVLuminanceSource
import com.mukeshsolanki.barcodes.internal.zxing.RGBLuminanceSource
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.common.GlobalHistogramBinarizer
import com.mukeshsolanki.barcodes.internal.zxing.common.HybridBinarizer
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.detector.QrModuleSizeEstimator

/** Low-level decoding entry points shared by scanImage and the camera scanner. */
@InternalBarcodesApi
public object BarcodeDecoder {
    /** Rotation step that turns a portrait plane back to landscape. */
    private const val HALF_TURN_DEGREES = 180

    /**
     * Decodes ARGB pixels: downscale so the longest side <= options.maxDimension, then decode,
     * with TRY_HARDER per options. First result wins.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    public fun decodeArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
        formats: Set<BarcodeFormat>,
        options: ScanOptions,
    ): Barcode? {
        if (!isPositiveSize(width, height) || pixels.size < width * height || formats.isEmpty()) return null
        return try {
            val source = downscaled(pixels, width, height, options.maxDimension)
            val hints = decodeHints(formats, options.tryHarder)
            val attempts = sequenceOf(
                { BinaryBitmap(HybridBinarizer(source)) },
                { BinaryBitmap(GlobalHistogramBinarizer(source)) },
                { BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source))) },
            )
            attempts.firstNotNullOfOrNull { decodeOrNull(it(), hints) }?.toBarcode()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes one camera luminance (Y) plane without TRY_HARDER. rotationDegrees is used only when
     * 1D formats are requested (the plane is rotated upright for them).
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    public fun decodeLuminance(
        luminance: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        formats: Set<BarcodeFormat>,
    ): LuminanceResult = try {
        decodePlane(luminance, rowStride, width, height, rotationDegrees, formats)
    } catch (e: Exception) {
        LuminanceResult.None
    }

    /** The unguarded body of [decodeLuminance]. */
    private fun decodePlane(
        luminance: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        formats: Set<BarcodeFormat>,
    ): LuminanceResult {
        if (!isPositiveSize(width, height) || rowStride < width || formats.isEmpty()) return LuminanceResult.None
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val barcode = decodeOrNull(bitmap, decodeHints(formats, tryHarder = false))?.toBarcode()
            ?: decodeRotated(luminance, rowStride, width, height, rotationDegrees, formats)
        return when {
            barcode != null -> LuminanceResult.Found(barcode)
            BarcodeFormat.QrCode in formats -> QrModuleSizeEstimator.estimate(bitmap.blackMatrix, null)
                ?.let { LuminanceResult.QrDetected(it) }
                ?: LuminanceResult.None
            else -> LuminanceResult.None
        }
    }

    /** Retries the requested 1D formats on the plane rotated upright; null when none apply or nothing reads. */
    private fun decodeRotated(
        luminance: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        formats: Set<BarcodeFormat>,
    ): Barcode? {
        val linear = formats intersect LinearFormats
        if (linear.isEmpty() || rotationDegrees % HALF_TURN_DEGREES == 0) return null
        val rotated = rotatedSource(luminance, rowStride, width, height)
        return decodeOrNull(BinaryBitmap(HybridBinarizer(rotated)), decodeHints(linear, tryHarder = false))?.toBarcode()
    }

    /** Whether both [width] and [height] are positive. */
    private fun isPositiveSize(width: Int, height: Int): Boolean = width > 0 && height > 0

    /** Runs one decode attempt, mapping zxing's "not found" family to null. */
    @Suppress("SwallowedException")
    private fun decodeOrNull(bitmap: BinaryBitmap, hints: Map<DecodeHintType, Any?>): Result? = try {
        MultiFormatReader().decode(bitmap, hints)
    } catch (e: ReaderException) {
        null
    }

    /** POSSIBLE_FORMATS for [formats], plus TRY_HARDER when requested. */
    private fun decodeHints(formats: Set<BarcodeFormat>, tryHarder: Boolean): Map<DecodeHintType, Any?> = buildMap {
        put(DecodeHintType.POSSIBLE_FORMATS, formats.map { it.toZxing() })
        if (tryHarder) put(DecodeHintType.TRY_HARDER, true)
    }

    /** Builds an RGB source, nearest-neighbour downscaled so the longest side is at most [maxDimension]. */
    private fun downscaled(pixels: IntArray, width: Int, height: Int, maxDimension: Int?): LuminanceSource {
        val longest = maxOf(width, height)
        if (maxDimension == null || maxDimension <= 0 || longest <= maxDimension) {
            return RGBLuminanceSource(width, height, pixels)
        }
        val newWidth = maxOf(1, (width.toLong() * maxDimension / longest).toInt())
        val newHeight = maxOf(1, (height.toLong() * maxDimension / longest).toInt())
        val scaled = IntArray(newWidth * newHeight) { i ->
            val srcX = (i % newWidth).toLong() * width / newWidth
            val srcY = (i / newWidth).toLong() * height / newHeight
            pixels[(srcY * width + srcX).toInt()]
        }
        return RGBLuminanceSource(newWidth, newHeight, scaled)
    }

    /** Copies the plane rotated 90 degrees clockwise into a tightly packed source. */
    private fun rotatedSource(luminance: ByteArray, rowStride: Int, width: Int, height: Int): LuminanceSource {
        val rotated = ByteArray(width * height) { i ->
            val x = i / height
            val y = height - 1 - i % height
            luminance[y * rowStride + x]
        }
        return PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)
    }
}

/** Maps a zxing result to a public [Barcode], or null for an unexposed format. */
private fun Result.toBarcode(): Barcode? {
    val format = barcodeFormat.toPublic() ?: return null
    val points = resultPoints.orEmpty().filterNotNull().map { BarcodePoint(it.x, it.y) }
    return Barcode(text, rawBytes, format, points)
}
