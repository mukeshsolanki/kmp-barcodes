/*
 * Copyright 2012 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417

import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder.Compaction
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder.Dimensions
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder.PDF417
import kotlin.math.min

/**
 * Renders PDF417 barcodes as a [BitMatrix]. The PDF417_AUTO_ECI hint is ignored.
 */
internal class PDF417Writer : Writer {

    /** Encodes [contents] with default hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes [contents] honouring PDF417_COMPACT, PDF417_COMPACTION, PDF417_DIMENSIONS, MARGIN, ERROR_CORRECTION and CHARACTER_SET. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        if (format != BarcodeFormat.PDF_417) {
            throw IllegalArgumentException("Can only encode PDF_417, but got $format")
        }
        val encoder = PDF417()
        var margin = WHITE_SPACE
        var errorCorrectionLevel = DEFAULT_ERROR_CORRECTION_LEVEL
        if (hints != null) {
            applyEncoderHints(encoder, hints)
            if (hints.containsKey(EncodeHintType.MARGIN)) {
                margin = hints[EncodeHintType.MARGIN].toString().toInt()
            }
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                errorCorrectionLevel = hints[EncodeHintType.ERROR_CORRECTION].toString().toInt()
            }
        }
        return bitMatrixFromEncoder(encoder, contents, errorCorrectionLevel, width, height, margin)
    }

    /** Applies the compact, compaction, dimensions and character set hints to [encoder]. */
    private fun applyEncoderHints(encoder: PDF417, hints: Map<EncodeHintType, Any?>) {
        if (hints.containsKey(EncodeHintType.PDF417_COMPACT)) {
            encoder.setCompact(hints[EncodeHintType.PDF417_COMPACT].toString().toBoolean())
        }
        if (hints.containsKey(EncodeHintType.PDF417_COMPACTION)) {
            encoder.setCompaction(Compaction.valueOf(hints[EncodeHintType.PDF417_COMPACTION].toString()))
        }
        if (hints.containsKey(EncodeHintType.PDF417_DIMENSIONS)) {
            val dimensions = hints[EncodeHintType.PDF417_DIMENSIONS] as Dimensions
            encoder.setDimensions(dimensions.maxCols, dimensions.minCols, dimensions.maxRows, dimensions.minRows)
        }
        if (hints.containsKey(EncodeHintType.CHARACTER_SET)) {
            val encoding = Charsets.forName(hints[EncodeHintType.CHARACTER_SET].toString())
                ?: throw IllegalArgumentException("Unsupported character set")
            encoder.setEncoding(encoding)
        }
    }

    /** Holds the writer defaults and the matrix helpers. */
    private companion object {
        /** Default white space (margin) around the code. */
        const val WHITE_SPACE: Int = 30

        /** Default error correction level. */
        const val DEFAULT_ERROR_CORRECTION_LEVEL: Int = 2

        /** Encodes, picks the orientation and integer scale that fit [width] x [height], and adds [margin]. */
        fun bitMatrixFromEncoder(
            encoder: PDF417,
            contents: String,
            errorCorrectionLevel: Int,
            width: Int,
            height: Int,
            margin: Int,
        ): BitMatrix {
            encoder.generateBarcodeLogic(contents, errorCorrectionLevel)
            val aspectRatio = 4
            var originalScale = encoder.barcodeMatrix.getScaledMatrix(1, aspectRatio)
            var rotated = false
            if ((height > width) != (originalScale[0].size < originalScale.size)) {
                originalScale = rotateArray(originalScale)
                rotated = true
            }
            val scaleX = width / originalScale[0].size
            val scaleY = height / originalScale.size
            val scale = min(scaleX, scaleY)
            if (scale > 1) {
                var scaledMatrix = encoder.barcodeMatrix.getScaledMatrix(scale, scale * aspectRatio)
                if (rotated) {
                    scaledMatrix = rotateArray(scaledMatrix)
                }
                return bitMatrixFromBitArray(scaledMatrix, margin)
            }
            return bitMatrixFromBitArray(originalScale, margin)
        }

        /** Converts [input] (1 = black, rows bottom-up) to a [BitMatrix] with [margin] on every side. */
        fun bitMatrixFromBitArray(input: Array<ByteArray>, margin: Int): BitMatrix {
            val output = BitMatrix(input[0].size + 2 * margin, input.size + 2 * margin)
            output.clear()
            var yOutput = output.height - margin - 1
            for (y in input.indices) {
                val inputY = input[y]
                for (x in 0 until input[0].size) {
                    if (inputY[x].toInt() == 1) {
                        output.set(x + margin, yOutput)
                    }
                }
                yOutput--
            }
            return output
        }

        /** Rotates [bitarray] by 90 degrees. */
        fun rotateArray(bitarray: Array<ByteArray>): Array<ByteArray> {
            val temp = Array(bitarray[0].size) { ByteArray(bitarray.size) }
            for (ii in bitarray.indices) {
                val inverseii = bitarray.size - ii - 1
                for (jj in 0 until bitarray[0].size) {
                    temp[jj][inverseii] = bitarray[ii][jj]
                }
            }
            return temp
        }
    }
}
