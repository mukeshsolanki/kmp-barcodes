/*
 * Copyright 2007 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Reader
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.decoder.Decoder
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.detector.Detector

/** Detects and decodes Data Matrix codes in an image. */
internal class DataMatrixReader : Reader {

    /** Decoder that turns sampled module bits into text. */
    private val decoder = Decoder()

    /** Locates and decodes a Data Matrix code with no hints. */
    override fun decode(image: BinaryBitmap): Result = decode(image, null)

    /** Locates and decodes a Data Matrix code, honouring PURE_BARCODE when present. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        val decoderResult: DecoderResult
        val points: Array<ResultPoint>
        if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
            val bits = extractPureBits(image.blackMatrix)
            decoderResult = decoder.decode(bits)
            points = NO_POINTS
        } else {
            val detectorResult = Detector(image.blackMatrix).detect()
            decoderResult = decoder.decode(detectorResult.bits)
            points = detectorResult.points
        }
        val result = Result(decoderResult.text, decoderResult.rawBytes, points, BarcodeFormat.DATA_MATRIX)
        val byteSegments = decoderResult.byteSegments
        if (byteSegments != null) {
            result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments)
        }
        val ecLevel = decoderResult.ecLevel
        if (ecLevel != null) {
            result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel)
        }
        result.putMetadata(ResultMetadataType.ERRORS_CORRECTED, decoderResult.errorsCorrected)
        result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]d" + decoderResult.symbologyModifier)
        return result
    }

    /** No state to reset. */
    override fun reset() = Unit

    /** Static helpers shared by all readers. */
    private companion object {

        /** Empty point array used for pure-barcode results. */
        private val NO_POINTS = emptyArray<ResultPoint>()

        /** Reads modules from an unrotated, unskewed code with a white border. */
        private fun extractPureBits(image: BitMatrix): BitMatrix {
            val leftTopBlack = image.topLeftOnBit
            val rightBottomBlack = image.bottomRightOnBit
            if (leftTopBlack == null || rightBottomBlack == null) {
                throw NotFoundException.notFoundInstance
            }

            val moduleSize = moduleSize(leftTopBlack, image)

            var top = leftTopBlack[1]
            val bottom = rightBottomBlack[1]
            var left = leftTopBlack[0]
            val right = rightBottomBlack[0]

            val matrixWidth = (right - left + 1) / moduleSize
            val matrixHeight = (bottom - top + 1) / moduleSize
            if (matrixWidth <= 0 || matrixHeight <= 0) {
                throw NotFoundException.notFoundInstance
            }

            val nudge = moduleSize / 2
            top += nudge
            left += nudge

            val bits = BitMatrix(matrixWidth, matrixHeight)
            for (y in 0 until matrixHeight) {
                val iOffset = top + y * moduleSize
                for (x in 0 until matrixWidth) {
                    if (image.get(left + x * moduleSize, iOffset)) {
                        bits.set(x, y)
                    }
                }
            }
            return bits
        }

        /** Measures the module size from the run of dark pixels at the top-left corner. */
        private fun moduleSize(leftTopBlack: IntArray, image: BitMatrix): Int {
            val width = image.width
            var x = leftTopBlack[0]
            val y = leftTopBlack[1]
            while (x < width && image.get(x, y)) {
                x++
            }
            if (x == width) {
                throw NotFoundException.notFoundInstance
            }

            val moduleSize = x - leftTopBlack[0]
            if (moduleSize == 0) {
                throw NotFoundException.notFoundInstance
            }
            return moduleSize
        }
    }
}
