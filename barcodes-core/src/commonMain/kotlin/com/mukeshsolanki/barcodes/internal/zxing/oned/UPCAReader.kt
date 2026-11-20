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
import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/** Implements decoding of the UPC-A format by reading EAN-13 codes that start with 0. */
internal class UPCAReader : UPCEANReader() {

    /** Underlying EAN-13 reader. */
    private val ean13Reader: UPCEANReader = EAN13Reader()

    /** Decodes an EAN-13 row from a known start guard and converts it to UPC-A. */
    override fun decodeRow(
        rowNumber: Int,
        row: BitArray,
        startGuardRange: IntArray,
        hints: Map<DecodeHintType, Any?>?,
    ): Result = maybeReturnResult(ean13Reader.decodeRow(rowNumber, row, startGuardRange, hints))

    /** Decodes an EAN-13 row and converts it to UPC-A. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result =
        maybeReturnResult(ean13Reader.decodeRow(rowNumber, row, hints))

    /** Decodes an EAN-13 image and converts it to UPC-A. */
    override fun decode(image: BinaryBitmap): Result = maybeReturnResult(ean13Reader.decode(image))

    /** Decodes an EAN-13 image with [hints] and converts it to UPC-A. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result =
        maybeReturnResult(ean13Reader.decode(image, hints))

    /** Always UPC-A. */
    override val barcodeFormat: BarcodeFormat
        get() = BarcodeFormat.UPC_A

    /** Delegates to the EAN-13 middle decoder. */
    override fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int =
        ean13Reader.decodeMiddle(row, startRange, resultString)

    /** Conversion helper. */
    private companion object {

        /** Strips the leading 0 of an EAN-13 [result] into a UPC-A result; throws FormatException otherwise. */
        fun maybeReturnResult(result: Result): Result {
            val text = result.text
            if (text[0] == '0') {
                val upcaResult = Result(text.substring(1), null, result.resultPoints, BarcodeFormat.UPC_A)
                result.resultMetadata?.let { upcaResult.putAllMetadata(it) }
                return upcaResult
            } else {
                throw FormatException.formatInstance
            }
        }
    }
}
