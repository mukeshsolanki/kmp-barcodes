/*
 * Copyright 2011 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.oned

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import kotlin.math.max

/**
 * Encapsulates functionality and implementation that is common to one-dimensional barcodes.
 */
internal abstract class OneDimensionalCodeWriter : Writer {

    /**
     * Encodes [contents] to a boolean array of horizontal modules (false = white, true = black),
     * including start and end codes but excluding side margins.
     */
    abstract fun encode(contents: String): BooleanArray

    /** Encodes [contents] with access to [hints]; defaults to [encode] without hints. */
    open fun encode(contents: String, hints: Map<EncodeHintType, Any?>?): BooleanArray = encode(contents)

    /** Encodes [contents] as [format] at the requested size without hints. */
    final override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /**
     * Encodes [contents] as [format]. The result may be larger than [width] x [height] when that is too small;
     * pass zero for both to get the minimum size. Negative sizes throw IllegalArgumentException.
     */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        if (contents.isEmpty()) {
            throw IllegalArgumentException("Found empty contents")
        }
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Negative size is not allowed. Input: " + width + 'x' + height)
        }
        val supportedFormats = supportedWriteFormats
        if (supportedFormats != null && !supportedFormats.contains(format)) {
            throw IllegalArgumentException("Can only encode $supportedFormats, but got $format")
        }

        var sidesMargin = defaultMargin
        if (hints != null && hints.containsKey(EncodeHintType.MARGIN)) {
            sidesMargin = hints[EncodeHintType.MARGIN].toString().toInt()
        }

        val code = encode(contents, hints)
        return renderResult(code, width, height, sidesMargin)
    }

    /** Formats this writer accepts, or null to accept any format. */
    protected open val supportedWriteFormats: Collection<BarcodeFormat>?
        get() = null

    /** Default quiet zone, in modules, added across both sides when no MARGIN hint is given. */
    open val defaultMargin: Int
        get() = 10

    /** Shared rendering and pattern helpers for 1D writers. */
    companion object {

        /** Matches strings made only of the digits 0-9. */
        private val NUMERIC = Regex("[0-9]+")

        /** Scales [code] into a [BitMatrix] of at least [width] x [height], centred with [sidesMargin] quiet modules. */
        private fun renderResult(code: BooleanArray, width: Int, height: Int, sidesMargin: Int): BitMatrix {
            val inputWidth = code.size
            val fullWidth = inputWidth + sidesMargin
            val outputWidth = max(width, fullWidth)
            val outputHeight = max(1, height)

            val multiple = outputWidth / fullWidth
            val leftPadding = (outputWidth - (inputWidth * multiple)) / 2

            val output = BitMatrix(outputWidth, outputHeight)
            var outputX = leftPadding
            for (inputX in 0 until inputWidth) {
                if (code[inputX]) {
                    output.setRegion(outputX, 0, multiple, outputHeight)
                }
                outputX += multiple
            }
            return output
        }

        /** Throws IllegalArgumentException if [contents] contains characters other than digits 0-9. */
        fun checkNumeric(contents: String) {
            if (!NUMERIC.matches(contents)) {
                throw IllegalArgumentException("Input should only contain digits 0-9")
            }
        }

        /**
         * Writes the black/white runs of [pattern] into [target] from [pos], starting with [startColor].
         * Returns the number of elements written.
         */
        fun appendPattern(target: BooleanArray, pos: Int, pattern: IntArray, startColor: Boolean): Int {
            var color = startColor
            var numAdded = 0
            var position = pos
            for (len in pattern) {
                for (j in 0 until len) {
                    target[position++] = color
                }
                numAdded += len
                color = !color
            }
            return numAdded
        }
    }
}
