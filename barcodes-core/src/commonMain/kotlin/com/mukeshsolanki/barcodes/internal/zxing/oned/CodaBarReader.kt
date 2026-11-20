/*
 * Copyright 2008 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Decodes Codabar barcodes. */
internal class CodaBarReader : OneDReader() {

    /** Reused buffer; holds alphabet offsets while decoding and characters afterwards. */
    private val decodeRowResult = StringBuilder(20)

    /** Run lengths of the whole row, starting with white. */
    private var counters = IntArray(80)

    /** Number of valid entries in [counters]. */
    private var counterLength = 0

    /** Decodes one Codabar symbol from [row]; start/end characters are kept only with RETURN_CODABAR_START_END. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        counters.fill(0)
        setCounters(row)
        val startOffset = findStartPattern()
        var nextStart = startOffset

        decodeRowResult.setLength(0)
        do {
            val charOffset = toNarrowWidePattern(nextStart)
            if (charOffset == -1) {
                throw NotFoundException.notFoundInstance
            }
            decodeRowResult.append(Char(charOffset))
            nextStart += 8
            if (decodeRowResult.length > 1 && arrayContains(STARTEND_ENCODING, ALPHABET[charOffset])) {
                break
            }
        } while (nextStart < counterLength)

        val trailingWhitespace = counters[nextStart - 1]
        var lastPatternSize = 0
        for (i in -8 until -1) {
            lastPatternSize += counters[nextStart + i]
        }

        if (nextStart < counterLength && trailingWhitespace < lastPatternSize / 2) {
            throw NotFoundException.notFoundInstance
        }

        validatePattern(startOffset)

        for (i in 0 until decodeRowResult.length) {
            decodeRowResult[i] = ALPHABET[decodeRowResult[i].code]
        }
        val startchar = decodeRowResult[0]
        if (!arrayContains(STARTEND_ENCODING, startchar)) {
            throw NotFoundException.notFoundInstance
        }
        val endchar = decodeRowResult[decodeRowResult.length - 1]
        if (!arrayContains(STARTEND_ENCODING, endchar)) {
            throw NotFoundException.notFoundInstance
        }

        if (decodeRowResult.length <= MIN_CHARACTER_LENGTH) {
            throw NotFoundException.notFoundInstance
        }

        if (hints == null || !hints.containsKey(DecodeHintType.RETURN_CODABAR_START_END)) {
            decodeRowResult.deleteAt(decodeRowResult.length - 1)
            decodeRowResult.deleteAt(0)
        }

        var runningCount = 0
        for (i in 0 until startOffset) {
            runningCount += counters[i]
        }
        val left = runningCount.toFloat()
        for (i in startOffset until nextStart - 1) {
            runningCount += counters[i]
        }
        val right = runningCount.toFloat()

        val result = Result(
            decodeRowResult.toString(),
            null,
            arrayOf(
                ResultPoint(left, rowNumber.toFloat()),
                ResultPoint(right, rowNumber.toFloat()),
            ),
            BarcodeFormat.CODABAR,
        )
        result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]F0")
        return result
    }

    /**
     * Checks that every bar and space from [start] falls within size thresholds derived from the
     * average short and long stripes; throws NotFoundException otherwise.
     */
    private fun validatePattern(start: Int) {
        val sizes = intArrayOf(0, 0, 0, 0)
        val counts = intArrayOf(0, 0, 0, 0)
        val end = decodeRowResult.length - 1

        var pos = start
        for (i in 0..end) {
            var pattern = CHARACTER_ENCODINGS[decodeRowResult[i].code]
            for (j in 6 downTo 0) {
                val category = (j and 1) + (pattern and 1) * 2
                sizes[category] += counters[pos + j]
                counts[category]++
                pattern = pattern shr 1
            }
            pos += 8
        }

        val maxes = FloatArray(4)
        val mins = FloatArray(4)
        for (i in 0 until 2) {
            mins[i] = 0.0f
            mins[i + 2] = (sizes[i].toFloat() / counts[i] + sizes[i + 2].toFloat() / counts[i + 2]) / 2.0f
            maxes[i] = mins[i + 2]
            maxes[i + 2] = (sizes[i + 2] * MAX_ACCEPTABLE + PADDING) / counts[i + 2]
        }

        pos = start
        for (i in 0..end) {
            var pattern = CHARACTER_ENCODINGS[decodeRowResult[i].code]
            for (j in 6 downTo 0) {
                val category = (j and 1) + (pattern and 1) * 2
                val size = counters[pos + j]
                if (size < mins[category] || size > maxes[category]) {
                    throw NotFoundException.notFoundInstance
                }
                pattern = pattern shr 1
            }
            pos += 8
        }
    }

