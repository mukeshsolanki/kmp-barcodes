/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */
package com.mukeshsolanki.barcodes.internal.zxing

/** Luminance source over camera YUV data whose Y plane comes first, optionally cropped to a rectangle. */
internal class PlanarYUVLuminanceSource(
    /** The raw camera data; its Y plane is read. */
    private val yuvData: ByteArray,
    /** Width of the full Y plane. */
    private val dataWidth: Int,
    /** Height of the full Y plane. */
    private val dataHeight: Int,
    /** Left edge of the crop rectangle. */
    private val left: Int,
    /** Top edge of the crop rectangle. */
    private val top: Int,
    width: Int,
    height: Int,
    reverseHorizontal: Boolean,
) : LuminanceSource(width, height) {

    init {
        require(left + width <= dataWidth && top + height <= dataHeight) {
            "Crop rectangle does not fit within image data."
        }
        if (reverseHorizontal) {
            reverseRows(width, height)
        }
    }

    /** Copies row [y] of the crop rectangle. */
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        require(y in 0 until height) { "Requested row is outside the image: $y" }
        val out = if (row == null || row.size < width) ByteArray(width) else row
        val offset = (y + top) * dataWidth + left
        yuvData.copyInto(out, 0, offset, offset + width)
        return out
    }

    /** The crop rectangle's data; the backing array itself when uncropped. */
    override val matrix: ByteArray
        get() = cropMatrix(yuvData, dataWidth, dataHeight, left, top, width, height)

    /** Cropping is supported. */
    override val isCropSupported: Boolean get() = true

    /** Returns a view of the given rectangle sharing the same data. */
    override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
        PlanarYUVLuminanceSource(yuvData, dataWidth, dataHeight, this.left + left, this.top + top, width, height, false)

    /** Renders a half-size greyscale ARGB thumbnail of the crop rectangle. */
    fun renderThumbnail(): IntArray {
        val width = thumbnailWidth
        val height = thumbnailHeight
        val pixels = IntArray(width * height)
        var inputOffset = top * dataWidth + left
        for (y in 0 until height) {
            val outputOffset = y * width
            for (x in 0 until width) {
                val grey = yuvData[inputOffset + x * THUMBNAIL_SCALE_FACTOR].toInt() and 0xff
                pixels[outputOffset + x] = OPAQUE_BLACK or (grey * 0x00010101)
            }
            inputOffset += dataWidth * THUMBNAIL_SCALE_FACTOR
        }
        return pixels
    }

    /** Width of the image from [renderThumbnail]. */
    val thumbnailWidth: Int get() = width / THUMBNAIL_SCALE_FACTOR

    /** Height of the image from [renderThumbnail]. */
    val thumbnailHeight: Int get() = height / THUMBNAIL_SCALE_FACTOR

    /** Mirrors each row of the crop rectangle in place. */
    private fun reverseRows(width: Int, height: Int) {
        var rowStart = top * dataWidth + left
        for (y in 0 until height) {
            val middle = rowStart + width / 2
            var x1 = rowStart
            var x2 = rowStart + width - 1
            while (x1 < middle) {
                val temp = yuvData[x1]
                yuvData[x1] = yuvData[x2]
                yuvData[x2] = temp
                x1++
                x2--
            }
            rowStart += dataWidth
        }
    }

    /** Thumbnail constants. */
    private companion object {
        /** Downscale factor of [renderThumbnail]. */
        const val THUMBNAIL_SCALE_FACTOR = 2

        /** Opaque alpha channel for thumbnail pixels. */
        const val OPAQUE_BLACK = -0x1000000
    }
}
