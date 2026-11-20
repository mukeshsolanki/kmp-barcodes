package com.mukeshsolanki.barcodes

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage

/** Decodes [bytes] with UIImage and redraws it into an RGBA8 bitmap context to get ARGB pixels. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun decodeImageToArgb(bytes: ByteArray): ArgbImage? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
    val image = UIImage.imageWithData(data)?.CGImage ?: return null
    val width = CGImageGetWidth(image).toInt()
    val height = CGImageGetHeight(image).toInt()
    if (width <= 0 || height <= 0) return null
    val rgba = drawRgba(image, width, height) ?: return null
    return ArgbImage(IntArray(width * height) { rgbaToArgb(rgba, it * 4) }, width, height)
}

/** Draws [image] into a premultiplied RGBA8 buffer, or null when the context cannot be created. */
@OptIn(ExperimentalForeignApi::class)
private fun drawRgba(image: CGImageRef, width: Int, height: Int): ByteArray? {
    val buffer = ByteArray(width * height * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val drawn = buffer.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            pinned.addressOf(0),
            width.toULong(),
            height.toULong(),
            8u,
            (width * 4).toULong(),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        context?.let {
            CGContextDrawImage(it, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
            CGContextRelease(it)
        } != null
    }
    CGColorSpaceRelease(colorSpace)
    return buffer.takeIf { drawn }
}

/** Packs the RGBA bytes at [offset] into one ARGB int. */
private fun rgbaToArgb(rgba: ByteArray, offset: Int): Int {
    val r = rgba[offset].toInt() and 0xFF
    val g = rgba[offset + 1].toInt() and 0xFF
    val b = rgba[offset + 2].toInt() and 0xFF
    val a = rgba[offset + 3].toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
