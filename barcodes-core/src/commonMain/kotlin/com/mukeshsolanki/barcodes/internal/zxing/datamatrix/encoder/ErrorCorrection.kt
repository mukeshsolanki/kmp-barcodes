/*
 * Copyright 2006 Jeremias Maerki.
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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder

/** ECC 200 Reed-Solomon error correction for Data Matrix. */
internal object ErrorCorrection {

    /** Error codeword counts, indexing into [FACTORS]. */
    private val FACTOR_SETS = intArrayOf(5, 7, 10, 11, 12, 14, 18, 20, 24, 28, 36, 42, 48, 56, 62, 68)

    /** Precomputed generator polynomial factors for ECC 200. */
    private val FACTORS: Array<IntArray> = arrayOf(
        intArrayOf(228, 48, 15, 111, 62),
        intArrayOf(23, 68, 144, 134, 240, 92, 254),
        intArrayOf(28, 24, 185, 166, 223, 248, 116, 255, 110, 61),
        intArrayOf(175, 138, 205, 12, 194, 168, 39, 245, 60, 97, 120),
        intArrayOf(41, 153, 158, 91, 61, 42, 142, 213, 97, 178, 100, 242),
        intArrayOf(156, 97, 192, 252, 95, 9, 157, 119, 138, 45, 18, 186, 83, 185),
        intArrayOf(83, 195, 100, 39, 188, 75, 66, 61, 241, 213, 109, 129, 94, 254, 225, 48, 90, 188),
        intArrayOf(15, 195, 244, 9, 233, 71, 168, 2, 188, 160, 153, 145, 253, 79, 108, 82, 27, 174, 186, 172),
        intArrayOf(
            52, 190, 88, 205, 109, 39, 176, 21, 155, 197, 251, 223, 155, 21, 5, 172,
            254, 124, 12, 181, 184, 96, 50, 193,
        ),
        intArrayOf(
            211, 231, 43, 97, 71, 96, 103, 174, 37, 151, 170, 53, 75, 34, 249, 121,
            17, 138, 110, 213, 141, 136, 120, 151, 233, 168, 93, 255,
        ),
        intArrayOf(
            245, 127, 242, 218, 130, 250, 162, 181, 102, 120, 84, 179, 220, 251, 80, 182,
            229, 18, 2, 4, 68, 33, 101, 137, 95, 119, 115, 44, 175, 184, 59, 25,
            225, 98, 81, 112,
        ),
        intArrayOf(
            77, 193, 137, 31, 19, 38, 22, 153, 247, 105, 122, 2, 245, 133, 242, 8,
            175, 95, 100, 9, 167, 105, 214, 111, 57, 121, 21, 1, 253, 57, 54, 101,
            248, 202, 69, 50, 150, 177, 226, 5, 9, 5,
        ),
        intArrayOf(
            245, 132, 172, 223, 96, 32, 117, 22, 238, 133, 238, 231, 205, 188, 237, 87,
            191, 106, 16, 147, 118, 23, 37, 90, 170, 205, 131, 88, 120, 100, 66, 138,
            186, 240, 82, 44, 176, 87, 187, 147, 160, 175, 69, 213, 92, 253, 225, 19,
        ),
        intArrayOf(
            175, 9, 223, 238, 12, 17, 220, 208, 100, 29, 175, 170, 230, 192, 215, 235,
            150, 159, 36, 223, 38, 200, 132, 54, 228, 146, 218, 234, 117, 203, 29, 232,
            144, 238, 22, 150, 201, 117, 62, 207, 164, 13, 137, 245, 127, 67, 247, 28,
            155, 43, 203, 107, 233, 53, 143, 46,
        ),
        intArrayOf(
            242, 93, 169, 50, 144, 210, 39, 118, 202, 188, 201, 189, 143, 108, 196, 37,
            185, 112, 134, 230, 245, 63, 197, 190, 250, 106, 185, 221, 175, 64, 114, 71,
            161, 44, 147, 6, 27, 218, 51, 63, 87, 10, 40, 130, 188, 17, 163, 31,
            176, 170, 4, 107, 232, 7, 94, 166, 224, 124, 86, 47, 11, 204,
        ),
        intArrayOf(
            220, 228, 173, 89, 251, 149, 159, 56, 89, 33, 147, 244, 154, 36, 73, 127,
            213, 136, 248, 180, 234, 197, 158, 177, 68, 122, 93, 213, 15, 160, 227, 236,
            66, 139, 153, 185, 202, 167, 179, 25, 220, 232, 96, 210, 231, 136, 223, 239,
            181, 241, 59, 52, 172, 25, 49, 232, 211, 189, 64, 54, 108, 153, 132, 63,
            96, 103, 82, 186,
        ),
    )

    /** GF(256) reduction polynomial x^8 + x^5 + x^3 + x^2 + 1. */
    private const val MODULO_VALUE = 0x12D

    /** Discrete log table of GF(256). */
    private val LOG = IntArray(256)

    /** Antilog (power) table of GF(256). */
    private val ALOG = IntArray(255)

    init {
        var p = 1
        for (i in 0 until 255) {
            ALOG[i] = p
            LOG[p] = i
            p *= 2
            if (p >= 256) {
                p = p xor MODULO_VALUE
            }
        }
    }

    /** Appends interleaved ECC 200 error correction to [codewords] for [symbolInfo]. */
    fun encodeECC200(codewords: String, symbolInfo: SymbolInfo): String {
        require(codewords.length == symbolInfo.dataCapacity) {
            "The number of codewords does not match the selected symbol"
        }
        val blockCount = symbolInfo.interleavedBlockCount
        if (blockCount == 1) {
            return codewords + createECCBlock(codewords, symbolInfo.errorCodewords)
        }
        val sb = CharArray(symbolInfo.dataCapacity + symbolInfo.errorCodewords)
        codewords.toCharArray().copyInto(sb)
        val dataSizes = IntArray(blockCount) { symbolInfo.getDataLengthForInterleavedBlock(it + 1) }
        val errorSizes = IntArray(blockCount) { symbolInfo.getErrorLengthForInterleavedBlock(it + 1) }
        for (block in 0 until blockCount) {
            val temp = StringBuilder(dataSizes[block])
            for (d in block until symbolInfo.dataCapacity step blockCount) {
                temp.append(codewords[d])
            }
            val ecc = createECCBlock(temp, errorSizes[block])
            var pos = 0
            for (e in block until errorSizes[block] * blockCount step blockCount) {
                sb[symbolInfo.dataCapacity + e] = ecc[pos++]
            }
        }
        return sb.concatToString()
    }

    /** Computes [numECWords] Reed-Solomon codewords for one block of [codewords]. */
    private fun createECCBlock(codewords: CharSequence, numECWords: Int): String {
        val table = FACTOR_SETS.indexOf(numECWords)
        require(table >= 0) { "Illegal number of error correction codewords specified: $numECWords" }
        val poly = FACTORS[table]
        val ecc = IntArray(numECWords)
        for (i in codewords.indices) {
            val m = ecc[numECWords - 1] xor codewords[i].code
            for (k in numECWords - 1 downTo 1) {
                ecc[k] = if (m != 0 && poly[k] != 0) {
                    ecc[k - 1] xor ALOG[(LOG[m] + LOG[poly[k]]) % 255]
                } else {
                    ecc[k - 1]
                }
            }
            ecc[0] = if (m != 0 && poly[0] != 0) ALOG[(LOG[m] + LOG[poly[0]]) % 255] else 0
        }
        return CharArray(numECWords) { ecc[numECWords - it - 1].toChar() }.concatToString()
    }
}
