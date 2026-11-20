/*
 * Copyright 2010 ZXing authors
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

/** Renders an ITF (Interleaved Two of Five) code. */
internal class ITFWriter : OneDimensionalCodeWriter() {

    /** Only ITF is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.ITF)

    /** Encodes an even number of digits (at most 80) to horizontal modules. */
    override fun encode(contents: String): BooleanArray {
        val length = contents.length
        if (length % 2 != 0) {
            throw IllegalArgumentException("The length of the input should be even")
        }
        if (length > 80) {
            throw IllegalArgumentException(
                "Requested contents should be less than 80 digits long, but got $length",
            )
        }

        checkNumeric(contents)

        val result = BooleanArray(9 + 9 * length)
        var pos = appendPattern(result, 0, START_PATTERN, true)
        var i = 0
        while (i < length) {
            val one = contents[i].digitToInt()
            val two = contents[i + 1].digitToInt()
            val encoding = IntArray(10)
            for (j in 0 until 5) {
                encoding[2 * j] = PATTERNS[one][j]
                encoding[2 * j + 1] = PATTERNS[two][j]
            }
            pos += appendPattern(result, pos, encoding, true)
            i += 2
        }
        appendPattern(result, pos, END_PATTERN, true)

        return result
    }

    /** ITF guard and digit patterns. */
    private companion object {

        /** Start guard pattern. */
        val START_PATTERN: IntArray = intArrayOf(1, 1, 1, 1)

        /** End guard pattern. */
        val END_PATTERN: IntArray = intArrayOf(3, 1, 1)

        /** Module width of a wide line. */
        const val W = 3

        /** Module width of a narrow line. */
        const val N = 1

        /** Wide/narrow patterns for each digit 0-9 (see ITFReader). */
        val PATTERNS: Array<IntArray> = arrayOf(
            intArrayOf(N, N, W, W, N),
            intArrayOf(W, N, N, N, W),
            intArrayOf(N, W, N, N, W),
            intArrayOf(W, W, N, N, N),
            intArrayOf(N, N, W, N, W),
            intArrayOf(W, N, W, N, N),
            intArrayOf(N, W, W, N, N),
            intArrayOf(N, N, N, W, W),
            intArrayOf(W, N, N, W, N),
            intArrayOf(N, W, N, W, N),
        )
    }
}
