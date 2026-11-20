/*
 * Copyright 2013 ZXing authors
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

/** Wraps a [LuminanceSource] and inverts its luminances: each value becomes 255 - value. */
internal class InvertedLuminanceSource(
    /** The source being inverted. */
    private val delegate: LuminanceSource,
) : LuminanceSource(delegate.width, delegate.height) {

    /** Fetches and inverts row [y]. */
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val inverted = delegate.getRow(y, row)
        for (i in 0 until width) {
            inverted[i] = (255 - (inverted[i].toInt() and 0xFF)).toByte()
        }
        return inverted
    }

    /** A freshly inverted copy of the delegate's matrix. */
    override val matrix: ByteArray
        get() {
            val source = delegate.matrix
            val length = width * height
            val invertedMatrix = ByteArray(length)
            for (i in 0 until length) {
                invertedMatrix[i] = (255 - (source[i].toInt() and 0xFF)).toByte()
            }
            return invertedMatrix
        }

    /** Delegates crop support. */
    override val isCropSupported: Boolean get() = delegate.isCropSupported

    /** Crops the delegate and keeps the inversion. */
    override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource =
        InvertedLuminanceSource(delegate.crop(left, top, width, height))

    /** Delegates rotation support. */
    override val isRotateSupported: Boolean get() = delegate.isRotateSupported

    /** Returns the original delegate, since inverting twice is a no-op. */
    override fun invert(): LuminanceSource = delegate

    /** Rotates the delegate and keeps the inversion. */
    override fun rotateCounterClockwise(): LuminanceSource =
        InvertedLuminanceSource(delegate.rotateCounterClockwise())

    /** Rotates the delegate by 45 degrees and keeps the inversion. */
    override fun rotateCounterClockwise45(): LuminanceSource =
        InvertedLuminanceSource(delegate.rotateCounterClockwise45())
}
