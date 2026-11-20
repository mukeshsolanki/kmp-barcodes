package com.mukeshsolanki.barcodes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Decodes an encoded image (PNG/JPEG/...) to ARGB pixels with the platform codec, or null when it cannot be read. */
internal expect fun decodeImageToArgb(bytes: ByteArray): ArgbImage?

/**
 * Decodes an encoded image (PNG/JPEG/...) off the main thread. Returns null when nothing reads.
 * Never throws (CancellationException excepted).
 */
@OptIn(InternalBarcodesApi::class)
public suspend fun scanImage(
    bytes: ByteArray,
    formats: Set<BarcodeFormat> = AllFormats,
    options: ScanOptions = ScanOptions(),
): Barcode? = withContext(Dispatchers.Default) {
    decodeOrNull { decodeImageToArgb(bytes) }?.let { scanArgb(it, formats, options) }
}

/** Runs [BarcodeDecoder.decodeArgb] on [image], returning null on any failure. */
@OptIn(InternalBarcodesApi::class)
internal fun scanArgb(image: ArgbImage, formats: Set<BarcodeFormat>, options: ScanOptions): Barcode? = decodeOrNull {
    BarcodeDecoder.decodeArgb(image.pixels, image.width, image.height, formats, options)
}

/** Runs [block], turning every failure except cancellation (including out-of-memory) into null. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal inline fun <T> decodeOrNull(block: () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}
