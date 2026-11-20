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
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import kotlin.math.min

/**
 * Implements decoding of the ITF format, or Interleaved Two of Five. Only lengths 6, 8, 10, 12 and 14
 * (or longer, or those in ALLOWED_LENGTHS) are accepted to avoid false positives; no checksum is applied.
 */
internal class ITFReader : OneDReader() {

    /** Width in pixels of a narrow line in the image being decoded. */
    private var narrowLineWidth = -1

    /** Decodes one ITF symbol from [row]. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        val startRange = decodeStart(row)
        val endRange = decodeEnd(row)

        val result = StringBuilder(20)
        decodeMiddle(row, startRange[1], endRange[0], result)
        val resultString = result.toString()

        val allowedLengths = (hints?.get(DecodeHintType.ALLOWED_LENGTHS) as IntArray?) ?: DEFAULT_ALLOWED_LENGTHS

        val length = resultString.length
        var lengthOK = false
        var maxAllowedLength = 0
        for (allowedLength in allowedLengths) {
            if (length == allowedLength) {
                lengthOK = true
                break
            }
            if (allowedLength > maxAllowedLength) {
                maxAllowedLength = allowedLength
            }
        }
        if (!lengthOK && length > maxAllowedLength) {
            lengthOK = true
        }
        if (!lengthOK) {
            throw FormatException.formatInstance
        }

        val resultObject = Result(
            resultString,
            null,
            arrayOf(
                ResultPoint(startRange[1].toFloat(), rowNumber.toFloat()),
                ResultPoint(endRange[0].toFloat(), rowNumber.toFloat()),
            ),
            BarcodeFormat.ITF,
        )
        resultObject.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]I0")
        return resultObject
    }

    /** Finds the start pattern, records the narrow line width and validates the leading quiet zone. */
    private fun decodeStart(row: BitArray): IntArray {
        val endStart = skipWhiteSpace(row)
        val startPattern = findGuardPattern(row, endStart, START_PATTERN)

        narrowLineWidth = (startPattern[1] - startPattern[0]) / 4

        validateQuietZone(row, startPattern[0])

        return startPattern
    }

    /**
     * Requires a quiet zone of ten narrow line widths (or up to the row edge) before [startPattern];
     * throws NotFoundException otherwise.
     */
    private fun validateQuietZone(row: BitArray, startPattern: Int) {
        var quietCount = narrowLineWidth * 10

        quietCount = min(quietCount, startPattern)

        var i = startPattern - 1
        while (quietCount > 0 && i >= 0) {
            if (row.get(i)) {
                break
            }
            quietCount--
            i--
        }
        if (quietCount != 0) {
            throw NotFoundException.notFoundInstance
        }
    }

    /** Finds the end pattern by searching the reversed row; the row is restored before returning. */
    private fun decodeEnd(row: BitArray): IntArray {
        row.reverse()
        try {
            val endStart = skipWhiteSpace(row)
            val endPattern = try {
                findGuardPattern(row, endStart, END_PATTERN_REVERSED[0])
            } catch (nfe: NotFoundException) {
                findGuardPattern(row, endStart, END_PATTERN_REVERSED[1])
            }

            validateQuietZone(row, endPattern[0])

            val temp = endPattern[0]
            endPattern[0] = row.size - endPattern[1]
            endPattern[1] = row.size - temp

            return endPattern
        } finally {
            row.reverse()
        }
    }

