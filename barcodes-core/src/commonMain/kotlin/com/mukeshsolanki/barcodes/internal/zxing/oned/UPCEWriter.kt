/*
 * Copyright 2009 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.FormatException

/** Renders a UPC-E code. */
internal class UPCEWriter : UPCEANWriter() {

    /** Only UPC-E is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.UPC_E)

    /** Encodes 7 digits (check digit added) or 8 digits (check digit verified) to horizontal modules. */
    override fun encode(contents: String): BooleanArray {
        val length = contents.length
        val text = when (length) {
            7 -> {
                val check = try {
                    UPCEANReader.getStandardUPCEANChecksum(UPCEReader.convertUPCEtoUPCA(contents))
                } catch (fe: FormatException) {
                    throw IllegalArgumentException(fe)
                }
                contents + check
            }
            8 -> {
                try {
                    if (!UPCEANReader.checkStandardUPCEANChecksum(UPCEReader.convertUPCEtoUPCA(contents))) {
                        throw IllegalArgumentException("Contents do not pass checksum")
                    }
                } catch (ignored: FormatException) {
                    throw IllegalArgumentException("Illegal contents")
                }
                contents
            }
            else -> throw IllegalArgumentException(
                "Requested contents should be 7 or 8 digits long, but got $length",
            )
        }

        checkNumeric(text)

        val firstDigit = text[0].digitToInt()
        if (firstDigit != 0 && firstDigit != 1) {
            throw IllegalArgumentException("Number system must be 0 or 1")
        }

        val checkDigit = text[7].digitToInt()
        val parities = UPCEReader.NUMSYS_AND_CHECK_DIGIT_PATTERNS[firstDigit][checkDigit]
        val result = BooleanArray(CODE_WIDTH)

        var pos = appendPattern(result, 0, UPCEANReader.START_END_PATTERN, true)

        for (i in 1..6) {
            var digit = text[i].digitToInt()
            if ((parities shr (6 - i) and 1) == 1) {
                digit += 10
            }
            pos += appendPattern(result, pos, UPCEANReader.L_AND_G_PATTERNS[digit], false)
        }

        appendPattern(result, pos, UPCEANReader.END_PATTERN, false)

        return result
    }

    /** Module width constant. */
    private companion object {

        /** Start guard, 6 digits and end guard. */
        const val CODE_WIDTH = 3 + (7 * 6) + 6
    }
}
