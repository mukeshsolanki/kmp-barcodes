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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode

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
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Decoder
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.QRCodeDecoderMetaData
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.detector.Detector

/**
 * Detects and decodes QR Codes in an image.
 */
internal class QRCodeReader : Reader {

    /** The module decoder. */
    private val decoder = Decoder()

    /** Locates and decodes a QR Code without hints. */
    override fun decode(image: BinaryBitmap): Result = decode(image, null)

    /** Locates and decodes a QR Code; throws NotFound, Checksum or Format exceptions on failure. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        val decoderResult: DecoderResult
        val points: Array<ResultPoint>
        if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
            val bits = extractPureBits(image.blackMatrix)
            decoderResult = decoder.decode(bits, hints)
            points = NO_POINTS
        } else {
            val detectorResult = Detector(image.blackMatrix).detect(hints)
            decoderResult = decoder.decode(detectorResult.bits, hints)
            points = detectorResult.points
        }
        (decoderResult.other as? QRCodeDecoderMetaData)?.applyMirroredCorrection(points)
        val result = Result(decoderResult.text, decoderResult.rawBytes, points, BarcodeFormat.QR_CODE)
        putMetadata(result, decoderResult)
        return result
    }

    /** Copies byte segments, EC level, structured append, error count and symbology id into [result]. */
    private fun putMetadata(result: Result, decoderResult: DecoderResult) {
        decoderResult.byteSegments?.let { result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, it) }
        decoderResult.ecLevel?.let { result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, it) }
        if (decoderResult.hasStructuredAppend()) {
            result.putMetadata(
                ResultMetadataType.STRUCTURED_APPEND_SEQUENCE,
                decoderResult.structuredAppendSequenceNumber,
            )
            result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY, decoderResult.structuredAppendParity)
        }
        result.putMetadata(ResultMetadataType.ERRORS_CORRECTED, decoderResult.errorsCorrected)
        result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]Q" + decoderResult.symbologyModifier)
    }

    /** Does nothing; the reader holds no state. */
    override fun reset() = Unit

    /** Pure-barcode extraction helpers. */
    private companion object {
        /** Empty points array for pure barcodes. */
        val NO_POINTS = emptyArray<ResultPoint>()

        /** Reads modules from an unrotated, unskewed code with a white border; throws [NotFoundException]. */
        fun extractPureBits(image: BitMatrix): BitMatrix {
            val leftTopBlack = image.topLeftOnBit
            val rightBottomBlack = image.bottomRightOnBit
            if (leftTopBlack == null || rightBottomBlack == null) {
                throw NotFoundException.notFoundInstance
            }
            val moduleSize = moduleSize(leftTopBlack, image)
            var top = leftTopBlack[1]
            val bottom = rightBottomBlack[1]
            var left = leftTopBlack[0]
            var right = rightBottomBlack[0]
            if (left >= right || top >= bottom) {
                throw NotFoundException.notFoundInstance
            }
            if (bottom - top != right - left) {
                right = left + (bottom - top)
                if (right >= image.width) {
                    throw NotFoundException.notFoundInstance
                }
            }
            val matrixWidth = MathUtils.round((right - left + 1) / moduleSize)
            val matrixHeight = MathUtils.round((bottom - top + 1) / moduleSize)
            if (matrixWidth <= 0 || matrixHeight <= 0) {
                throw NotFoundException.notFoundInstance
            }
            if (matrixHeight != matrixWidth) {
                throw NotFoundException.notFoundInstance
            }
            val nudge = (moduleSize / 2.0f).toInt()
            top += nudge
            left += nudge
            val nudgedTooFarRight = left + ((matrixWidth - 1) * moduleSize).toInt() - right
            if (nudgedTooFarRight > 0) {
                if (nudgedTooFarRight > nudge) {
                    throw NotFoundException.notFoundInstance
                }
                left -= nudgedTooFarRight
            }
            val nudgedTooFarDown = top + ((matrixHeight - 1) * moduleSize).toInt() - bottom
            if (nudgedTooFarDown > 0) {
                if (nudgedTooFarDown > nudge) {
                    throw NotFoundException.notFoundInstance
                }
                top -= nudgedTooFarDown
            }
            val bits = BitMatrix(matrixWidth, matrixHeight)
            for (y in 0 until matrixHeight) {
                val iOffset = top + (y * moduleSize).toInt()
                for (x in 0 until matrixWidth) {
                    if (image.get(left + (x * moduleSize).toInt(), iOffset)) {
                        bits.set(x, y)
                    }
                }
            }
            return bits
        }

        /** Estimates module size from the first five diagonal transitions from the top-left black pixel. */
        fun moduleSize(leftTopBlack: IntArray, image: BitMatrix): Float {
            val height = image.height
            val width = image.width
            var x = leftTopBlack[0]
            var y = leftTopBlack[1]
            var inBlack = true
            var transitions = 0
            while (x < width && y < height) {
                if (inBlack != image.get(x, y)) {
                    if (++transitions == 5) {
                        break
                    }
                    inBlack = !inBlack
                }
                x++
                y++
            }
            if (x == width || y == height) {
                throw NotFoundException.notFoundInstance
            }
            return (x - leftTopBlack[0]) / 7.0f
        }
    }
}