    /** ITF patterns and digit helpers. */
    private companion object {

        /** Maximum average variance accepted for a pattern match. */
        const val MAX_AVG_VARIANCE = 0.38f

        /** Maximum variance any single counter may have in a pattern match. */
        const val MAX_INDIVIDUAL_VARIANCE = 0.5f

        /** Module width of a 3x wide line. */
        const val W = 3

        /** Module width of a 2x wide line. */
        const val w = 2

        /** Module width of a narrow line. */
        const val N = 1

        /** Valid ITF lengths. Anything longer than the largest value is also allowed. */
        val DEFAULT_ALLOWED_LENGTHS: IntArray = intArrayOf(6, 8, 10, 12, 14)

        /** Start guard pattern. */
        val START_PATTERN: IntArray = intArrayOf(N, N, N, N)

        /** End guard patterns (2x and 3x wide), reversed because the row is reversed before searching. */
        val END_PATTERN_REVERSED: Array<IntArray> = arrayOf(
            intArrayOf(N, N, w),
            intArrayOf(N, N, W),
        )

        /** Wide/narrow patterns for each digit, first with 2x then with 3x wide lines. */
        val PATTERNS: Array<IntArray> = arrayOf(
            intArrayOf(N, N, w, w, N),
            intArrayOf(w, N, N, N, w),
            intArrayOf(N, w, N, N, w),
            intArrayOf(w, w, N, N, N),
            intArrayOf(N, N, w, N, w),
            intArrayOf(w, N, w, N, N),
            intArrayOf(N, w, w, N, N),
            intArrayOf(N, N, N, w, w),
            intArrayOf(w, N, N, w, N),
            intArrayOf(N, w, N, w, N),
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

        /** Decodes interleaved digit pairs between [payloadStart] and [payloadEnd] into [resultString]. */
        fun decodeMiddle(row: BitArray, payloadStart: Int, payloadEnd: Int, resultString: StringBuilder) {
            val counterDigitPair = IntArray(10)
            val counterBlack = IntArray(5)
            val counterWhite = IntArray(5)

            var position = payloadStart
            while (position < payloadEnd) {
                OneDReader.recordPattern(row, position, counterDigitPair)
                for (k in 0 until 5) {
                    val twoK = 2 * k
                    counterBlack[k] = counterDigitPair[twoK]
                    counterWhite[k] = counterDigitPair[twoK + 1]
                }

                var bestMatch = decodeDigit(counterBlack)
                resultString.append('0' + bestMatch)
                bestMatch = decodeDigit(counterWhite)
                resultString.append('0' + bestMatch)

                for (counterDigit in counterDigitPair) {
                    position += counterDigit
                }
            }
        }

        /** Returns the index of the first set bit in [row]; throws NotFoundException if the row is all white. */
        fun skipWhiteSpace(row: BitArray): Int {
            val width = row.size
            val endStart = row.getNextSet(0)
            if (endStart == width) {
                throw NotFoundException.notFoundInstance
            }
            return endStart
        }

        /** Finds [pattern] (starting with black) in [row] from [rowOffset]; returns its start/end offsets. */
        fun findGuardPattern(row: BitArray, rowOffset: Int, pattern: IntArray): IntArray {
            val patternLength = pattern.size
            val counters = IntArray(patternLength)
            val width = row.size
            var isWhite = false

            var counterPosition = 0
            var patternStart = rowOffset
            for (x in rowOffset until width) {
                if (row.get(x) != isWhite) {
                    counters[counterPosition]++
                } else {
                    if (counterPosition == patternLength - 1) {
                        if (OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE) < MAX_AVG_VARIANCE) {
                            return intArrayOf(patternStart, x)
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

        /** Decodes five run lengths into a digit; ties or no match throw NotFoundException. */
        fun decodeDigit(counters: IntArray): Int {
            var bestVariance = MAX_AVG_VARIANCE
            var bestMatch = -1
            val max = PATTERNS.size
            for (i in 0 until max) {
                val pattern = PATTERNS[i]
                val variance = OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE)
                if (variance < bestVariance) {
                    bestVariance = variance
                    bestMatch = i
                } else if (variance == bestVariance) {
                    bestMatch = -1
                }
            }
            if (bestMatch >= 0) {
                return bestMatch % 10
            } else {
                throw NotFoundException.notFoundInstance
            }
        }
    }
}
