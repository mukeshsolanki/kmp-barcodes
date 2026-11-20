/*
 * Copyright 2013 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Common
import kotlin.math.max

/**
 * A left or right row indicator column, which also carries the symbol metadata.
 */
internal class DetectionResultRowIndicatorColumn(
    boundingBox: BoundingBox,
    /** True for the left row indicator column. */
    val isLeft: Boolean,
) : DetectionResultColumn(boundingBox) {

    /** Derives the row number of every codeword from its indicator value. */
    private fun setRowNumbers() {
        for (codeword in codewords) {
            codeword?.setRowNumberAsRowIndicatorColumn()
        }
    }

    /** Assigns row numbers and drops codewords inconsistent with [barcodeMetadata] or their neighbours. */
    fun adjustCompleteIndicatorColumnRowNumbers(barcodeMetadata: BarcodeMetadata) {
        setRowNumbers()
        removeIncorrectCodewords(codewords, barcodeMetadata)
        val top = if (isLeft) boundingBox.topLeft else boundingBox.topRight
        val bottom = if (isLeft) boundingBox.bottomLeft else boundingBox.bottomRight
        val firstRow = imageRowToCodewordIndex(top.y.toInt())
        val lastRow = imageRowToCodewordIndex(bottom.y.toInt())
        var barcodeRow = -1
        var maxRowHeight = 1
        var currentRowHeight = 0
        for (codewordsRow in firstRow until lastRow) {
            val codeword = codewords[codewordsRow] ?: continue
            val rowDifference = codeword.rowNumber - barcodeRow
            if (rowDifference == 0) {
                currentRowHeight++
            } else if (rowDifference == 1) {
                maxRowHeight = max(maxRowHeight, currentRowHeight)
                currentRowHeight = 1
                barcodeRow = codeword.rowNumber
            } else if (rowDifference < 0 ||
                codeword.rowNumber >= barcodeMetadata.rowCount ||
                rowDifference > codewordsRow
            ) {
                codewords[codewordsRow] = null
            } else if (closePreviousCodewordFound(codewords, codewordsRow, rowDifference, maxRowHeight)) {
                codewords[codewordsRow] = null
            } else {
                barcodeRow = codeword.rowNumber
                currentRowHeight = 1
            }
        }
    }

    /** True when a codeword exists within the rows that should be empty before a jump of [rowDifference] rows. */
    private fun closePreviousCodewordFound(
        codewords: Array<Codeword?>,
        codewordsRow: Int,
        rowDifference: Int,
        maxRowHeight: Int,
    ): Boolean {
        val checkedRows = if (maxRowHeight > 2) (maxRowHeight - 2) * rowDifference else rowDifference
        var found = checkedRows >= codewordsRow
        var i = 1
        while (i <= checkedRows && !found) {
            found = codewords[codewordsRow - i] != null
            i++
        }
        return found
    }

    /** Returns the number of codewords seen per barcode row, or null when the metadata is unreadable. */
    fun getRowHeights(): IntArray? {
        val barcodeMetadata = getBarcodeMetadata() ?: return null
        adjustIncompleteIndicatorColumnRowNumbers(barcodeMetadata)
        val result = IntArray(barcodeMetadata.rowCount)
        for (codeword in codewords) {
            if (codeword != null) {
                val rowNumber = codeword.rowNumber
                if (rowNumber >= result.size) {
                    continue
                }
                result[rowNumber]++
            }
        }
        return result
    }

    /** Assigns row numbers, dropping codewords beyond the row count in [barcodeMetadata]. */
    private fun adjustIncompleteIndicatorColumnRowNumbers(barcodeMetadata: BarcodeMetadata) {
        val top = if (isLeft) boundingBox.topLeft else boundingBox.topRight
        val bottom = if (isLeft) boundingBox.bottomLeft else boundingBox.bottomRight
        val firstRow = imageRowToCodewordIndex(top.y.toInt())
        val lastRow = imageRowToCodewordIndex(bottom.y.toInt())
        var barcodeRow = -1
        var maxRowHeight = 1
        var currentRowHeight = 0
        for (codewordsRow in firstRow until lastRow) {
            val codeword = codewords[codewordsRow] ?: continue
            codeword.setRowNumberAsRowIndicatorColumn()
            val rowDifference = codeword.rowNumber - barcodeRow
            if (rowDifference == 0) {
                currentRowHeight++
            } else if (rowDifference == 1) {
                maxRowHeight = max(maxRowHeight, currentRowHeight)
                currentRowHeight = 1
                barcodeRow = codeword.rowNumber
            } else if (codeword.rowNumber >= barcodeMetadata.rowCount) {
                codewords[codewordsRow] = null
            } else {
                barcodeRow = codeword.rowNumber
                currentRowHeight = 1
            }
        }
    }

    /** Reads the column count, row count and EC level by majority vote, or null when inconsistent. */
    fun getBarcodeMetadata(): BarcodeMetadata? {
        val barcodeColumnCount = BarcodeValue()
        val barcodeRowCountUpperPart = BarcodeValue()
        val barcodeRowCountLowerPart = BarcodeValue()
        val barcodeECLevel = BarcodeValue()
        for (codeword in codewords) {
            if (codeword == null) {
                continue
            }
            codeword.setRowNumberAsRowIndicatorColumn()
            val rowIndicatorValue = codeword.value % 30
            var codewordRowNumber = codeword.rowNumber
            if (!isLeft) {
                codewordRowNumber += 2
            }
            when (codewordRowNumber % 3) {
                0 -> barcodeRowCountUpperPart.setValue(rowIndicatorValue * 3 + 1)
                1 -> {
                    barcodeECLevel.setValue(rowIndicatorValue / 3)
                    barcodeRowCountLowerPart.setValue(rowIndicatorValue % 3)
                }
                2 -> barcodeColumnCount.setValue(rowIndicatorValue + 1)
            }
        }
        val columnCount = barcodeColumnCount.value
        val rowCountUpperPart = barcodeRowCountUpperPart.value
        val rowCountLowerPart = barcodeRowCountLowerPart.value
        val ecLevel = barcodeECLevel.value
        if (columnCount.isEmpty() ||
            rowCountUpperPart.isEmpty() ||
            rowCountLowerPart.isEmpty() ||
            ecLevel.isEmpty() ||
            columnCount[0] < 1 ||
            rowCountUpperPart[0] + rowCountLowerPart[0] < PDF417Common.MIN_ROWS_IN_BARCODE ||
            rowCountUpperPart[0] + rowCountLowerPart[0] > PDF417Common.MAX_ROWS_IN_BARCODE
        ) {
            return null
        }
        val barcodeMetadata = BarcodeMetadata(columnCount[0], rowCountUpperPart[0], rowCountLowerPart[0], ecLevel[0])
        removeIncorrectCodewords(codewords, barcodeMetadata)
        return barcodeMetadata
    }

    /** Drops codewords whose indicator value contradicts [barcodeMetadata]. */
    private fun removeIncorrectCodewords(codewords: Array<Codeword?>, barcodeMetadata: BarcodeMetadata) {
        for (codewordRow in codewords.indices) {
            val codeword = codewords[codewordRow] ?: continue
            val rowIndicatorValue = codeword.value % 30
            var codewordRowNumber = codeword.rowNumber
            if (codewordRowNumber > barcodeMetadata.rowCount) {
                codewords[codewordRow] = null
                continue
            }
            if (!isLeft) {
                codewordRowNumber += 2
            }
            val matches = when (codewordRowNumber % 3) {
                0 -> rowIndicatorValue * 3 + 1 == barcodeMetadata.rowCountUpperPart
                1 -> rowIndicatorValue / 3 == barcodeMetadata.errorCorrectionLevel &&
                    rowIndicatorValue % 3 == barcodeMetadata.rowCountLowerPart
                2 -> rowIndicatorValue + 1 == barcodeMetadata.columnCount
                else -> true
            }
            if (!matches) {
                codewords[codewordRow] = null
            }
        }
    }

    /** Renders the side followed by the column rows. */
    override fun toString(): String = "IsLeft: $isLeft\n" + super.toString()
}
