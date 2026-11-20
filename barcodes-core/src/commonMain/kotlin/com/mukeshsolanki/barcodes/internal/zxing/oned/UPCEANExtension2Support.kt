/*
 * Copyright (C) 2012 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Decodes the 2 digit UPC/EAN add-on extension (issue number). See [UPCEANExtension5Support]. */
internal class UPCEANExtension2Support {

    /** Reused run-length counters. */
    private val decodeMiddleCounters = IntArray(4)

    /** Reused buffer for decoded digits. */
    private val decodeRowStringBuffer = StringBuilder()

    /** Decodes the extension whose start guard spans [extensionStartRange]. */
    fun decodeRow(rowNumber: Int, row: BitArray, extensionStartRange: IntArray): Result {
        val result = decodeRowStringBuffer
        result.setLength(0)
        val end = decodeMiddle(row, extensionStartRange, result)

        val resultString = result.toString()
        val extensionData = parseExtensionString(resultString)

        val extensionResult = Result(
            resultString,
            null,
            arrayOf(
                ResultPoint((extensionStartRange[0] + extensionStartRange[1]) / 2.0f, rowNumber.toFloat()),
                ResultPoint(end.toFloat(), rowNumber.toFloat()),
            ),
            BarcodeFormat.UPC_EAN_EXTENSION,
        )
        if (extensionData != null) {
            extensionResult.putAllMetadata(extensionData)
        }
        return extensionResult
    }

    /** Decodes the two digits and verifies their parity; returns the offset after the last digit. */
    private fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int {
        val counters = decodeMiddleCounters
        counters[0] = 0
        counters[1] = 0
        counters[2] = 0
        counters[3] = 0
        val end = row.size
        var rowOffset = startRange[1]

        var checkParity = 0

        var x = 0
        while (x < 2 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_AND_G_PATTERNS)
            resultString.append('0' + bestMatch % 10)
            for (counter in counters) {
                rowOffset += counter
            }
            if (bestMatch >= 10) {
                checkParity = checkParity or (1 shl (1 - x))
            }
            if (x != 1) {
                rowOffset = row.getNextSet(rowOffset)
                rowOffset = row.getNextUnset(rowOffset)
            }
            x++
        }

        if (resultString.length != 2) {
            throw NotFoundException.notFoundInstance
        }

        if (resultString.toString().toInt() % 4 != checkParity) {
            throw NotFoundException.notFoundInstance
        }

        return rowOffset
    }

    /** Parsing helpers. */
    private companion object {

        /** Maps a 2 digit extension to its ISSUE_NUMBER metadata, or null when [raw] is not 2 digits. */
        fun parseExtensionString(raw: String): Map<ResultMetadataType, Any>? {
            if (raw.length != 2) {
                return null
            }
            return mapOf(ResultMetadataType.ISSUE_NUMBER to raw.toInt())
        }
    }
}
