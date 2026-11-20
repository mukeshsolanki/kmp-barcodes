/*
 * Copyright 2009 ZXing authors
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

/**
 * Constants and symbol/codeword lookups shared by the PDF417 reader and writer.
 */
internal object PDF417Common {

    /** Number of distinct PDF417 codewords. */
    const val NUMBER_OF_CODEWORDS: Int = 929

    /** Maximum codewords (data + error) in one symbol. */
    const val MAX_CODEWORDS_IN_BARCODE: Int = NUMBER_OF_CODEWORDS - 1

    /** Minimum number of rows in a symbol. */
    const val MIN_ROWS_IN_BARCODE: Int = 3

    /** Maximum number of rows in a symbol. */
    const val MAX_ROWS_IN_BARCODE: Int = 90

    /** Modules per codeword. */
    const val MODULES_IN_CODEWORD: Int = 17

    /** Modules in the stop pattern. */
    const val MODULES_IN_STOP_PATTERN: Int = 18

    /** Bars and spaces per codeword. */
    const val BARS_IN_MODULE: Int = 8

    /** The sorted table of all possible symbols; its index is the index into the codeword table. */
    val SYMBOL_TABLE: IntArray = PDF417_SYMBOL_TABLE

    /** The codeword (plus one) for each entry of [SYMBOL_TABLE]. */
    private val CODEWORD_TABLE: IntArray = PDF417_CODEWORD_TABLE

    /** Shared empty result for [toIntArray]. */
    private val EMPTY_INT_ARRAY: IntArray = IntArray(0)

    /** Copies [list] into an [IntArray], returning a shared empty array for null or empty input. */
    fun toIntArray(list: Collection<Int>?): IntArray {
        if (list == null || list.isEmpty()) {
            return EMPTY_INT_ARRAY
        }
        return list.toIntArray()
    }

    /** Translates an encoded [symbol] to its codeword, or -1 when the symbol is unknown. */
    fun getCodeword(symbol: Int): Int {
        val i = SYMBOL_TABLE.asList().binarySearch(symbol and 0x3FFFF)
        if (i < 0) {
            return -1
        }
        return (CODEWORD_TABLE[i] - 1) % NUMBER_OF_CODEWORDS
    }
}
