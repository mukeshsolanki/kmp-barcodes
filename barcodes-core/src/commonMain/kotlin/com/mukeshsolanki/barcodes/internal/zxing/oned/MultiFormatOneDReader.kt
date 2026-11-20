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

/** Tries every 1D reader selected by the POSSIBLE_FORMATS hint (all of them when absent) on each row. */
internal class MultiFormatOneDReader(hints: Map<DecodeHintType, Any?>?) : OneDReader() {

    /** Readers tried in order. */
    private val readers: Array<OneDReader> = buildReaders(hints)

    /** Returns the first successful decode of [row]; throws NotFoundException if no reader decodes it. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result {
        for (reader in readers) {
            try {
                return reader.decodeRow(rowNumber, row, hints)
            } catch (re: ReaderException) {
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

        /** Builds the reader list for the POSSIBLE_FORMATS and ASSUME_CODE_39_CHECK_DIGIT hints. */
        fun buildReaders(hints: Map<DecodeHintType, Any?>?): Array<OneDReader> {
            @Suppress("UNCHECKED_CAST")
            val possibleFormats = hints?.get(DecodeHintType.POSSIBLE_FORMATS) as Collection<BarcodeFormat>?
            val useCode39CheckDigit = hints != null && hints[DecodeHintType.ASSUME_CODE_39_CHECK_DIGIT] != null
            val readers = ArrayList<OneDReader>()
            if (possibleFormats != null) {
                if (possibleFormats.contains(BarcodeFormat.EAN_13) ||
                    possibleFormats.contains(BarcodeFormat.UPC_A) ||
                    possibleFormats.contains(BarcodeFormat.EAN_8) ||
                    possibleFormats.contains(BarcodeFormat.UPC_E)
                ) {
                    readers.add(MultiFormatUPCEANReader(hints))
                }
                if (possibleFormats.contains(BarcodeFormat.CODE_39)) {
                    readers.add(Code39Reader(useCode39CheckDigit))
                }
                if (possibleFormats.contains(BarcodeFormat.CODE_93)) {
                    readers.add(Code93Reader())
                }
                if (possibleFormats.contains(BarcodeFormat.CODE_128)) {
                    readers.add(Code128Reader())
                }
                if (possibleFormats.contains(BarcodeFormat.ITF)) {
                    readers.add(ITFReader())
                }
                if (possibleFormats.contains(BarcodeFormat.CODABAR)) {
                    readers.add(CodaBarReader())
                }
            }
            if (readers.isEmpty()) {
                readers.add(MultiFormatUPCEANReader(hints))
                readers.add(Code39Reader())
                readers.add(CodaBarReader())
                readers.add(Code93Reader())
                readers.add(Code128Reader())
                readers.add(ITFReader())
            }
            return readers.toTypedArray()
        }
    }
}
