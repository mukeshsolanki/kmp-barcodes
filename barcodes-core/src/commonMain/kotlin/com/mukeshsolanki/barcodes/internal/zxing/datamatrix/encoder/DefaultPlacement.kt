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

/** Places codeword bits into the data region matrix (ISO/IEC 16022:2000 Annex M.1). */
internal class DefaultPlacement(
    /** Codewords to place, one char per byte value. */
    private val codewords: CharSequence,
    /** Number of columns in the data matrix. */
    val numcols: Int,
    /** Number of rows in the data matrix. */
    val numrows: Int,
) {

    /** Module values: 1 dark, 0 light, -1 not yet set. */
    val bits: ByteArray = ByteArray(numcols * numrows).apply { fill(-1) }

    /** True when the module at ([col], [row]) is dark. */
    fun getBit(col: Int, row: Int): Boolean = bits[row * numcols + col] == 1.toByte()

    /** Sets the module at ([col], [row]). */
    private fun setBit(col: Int, row: Int, bit: Boolean) {
        bits[row * numcols + col] = if (bit) 1 else 0
    }

    /** True when the module at ([col], [row]) has not been set. */
    private fun noBit(col: Int, row: Int): Boolean = bits[row * numcols + col] < 0

    /** Places every codeword, then fills the fixed lower-right pattern if untouched. */
    fun place() {
        var pos = 0
        var row = 4
        var col = 0

        do {
            if (row == numrows && col == 0) {
                corner1(pos++)
            }
            if (row == numrows - 2 && col == 0 && numcols % 4 != 0) {
                corner2(pos++)
            }
            if (row == numrows - 2 && col == 0 && numcols % 8 == 4) {
                corner3(pos++)
            }
            if (row == numrows + 4 && col == 2 && numcols % 8 == 0) {
                corner4(pos++)
            }
            do {
                if (row < numrows && col >= 0 && noBit(col, row)) {
                    utah(row, col, pos++)
                }
                row -= 2
                col += 2
            } while (row >= 0 && col < numcols)
            row++
            col += 3

            do {
                if (row >= 0 && col < numcols && noBit(col, row)) {
                    utah(row, col, pos++)
                }
                row += 2
                col -= 2
            } while (row < numrows && col >= 0)
            row += 3
            col++
        } while (row < numrows || col < numcols)

        if (noBit(numcols - 1, numrows - 1)) {
            setBit(numcols - 1, numrows - 1, true)
            setBit(numcols - 2, numrows - 2, true)
        }
    }

    /** Places bit [bit] (1 = MSB) of codeword [pos] at ([row], [col]), wrapping around the edges. */
    private fun module(row: Int, col: Int, pos: Int, bit: Int) {
        var r = row
        var c = col
        if (r < 0) {
            r += numrows
            c += 4 - ((numrows + 4) % 8)
        }
        if (c < 0) {
            c += numcols
            r += 4 - ((numcols + 4) % 8)
        }
        val v = codewords[pos].code and (1 shl (8 - bit))
        setBit(c, r, v != 0)
    }

    /** Places the 8 bits of a Utah-shaped symbol character anchored at ([row], [col]). */
    private fun utah(row: Int, col: Int, pos: Int) {
        module(row - 2, col - 2, pos, 1)
        module(row - 2, col - 1, pos, 2)
        module(row - 1, col - 2, pos, 3)
        module(row - 1, col - 1, pos, 4)
        module(row - 1, col, pos, 5)
        module(row, col - 2, pos, 6)
        module(row, col - 1, pos, 7)
        module(row, col, pos, 8)
    }

    /** Places codeword [pos] in corner pattern 1. */
    private fun corner1(pos: Int) {
        module(numrows - 1, 0, pos, 1)
        module(numrows - 1, 1, pos, 2)
        module(numrows - 1, 2, pos, 3)
        module(0, numcols - 2, pos, 4)
        module(0, numcols - 1, pos, 5)
        module(1, numcols - 1, pos, 6)
        module(2, numcols - 1, pos, 7)
        module(3, numcols - 1, pos, 8)
    }

    /** Places codeword [pos] in corner pattern 2. */
    private fun corner2(pos: Int) {
        module(numrows - 3, 0, pos, 1)
        module(numrows - 2, 0, pos, 2)
        module(numrows - 1, 0, pos, 3)
        module(0, numcols - 4, pos, 4)
        module(0, numcols - 3, pos, 5)
        module(0, numcols - 2, pos, 6)
        module(0, numcols - 1, pos, 7)
        module(1, numcols - 1, pos, 8)
    }

    /** Places codeword [pos] in corner pattern 3. */
    private fun corner3(pos: Int) {
        module(numrows - 3, 0, pos, 1)
        module(numrows - 2, 0, pos, 2)
        module(numrows - 1, 0, pos, 3)
        module(0, numcols - 2, pos, 4)
        module(0, numcols - 1, pos, 5)
        module(1, numcols - 1, pos, 6)
        module(2, numcols - 1, pos, 7)
        module(3, numcols - 1, pos, 8)
    }

    /** Places codeword [pos] in corner pattern 4. */
    private fun corner4(pos: Int) {
        module(numrows - 1, 0, pos, 1)
        module(numrows - 1, numcols - 1, pos, 2)
        module(0, numcols - 3, pos, 3)
        module(0, numcols - 2, pos, 4)
        module(0, numcols - 1, pos, 5)
        module(1, numcols - 3, pos, 6)
        module(1, numcols - 2, pos, 7)
        module(1, numcols - 1, pos, 8)
    }
}
