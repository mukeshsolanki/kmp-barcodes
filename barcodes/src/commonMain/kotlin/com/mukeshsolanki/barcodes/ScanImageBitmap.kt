package com.mukeshsolanki.barcodes

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the first barcode of [formats] in [bitmap] off the main thread.
 * Returns null when nothing reads; never throws except [CancellationException].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
public suspend fun scanImage(
    bitmap: ImageBitmap,
    formats: Set<BarcodeFormat> = AllFormats,
    options: ScanOptions = ScanOptions(),
): Barcode? = withContext(Dispatchers.Default) {
    try {
        decodeBitmap(bitmap, formats, options)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** Reads [bitmap]'s ARGB pixels and decodes them; null for empty bitmaps or no formats. */
@OptIn(InternalBarcodesApi::class)
private fun decodeBitmap(bitmap: ImageBitmap, formats: Set<BarcodeFormat>, options: ScanOptions): Barcode? {
    if (bitmap.width <= 0 || bitmap.height <= 0 || formats.isEmpty()) return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    return BarcodeDecoder.decodeArgb(pixels, bitmap.width, bitmap.height, formats, options)
}
