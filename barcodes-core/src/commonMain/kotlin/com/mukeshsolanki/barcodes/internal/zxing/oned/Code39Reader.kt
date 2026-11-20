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
import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import kotlin.math.max

/**
 * Decodes Code 39 barcodes. When [usingCheckDigit] is true the last data character is verified as a
 * check digit; when [extendedMode] is true "Full ASCII Code 39" sequences are decoded.
 */
internal class Code39Reader(
    private val usingCheckDigit: Boolean = false,
    private val extendedMode: Boolean = false,
) : OneDReader() {

    /** Reused buffer for decoded characters. */
    private val decodeRowResult = StringBuilder(20)

    /** Reused run-length counters. */
    private val counters = IntArray(9)

    /** Decodes one Code 39 symbol from [row]. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        val theCounters = counters
        theCounters.fill(0)
        val result = decodeRowResult
        result.setLength(0)

        val start = findAsteriskPattern(row, theCounters)
        var nextStart = row.getNextSet(start[1])
        val end = row.size

        var decodedChar: Char
        var lastStart: Int
        do {
            OneDReader.recordPattern(row, nextStart, theCounters)
            val pattern = toNarrowWidePattern(theCounters)
            if (pattern < 0) {
                throw NotFoundException.notFoundInstance
            }
            decodedChar = patternToChar(pattern)
            result.append(decodedChar)
            lastStart = nextStart
            for (counter in theCounters) {
                nextStart += counter
            }
            nextStart = row.getNextSet(nextStart)
        } while (decodedChar != '*')
        result.setLength(result.length - 1)

        var lastPatternSize = 0
        for (counter in theCounters) {
            lastPatternSize += counter
        }
        val whiteSpaceAfterEnd = nextStart - lastStart - lastPatternSize
        if (nextStart != end && (whiteSpaceAfterEnd * 2) < lastPatternSize) {
            throw NotFoundException.notFoundInstance
        }

        if (usingCheckDigit) {
            val max = result.length - 1
            var total = 0
            for (i in 0 until max) {
                total += ALPHABET_STRING.indexOf(decodeRowResult[i])
            }
            if (result[max] != ALPHABET_STRING[total % 43]) {
                throw ChecksumException.checksumInstance
            }
            result.setLength(max)
        }

        if (result.isEmpty()) {
            throw NotFoundException.notFoundInstance
        }

        val resultString = if (extendedMode) decodeExtended(result) else result.toString()

        val left = (start[1] + start[0]) / 2.0f
        val right = lastStart + lastPatternSize / 2.0f

        val resultObject = Result(
            resultString,
            null,
            arrayOf(
                ResultPoint(left, rowNumber.toFloat()),
                ResultPoint(right, rowNumber.toFloat()),
            ),
            BarcodeFormat.CODE_39,
        )
        resultObject.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]A0")
        return resultObject
    }

    /** Code 39 alphabet, encodings and pattern helpers. */
    companion object {

        /** Characters of the Code 39 alphabet, in encoding order. */
        const val ALPHABET_STRING = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"

        /**
         * Encodings of the alphabet characters: the 9 least-significant bits are the wide (1) / narrow (0)
         * pattern of bars and spaces.
         */
        val CHARACTER_ENCODINGS: IntArray = intArrayOf(
            0x034, 0x121, 0x061, 0x160, 0x031, 0x130, 0x070, 0x025, 0x124, 0x064,
            0x109, 0x049, 0x148, 0x019, 0x118, 0x058, 0x00D, 0x10C, 0x04C, 0x01C,
            0x103, 0x043, 0x142, 0x013, 0x112, 0x052, 0x007, 0x106, 0x046, 0x016,
            0x181, 0x0C1, 0x1C0, 0x091, 0x190, 0x0D0, 0x085, 0x184, 0x0C4, 0x0A8,
            0x0A2, 0x08A, 0x02A,
        )

        /** Encoding of the '*' start/stop character. */
        const val ASTERISK_ENCODING = 0x094

        /**
         * Finds the '*' start pattern preceded by enough whitespace, recording runs into [counters].
         * Returns its start/end offsets; throws NotFoundException if not found.
         */
        private fun findAsteriskPattern(row: BitArray, counters: IntArray): IntArray {
            val width = row.size
            val rowOffset = row.getNextSet(0)

            var counterPosition = 0
            var patternStart = rowOffset
            var isWhite = false
            val patternLength = counters.size

            for (i in rowOffset until width) {
                if (row.get(i) != isWhite) {
                    counters[counterPosition]++
                } else {
                    if (counterPosition == patternLength - 1) {
                        if (toNarrowWidePattern(counters) == ASTERISK_ENCODING &&
                            row.isRange(max(0, patternStart - ((i - patternStart) / 2)), patternStart, false)
                        ) {
                            return intArrayOf(patternStart, i)
                        }
                        patternStart += counters[0] + counters[1]
                        counters.copyInto(counters, 0, 2, counterPosition + 1)
                        counters[counterPosition - 1] = 0
                        counters[counterPosition] = 0
                        counterPosition--
                    } else {
                        counterPosition++
                    }
                    counters[counterPosition] = 1
                    isWhite = !isWhite
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /**
         * Converts [counters] to a 9 bit wide/narrow pattern with exactly three wide elements, or -1 on failure.
         * Returning -1 instead of throwing saves many exceptions per image.
         */
        private fun toNarrowWidePattern(counters: IntArray): Int {
            val numCounters = counters.size
            var maxNarrowCounter = 0
            var wideCounters: Int
            do {
                var minCounter = Int.MAX_VALUE
                for (counter in counters) {
                    if (counter < minCounter && counter > maxNarrowCounter) {
                        minCounter = counter
                    }
                }
                maxNarrowCounter = minCounter
                wideCounters = 0
                var totalWideCountersWidth = 0
                var pattern = 0
                for (i in 0 until numCounters) {
                    val counter = counters[i]
                    if (counter > maxNarrowCounter) {
                        pattern = pattern or (1 shl (numCounters - 1 - i))
                        wideCounters++
                        totalWideCountersWidth += counter
                    }
                }
                if (wideCounters == 3) {
                    var i = 0
                    while (i < numCounters && wideCounters > 0) {
                        val counter = counters[i]
                        if (counter > maxNarrowCounter) {
                            wideCounters--
                            if ((counter * 2) >= totalWideCountersWidth) {
                                return -1
                            }
                        }
                        i++
                    }
                    return pattern
                }
            } while (wideCounters > 3)
            return -1
        }

        /** Maps a wide/narrow [pattern] to its character; throws NotFoundException if unknown. */
        private fun patternToChar(pattern: Int): Char {
            for (i in CHARACTER_ENCODINGS.indices) {
                if (CHARACTER_ENCODINGS[i] == pattern) {
                    return ALPHABET_STRING[i]
                }
            }
            if (pattern == ASTERISK_ENCODING) {
                return '*'
            }
            throw NotFoundException.notFoundInstance
        }

        /** Decodes Full ASCII shift sequences (+, $, %, /) in [encoded]; throws FormatException on invalid pairs. */
        private fun decodeExtended(encoded: CharSequence): String {
            val length = encoded.length
            val decoded = StringBuilder(length)
            var i = 0
            while (i < length) {
                val c = encoded[i]
                if (c == '+' || c == '$' || c == '%' || c == '/') {
                    val next = encoded[i + 1]
                    decoded.append(decodeShifted(c, next))
                    i++
                } else {
                    decoded.append(c)
                }
                i++
            }
            return decoded.toString()
        }

        /** Decodes the shift character [c] followed by [next]; throws FormatException on invalid pairs. */
        private fun decodeShifted(c: Char, next: Char): Char = when (c) {
            '+' -> if (next in 'A'..'Z') next + 32 else throw FormatException.formatInstance
            '$' -> if (next in 'A'..'Z') next - 64 else throw FormatException.formatInstance
            '%' -> when {
                next in 'A'..'E' -> next - 38
                next in 'F'..'J' -> next - 11
                next in 'K'..'O' -> next + 16
                next in 'P'..'T' -> next + 43
                next == 'U' -> '\u0000'
                next == 'V' -> '@'
                next == 'W' -> '`'
                next == 'X' || next == 'Y' || next == 'Z' -> '\u007F'
                else -> throw FormatException.formatInstance
            }
            else -> when {
                next in 'A'..'O' -> next - 32
                next == 'Z' -> ':'
                else -> throw FormatException.formatInstance
            }
        }
    }
}
