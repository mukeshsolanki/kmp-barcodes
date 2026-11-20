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

/** Implements decoding of the EAN-13 format. */
internal class EAN13Reader : UPCEANReader() {

    /** Reused run-length counters. */
    private val decodeMiddleCounters = IntArray(4)

    /** Decodes the six left digits (recovering the implicit first digit from their parity) and six right digits. */
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

        determineFirstDigit(resultString, lgPatternFound)

        val middleRange = UPCEANReader.findGuardPattern(row, rowOffset, true, UPCEANReader.MIDDLE_PATTERN)
        rowOffset = middleRange[1]

        x = 0
        while (x < 6 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_PATTERNS)
            resultString.append('0' + bestMatch)
            for (counter in counters) {
                rowOffset += counter
            }
            x++
        }

        return rowOffset
    }

    /** Always EAN-13. */
    override val barcodeFormat: BarcodeFormat
        get() = BarcodeFormat.EAN_13

    /** First digit parity table and helpers. */
    companion object {

        /**
         * Odd (0) / even (1) parity patterns of the six left digits that encode the implicit
         * first digit 0-9 of an EAN-13 barcode.
         */
        val FIRST_DIGIT_ENCODINGS: IntArray = intArrayOf(
            0x00, 0x0B, 0x0D, 0xE, 0x13, 0x19, 0x1C, 0x15, 0x16, 0x1A,
        )

        /** Inserts the first digit implied by [lgPatternFound] at the start of [resultString]; throws NotFoundException if none. */
        private fun determineFirstDigit(resultString: StringBuilder, lgPatternFound: Int) {
            for (d in 0 until 10) {
                if (lgPatternFound == FIRST_DIGIT_ENCODINGS[d]) {
                    resultString.insert(0, '0' + d)
                    return
                }
            }
            throw NotFoundException.notFoundInstance
        }
    }
}
