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
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Implements decoding of the UPC-E format. */
internal class UPCEReader : UPCEANReader() {

    /** Reused run-length counters. */
    private val decodeMiddleCounters = IntArray(4)

    /** Decodes the six digits and recovers the number system and check digit from their parity. */
    override fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int {
        val counters = decodeMiddleCounters
        counters[0] = 0
        counters[1] = 0
        counters[2] = 0
        counters[3] = 0
        val end = row.size
        var rowOffset = startRange[1]

        var lgPatternFound = 0

        var x = 0
        while (x < 6 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_AND_G_PATTERNS)
            resultString.append('0' + bestMatch % 10)
            for (counter in counters) {
                rowOffset += counter
            }
            if (bestMatch >= 10) {
                lgPatternFound = lgPatternFound or (1 shl (5 - x))
            }
            x++
        }

        determineNumSysAndCheckDigit(resultString, lgPatternFound)

        return rowOffset
    }

    /** UPC-E ends with the combined middle/end guard pattern. */
    override fun decodeEnd(row: BitArray, endStart: Int): IntArray =
        UPCEANReader.findGuardPattern(row, endStart, true, MIDDLE_END_PATTERN)

    /** Verifies the checksum of the equivalent UPC-A code. */
    override fun checkChecksum(s: String): Boolean = super.checkChecksum(convertUPCEtoUPCA(s))

    /** Always UPC-E. */
    override val barcodeFormat: BarcodeFormat
        get() = BarcodeFormat.UPC_E

    /** UPC-E parity tables and conversion helpers. */
    companion object {

        /** The pattern that marks the middle, and end, of a UPC-E pattern. */
        private val MIDDLE_END_PATTERN: IntArray = intArrayOf(1, 1, 1, 1, 1, 1)

        /**
         * Even/odd parity patterns of the six digits that imply both the number system (0 or 1)
         * and the check digit.
         */
        val NUMSYS_AND_CHECK_DIGIT_PATTERNS: Array<IntArray> = arrayOf(
            intArrayOf(0x38, 0x34, 0x32, 0x31, 0x2C, 0x26, 0x23, 0x2A, 0x29, 0x25),
            intArrayOf(0x07, 0x0B, 0x0D, 0x0E, 0x13, 0x19, 0x1C, 0x15, 0x16, 0x1A),
        )

        /** Prepends the number system and appends the check digit implied by [lgPatternFound]; throws NotFoundException if none. */
        private fun determineNumSysAndCheckDigit(resultString: StringBuilder, lgPatternFound: Int) {
            for (numSys in 0..1) {
                for (d in 0 until 10) {
                    if (lgPatternFound == NUMSYS_AND_CHECK_DIGIT_PATTERNS[numSys][d]) {
                        resultString.insert(0, '0' + numSys)
                        resultString.append('0' + d)
                        return
                    }
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /** Expands a UPC-E value [upce] back into its full, equivalent UPC-A code value. */
        fun convertUPCEtoUPCA(upce: String): String {
            val upceChars = upce.substring(1, 7)
            val result = StringBuilder(12)
            result.append(upce[0])
            val lastChar = upceChars[5]
            when (lastChar) {
                '0', '1', '2' -> {
                    result.append(upceChars.substring(0, 2))
                    result.append(lastChar)
                    result.append("0000")
                    result.append(upceChars.substring(2, 5))
                }
                '3' -> {
                    result.append(upceChars.substring(0, 3))
                    result.append("00000")
                    result.append(upceChars.substring(3, 5))
                }
                '4' -> {
                    result.append(upceChars.substring(0, 4))
                    result.append("00000")
                    result.append(upceChars[4])
                }
                else -> {
                    result.append(upceChars.substring(0, 5))
                    result.append("0000")
                    result.append(lastChar)
                }
            }
            if (upce.length >= 8) {
                result.append(upce[7])
            }
            return result.toString()
        }
    }
}
