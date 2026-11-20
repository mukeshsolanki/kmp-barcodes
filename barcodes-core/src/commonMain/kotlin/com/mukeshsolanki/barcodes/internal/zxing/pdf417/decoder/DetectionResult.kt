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

/**
 * All detected columns of one symbol plus its metadata; resolves codeword row numbers.
 */
internal class DetectionResult(
    /** Metadata read from the row indicator columns. */
    private val barcodeMetadata: BarcodeMetadata,
    /** The region the symbol was detected in. */
    var boundingBox: BoundingBox?,
) {

    /** Number of data columns. */
    val barcodeColumnCount: Int = barcodeMetadata.columnCount

    /** Columns 0 and [barcodeColumnCount] + 1 are the row indicators; the rest hold data. */
    private val detectionResultColumns: Array<DetectionResultColumn?> = arrayOfNulls(barcodeColumnCount + 2)

    /** Number of rows. */
    val barcodeRowCount: Int get() = barcodeMetadata.rowCount

    /** Error correction level. */
    val barcodeECLevel: Int get() = barcodeMetadata.errorCorrectionLevel

    /** Resolves row numbers until no further progress is made, then returns all columns. */
    fun getDetectionResultColumns(): Array<DetectionResultColumn?> {
        adjustIndicatorColumnRowNumbers(detectionResultColumns[0])
        adjustIndicatorColumnRowNumbers(detectionResultColumns[barcodeColumnCount + 1])
        var unadjustedCodewordCount = PDF417Common.MAX_CODEWORDS_IN_BARCODE
        var previousUnadjustedCount: Int
        do {
            previousUnadjustedCount = unadjustedCodewordCount
            unadjustedCodewordCount = adjustRowNumbers()
        } while (unadjustedCodewordCount > 0 && unadjustedCodewordCount < previousUnadjustedCount)
        return detectionResultColumns
    }

    /** Adjusts the row numbers of a row indicator column, if present. */
    private fun adjustIndicatorColumnRowNumbers(detectionResultColumn: DetectionResultColumn?) {
        if (detectionResultColumn != null) {
            (detectionResultColumn as DetectionResultRowIndicatorColumn)
                .adjustCompleteIndicatorColumnRowNumbers(barcodeMetadata)
        }
    }

    /** One adjustment pass; returns a rough count of codewords still lacking a valid row number. */
    private fun adjustRowNumbers(): Int {
        val unadjustedCount = adjustRowNumbersByRow()
        if (unadjustedCount == 0) {
            return 0
        }
        for (barcodeColumn in 1 until barcodeColumnCount + 1) {
            val codewords = detectionResultColumns[barcodeColumn]!!.codewords
            for (codewordsRow in codewords.indices) {
                val codeword = codewords[codewordsRow] ?: continue
                if (!codeword.hasValidRowNumber()) {
                    adjustRowNumbers(barcodeColumn, codewordsRow, codewords)
                }
            }
        }
        return unadjustedCount
    }

    /** Adjusts row numbers from both row indicator columns. */
    private fun adjustRowNumbersByRow(): Int {
        adjustRowNumbersFromBothRI()
        val unadjustedCount = adjustRowNumbersFromLRI()
        return unadjustedCount + adjustRowNumbersFromRRI()
    }

    /** Where both indicators agree on a row, applies that row number across the data columns. */
    private fun adjustRowNumbersFromBothRI() {
        val left = detectionResultColumns[0]
        val right = detectionResultColumns[barcodeColumnCount + 1]
        if (left == null || right == null) {
            return
        }
        val lriCodewords = left.codewords
        val rriCodewords = right.codewords
        for (codewordsRow in lriCodewords.indices) {
            val lri = lriCodewords[codewordsRow]
            val rri = rriCodewords[codewordsRow]
            if (lri != null && rri != null && lri.rowNumber == rri.rowNumber) {
                for (barcodeColumn in 1..barcodeColumnCount) {
                    val columnCodewords = detectionResultColumns[barcodeColumn]!!.codewords
                    val codeword = columnCodewords[codewordsRow] ?: continue
                    codeword.rowNumber = lri.rowNumber
                    if (!codeword.hasValidRowNumber()) {
                        columnCodewords[codewordsRow] = null
                    }
                }
            }
        }
    }

    /** Propagates right indicator row numbers leftwards; returns the count still unadjusted. */
    private fun adjustRowNumbersFromRRI(): Int {
        val right = detectionResultColumns[barcodeColumnCount + 1] ?: return 0
        var unadjustedCount = 0
        val codewords = right.codewords
        for (codewordsRow in codewords.indices) {
            val indicator = codewords[codewordsRow] ?: continue
            val rowIndicatorRowNumber = indicator.rowNumber
            var invalidRowCounts = 0
            var barcodeColumn = barcodeColumnCount + 1
            while (barcodeColumn > 0 && invalidRowCounts < ADJUST_ROW_NUMBER_SKIP) {
                val codeword = detectionResultColumns[barcodeColumn]!!.codewords[codewordsRow]
                if (codeword != null) {
                    invalidRowCounts = adjustRowNumberIfValid(rowIndicatorRowNumber, invalidRowCounts, codeword)
                    if (!codeword.hasValidRowNumber()) {
                        unadjustedCount++
                    }
                }
                barcodeColumn--
            }
        }
        return unadjustedCount
    }

    /** Propagates left indicator row numbers rightwards; returns the count still unadjusted. */
    private fun adjustRowNumbersFromLRI(): Int {
        val left = detectionResultColumns[0] ?: return 0
        var unadjustedCount = 0
        val codewords = left.codewords
        for (codewordsRow in codewords.indices) {
            val indicator = codewords[codewordsRow] ?: continue
            val rowIndicatorRowNumber = indicator.rowNumber
            var invalidRowCounts = 0
            var barcodeColumn = 1
            while (barcodeColumn < barcodeColumnCount + 1 && invalidRowCounts < ADJUST_ROW_NUMBER_SKIP) {
                val codeword = detectionResultColumns[barcodeColumn]!!.codewords[codewordsRow]
                if (codeword != null) {
                    invalidRowCounts = adjustRowNumberIfValid(rowIndicatorRowNumber, invalidRowCounts, codeword)
                    if (!codeword.hasValidRowNumber()) {
                        unadjustedCount++
                    }
                }
                barcodeColumn++
            }
        }
        return unadjustedCount
    }

    /** Fills in [codeword]'s row number from its neighbours in this and the adjacent columns. */
    private fun adjustRowNumbers(barcodeColumn: Int, codewordsRow: Int, codewords: Array<Codeword?>) {
        val codeword = codewords[codewordsRow]!!
        val previousColumnCodewords = detectionResultColumns[barcodeColumn - 1]!!.codewords
        val nextColumnCodewords = detectionResultColumns[barcodeColumn + 1]?.codewords ?: previousColumnCodewords
        val otherCodewords = arrayOfNulls<Codeword>(14)
        otherCodewords[2] = previousColumnCodewords[codewordsRow]
        otherCodewords[3] = nextColumnCodewords[codewordsRow]
        if (codewordsRow > 0) {
            otherCodewords[0] = codewords[codewordsRow - 1]
            otherCodewords[4] = previousColumnCodewords[codewordsRow - 1]
            otherCodewords[5] = nextColumnCodewords[codewordsRow - 1]
        }
        if (codewordsRow > 1) {
            otherCodewords[8] = codewords[codewordsRow - 2]
            otherCodewords[10] = previousColumnCodewords[codewordsRow - 2]
            otherCodewords[11] = nextColumnCodewords[codewordsRow - 2]
        }
        if (codewordsRow < codewords.size - 1) {
            otherCodewords[1] = codewords[codewordsRow + 1]
            otherCodewords[6] = previousColumnCodewords[codewordsRow + 1]
            otherCodewords[7] = nextColumnCodewords[codewordsRow + 1]
        }
        if (codewordsRow < codewords.size - 2) {
            otherCodewords[9] = codewords[codewordsRow + 2]
            otherCodewords[12] = previousColumnCodewords[codewordsRow + 2]
            otherCodewords[13] = nextColumnCodewords[codewordsRow + 2]
        }
        for (otherCodeword in otherCodewords) {
            if (adjustRowNumber(codeword, otherCodeword)) {
                return
            }
        }
    }

    /** Stores the column at [barcodeColumn]. */
    fun setDetectionResultColumn(barcodeColumn: Int, detectionResultColumn: DetectionResultColumn?) {
        detectionResultColumns[barcodeColumn] = detectionResultColumn
    }

    /** Returns the column at [barcodeColumn], or null. */
    fun getDetectionResultColumn(barcodeColumn: Int): DetectionResultColumn? = detectionResultColumns[barcodeColumn]

    /** Renders a grid of `rowNumber|value` per codeword row and column. */
    override fun toString(): String {
        val rowIndicatorColumn = detectionResultColumns[0] ?: detectionResultColumns[barcodeColumnCount + 1]!!
        val sb = StringBuilder()
        for (codewordsRow in rowIndicatorColumn.codewords.indices) {
            sb.append("CW ").append(DetectionResultColumn.pad(codewordsRow, 3)).append(':')
            for (barcodeColumn in 0 until barcodeColumnCount + 2) {
                val codeword = detectionResultColumns[barcodeColumn]?.codewords?.get(codewordsRow)
                if (codeword == null) {
                    sb.append("    |   ")
                    continue
                }
                sb.append(' ').append(DetectionResultColumn.pad(codeword.rowNumber, 3)).append('|')
                    .append(DetectionResultColumn.pad(codeword.value, 3))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Holds the skip limit and the stateless row number helpers. */
    private companion object {
        /** Consecutive invalid codewords after which propagation along a row stops. */
        const val ADJUST_ROW_NUMBER_SKIP = 2

        /** Applies [rowIndicatorRowNumber] to [codeword] when consistent; returns the updated invalid count. */
        fun adjustRowNumberIfValid(rowIndicatorRowNumber: Int, invalidRowCounts: Int, codeword: Codeword?): Int {
            if (codeword == null) {
                return invalidRowCounts
            }
            var count = invalidRowCounts
            if (!codeword.hasValidRowNumber()) {
                if (codeword.isValidRowNumber(rowIndicatorRowNumber)) {
                    codeword.rowNumber = rowIndicatorRowNumber
                    count = 0
                } else {
                    ++count
                }
            }
            return count
        }

        /** Copies [otherCodeword]'s row number to [codeword] when valid and in the same bucket. */
        fun adjustRowNumber(codeword: Codeword, otherCodeword: Codeword?): Boolean {
            if (otherCodeword == null) {
                return false
            }
            if (otherCodeword.hasValidRowNumber() && otherCodeword.bucket == codeword.bucket) {
                codeword.rowNumber = otherCodeword.rowNumber
                return true
            }
            return false
        }
    }
}
