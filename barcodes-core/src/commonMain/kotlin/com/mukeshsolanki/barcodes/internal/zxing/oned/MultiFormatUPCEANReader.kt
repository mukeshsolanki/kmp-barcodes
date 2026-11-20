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
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/**
 * Reads all requested UPC/EAN formats, locating the start guard once per row and sharing it
 * across the individual readers.
 */
internal class MultiFormatUPCEANReader(hints: Map<DecodeHintType, Any?>?) : OneDReader() {

    /** Readers tried in order. */
    private val readers: Array<UPCEANReader> = buildReaders(hints)

    /**
     * Returns the first successful decode of [row]. An EAN-13 result starting with 0 is reported as
     * UPC-A when UPC-A was requested (or no formats were given).
     */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        val startGuardPattern = UPCEANReader.findStartGuardPattern(row)
        for (reader in readers) {
            try {
                val result = reader.decodeRow(rowNumber, row, startGuardPattern, hints)
                val ean13MayBeUPCA = result.barcodeFormat == BarcodeFormat.EAN_13 && result.text[0] == '0'
                @Suppress("UNCHECKED_CAST")
                val possibleFormats = hints?.get(DecodeHintType.POSSIBLE_FORMATS) as Collection<BarcodeFormat>?
                val canReturnUPCA = possibleFormats == null || possibleFormats.contains(BarcodeFormat.UPC_A)

                if (ean13MayBeUPCA && canReturnUPCA) {
                    val resultUPCA = Result(
                        result.text.substring(1),
                        result.rawBytes,
                        result.resultPoints,
                        BarcodeFormat.UPC_A,
                    )
                    result.resultMetadata?.let { resultUPCA.putAllMetadata(it) }
                    return resultUPCA
                }
                return result
            } catch (ignored: ReaderException) {
            }
        }
        throw NotFoundException.notFoundInstance
    }

    /** Resets every underlying reader. */
    override fun reset() {
        for (reader in readers) {
            reader.reset()
        }
    }

    /** Reader selection. */
    private companion object {

        /** Builds the UPC/EAN reader list for the POSSIBLE_FORMATS hint; UPC-A is covered by EAN-13 by default. */
        fun buildReaders(hints: Map<DecodeHintType, Any?>?): Array<UPCEANReader> {
            @Suppress("UNCHECKED_CAST")
            val possibleFormats = hints?.get(DecodeHintType.POSSIBLE_FORMATS) as Collection<BarcodeFormat>?
            val readers = ArrayList<UPCEANReader>()
            if (possibleFormats != null) {
                if (possibleFormats.contains(BarcodeFormat.EAN_13)) {
                    readers.add(EAN13Reader())
                } else if (possibleFormats.contains(BarcodeFormat.UPC_A)) {
                    readers.add(UPCAReader())
                }
                if (possibleFormats.contains(BarcodeFormat.EAN_8)) {
                    readers.add(EAN8Reader())
                }
                if (possibleFormats.contains(BarcodeFormat.UPC_E)) {
                    readers.add(UPCEReader())
                }
            }
            if (readers.isEmpty()) {
                readers.add(EAN13Reader())
                readers.add(EAN8Reader())
                readers.add(UPCEReader())
            }
            return readers.toTypedArray()
        }
    }
}
