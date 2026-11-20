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
import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils

/** Decodes Code 93 barcodes. */
internal class Code93Reader : OneDReader() {

    /** Reused buffer for decoded characters. */
    private val decodeRowResult = StringBuilder(20)

    /** Reused run-length counters. */
    private val counters = IntArray(6)

    /** Decodes one Code 93 symbol from [row], verifying both check characters. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        val start = findAsteriskPattern(row)
        var nextStart = row.getNextSet(start[1])
        val end = row.size

        val theCounters = counters
        theCounters.fill(0)
        val result = decodeRowResult
        result.setLength(0)

        var decodedChar: Char
        var lastStart: Int
        do {
            OneDReader.recordPattern(row, nextStart, theCounters)
            val pattern = toPattern(theCounters)
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
        result.deleteAt(result.length - 1)

        var lastPatternSize = 0
        for (counter in theCounters) {
            lastPatternSize += counter
        }

        if (nextStart == end || !row.get(nextStart)) {
            throw NotFoundException.notFoundInstance
        }

        if (result.length < 2) {
            throw NotFoundException.notFoundInstance
        }

        checkChecksums(result)
        result.setLength(result.length - 2)

        val resultString = decodeExtended(result)

        val left = (start[1] + start[0]) / 2.0f
        val right = lastStart + lastPatternSize / 2.0f

        val resultObject = Result(
            resultString,
            null,
            arrayOf(
                ResultPoint(left, rowNumber.toFloat()),
                ResultPoint(right, rowNumber.toFloat()),
            ),
            BarcodeFormat.CODE_93,
        )
        resultObject.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]G0")
        return resultObject
    }

    /** Finds the '*' start pattern; returns its start/end offsets or throws NotFoundException. */
    private fun findAsteriskPattern(row: BitArray): IntArray {
        val width = row.size
        val rowOffset = row.getNextSet(0)

        counters.fill(0)
        val theCounters = counters
        var patternStart = rowOffset
        var isWhite = false
        val patternLength = theCounters.size

        var counterPosition = 0
        for (i in rowOffset until width) {
            if (row.get(i) != isWhite) {
                theCounters[counterPosition]++
            } else {
                if (counterPosition == patternLength - 1) {
                    if (toPattern(theCounters) == ASTERISK_ENCODING) {
                        return intArrayOf(patternStart, i)
                    }
                    patternStart += theCounters[0] + theCounters[1]
                    theCounters.copyInto(theCounters, 0, 2, counterPosition + 1)
                    theCounters[counterPosition - 1] = 0
                    theCounters[counterPosition] = 0
                    counterPosition--
                } else {
                    counterPosition++
                }
                theCounters[counterPosition] = 1
                isWhite = !isWhite
            }
        }
        throw NotFoundException.notFoundInstance
    }

    /** Code 93 alphabet, encodings and pattern helpers. */
    companion object {

        /** Characters of the Code 93 alphabet; 'abcd' stand in for the ($) (%) (/) (+) shift characters. */
        const val ALPHABET_STRING = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%abcd*"

        /** The alphabet as an array. */
        private val ALPHABET: CharArray = ALPHABET_STRING.toCharArray()

        /** Encodings of the alphabet characters as 9 bit bar/space module patterns. */
        val CHARACTER_ENCODINGS: IntArray = intArrayOf(
            0x114, 0x148, 0x144, 0x142, 0x128, 0x124, 0x122, 0x150, 0x112, 0x10A,
            0x1A8, 0x1A4, 0x1A2, 0x194, 0x192, 0x18A, 0x168, 0x164, 0x162, 0x134,
            0x11A, 0x158, 0x14C, 0x146, 0x12C, 0x116, 0x1B4, 0x1B2, 0x1AC, 0x1A6,
            0x196, 0x19A, 0x16C, 0x166, 0x136, 0x13A,
            0x12E, 0x1D4, 0x1D2, 0x1CA, 0x16E, 0x176, 0x1AE,
            0x126, 0x1DA, 0x1D6, 0x132, 0x15E,
        )

        /** Encoding of the '*' start/stop character. */
        val ASTERISK_ENCODING: Int = CHARACTER_ENCODINGS[47]

        /** Converts six run lengths into a 9 module pattern, or -1 if any run is not 1-4 modules wide. */
        private fun toPattern(counters: IntArray): Int {
            var sum = 0
            for (counter in counters) {
                sum += counter
            }
            var pattern = 0
            val max = counters.size
            for (i in 0 until max) {
                val scaled = MathUtils.round(counters[i] * 9.0f / sum)
                if (scaled < 1 || scaled > 4) {
                    return -1
                }
                if ((i and 0x01) == 0) {
                    for (j in 0 until scaled) {
                        pattern = (pattern shl 1) or 0x01
                    }
                } else {
                    pattern = pattern shl scaled
                }
            }
            return pattern
        }

        /** Maps a module [pattern] to its character; throws NotFoundException if unknown. */
        private fun patternToChar(pattern: Int): Char {
            for (i in CHARACTER_ENCODINGS.indices) {
                if (CHARACTER_ENCODINGS[i] == pattern) {
                    return ALPHABET[i]
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /** Decodes the a-d shift sequences in [encoded]; throws FormatException on invalid pairs. */
        private fun decodeExtended(encoded: CharSequence): String {
            val length = encoded.length
            val decoded = StringBuilder(length)
            var i = 0
            while (i < length) {
                val c = encoded[i]
                if (c in 'a'..'d') {
                    if (i >= length - 1) {
                        throw FormatException.formatInstance
                    }
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

        /** Decodes the shift character [c] (a-d) followed by [next]; throws FormatException on invalid pairs. */
        private fun decodeShifted(c: Char, next: Char): Char = when (c) {
            'd' -> if (next in 'A'..'Z') next + 32 else throw FormatException.formatInstance
            'a' -> if (next in 'A'..'Z') next - 64 else throw FormatException.formatInstance
            'b' -> when {
                next in 'A'..'E' -> next - 38
                next in 'F'..'J' -> next - 11
                next in 'K'..'O' -> next + 16
                next in 'P'..'T' -> next + 43
                next == 'U' -> '\u0000'
                next == 'V' -> '@'
                next == 'W' -> '`'
                next in 'X'..'Z' -> '\u007F'
                else -> throw FormatException.formatInstance
            }
            else -> when {
                next in 'A'..'O' -> next - 32
                next == 'Z' -> ':'
                else -> throw FormatException.formatInstance
            }
        }

        /** Verifies both check characters at the end of [result]; throws ChecksumException on mismatch. */
        private fun checkChecksums(result: CharSequence) {
            val length = result.length
            checkOneChecksum(result, length - 2, 20)
            checkOneChecksum(result, length - 1, 15)
        }

        /** Verifies the check character at [checkPosition] using weights cycling up to [weightMax]. */
        private fun checkOneChecksum(result: CharSequence, checkPosition: Int, weightMax: Int) {
            var weight = 1
            var total = 0
            for (i in checkPosition - 1 downTo 0) {
                total += weight * ALPHABET_STRING.indexOf(result[i])
                if (++weight > weightMax) {
                    weight = 1
                }
            }
            if (result[checkPosition] != ALPHABET[total % 47]) {
                throw ChecksumException.checksumInstance
            }
        }
    }
}
