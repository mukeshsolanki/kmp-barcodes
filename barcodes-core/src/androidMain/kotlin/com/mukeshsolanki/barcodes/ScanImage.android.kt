package com.mukeshsolanki.barcodes

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes [bytes] with BitmapFactory into software ARGB_8888 pixels. */
internal actual fun decodeImageToArgb(bytes: ByteArray): ArgbImage? {
    if (bytes.isEmpty()) return null
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.toArgbImage()
}

/** Decodes the image at [uri] with a software-allocated bitmap (pre-scaled to options.maxDimension) and scans it. Returns null when nothing reads. Never throws (CancellationException excepted). */
public suspend fun scanImage(
    context: Context,
    uri: Uri,
    formats: Set<BarcodeFormat> = AllFormats,
    options: ScanOptions = ScanOptions(),
): Barcode? = withContext(Dispatchers.IO) {
    decodeOrNull { loadBitmap(context, uri, options.maxDimension)?.toArgbImage() }
        ?.let { scanArgb(it, formats, options) }
}

/** Loads [uri] as a software bitmap no larger than needed for [maxDimension]. */
private fun loadBitmap(context: Context, uri: Uri, maxDimension: Int?): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        loadWithImageDecoder(context, uri, maxDimension)
    } else {
        loadWithBitmapFactory(context, uri, maxDimension)
    }

/** API 28+: ImageDecoder with ALLOCATOR_SOFTWARE and a target size that fits [maxDimension]. */
@TargetApi(Build.VERSION_CODES.P)
private fun loadWithImageDecoder(context: Context, uri: Uri, maxDimension: Int?): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = maxOf(width, height)
        if (maxDimension != null && maxDimension in 1 until longest) {
            decoder.setTargetSize(
                maxOf(1, (width.toLong() * maxDimension / longest).toInt()),
                maxOf(1, (height.toLong() * maxDimension / longest).toInt()),
            )
        }
    }
}

/** Below API 28: BitmapFactory with a power-of-two inSampleSize that keeps the longest side >= [maxDimension]. */
private fun loadWithBitmapFactory(context: Context, uri: Uri, maxDimension: Int?): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = resolver.openInputStream(uri) ?: return null
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), maxDimension)
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** Largest power of two that keeps [longest] / sample at or above [maxDimension]. */
private fun sampleSizeFor(longest: Int, maxDimension: Int?): Int {
    if (maxDimension == null || maxDimension <= 0) return 1
    var sample = 1
    while (longest / (sample * 2) >= maxDimension) sample *= 2
    return sample
}

/** Copies this bitmap's pixels as ARGB and recycles it. */
private fun Bitmap.toArgbImage(): ArgbImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val image = ArgbImage(pixels, width, height)
    recycle()
    return image
}
