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

import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/** Reads the codewords out of a sampled Data Matrix symbol (ISO 16022:2006 5.8 and Annex F). */
internal class BitMatrixParser(bitMatrix: BitMatrix) {

    /** Symbol version deduced from the matrix dimensions. */
    val version: Version

    /** Data regions with finder and timing patterns removed. */
    private val mappingBitMatrix: BitMatrix

    /** Marks which modules of [mappingBitMatrix] have been read. */
    private val readMappingMatrix: BitMatrix

    init {
        val dimension = bitMatrix.height
        if (dimension < 8 || dimension > 144 || (dimension and 0x01) != 0) {
            throw FormatException.formatInstance
        }
        version = Version.getVersionForDimensions(bitMatrix.height, bitMatrix.width)
        mappingBitMatrix = extractDataRegion(bitMatrix)
        readMappingMatrix = BitMatrix(mappingBitMatrix.width, mappingBitMatrix.height)
    }

    /** Reads every codeword in placement order, or throws [FormatException] when the count is wrong. */
    fun readCodewords(): ByteArray {
        val result = ByteArray(version.totalCodewords)
        var resultOffset = 0

        var row = 4
        var column = 0

        val numRows = mappingBitMatrix.height
        val numColumns = mappingBitMatrix.width

        var corner1Read = false
        var corner2Read = false
        var corner3Read = false
        var corner4Read = false

        do {
            if (row == numRows && column == 0 && !corner1Read) {
                result[resultOffset++] = readCorner1(numRows, numColumns).toByte()
                row -= 2
                column += 2
                corner1Read = true
            } else if (row == numRows - 2 && column == 0 && (numColumns and 0x03) != 0 && !corner2Read) {
                result[resultOffset++] = readCorner2(numRows, numColumns).toByte()
                row -= 2
                column += 2
                corner2Read = true
            } else if (row == numRows + 4 && column == 2 && (numColumns and 0x07) == 0 && !corner3Read) {
                result[resultOffset++] = readCorner3(numRows, numColumns).toByte()
                row -= 2
                column += 2
                corner3Read = true
            } else if (row == numRows - 2 && column == 0 && (numColumns and 0x07) == 4 && !corner4Read) {
                result[resultOffset++] = readCorner4(numRows, numColumns).toByte()
                row -= 2
                column += 2
                corner4Read = true
            } else {
                do {
                    if (row < numRows && column >= 0 && !readMappingMatrix.get(column, row)) {
                        result[resultOffset++] = readUtah(row, column, numRows, numColumns).toByte()
                    }
                    row -= 2
                    column += 2
                } while (row >= 0 && column < numColumns)
                row += 1
                column += 3

                do {
                    if (row >= 0 && column < numColumns && !readMappingMatrix.get(column, row)) {
                        result[resultOffset++] = readUtah(row, column, numRows, numColumns).toByte()
                    }
                    row += 2
                    column -= 2
                } while (row < numRows && column >= 0)
                row += 3
                column += 1
            }
        } while (row < numRows || column < numColumns)

        if (resultOffset != version.totalCodewords) {
            throw FormatException.formatInstance
        }
        return result
    }

    /** Reads one module of the mapping matrix, wrapping around the edges, and marks it read. */
    private fun readModule(row: Int, column: Int, numRows: Int, numColumns: Int): Boolean {
        var r = row
        var c = column
        if (r < 0) {
            r += numRows
            c += 4 - ((numRows + 4) and 0x07)
        }
        if (c < 0) {
            c += numColumns
            r += 4 - ((numColumns + 4) and 0x07)
        }
        if (r >= numRows) {
            r -= numRows
        }
        readMappingMatrix.set(c, r)
        return mappingBitMatrix.get(c, r)
    }

    /** Reads 8 modules at the given (row, column) pairs, most significant bit first. */
    private fun readByte(numRows: Int, numColumns: Int, vararg positions: Int): Int {
        var currentByte = 0
        for (i in positions.indices step 2) {
            currentByte = currentByte shl 1
            if (readModule(positions[i], positions[i + 1], numRows, numColumns)) {
                currentByte = currentByte or 1
            }
        }
        return currentByte
    }

