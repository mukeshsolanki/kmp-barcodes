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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.decoder

import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonDecoder
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonException

/** Decodes the sampled modules of a Data Matrix symbol into text and bytes. */
internal class Decoder {

    /** Reed-Solomon decoder over the Data Matrix GF(256) field. */
    private val rsDecoder = ReedSolomonDecoder(GenericGF.DATA_MATRIX_FIELD_256)

    /** Decodes a symbol given as a [BitMatrix], set bits meaning dark modules. */
    fun decode(bits: BitMatrix): DecoderResult {
        val parser = BitMatrixParser(bits)
        val version = parser.version

        val codewords = parser.readCodewords()
        val dataBlocks = DataBlock.getDataBlocks(codewords, version)

        val totalBytes = dataBlocks.sumOf { it.numDataCodewords }
        val resultBytes = ByteArray(totalBytes)

        var errorsCorrected = 0
        val dataBlocksCount = dataBlocks.size
        for (j in 0 until dataBlocksCount) {
            val dataBlock = dataBlocks[j]
            val codewordBytes = dataBlock.codewords
            val numDataCodewords = dataBlock.numDataCodewords
            errorsCorrected += correctErrors(codewordBytes, numDataCodewords)
            for (i in 0 until numDataCodewords) {
                resultBytes[i * dataBlocksCount + j] = codewordBytes[i]
            }
        }

        val result = DecodedBitStreamParser.decode(resultBytes)
        result.errorsCorrected = errorsCorrected
        return result
    }

    /** Corrects [codewordBytes] in place and returns the number of errors fixed, or throws [ChecksumException]. */
    private fun correctErrors(codewordBytes: ByteArray, numDataCodewords: Int): Int {
        val numCodewords = codewordBytes.size
        val codewordsInts = IntArray(numCodewords) { codewordBytes[it].toInt() and 0xFF }
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