    /** Records the length of every run in [row] into [counters], starting from the first white pixel. */
    private fun setCounters(row: BitArray) {
        counterLength = 0
        var i = row.getNextUnset(0)
        val end = row.size
        if (i >= end) {
            throw NotFoundException.notFoundInstance
        }
        var isWhite = true
        var count = 0
        while (i < end) {
            if (row.get(i) != isWhite) {
                count++
            } else {
                counterAppend(count)
                count = 1
                isWhite = !isWhite
            }
            i++
        }
        counterAppend(count)
    }

    /** Appends [e] to [counters], doubling the array when it is full. */
    private fun counterAppend(e: Int) {
        counters[counterLength] = e
        counterLength++
        if (counterLength >= counters.size) {
            counters = counters.copyOf(counterLength * 2)
        }
    }

    /** Returns the counter index of a start character preceded by enough whitespace; throws NotFoundException if none. */
    private fun findStartPattern(): Int {
        var i = 1
        while (i < counterLength) {
            val charOffset = toNarrowWidePattern(i)
            if (charOffset != -1 && arrayContains(STARTEND_ENCODING, ALPHABET[charOffset])) {
                var patternSize = 0
                for (j in i until i + 7) {
                    patternSize += counters[j]
                }
                if (i == 1 || counters[i - 1] >= patternSize / 2) {
                    return i
                }
            }
            i += 2
        }
        throw NotFoundException.notFoundInstance
    }

    /** Returns the alphabet offset of the character whose bar starts at counter [position], or -1. */
    private fun toNarrowWidePattern(position: Int): Int {
        val end = position + 7
        if (end >= counterLength) {
            return -1
        }

        val theCounters = counters

        var maxBar = 0
        var minBar = Int.MAX_VALUE
        var j = position
        while (j < end) {
            val currentCounter = theCounters[j]
            if (currentCounter < minBar) {
                minBar = currentCounter
            }
            if (currentCounter > maxBar) {
                maxBar = currentCounter
            }
            j += 2
        }
        val thresholdBar = (minBar + maxBar) / 2

        var maxSpace = 0
        var minSpace = Int.MAX_VALUE
        j = position + 1
        while (j < end) {
            val currentCounter = theCounters[j]
            if (currentCounter < minSpace) {
                minSpace = currentCounter
            }
            if (currentCounter > maxSpace) {
                maxSpace = currentCounter
            }
            j += 2
        }
        val thresholdSpace = (minSpace + maxSpace) / 2

        var bitmask = 1 shl 7
        var pattern = 0
        for (i in 0 until 7) {
            val threshold = if ((i and 1) == 0) thresholdBar else thresholdSpace
            bitmask = bitmask shr 1
            if (theCounters[position + i] > threshold) {
                pattern = pattern or bitmask
            }
        }

        for (i in CHARACTER_ENCODINGS.indices) {
            if (CHARACTER_ENCODINGS[i] == pattern) {
                return i
            }
        }
        return -1
    }

    /** Codabar alphabet, encodings and thresholds. */
    companion object {

        /** Stripes larger than this multiple of the average long stripe are rejected. */
        private const val MAX_ACCEPTABLE = 2.0f

        /** Padding added to the long stripe threshold. */
        private const val PADDING = 1.5f

        /** Characters of the Codabar alphabet, in encoding order. */
        private const val ALPHABET_STRING = "0123456789-$:/.+ABCD"

        /** The alphabet as an array. */
        val ALPHABET: CharArray = ALPHABET_STRING.toCharArray()

        /** Encodings of the alphabet characters: the 7 least-significant bits are the wide (1) / narrow (0) pattern. */
        val CHARACTER_ENCODINGS: IntArray = intArrayOf(
            0x003, 0x006, 0x009, 0x060, 0x012, 0x042, 0x021, 0x024, 0x030, 0x048,
            0x00c, 0x018, 0x045, 0x051, 0x054, 0x015, 0x01A, 0x029, 0x00B, 0x00E,
        )

        /** Minimal number of characters, including start and stop, for a valid symbol. */
        private const val MIN_CHARACTER_LENGTH = 3

        /** Official start and end characters. */
        private val STARTEND_ENCODING: CharArray = charArrayOf('A', 'B', 'C', 'D')

        /** Returns whether [array] contains [key]. */
        fun arrayContains(array: CharArray?, key: Char): Boolean {
            if (array != null) {
                for (c in array) {
                    if (c == key) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
