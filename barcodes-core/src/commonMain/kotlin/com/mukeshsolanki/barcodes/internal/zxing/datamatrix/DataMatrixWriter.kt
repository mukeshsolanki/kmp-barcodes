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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.Writer
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.DefaultPlacement
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.ErrorCorrection
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.HighLevelEncoder
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.SymbolInfo
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder.SymbolShapeHint

/**
 * Renders a Data Matrix code as a [BitMatrix]. Only DATA_MATRIX_SHAPE is honoured; like zxing's default
 * (non-compact) path, CHARACTER_SET is ignored and content is encoded as ISO-8859-1.
 */
internal class DataMatrixWriter : Writer {

    /** Encodes [contents] with no hints. */
    override fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        encode(contents, format, width, height, null)

    /** Encodes [contents] into a Data Matrix scaled into [width] x [height]. */
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType, Any?>?,
    ): BitMatrix {
        require(contents.isNotEmpty()) { "Found empty contents" }
        require(format == BarcodeFormat.DATA_MATRIX) { "Can only encode DATA_MATRIX, but got $format" }
        require(width >= 0 && height >= 0) { "Requested dimensions can't be negative: ${width}x$height" }

        val shape = shapeHint(hints)
        val encoded = HighLevelEncoder.encodeHighLevel(contents, shape)
        val symbolInfo = SymbolInfo.lookup(encoded.length, shape)
        val codewords = ErrorCorrection.encodeECC200(encoded, symbolInfo)
        val placement = DefaultPlacement(codewords, symbolInfo.symbolDataWidth, symbolInfo.symbolDataHeight)
        placement.place()
        return encodeLowLevel(placement, symbolInfo, width, height)
    }

    /** Static helpers for symbol layout and scaling. */
    private companion object {

        /** Reads DATA_MATRIX_SHAPE as a [SymbolShapeHint] or its name, defaulting to FORCE_NONE. */
        private fun shapeHint(hints: Map<EncodeHintType, Any?>?): SymbolShapeHint =
            when (val requested = hints?.get(EncodeHintType.DATA_MATRIX_SHAPE)) {
                null -> SymbolShapeHint.FORCE_NONE
                is SymbolShapeHint -> requested
                else -> SymbolShapeHint.valueOf(requested.toString())
            }

        /** Lays out data regions, finder edges and timing patterns into a symbol-sized matrix. */
        private fun encodeLowLevel(
            placement: DefaultPlacement,
            symbolInfo: SymbolInfo,
            width: Int,
            height: Int,
        ): BitMatrix {
            val symbolWidth = symbolInfo.symbolDataWidth
            val symbolHeight = symbolInfo.symbolDataHeight

            val matrix = BitMatrix(symbolInfo.symbolWidth, symbolInfo.symbolHeight)

            var matrixY = 0

            for (y in 0 until symbolHeight) {
                var matrixX: Int
                if (y % symbolInfo.matrixHeight == 0) {
                    matrixX = 0
                    for (x in 0 until symbolInfo.symbolWidth) {
                        matrix.put(matrixX, matrixY, x % 2 == 0)
                        matrixX++
                    }
                    matrixY++
                }
                matrixX = 0
                for (x in 0 until symbolWidth) {
                    if (x % symbolInfo.matrixWidth == 0) {
                        matrix.put(matrixX, matrixY, true)
                        matrixX++
                    }
                    matrix.put(matrixX, matrixY, placement.getBit(x, y))
                    matrixX++
                    if (x % symbolInfo.matrixWidth == symbolInfo.matrixWidth - 1) {
                        matrix.put(matrixX, matrixY, y % 2 == 0)
                        matrixX++
                    }
                }
                matrixY++
                if (y % symbolInfo.matrixHeight == symbolInfo.matrixHeight - 1) {
                    matrixX = 0
                    for (x in 0 until symbolInfo.symbolWidth) {
                        matrix.put(matrixX, matrixY, true)
                        matrixX++
                    }
                    matrixY++
                }
            }

            return scaleToRequestedSize(matrix, width, height)
        }

        /** Sets the module at ([x], [y]) when [value] is true; the matrix starts all light. */
        private fun BitMatrix.put(x: Int, y: Int, value: Boolean) {
            if (value) set(x, y)
        }

        /** Scales and centres the symbol into the requested size exactly as zxing does. */
        private fun scaleToRequestedSize(matrix: BitMatrix, reqWidth: Int, reqHeight: Int): BitMatrix {
            val matrixWidth = matrix.width
            val matrixHeight = matrix.height
            val outputWidth = maxOf(reqWidth, matrixWidth)
            val outputHeight = maxOf(reqHeight, matrixHeight)

            val multiple = minOf(outputWidth / matrixWidth, outputHeight / matrixHeight)

            var leftPadding = (outputWidth - matrixWidth * multiple) / 2
            var topPadding = (outputHeight - matrixHeight * multiple) / 2

            val output: BitMatrix
            if (reqHeight < matrixHeight || reqWidth < matrixWidth) {
                leftPadding = 0
                topPadding = 0
                output = BitMatrix(matrixWidth, matrixHeight)
            } else {
                output = BitMatrix(reqWidth, reqHeight)
            }

            output.clear()
            var outputY = topPadding
            for (inputY in 0 until matrixHeight) {
                var outputX = leftPadding
                for (inputX in 0 until matrixWidth) {
                    if (matrix.get(inputX, inputY)) {
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
