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

/**
 * One block of data and EC codewords within a QR Code.
 */
internal class DataBlock private constructor(
    /** Number of data codewords in this block. */
    val numDataCodewords: Int,
    /** Data codewords followed by EC codewords. */
    val codewords: ByteArray,
) {
    /** De-interleaving of raw codewords into blocks. */
    companion object {
        /** Separates interleaved [rawCodewords] into the blocks used by [version] at [ecLevel]. */
        fun getDataBlocks(rawCodewords: ByteArray, version: Version, ecLevel: ErrorCorrectionLevel): Array<DataBlock> {
            if (rawCodewords.size != version.totalCodewords) {
                throw IllegalArgumentException()
            }
            val ecBlocks = version.getECBlocksForLevel(ecLevel)
            var totalBlocks = 0
            val ecBlockArray = ecBlocks.ecBlocks
            for (ecBlock in ecBlockArray) {
                totalBlocks += ecBlock.count
            }
            val result = arrayOfNulls<DataBlock>(totalBlocks)
            var numResultBlocks = 0
            for (ecBlock in ecBlockArray) {
                for (i in 0 until ecBlock.count) {
                    val numDataCodewords = ecBlock.dataCodewords
                    val numBlockCodewords = ecBlocks.ecCodewordsPerBlock + numDataCodewords
                    result[numResultBlocks++] = DataBlock(numDataCodewords, ByteArray(numBlockCodewords))
                }
            }
            val blocks = result.requireNoNulls()
            val shorterBlocksTotalCodewords = blocks[0].codewords.size
            var longerBlocksStartAt = blocks.size - 1
            while (longerBlocksStartAt >= 0) {
                val numCodewords = blocks[longerBlocksStartAt].codewords.size
                if (numCodewords == shorterBlocksTotalCodewords) {
                    break
                }
                longerBlocksStartAt--
            }
            longerBlocksStartAt++
            val shorterBlocksNumDataCodewords = shorterBlocksTotalCodewords - ecBlocks.ecCodewordsPerBlock
            var rawCodewordsOffset = 0
            for (i in 0 until shorterBlocksNumDataCodewords) {
                for (j in 0 until numResultBlocks) {
                    blocks[j].codewords[i] = rawCodewords[rawCodewordsOffset++]
                }
            }
            for (j in longerBlocksStartAt until numResultBlocks) {
                blocks[j].codewords[shorterBlocksNumDataCodewords] = rawCodewords[rawCodewordsOffset++]
            }
            val max = blocks[0].codewords.size
            for (i in shorterBlocksNumDataCodewords until max) {
                for (j in 0 until numResultBlocks) {
                    val iOffset = if (j < longerBlocksStartAt) i else i + 1
                    blocks[j].codewords[iOffset] = rawCodewords[rawCodewordsOffset++]
                }
            }
            return blocks
        }
    }
}
