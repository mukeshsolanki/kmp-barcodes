/*
 * Copyright 2010 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/** Renders a UPC-A code as the equivalent EAN-13 code with a leading 0. */
internal class UPCAWriter : Writer {

    /** Underlying EAN-13 writer. */
    private val subWriter = EAN13Writer()

    /** Encodes [contents] as UPC-A without hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes [contents] as UPC-A by writing "0" + [contents] as EAN-13. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        if (format != BarcodeFormat.UPC_A) {
            throw IllegalArgumentException("Can only encode UPC-A, but got $format")
        }
        return subWriter.encode("0$contents", BarcodeFormat.EAN_13, width, height, hints)
    }
}
