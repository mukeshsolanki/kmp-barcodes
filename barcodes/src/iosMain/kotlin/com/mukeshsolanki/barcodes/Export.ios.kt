package com.mukeshsolanki.barcodes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Bytes per BGRA_8888 pixel. */
private const val BYTES_PER_PIXEL = 4

/** Encodes through a Skia [Image]. */
internal actual fun ImageBitmap.encode(format: ImageFormat, quality: Int): ByteArray {
    val data = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(format.skiaFormat(), quality)
    return checkNotNull(data) { "Image encoding failed" }.bytes
}

/** Installs [pixels] into an unpremultiplied BGRA_8888 Skia bitmap, whose little-endian layout matches ARGB ints. */
internal actual fun argbImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    check(bitmap.installPixels(info, pixels.toBgraBytes(), width * BYTES_PER_PIXEL)) { "Bitmap allocation failed" }
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

/** Little-endian bytes (B, G, R, A per pixel) of these ARGB ints. */
private fun IntArray.toBgraBytes(): ByteArray = ByteArray(size * BYTES_PER_PIXEL) { index ->
    (this[index / BYTES_PER_PIXEL] ushr (index % BYTES_PER_PIXEL * Byte.SIZE_BITS)).toByte()
}

/** Skia codec for this format. */
private fun ImageFormat.skiaFormat(): EncodedImageFormat = when (this) {
    ImageFormat.Png -> EncodedImageFormat.PNG
    ImageFormat.Jpeg -> EncodedImageFormat.JPEG
    ImageFormat.Webp -> EncodedImageFormat.WEBP
}
