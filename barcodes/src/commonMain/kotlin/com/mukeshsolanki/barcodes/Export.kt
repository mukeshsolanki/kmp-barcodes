package com.mukeshsolanki.barcodes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Highest encoder quality accepted by [toByteArray]. */
private const val MAX_QUALITY = 100

/** Renders this painter into a new [width] x [height] ARGB [ImageBitmap]. */
public fun Painter.toImageBitmap(width: Int, height: Int): ImageBitmap {
    require(width > 0 && height > 0) { "Width and height must be positive" }
    val bitmap = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        with(this@toImageBitmap) { draw(size) }
    }
    return bitmap
}

/** Renders this painter at [width] x [height] and encodes it as [format] with [quality] (0..100; ignored by PNG). */
public fun Painter.toByteArray(
    width: Int,
    height: Int,
    format: ImageFormat = ImageFormat.Png,
    quality: Int = MAX_QUALITY,
): ByteArray {
    require(quality in 0..MAX_QUALITY) { "Quality must be within 0..100" }
    return toImageBitmap(width, height).encode(format, quality)
}

/**
 * Rasterises this matrix at exactly one pixel per cell with no filtering, so
 * `BarcodeEncoder.encode(content, format, width, height)` yields a zxing-exact [width] x [height] image.
 */
public fun BarcodeMatrix.toImageBitmap(dark: Color = Color.Black, light: Color = Color.White): ImageBitmap =
    argbImageBitmap(argbPixels(dark.toArgb(), light.toArgb()), width, height)

/** Row-major ARGB pixels of this matrix: [dark] for set cells, [light] otherwise. */
internal fun BarcodeMatrix.argbPixels(dark: Int, light: Int): IntArray =
    IntArray(width * height) { if (this[it % width, it / width]) dark else light }

/** Encodes this bitmap as [format] at [quality] with the platform codec. */
internal expect fun ImageBitmap.encode(format: ImageFormat, quality: Int): ByteArray

/** Builds an [ImageBitmap] holding exactly the row-major ARGB [pixels] of a [width] x [height] image. */
internal expect fun argbImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap
