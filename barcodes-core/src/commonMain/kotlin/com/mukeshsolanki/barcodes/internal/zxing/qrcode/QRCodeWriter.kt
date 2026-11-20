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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder.Encoder
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder.QRCode
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a QR Code as a [BitMatrix], scaled and centred exactly like zxing.
 */
internal class QRCodeWriter : Writer {

    /** Encodes [contents] as a QR Code of at least [width] x [height] with default hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes [contents] honouring ERROR_CORRECTION, MARGIN, CHARACTER_SET and QR_MASK_PATTERN hints. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        if (contents.isEmpty()) {
            throw IllegalArgumentException("Found empty contents")
        }
        if (format != BarcodeFormat.QR_CODE) {
            throw IllegalArgumentException("Can only encode QR_CODE, but got $format")
        }
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Requested dimensions are too small: " + width + 'x' + height)
        }
        var errorCorrectionLevel = ErrorCorrectionLevel.L
        var quietZone = QUIET_ZONE_SIZE
        if (hints != null) {
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints[EncodeHintType.ERROR_CORRECTION].toString())
            }
            if (hints.containsKey(EncodeHintType.MARGIN)) {
                quietZone = hints[EncodeHintType.MARGIN].toString().toInt()
            }
        }
        val code = Encoder.encode(contents, errorCorrectionLevel, hints)
        return renderResult(code, width, height, quietZone)
    }

    /** Rendering constants and helpers. */
    private companion object {
        /** Default quiet zone in modules. */
        const val QUIET_ZONE_SIZE = 4

        /** Scales the module matrix by the largest integer multiple that fits and centres it; the remainder is padding. */
        fun renderResult(code: QRCode, width: Int, height: Int, quietZone: Int): BitMatrix {
            val input = code.matrix ?: throw IllegalStateException()
            val inputWidth = input.width
            val inputHeight = input.height
            val qrWidth = inputWidth + (quietZone * 2)
            val qrHeight = inputHeight + (quietZone * 2)
            val outputWidth = max(width, qrWidth)
            val outputHeight = max(height, qrHeight)
            val multiple = min(outputWidth / qrWidth, outputHeight / qrHeight)
            val leftPadding = (outputWidth - (inputWidth * multiple)) / 2
            val topPadding = (outputHeight - (inputHeight * multiple)) / 2
            val output = BitMatrix(outputWidth, outputHeight)
            var outputY = topPadding
            for (inputY in 0 until inputHeight) {
                var outputX = leftPadding
                for (inputX in 0 until inputWidth) {
                    if (input.get(inputX, inputY).toInt() == 1) {
                        output.setRegion(outputX, outputY, multiple, multiple)
                    }
                    outputX += multiple
                }
                outputY += multiple
            }
            return output
        }
    }
}
