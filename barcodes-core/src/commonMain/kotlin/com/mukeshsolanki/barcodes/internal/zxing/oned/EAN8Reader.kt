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
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Implements decoding of the EAN-8 format. */
internal class EAN8Reader : UPCEANReader() {

    /** Reused run-length counters. */
    private val decodeMiddleCounters = IntArray(4)

    /** Decodes the four left and four right digits around the middle guard. */
    override fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int {
        val counters = decodeMiddleCounters
        counters[0] = 0
        counters[1] = 0
        counters[2] = 0
        counters[3] = 0
        val end = row.size
        var rowOffset = startRange[1]

        var x = 0
        while (x < 4 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_PATTERNS)
            resultString.append('0' + bestMatch)
            for (counter in counters) {
                rowOffset += counter
            }
            x++
        }

        val middleRange = UPCEANReader.findGuardPattern(row, rowOffset, true, UPCEANReader.MIDDLE_PATTERN)
        rowOffset = middleRange[1]

        x = 0
        while (x < 4 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_PATTERNS)
            resultString.append('0' + bestMatch)
            for (counter in counters) {
                rowOffset += counter
            }
            x++
        }

        return rowOffset
    }

    /** Always EAN-8. */
    override val barcodeFormat: BarcodeFormat
        get() = BarcodeFormat.EAN_8
}
