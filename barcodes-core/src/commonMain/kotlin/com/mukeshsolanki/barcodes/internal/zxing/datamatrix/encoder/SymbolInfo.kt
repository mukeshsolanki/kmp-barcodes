/*
 * Copyright 2006 Jeremias Maerki
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

/** Capacity and layout of one Data Matrix symbol size. */
internal open class SymbolInfo(
    /** True for rectangular symbols. */
    private val rectangular: Boolean,
    /** Data codewords the symbol holds. */
    val dataCapacity: Int,
    /** Error-correction codewords the symbol holds. */
    val errorCodewords: Int,
    /** Width of one data region in modules. */
    val matrixWidth: Int,
    /** Height of one data region in modules. */
    val matrixHeight: Int,
    /** Number of data regions. */
    private val dataRegions: Int,
    /** Data codewords per interleaved Reed-Solomon block. */
    private val rsBlockData: Int = dataCapacity,
    /** Error codewords per interleaved Reed-Solomon block. */
    private val rsBlockError: Int = errorCodewords,
) {

    /** Data regions across the symbol. */
    private val horizontalDataRegions: Int
        get() = when (dataRegions) {
            1 -> 1
            2, 4 -> 2
            16 -> 4
            36 -> 6
            else -> error("Cannot handle this number of data regions")
        }

    /** Data regions down the symbol. */
    private val verticalDataRegions: Int
        get() = when (dataRegions) {
            1, 2 -> 1
            4 -> 2
            16 -> 4
            36 -> 6
            else -> error("Cannot handle this number of data regions")
        }

    /** Width of all data regions combined, without alignment patterns. */
    val symbolDataWidth: Int
        get() = horizontalDataRegions * matrixWidth

    /** Height of all data regions combined, without alignment patterns. */
    val symbolDataHeight: Int
        get() = verticalDataRegions * matrixHeight

    /** Full symbol width in modules. */
    val symbolWidth: Int
        get() = symbolDataWidth + horizontalDataRegions * 2

    /** Full symbol height in modules. */
    val symbolHeight: Int
        get() = symbolDataHeight + verticalDataRegions * 2

    /** Total data plus error codewords. */
    val codewordCount: Int
        get() = dataCapacity + errorCodewords

    /** Number of interleaved Reed-Solomon blocks. */
    open val interleavedBlockCount: Int
        get() = dataCapacity / rsBlockData

    /** Data codewords in interleaved block [index] (1-based). */
    open fun getDataLengthForInterleavedBlock(index: Int): Int = rsBlockData

    /** Error codewords in interleaved block [index] (1-based). */
    fun getErrorLengthForInterleavedBlock(index: Int): Int = rsBlockError

    /** Describes the symbol like zxing does. */
    override fun toString(): String =
        (if (rectangular) "Rectangular Symbol:" else "Square Symbol:") +
            " data region " + matrixWidth + 'x' + matrixHeight +
            ", symbol size " + symbolWidth + 'x' + symbolHeight +
            ", symbol data size " + symbolDataWidth + 'x' + symbolDataHeight +
            ", codewords " + dataCapacity + '+' + errorCodewords

    /** Symbol table and lookup. */
    companion object {

        /** Every production symbol size, smallest first. */
        val PROD_SYMBOLS: Array<SymbolInfo> = arrayOf(
            SymbolInfo(false, 3, 5, 8, 8, 1),
            SymbolInfo(false, 5, 7, 10, 10, 1),
            SymbolInfo(true, 5, 7, 16, 6, 1),
            SymbolInfo(false, 8, 10, 12, 12, 1),
            SymbolInfo(true, 10, 11, 14, 6, 2),
            SymbolInfo(false, 12, 12, 14, 14, 1),
            SymbolInfo(true, 16, 14, 24, 10, 1),

            SymbolInfo(false, 18, 14, 16, 16, 1),
            SymbolInfo(false, 22, 18, 18, 18, 1),
            SymbolInfo(true, 22, 18, 16, 10, 2),
            SymbolInfo(false, 30, 20, 20, 20, 1),
            SymbolInfo(true, 32, 24, 16, 14, 2),
            SymbolInfo(false, 36, 24, 22, 22, 1),
            SymbolInfo(false, 44, 28, 24, 24, 1),
            SymbolInfo(true, 49, 28, 22, 14, 2),

            SymbolInfo(false, 62, 36, 14, 14, 4),
            SymbolInfo(false, 86, 42, 16, 16, 4),
            SymbolInfo(false, 114, 48, 18, 18, 4),
            SymbolInfo(false, 144, 56, 20, 20, 4),
            SymbolInfo(false, 174, 68, 22, 22, 4),

            SymbolInfo(false, 204, 84, 24, 24, 4, 102, 42),
            SymbolInfo(false, 280, 112, 14, 14, 16, 140, 56),
            SymbolInfo(false, 368, 144, 16, 16, 16, 92, 36),
            SymbolInfo(false, 456, 192, 18, 18, 16, 114, 48),
            SymbolInfo(false, 576, 224, 20, 20, 16, 144, 56),
            SymbolInfo(false, 696, 272, 22, 22, 16, 174, 68),
            SymbolInfo(false, 816, 336, 24, 24, 16, 136, 56),
            SymbolInfo(false, 1050, 408, 18, 18, 36, 175, 68),
            SymbolInfo(false, 1304, 496, 20, 20, 36, 163, 62),
            DataMatrixSymbolInfo144(),
        )

        /** Returns the smallest symbol of [shape] holding [dataCodewords], or throws [IllegalArgumentException]. */
        fun lookup(dataCodewords: Int, shape: SymbolShapeHint = SymbolShapeHint.FORCE_NONE): SymbolInfo =
            PROD_SYMBOLS.firstOrNull { symbol ->
                when {
                    shape == SymbolShapeHint.FORCE_SQUARE && symbol.rectangular -> false
                    shape == SymbolShapeHint.FORCE_RECTANGLE && !symbol.rectangular -> false
                    else -> dataCodewords <= symbol.dataCapacity
                }
            } ?: throw IllegalArgumentException(
                "Can't find a symbol arrangement that matches the message. Data codewords: $dataCodewords",
            )
    }
}
