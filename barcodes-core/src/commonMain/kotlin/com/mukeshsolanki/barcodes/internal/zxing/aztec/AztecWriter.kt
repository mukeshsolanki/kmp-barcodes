/*
 * Copyright 2013 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.aztec

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.aztec.encoder.AztecCode
import com.mukeshsolanki.barcodes.internal.zxing.aztec.encoder.Encoder
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Renders an Aztec code as a [BitMatrix].
 */
internal class AztecWriter : Writer {

    /** Encodes [contents] with default hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes [contents] honouring CHARACTER_SET, ERROR_CORRECTION and AZTEC_LAYERS hints. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        var charset: Charset? = null
        var eccPercent = Encoder.DEFAULT_EC_PERCENT
        var layers = Encoder.DEFAULT_AZTEC_LAYERS
        if (hints != null) {
            if (hints.containsKey(EncodeHintType.CHARACTER_SET)) {
                charset = Charsets.forName(hints[EncodeHintType.CHARACTER_SET].toString())
                    ?: throw IllegalArgumentException("Unsupported character set")
            }
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                eccPercent = hints[EncodeHintType.ERROR_CORRECTION].toString().toInt()
            }
            if (hints.containsKey(EncodeHintType.AZTEC_LAYERS)) {
                layers = hints[EncodeHintType.AZTEC_LAYERS].toString().toInt()
            }
        }
        if (format != BarcodeFormat.AZTEC) {
            throw IllegalArgumentException("Can only encode AZTEC, but got $format")
        }
        val aztec = Encoder.encode(contents, eccPercent, layers, charset)
        return renderResult(aztec, width, height)
    }

    /** Scales the symbol by the largest integer multiple that fits and centres it, exactly like zxing. */
    private fun renderResult(code: AztecCode, width: Int, height: Int): BitMatrix {
        val input = checkNotNull(code.matrix)
        val inputWidth = input.width
        val inputHeight = input.height
        val outputWidth = maxOf(width, inputWidth)
        val outputHeight = maxOf(height, inputHeight)

        val multiple = minOf(outputWidth / inputWidth, outputHeight / inputHeight)
        val leftPadding = (outputWidth - (inputWidth * multiple)) / 2
        val topPadding = (outputHeight - (inputHeight * multiple)) / 2

        val output = BitMatrix(outputWidth, outputHeight)
        var outputY = topPadding
        for (inputY in 0 until inputHeight) {
            var outputX = leftPadding
            for (inputX in 0 until inputWidth) {
                if (input.get(inputX, inputY)) {
                    output.setRegion(outputX, outputY, multiple, multiple)
                }
                outputX += multiple
            }
            outputY += multiple
        }
        return output
    }
}
