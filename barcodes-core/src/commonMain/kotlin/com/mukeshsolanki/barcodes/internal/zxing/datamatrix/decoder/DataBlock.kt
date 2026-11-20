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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.decoder

/** One de-interleaved block of data plus error-correction codewords. */
internal class DataBlock private constructor(
    /** Number of leading codewords that are data. */
    val numDataCodewords: Int,
    /** Data codewords followed by error-correction codewords. */
    val codewords: ByteArray,
) {

    /** Block splitting. */
    companion object {

        /** Separates the interleaved [rawCodewords] of a symbol of [version] into its original blocks. */
        fun getDataBlocks(rawCodewords: ByteArray, version: Version): Array<DataBlock> {
            val ecBlocks = version.ecBlocks
            val ecBlockArray = ecBlocks.ecBlocks
            val result = ecBlockArray.flatMap { ecBlock ->
                List(ecBlock.count) {
                    DataBlock(ecBlock.dataCodewords, ByteArray(ecBlocks.ecCodewords + ecBlock.dataCodewords))
                }
            }.toTypedArray()
            val numResultBlocks = result.size

            val longerBlocksTotalCodewords = result[0].codewords.size
            val longerBlocksNumDataCodewords = longerBlocksTotalCodewords - ecBlocks.ecCodewords
            val shorterBlocksNumDataCodewords = longerBlocksNumDataCodewords - 1
            var rawCodewordsOffset = 0
            for (i in 0 until shorterBlocksNumDataCodewords) {
                for (j in 0 until numResultBlocks) {
                    result[j].codewords[i] = rawCodewords[rawCodewordsOffset++]
                }
            }

            val specialVersion = version.versionNumber == 24
            val numLongerBlocks = if (specialVersion) 8 else numResultBlocks
            for (j in 0 until numLongerBlocks) {
                result[j].codewords[longerBlocksNumDataCodewords - 1] = rawCodewords[rawCodewordsOffset++]
            }

            val max = result[0].codewords.size
            for (i in longerBlocksNumDataCodewords until max) {
                for (j in 0 until numResultBlocks) {
                    val jOffset = if (specialVersion) (j + 8) % numResultBlocks else j
                    val iOffset = if (specialVersion && jOffset > 7) i - 1 else i
                    result[jOffset].codewords[iOffset] = rawCodewords[rawCodewordsOffset++]
                }
            }

            require(rawCodewordsOffset == rawCodewords.size)

            return result
        }
    }
}
