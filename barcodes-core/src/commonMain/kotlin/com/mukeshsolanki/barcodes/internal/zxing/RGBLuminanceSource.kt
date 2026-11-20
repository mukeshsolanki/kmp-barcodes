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

/** Luminance source over an ARGB pixel array, converted up front with a green-favouring average. Supports cropping. */
internal class RGBLuminanceSource private constructor(
    /** Greyscale data for the full image. */
    private val luminances: ByteArray,
    /** Width of the full image data. */
    private val dataWidth: Int,
    /** Height of the full image data. */
    private val dataHeight: Int,
    /** Left edge of the crop rectangle. */
    private val left: Int,
    /** Top edge of the crop rectangle. */
    private val top: Int,
    width: Int,
    height: Int,
) : LuminanceSource(width, height) {

    init {
        require(left + width <= dataWidth && top + height <= dataHeight) {
            "Crop rectangle does not fit within image data."
        }
    }

    /** Creates a source from [width] x [height] ARGB [pixels]. */
    constructor(width: Int, height: Int, pixels: IntArray) :
        this(toLuminances(width * height, pixels), width, height, 0, 0, width, height)

    /** Copies row [y] of the crop rectangle. */
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        require(y in 0 until height) { "Requested row is outside the image: $y" }
        val out = if (row == null || row.size < width) ByteArray(width) else row
        val offset = (y + top) * dataWidth + left
        luminances.copyInto(out, 0, offset, offset + width)
        return out
    }

    /** The crop rectangle's data; the backing array itself when uncropped. */
    override val matrix: ByteArray
        get() = cropMatrix(luminances, dataWidth, dataHeight, left, top, width, height)

    /** Cropping is supported. */
    override val isCropSupported: Boolean get() = true

    /** Returns a view of the given rectangle sharing the same data. */
    override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
        RGBLuminanceSource(luminances, dataWidth, dataHeight, this.left + left, this.top + top, width, height)

    /** Conversion helpers. */
    private companion object {
        /** Converts the first [size] ARGB [pixels] to greyscale as `(r + 2g + b) / 4`. */
        fun toLuminances(size: Int, pixels: IntArray): ByteArray {
            val luminances = ByteArray(size)
            for (offset in 0 until size) {
                val pixel = pixels[offset]
                val r = (pixel shr 16) and 0xff
                val g2 = (pixel shr 7) and 0x1fe
                val b = pixel and 0xff
                luminances[offset] = ((r + g2 + b) / 4).toByte()
            }
            return luminances
        }
    }
}

/** Returns the [width] x [height] rectangle at ([left], [top]) of [data]; [data] itself when the rectangle is the whole image. */
internal fun cropMatrix(
    data: ByteArray,
    dataWidth: Int,
    dataHeight: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
): ByteArray {
    if (width == dataWidth && height == dataHeight) {
        return data
    }
    val area = width * height
    val matrix = ByteArray(area)
    var inputOffset = top * dataWidth + left
    if (width == dataWidth) {
        data.copyInto(matrix, 0, inputOffset, inputOffset + area)
        return matrix
    }
    for (y in 0 until height) {
        data.copyInto(matrix, y * width, inputOffset, inputOffset + width)
        inputOffset += dataWidth
    }
    return matrix
}