    /** Reads the standard Utah-shaped codeword anchored at its last bit (ISO 16022:2006 Figure 6). */
    private fun readUtah(row: Int, column: Int, numRows: Int, numColumns: Int): Int = readByte(
        numRows, numColumns,
        row - 2, column - 2,
        row - 2, column - 1,
        row - 1, column - 2,
        row - 1, column - 1,
        row - 1, column,
        row, column - 2,
        row, column - 1,
        row, column,
    )

    /** Reads corner condition 1 (ISO 16022:2006 Figure F.3). */
    private fun readCorner1(numRows: Int, numColumns: Int): Int = readByte(
        numRows, numColumns,
        numRows - 1, 0,
        numRows - 1, 1,
        numRows - 1, 2,
        0, numColumns - 2,
        0, numColumns - 1,
        1, numColumns - 1,
        2, numColumns - 1,
        3, numColumns - 1,
    )

    /** Reads corner condition 2 (ISO 16022:2006 Figure F.4). */
    private fun readCorner2(numRows: Int, numColumns: Int): Int = readByte(
        numRows, numColumns,
        numRows - 3, 0,
        numRows - 2, 0,
        numRows - 1, 0,
        0, numColumns - 4,
        0, numColumns - 3,
        0, numColumns - 2,
        0, numColumns - 1,
        1, numColumns - 1,
    )

    /** Reads corner condition 3 (ISO 16022:2006 Figure F.5). */
    private fun readCorner3(numRows: Int, numColumns: Int): Int = readByte(
        numRows, numColumns,
        numRows - 1, 0,
        numRows - 1, numColumns - 1,
        0, numColumns - 3,
        0, numColumns - 2,
        0, numColumns - 1,
        1, numColumns - 3,
        1, numColumns - 2,
        1, numColumns - 1,
    )

    /** Reads corner condition 4 (ISO 16022:2006 Figure F.6). */
    private fun readCorner4(numRows: Int, numColumns: Int): Int = readByte(
        numRows, numColumns,
        numRows - 3, 0,
        numRows - 2, 0,
        numRows - 1, 0,
        0, numColumns - 2,
        0, numColumns - 1,
        1, numColumns - 1,
        2, numColumns - 1,
        3, numColumns - 1,
    )

    /** Copies the data regions of [bitMatrix] into one matrix, dropping alignment patterns. */
    private fun extractDataRegion(bitMatrix: BitMatrix): BitMatrix {
        val symbolSizeRows = version.symbolSizeRows
        val symbolSizeColumns = version.symbolSizeColumns

        require(bitMatrix.height == symbolSizeRows) { "Dimension of bitMatrix must match the version size" }

        val dataRegionSizeRows = version.dataRegionSizeRows
        val dataRegionSizeColumns = version.dataRegionSizeColumns

        val numDataRegionsRow = symbolSizeRows / dataRegionSizeRows
        val numDataRegionsColumn = symbolSizeColumns / dataRegionSizeColumns

        val sizeDataRegionRow = numDataRegionsRow * dataRegionSizeRows
        val sizeDataRegionColumn = numDataRegionsColumn * dataRegionSizeColumns

        val bitMatrixWithoutAlignment = BitMatrix(sizeDataRegionColumn, sizeDataRegionRow)
        for (dataRegionRow in 0 until numDataRegionsRow) {
            val dataRegionRowOffset = dataRegionRow * dataRegionSizeRows
            for (dataRegionColumn in 0 until numDataRegionsColumn) {
                val dataRegionColumnOffset = dataRegionColumn * dataRegionSizeColumns
                for (i in 0 until dataRegionSizeRows) {
                    val readRowOffset = dataRegionRow * (dataRegionSizeRows + 2) + 1 + i
                    val writeRowOffset = dataRegionRowOffset + i
                    for (j in 0 until dataRegionSizeColumns) {
                        val readColumnOffset = dataRegionColumn * (dataRegionSizeColumns + 2) + 1 + j
                        if (bitMatrix.get(readColumnOffset, readRowOffset)) {
                            bitMatrixWithoutAlignment.set(dataRegionColumnOffset + j, writeRowOffset)
                        }
                    }
                }
            }
        }
        return bitMatrixWithoutAlignment
    }
}
