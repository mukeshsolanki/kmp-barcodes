/*
 * Copyright (C) 2010 ZXing authors
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

/** Decodes the 5 digit UPC/EAN add-on extension (suggested price). See [UPCEANExtension2Support]. */
internal class UPCEANExtension5Support {

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

    /** Decodes the five digits and verifies the parity-encoded check digit; returns the offset after the last digit. */
    private fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int {
        val counters = decodeMiddleCounters
        counters[0] = 0
        counters[1] = 0
        counters[2] = 0
        counters[3] = 0
        val end = row.size
        var rowOffset = startRange[1]

        var lgPatternFound = 0

        var x = 0
        while (x < 5 && rowOffset < end) {
            val bestMatch = UPCEANReader.decodeDigit(row, counters, rowOffset, UPCEANReader.L_AND_G_PATTERNS)
            resultString.append('0' + bestMatch % 10)
            for (counter in counters) {
                rowOffset += counter
            }
            if (bestMatch >= 10) {
                lgPatternFound = lgPatternFound or (1 shl (4 - x))
            }
            if (x != 4) {
                rowOffset = row.getNextSet(rowOffset)
                rowOffset = row.getNextUnset(rowOffset)
            }
            x++
        }

        if (resultString.length != 5) {
            throw NotFoundException.notFoundInstance
        }

        val checkDigit = determineCheckDigit(lgPatternFound)
        if (extensionChecksum(resultString.toString()) != checkDigit) {
            throw NotFoundException.notFoundInstance
        }

        return rowOffset
    }

    /** Check digit tables and parsing helpers. */
    private companion object {

        /** Parity patterns encoding each check digit 0-9. */
        val CHECK_DIGIT_ENCODINGS: IntArray = intArrayOf(
            0x18, 0x14, 0x12, 0x11, 0x0C, 0x06, 0x03, 0x0A, 0x09, 0x05,
        )

        /** Computes the extension checksum of the digit string [s]. */
        fun extensionChecksum(s: CharSequence): Int {
            val length = s.length
            var sum = 0
            var i = length - 2
            while (i >= 0) {
                sum += s[i] - '0'
                i -= 2
            }
            sum *= 3
            i = length - 1
            while (i >= 0) {
                sum += s[i] - '0'
                i -= 2
            }
            sum *= 3
            return sum % 10
        }

        /** Maps the parity pattern [lgPatternFound] to its check digit; throws NotFoundException if unknown. */
        fun determineCheckDigit(lgPatternFound: Int): Int {
            for (d in 0 until 10) {
                if (lgPatternFound == CHECK_DIGIT_ENCODINGS[d]) {
                    return d
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /** Maps a 5 digit extension to its SUGGESTED_PRICE metadata, or null when unknown. */
        fun parseExtensionString(raw: String): Map<ResultMetadataType, Any>? {
            if (raw.length != 5) {
                return null
            }
            val value = parseExtension5String(raw) ?: return null
            return mapOf(ResultMetadataType.SUGGESTED_PRICE to value)
        }

        /** Formats a 5 digit price extension as a currency string, or null for "no suggested price". */
        fun parseExtension5String(raw: String): String? {
            val currency: String
            when (raw[0]) {
                '0' -> currency = "£"
                '5' -> currency = "$"
                '9' -> {
                    when (raw) {
                        "90000" -> return null
                        "99991" -> return "0.00"
                        "99990" -> return "Used"
                    }
                    currency = ""
                }
                else -> currency = ""
            }
            val rawAmount = raw.substring(1).toInt()
            val unitsString = (rawAmount / 100).toString()
            val hundredths = rawAmount % 100
            val hundredthsString = if (hundredths < 10) "0$hundredths" else hundredths.toString()
            return "$currency$unitsString.$hundredthsString"
        }
    }
}
