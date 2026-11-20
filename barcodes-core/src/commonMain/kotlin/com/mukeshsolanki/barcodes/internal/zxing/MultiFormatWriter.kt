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
package com.mukeshsolanki.barcodes.internal.zxing

import com.mukeshsolanki.barcodes.internal.zxing.aztec.AztecWriter
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.DataMatrixWriter
import com.mukeshsolanki.barcodes.internal.zxing.oned.CodaBarWriter
import com.mukeshsolanki.barcodes.internal.zxing.oned.Code128Writer
import com.mukeshsolanki.barcodes.internal.zxing.oned.Code39Writer
import com.mukeshsolanki.barcodes.internal.zxing.oned.Code93Writer
import com.mukeshsolanki.barcodes.internal.zxing.oned.EAN13Writer
import com.mukeshsolanki.barcodes.internal.zxing.oned.EAN8Writer
import com.mukeshsolanki.barcodes.internal.zxing.oned.ITFWriter
import com.mukeshsolanki.barcodes.internal.zxing.oned.UPCAWriter
import com.mukeshsolanki.barcodes.internal.zxing.oned.UPCEWriter
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Writer
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.QRCodeWriter

/** Picks the [Writer] for the requested [BarcodeFormat] and encodes with it. */
internal class MultiFormatWriter : Writer {

    /** Encodes with no hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes with the format's writer; throws [IllegalArgumentException] for unsupported formats. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix = writerFor(format).encode(contents, format, width, height, hints)

    /** Returns a new writer for [format]. */
    private fun writerFor(format: BarcodeFormat): Writer = when (format) {
        BarcodeFormat.EAN_8 -> EAN8Writer()
        BarcodeFormat.UPC_E -> UPCEWriter()
        BarcodeFormat.EAN_13 -> EAN13Writer()
        BarcodeFormat.UPC_A -> UPCAWriter()
        BarcodeFormat.QR_CODE -> QRCodeWriter()
        BarcodeFormat.CODE_39 -> Code39Writer()
        BarcodeFormat.CODE_93 -> Code93Writer()
        BarcodeFormat.CODE_128 -> Code128Writer()
        BarcodeFormat.ITF -> ITFWriter()
        BarcodeFormat.PDF_417 -> PDF417Writer()
        BarcodeFormat.CODABAR -> CodaBarWriter()
        BarcodeFormat.DATA_MATRIX -> DataMatrixWriter()
        BarcodeFormat.AZTEC -> AztecWriter()
        else -> throw IllegalArgumentException("No encoder available for format $format")
    }
}
