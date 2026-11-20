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
import kotlin.math.min

/** Decodes Code 128 barcodes. */
internal class Code128Reader : OneDReader() {

    /** Decodes one Code 128 symbol from [row], including GS1 FNC1 handling when ASSUME_GS1 is set. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        val convertFNC1 = hints != null && hints.containsKey(DecodeHintType.ASSUME_GS1)

        var symbologyModifier = 0

        val startPatternInfo = findStartPattern(row)
        val startCode = startPatternInfo[2]

        val rawCodes = ArrayList<Byte>(20)
        rawCodes.add(startCode.toByte())

        var codeSet = when (startCode) {
            CODE_START_A -> CODE_CODE_A
            CODE_START_B -> CODE_CODE_B
            CODE_START_C -> CODE_CODE_C
            else -> throw FormatException.formatInstance
        }

        var done = false
        var isNextShifted = false

        val result = StringBuilder(20)

        var lastStart = startPatternInfo[0]
        var nextStart = startPatternInfo[1]
        val counters = IntArray(6)

        var lastCode = 0
        var code = 0
        var checksumTotal = startCode
        var multiplier = 0
        var lastCharacterWasPrintable = true
        var upperMode = false
        var shiftUpperMode = false

        while (!done) {

            val unshift = isNextShifted
            isNextShifted = false

            lastCode = code

            code = decodeCode(row, counters, nextStart)

            rawCodes.add(code.toByte())

            if (code != CODE_STOP) {
                lastCharacterWasPrintable = true
            }

            if (code != CODE_STOP) {
                multiplier++
                checksumTotal += multiplier * code
            }

            lastStart = nextStart
            for (counter in counters) {
                nextStart += counter
            }

            when (code) {
                CODE_START_A, CODE_START_B, CODE_START_C -> throw FormatException.formatInstance
            }

            when (codeSet) {

                CODE_CODE_A -> if (code < 64) {
                    if (shiftUpperMode == upperMode) {
                        result.append(' ' + code)
                    } else {
                        result.append(' ' + code + 128)
                    }
                    shiftUpperMode = false
                } else if (code < 96) {
                    if (shiftUpperMode == upperMode) {
                        result.append(Char(code - 64))
                    } else {
                        result.append(Char(code + 64))
                    }
                    shiftUpperMode = false
                } else {
                    if (code != CODE_STOP) {
                        lastCharacterWasPrintable = false
                    }
                    when (code) {
                        CODE_FNC_1 -> symbologyModifier = applyFnc1(result, convertFNC1, symbologyModifier)
                        CODE_FNC_2 -> symbologyModifier = 4
                        CODE_FNC_3 -> Unit
                        CODE_FNC_4_A -> if (!upperMode && shiftUpperMode) {
                            upperMode = true
                            shiftUpperMode = false
                        } else if (upperMode && shiftUpperMode) {
                            upperMode = false
                            shiftUpperMode = false
                        } else {
                            shiftUpperMode = true
                        }
                        CODE_SHIFT -> {
                            isNextShifted = true
                            codeSet = CODE_CODE_B
                        }
                        CODE_CODE_B -> codeSet = CODE_CODE_B
                        CODE_CODE_C -> codeSet = CODE_CODE_C
                        CODE_STOP -> done = true
                    }
                }

                CODE_CODE_B -> if (code < 96) {
                    if (shiftUpperMode == upperMode) {
                        result.append(' ' + code)
                    } else {
                        result.append(' ' + code + 128)
                    }
                    shiftUpperMode = false
                } else {
                    if (code != CODE_STOP) {
                        lastCharacterWasPrintable = false
                    }
                    when (code) {
                        CODE_FNC_1 -> symbologyModifier = applyFnc1(result, convertFNC1, symbologyModifier)
                        CODE_FNC_2 -> symbologyModifier = 4
                        CODE_FNC_3 -> Unit
                        CODE_FNC_4_B -> if (!upperMode && shiftUpperMode) {
                            upperMode = true
                            shiftUpperMode = false
                        } else if (upperMode && shiftUpperMode) {
                            upperMode = false
                            shiftUpperMode = false
                        } else {
                            shiftUpperMode = true
                        }
                        CODE_SHIFT -> {
                            isNextShifted = true
                            codeSet = CODE_CODE_A
                        }
                        CODE_CODE_A -> codeSet = CODE_CODE_A
                        CODE_CODE_C -> codeSet = CODE_CODE_C
                        CODE_STOP -> done = true
                    }
                }

                CODE_CODE_C -> if (code < 100) {
                    if (code < 10) {
                        result.append('0')
                    }
                    result.append(code)
                } else {
                    if (code != CODE_STOP) {
                        lastCharacterWasPrintable = false
                    }
                    when (code) {
                        CODE_FNC_1 -> symbologyModifier = applyFnc1(result, convertFNC1, symbologyModifier)
                        CODE_CODE_A -> codeSet = CODE_CODE_A
                        CODE_CODE_B -> codeSet = CODE_CODE_B
                        CODE_STOP -> done = true
                    }
                }
            }

            if (unshift) {
                codeSet = if (codeSet == CODE_CODE_A) CODE_CODE_B else CODE_CODE_A
            }
        }

        val lastPatternSize = nextStart - lastStart

        nextStart = row.getNextUnset(nextStart)
        if (!row.isRange(nextStart, min(row.size, nextStart + (nextStart - lastStart) / 2), false)) {
            throw NotFoundException.notFoundInstance
        }

        checksumTotal -= multiplier * lastCode
        if (checksumTotal % 103 != lastCode) {
            throw ChecksumException.checksumInstance
        }

        val resultLength = result.length
        if (resultLength == 0) {
            throw NotFoundException.notFoundInstance
        }

        if (resultLength > 0 && lastCharacterWasPrintable) {
            if (codeSet == CODE_CODE_C) {
                result.deleteRange(resultLength - 2, resultLength)
            } else {
                result.deleteRange(resultLength - 1, resultLength)
            }
        }

        val left = (startPatternInfo[1] + startPatternInfo[0]) / 2.0f
        val right = lastStart + lastPatternSize / 2.0f

        val rawBytes = rawCodes.toByteArray()

        val resultObject = Result(
            result.toString(),
            rawBytes,
            arrayOf(
                ResultPoint(left, rowNumber.toFloat()),
                ResultPoint(right, rowNumber.toFloat()),
            ),
            BarcodeFormat.CODE_128,
        )
        resultObject.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]C$symbologyModifier")
        return resultObject
    }

    /** Code 128 pattern table, code constants and run-length helpers. */
    companion object {

        /** Bar/space widths of every Code 128 symbol value 0-106 (106 is the 7 element stop pattern). */
        val CODE_PATTERNS: Array<IntArray> = arrayOf(
            intArrayOf(2, 1, 2, 2, 2, 2),
            intArrayOf(2, 2, 2, 1, 2, 2),
            intArrayOf(2, 2, 2, 2, 2, 1),
            intArrayOf(1, 2, 1, 2, 2, 3),
            intArrayOf(1, 2, 1, 3, 2, 2),
            intArrayOf(1, 3, 1, 2, 2, 2),
            intArrayOf(1, 2, 2, 2, 1, 3),
            intArrayOf(1, 2, 2, 3, 1, 2),
            intArrayOf(1, 3, 2, 2, 1, 2),
            intArrayOf(2, 2, 1, 2, 1, 3),
            intArrayOf(2, 2, 1, 3, 1, 2),
            intArrayOf(2, 3, 1, 2, 1, 2),
            intArrayOf(1, 1, 2, 2, 3, 2),
            intArrayOf(1, 2, 2, 1, 3, 2),
            intArrayOf(1, 2, 2, 2, 3, 1),
            intArrayOf(1, 1, 3, 2, 2, 2),
            intArrayOf(1, 2, 3, 1, 2, 2),
            intArrayOf(1, 2, 3, 2, 2, 1),
            intArrayOf(2, 2, 3, 2, 1, 1),
            intArrayOf(2, 2, 1, 1, 3, 2),
            intArrayOf(2, 2, 1, 2, 3, 1),
            intArrayOf(2, 1, 3, 2, 1, 2),
            intArrayOf(2, 2, 3, 1, 1, 2),
            intArrayOf(3, 1, 2, 1, 3, 1),
            intArrayOf(3, 1, 1, 2, 2, 2),
            intArrayOf(3, 2, 1, 1, 2, 2),
            intArrayOf(3, 2, 1, 2, 2, 1),
            intArrayOf(3, 1, 2, 2, 1, 2),
            intArrayOf(3, 2, 2, 1, 1, 2),
            intArrayOf(3, 2, 2, 2, 1, 1),
            intArrayOf(2, 1, 2, 1, 2, 3),
            intArrayOf(2, 1, 2, 3, 2, 1),
            intArrayOf(2, 3, 2, 1, 2, 1),
            intArrayOf(1, 1, 1, 3, 2, 3),
            intArrayOf(1, 3, 1, 1, 2, 3),
            intArrayOf(1, 3, 1, 3, 2, 1),
            intArrayOf(1, 1, 2, 3, 1, 3),
            intArrayOf(1, 3, 2, 1, 1, 3),
            intArrayOf(1, 3, 2, 3, 1, 1),
            intArrayOf(2, 1, 1, 3, 1, 3),
            intArrayOf(2, 3, 1, 1, 1, 3),
            intArrayOf(2, 3, 1, 3, 1, 1),
            intArrayOf(1, 1, 2, 1, 3, 3),
            intArrayOf(1, 1, 2, 3, 3, 1),
            intArrayOf(1, 3, 2, 1, 3, 1),
            intArrayOf(1, 1, 3, 1, 2, 3),
            intArrayOf(1, 1, 3, 3, 2, 1),
            intArrayOf(1, 3, 3, 1, 2, 1),
            intArrayOf(3, 1, 3, 1, 2, 1),
            intArrayOf(2, 1, 1, 3, 3, 1),
            intArrayOf(2, 3, 1, 1, 3, 1),
            intArrayOf(2, 1, 3, 1, 1, 3),
            intArrayOf(2, 1, 3, 3, 1, 1),
            intArrayOf(2, 1, 3, 1, 3, 1),
            intArrayOf(3, 1, 1, 1, 2, 3),
            intArrayOf(3, 1, 1, 3, 2, 1),
            intArrayOf(3, 3, 1, 1, 2, 1),
            intArrayOf(3, 1, 2, 1, 1, 3),
            intArrayOf(3, 1, 2, 3, 1, 1),
            intArrayOf(3, 3, 2, 1, 1, 1),
            intArrayOf(3, 1, 4, 1, 1, 1),
            intArrayOf(2, 2, 1, 4, 1, 1),
            intArrayOf(4, 3, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 2, 2, 4),
            intArrayOf(1, 1, 1, 4, 2, 2),
            intArrayOf(1, 2, 1, 1, 2, 4),
            intArrayOf(1, 2, 1, 4, 2, 1),
            intArrayOf(1, 4, 1, 1, 2, 2),
            intArrayOf(1, 4, 1, 2, 2, 1),
            intArrayOf(1, 1, 2, 2, 1, 4),
            intArrayOf(1, 1, 2, 4, 1, 2),
            intArrayOf(1, 2, 2, 1, 1, 4),
            intArrayOf(1, 2, 2, 4, 1, 1),
            intArrayOf(1, 4, 2, 1, 1, 2),
            intArrayOf(1, 4, 2, 2, 1, 1),
            intArrayOf(2, 4, 1, 2, 1, 1),
            intArrayOf(2, 2, 1, 1, 1, 4),
            intArrayOf(4, 1, 3, 1, 1, 1),
            intArrayOf(2, 4, 1, 1, 1, 2),
            intArrayOf(1, 3, 4, 1, 1, 1),
            intArrayOf(1, 1, 1, 2, 4, 2),
            intArrayOf(1, 2, 1, 1, 4, 2),
            intArrayOf(1, 2, 1, 2, 4, 1),
            intArrayOf(1, 1, 4, 2, 1, 2),
            intArrayOf(1, 2, 4, 1, 1, 2),
            intArrayOf(1, 2, 4, 2, 1, 1),
            intArrayOf(4, 1, 1, 2, 1, 2),
            intArrayOf(4, 2, 1, 1, 1, 2),
            intArrayOf(4, 2, 1, 2, 1, 1),
            intArrayOf(2, 1, 2, 1, 4, 1),
            intArrayOf(2, 1, 4, 1, 2, 1),
            intArrayOf(4, 1, 2, 1, 2, 1),
            intArrayOf(1, 1, 1, 1, 4, 3),
            intArrayOf(1, 1, 1, 3, 4, 1),
            intArrayOf(1, 3, 1, 1, 4, 1),
            intArrayOf(1, 1, 4, 1, 1, 3),
            intArrayOf(1, 1, 4, 3, 1, 1),
            intArrayOf(4, 1, 1, 1, 1, 3),
            intArrayOf(4, 1, 1, 3, 1, 1),
            intArrayOf(1, 1, 3, 1, 4, 1),
            intArrayOf(1, 1, 4, 1, 3, 1),
            intArrayOf(3, 1, 1, 1, 4, 1),
            intArrayOf(4, 1, 1, 1, 3, 1),
            intArrayOf(2, 1, 1, 4, 1, 2),
            intArrayOf(2, 1, 1, 2, 1, 4),
            intArrayOf(2, 1, 1, 2, 3, 2),
            intArrayOf(2, 3, 3, 1, 1, 1, 2),
        )

        /** Maximum average variance accepted for a pattern match. */
        private const val MAX_AVG_VARIANCE = 0.25f

        /** Maximum variance any single counter may have in a pattern match. */
        private const val MAX_INDIVIDUAL_VARIANCE = 0.7f

        /** Shift to the other of code sets A/B for one character. */
        private const val CODE_SHIFT = 98

        /** Switch to code set C. */
        private const val CODE_CODE_C = 99

        /** Switch to code set B. */
        private const val CODE_CODE_B = 100

        /** Switch to code set A. */
        private const val CODE_CODE_A = 101

        /** Function code 1. */
        private const val CODE_FNC_1 = 102

        /** Function code 2. */
        private const val CODE_FNC_2 = 97

        /** Function code 3. */
        private const val CODE_FNC_3 = 96

        /** Function code 4 in code set A. */
        private const val CODE_FNC_4_A = 101

        /** Function code 4 in code set B. */
        private const val CODE_FNC_4_B = 100

        /** Start in code set A. */
        private const val CODE_START_A = 103

        /** Start in code set B. */
        private const val CODE_START_B = 104

        /** Start in code set C. */
        private const val CODE_START_C = 105

        /** Stop code. */
        private const val CODE_STOP = 106

        /**
         * Applies an FNC1 to the symbology modifier (first or second position) and, when [convertFNC1]
         * is set, appends "]C1" or a GS separator to [result]. Returns the updated modifier.
         */
        private fun applyFnc1(result: StringBuilder, convertFNC1: Boolean, symbologyModifier: Int): Int {
            val modifier = when (result.length) {
                0 -> 1
                1 -> 2
                else -> symbologyModifier
            }
            if (convertFNC1) {
                if (result.length == 0) {
                    result.append("]C1")
                } else {
                    result.append(Char(29))
                }
            }
            return modifier
        }

        /**
         * Finds a start pattern preceded by enough whitespace. Returns its start, end and start code;
         * throws NotFoundException if none is found.
         */
        private fun findStartPattern(row: BitArray): IntArray {
            val width = row.size
            val rowOffset = row.getNextSet(0)

            var counterPosition = 0
            val counters = IntArray(6)
            var patternStart = rowOffset
            var isWhite = false
            val patternLength = counters.size

            for (i in rowOffset until width) {
                if (row.get(i) != isWhite) {
                    counters[counterPosition]++
                } else {
                    if (counterPosition == patternLength - 1) {
                        var bestVariance = MAX_AVG_VARIANCE
                        var bestMatch = -1
                        for (startCode in CODE_START_A..CODE_START_C) {
                            val variance = OneDReader.patternMatchVariance(
                                counters,
                                CODE_PATTERNS[startCode],
                                MAX_INDIVIDUAL_VARIANCE,
                            )
                            if (variance < bestVariance) {
                                bestVariance = variance
                                bestMatch = startCode
                            }
                        }
                        if (bestMatch >= 0 &&
                            row.isRange(max(0, patternStart - (i - patternStart) / 2), patternStart, false)
                        ) {
                            return intArrayOf(patternStart, i, bestMatch)
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

        /** Decodes the symbol value at [rowOffset]; throws NotFoundException if no pattern matches. */
        private fun decodeCode(row: BitArray, counters: IntArray, rowOffset: Int): Int {
            OneDReader.recordPattern(row, rowOffset, counters)
            var bestVariance = MAX_AVG_VARIANCE
            var bestMatch = -1
            for (d in CODE_PATTERNS.indices) {
                val pattern = CODE_PATTERNS[d]
                val variance = OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE)
                if (variance < bestVariance) {
                    bestVariance = variance
                    bestMatch = d
                }
            }
            if (bestMatch >= 0) {
                return bestMatch
            } else {
                throw NotFoundException.notFoundInstance
            }
        }
    }
}
