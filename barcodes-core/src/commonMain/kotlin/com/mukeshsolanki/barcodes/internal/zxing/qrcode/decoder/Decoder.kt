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

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder

import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonDecoder
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonException

/**
 * Decodes the modules of a located QR Code into text and bytes.
 */
internal class Decoder {

    /** Reed-Solomon decoder over the QR GF(256) field. */
    private val rsDecoder = ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256)

    /** Decodes [bits] (true = black) without hints. */
    fun decode(bits: BitMatrix): DecoderResult = decode(bits, null)

    /** Decodes [bits], retrying as a mirrored code; throws [FormatException] or [ChecksumException] from the first attempt. */
    fun decode(bits: BitMatrix, hints: Map<DecodeHintType, Any?>?): DecoderResult {
        val parser = BitMatrixParser(bits)
        var fe: FormatException? = null
        var ce: ChecksumException? = null
        try {
            return decode(parser, hints)
        } catch (e: FormatException) {
            fe = e
        } catch (e: ChecksumException) {
            ce = e
        }
        try {
            parser.remask()
            parser.setMirror(true)
            parser.readVersion()
            parser.readFormatInformation()
            parser.mirror()
            val result = decode(parser, hints)
            result.other = QRCodeDecoderMetaData(true)
            return result
        } catch (e: FormatException) {
            throw fe ?: ce!!
        } catch (e: ChecksumException) {
            throw fe ?: ce!!
        }
    }

    /** Reads, error-corrects and parses the codewords held by [parser]. */
    private fun decode(parser: BitMatrixParser, hints: Map<DecodeHintType, Any?>?): DecoderResult {
        val version = parser.readVersion()
        val ecLevel = parser.readFormatInformation().errorCorrectionLevel
        val codewords = parser.readCodewords()
        val dataBlocks = DataBlock.getDataBlocks(codewords, version, ecLevel)
        var totalBytes = 0
        for (dataBlock in dataBlocks) {
            totalBytes += dataBlock.numDataCodewords
        }
        val resultBytes = ByteArray(totalBytes)
        var resultOffset = 0
        var errorsCorrected = 0
        for (dataBlock in dataBlocks) {
            val codewordBytes = dataBlock.codewords
            val numDataCodewords = dataBlock.numDataCodewords
            errorsCorrected += correctErrors(codewordBytes, numDataCodewords)
            for (i in 0 until numDataCodewords) {
                resultBytes[resultOffset++] = codewordBytes[i]
            }
        }
        val result = DecodedBitStreamParser.decode(resultBytes, version, ecLevel, hints)
        result.errorsCorrected = errorsCorrected
        return result
    }

    /** Corrects [codewordBytes] in place with Reed-Solomon and returns the error count; throws [ChecksumException] on failure. */
    private fun correctErrors(codewordBytes: ByteArray, numDataCodewords: Int): Int {
        val numCodewords = codewordBytes.size
        val codewordsInts = IntArray(numCodewords)
        for (i in 0 until numCodewords) {
            codewordsInts[i] = codewordBytes[i].toInt() and 0xFF
        }
        val errorsCorrected = try {
            rsDecoder.decodeWithECCount(codewordsInts, codewordBytes.size - numDataCodewords)
        } catch (ignored: ReedSolomonException) {
            throw ChecksumException.checksumInstance
        }
        for (i in 0 until numDataCodewords) {
            codewordBytes[i] = codewordsInts[i].toByte()
        }
        return errorsCorrected
    }
}
