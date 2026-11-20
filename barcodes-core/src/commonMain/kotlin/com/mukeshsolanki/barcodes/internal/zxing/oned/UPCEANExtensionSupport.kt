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

import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Reads the optional 2 or 5 digit add-on extension that may follow a UPC/EAN barcode. */
internal class UPCEANExtensionSupport {

    /** Reader for 2 digit extensions. */
    private val twoSupport = UPCEANExtension2Support()

    /** Reader for 5 digit extensions. */
    private val fiveSupport = UPCEANExtension5Support()

    /** Decodes an extension starting at or after [rowOffset], trying 5 digits first and then 2. */
    fun decodeRow(rowNumber: Int, row: BitArray, rowOffset: Int): Result {
        val extensionStartRange = UPCEANReader.findGuardPattern(row, rowOffset, false, EXTENSION_START_PATTERN)
        return try {
            fiveSupport.decodeRow(rowNumber, row, extensionStartRange)
        } catch (ignored: ReaderException) {
            twoSupport.decodeRow(rowNumber, row, extensionStartRange)
        }
    }

    /** Extension start pattern constant. */
    private companion object {

        /** Guard pattern that opens an extension. */
        val EXTENSION_START_PATTERN: IntArray = intArrayOf(1, 1, 2)
    }
}
