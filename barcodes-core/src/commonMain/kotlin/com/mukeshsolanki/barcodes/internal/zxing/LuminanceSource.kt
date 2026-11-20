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

/** Abstracts a greyscale luminance bitmap; crop and rotation return copies so readers never mutate a shared source. */
internal abstract class LuminanceSource protected constructor(
    /** The width of the bitmap. */
    val width: Int,
    /** The height of the bitmap. */
    val height: Int,
) {
    /** Fetches row [y] (values 0..255, read with `and 0xFF`), reusing [row] when it is large enough. */
    abstract fun getRow(y: Int, row: ByteArray?): ByteArray

    /** Row-major luminance data; its size may exceed width * height and it must not be modified. */
    abstract val matrix: ByteArray

    /** Whether this source supports cropping. */
    open val isCropSupported: Boolean get() = false

    /** Returns a cropped copy; only callable when [isCropSupported]. */
    open fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
        throw UnsupportedOperationException("This luminance source does not support cropping.")

    /** Whether this source supports counter-clockwise rotation. */
    open val isRotateSupported: Boolean get() = false

    /** Returns a wrapper that inverts every luminance value (255 - value). */
    open fun invert(): LuminanceSource = InvertedLuminanceSource(this)

    /** Returns a copy rotated 90 degrees counter-clockwise; only callable when [isRotateSupported]. */
    open fun rotateCounterClockwise(): LuminanceSource =
        throw UnsupportedOperationException("This luminance source does not support rotation by 90 degrees.")

    /** Returns a copy rotated 45 degrees counter-clockwise; only callable when [isRotateSupported]. */
    open fun rotateCounterClockwise45(): LuminanceSource =
        throw UnsupportedOperationException("This luminance source does not support rotation by 45 degrees.")

    /** Renders the bitmap as ASCII art, one char per pixel. */
    final override fun toString(): String {
        var row = ByteArray(width)
        val result = StringBuilder(height * (width + 1))
        for (y in 0 until height) {
            row = getRow(y, row)
            for (x in 0 until width) {
                result.append(asciiFor(row[x].toInt() and 0xFF))
            }
            result.append('\n')
        }
        return result.toString()
    }

    /** Maps a [luminance] to the ASCII char used by [toString]. */
    private fun asciiFor(luminance: Int): Char = when {
        luminance < 0x40 -> '#'
        luminance < 0x80 -> '+'
        luminance < 0xC0 -> '.'
        else -> ' '
    }
}
