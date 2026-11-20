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

/** Renders an EAN-8 code. */
internal class EAN8Writer : UPCEANWriter() {

    /** Only EAN-8 is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.EAN_8)

    /** Encodes 7 digits (check digit added) or 8 digits (check digit verified) to horizontal modules. */
    override fun encode(contents: String): BooleanArray {
        val length = contents.length
        val text = when (length) {
            7 -> {
                val check = try {
                    UPCEANReader.getStandardUPCEANChecksum(contents)
                } catch (fe: FormatException) {
                    throw IllegalArgumentException(fe)
                }
                contents + check
            }
            8 -> {
                try {
                    if (!UPCEANReader.checkStandardUPCEANChecksum(contents)) {
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

        val result = BooleanArray(CODE_WIDTH)
        var pos = 0

        pos += appendPattern(result, pos, UPCEANReader.START_END_PATTERN, true)

        for (i in 0..3) {
            val digit = text[i].digitToInt()
            pos += appendPattern(result, pos, UPCEANReader.L_PATTERNS[digit], false)
        }

        pos += appendPattern(result, pos, UPCEANReader.MIDDLE_PATTERN, false)

        for (i in 4..7) {
            val digit = text[i].digitToInt()
            pos += appendPattern(result, pos, UPCEANReader.L_PATTERNS[digit], true)
        }
        appendPattern(result, pos, UPCEANReader.START_END_PATTERN, true)

        return result
    }

    /** Module width constant. */
    private companion object {

        /** Start guard, 4 left digits, middle guard, 4 right digits and end guard. */
        const val CODE_WIDTH = 3 + (7 * 4) + 5 + (7 * 4) + 3
    }
}
