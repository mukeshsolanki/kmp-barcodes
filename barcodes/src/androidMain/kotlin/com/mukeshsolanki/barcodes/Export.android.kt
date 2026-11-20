package com.mukeshsolanki.barcodes

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** Encodes with [Bitmap.compress]. */
internal actual fun ImageBitmap.encode(format: ImageFormat, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    check(asAndroidBitmap().compress(format.compressFormat(), quality, out)) { "Image encoding failed" }
    return out.toByteArray()
}

/** Wraps an immutable ARGB_8888 [Bitmap] created straight from [pixels]. */
internal actual fun argbImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Rasterises this matrix into a mutable ARGB_8888 [Bitmap] at exactly one pixel per cell. */
public fun BarcodeMatrix.toBitmap(
    dark: Int = android.graphics.Color.BLACK,
    light: Int = android.graphics.Color.WHITE,
): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
    setPixels(argbPixels(dark, light), 0, this@toBitmap.width, 0, 0, this@toBitmap.width, this@toBitmap.height)
}

/** Platform codec for this format; WebP is lossy (WEBP_LOSSY on API 30+). */
private fun ImageFormat.compressFormat(): Bitmap.CompressFormat = when (this) {
    ImageFormat.Png -> Bitmap.CompressFormat.PNG
    ImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
    ImageFormat.Webp -> webpFormat()
}

/** Lossy WebP codec for the running API level. */
@Suppress("DEPRECATION")
private fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
