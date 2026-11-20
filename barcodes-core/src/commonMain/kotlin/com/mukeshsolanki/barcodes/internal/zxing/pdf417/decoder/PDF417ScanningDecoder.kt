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

import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Common
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder.ec.ErrorCorrection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the codewords of a detected PDF417 symbol column by column and decodes them.
 */
internal object PDF417ScanningDecoder {

    /** Allowed pixel skew of a codeword start or width. */
    private const val CODEWORD_SKEW_SIZE = 2

    /** Extra erasures tolerated beyond half the EC codewords. */
    private const val MAX_ERRORS = 3

    /** Maximum number of EC codewords. */
    private const val MAX_EC_CODEWORDS = 512

    /** Shared error corrector. */
    private val errorCorrection: ErrorCorrection = ErrorCorrection()

    /**
     * Decodes the symbol whose codeword area corners are given (left or right pair may be null).
     * Throws NotFoundException, FormatException or ChecksumException.
     */
    fun decode(
        image: BitMatrix,
        imageTopLeft: ResultPoint?,
        imageBottomLeft: ResultPoint?,
        imageTopRight: ResultPoint?,
        imageBottomRight: ResultPoint?,
        minCodewordWidth: Int,
        maxCodewordWidth: Int,
    ): DecoderResult {
        var boundingBox = BoundingBox(image, imageTopLeft, imageBottomLeft, imageTopRight, imageBottomRight)
        var leftRowIndicatorColumn: DetectionResultRowIndicatorColumn? = null
        var rightRowIndicatorColumn: DetectionResultRowIndicatorColumn? = null
        lateinit var detectionResult: DetectionResult
        var firstPass = true
        while (true) {
            if (imageTopLeft != null) {
                leftRowIndicatorColumn = getRowIndicatorColumn(
                    image, boundingBox, imageTopLeft, true, minCodewordWidth, maxCodewordWidth,
                )
            }
            if (imageTopRight != null) {
                rightRowIndicatorColumn = getRowIndicatorColumn(
                    image, boundingBox, imageTopRight, false, minCodewordWidth, maxCodewordWidth,
                )
            }
            detectionResult = merge(leftRowIndicatorColumn, rightRowIndicatorColumn)
                ?: throw NotFoundException.notFoundInstance
            val resultBox = detectionResult.boundingBox
            if (firstPass && resultBox != null &&
                (resultBox.minY < boundingBox.minY || resultBox.maxY > boundingBox.maxY)
            ) {
                boundingBox = resultBox
            } else {
                break
            }
            firstPass = false
        }
        detectionResult.boundingBox = boundingBox
        val maxBarcodeColumn = detectionResult.barcodeColumnCount + 1
        detectionResult.setDetectionResultColumn(0, leftRowIndicatorColumn)
        detectionResult.setDetectionResultColumn(maxBarcodeColumn, rightRowIndicatorColumn)
        val leftToRight = leftRowIndicatorColumn != null
        var minWidth = minCodewordWidth
        var maxWidth = maxCodewordWidth
        for (barcodeColumnCount in 1..maxBarcodeColumn) {
            val barcodeColumn = if (leftToRight) barcodeColumnCount else maxBarcodeColumn - barcodeColumnCount
            if (detectionResult.getDetectionResultColumn(barcodeColumn) != null) {
                continue
            }
            val detectionResultColumn = if (barcodeColumn == 0 || barcodeColumn == maxBarcodeColumn) {
                DetectionResultRowIndicatorColumn(boundingBox, barcodeColumn == 0)
            } else {
                DetectionResultColumn(boundingBox)
            }
            detectionResult.setDetectionResultColumn(barcodeColumn, detectionResultColumn)
            var startColumn: Int
            var previousStartColumn = -1
            for (imageRow in boundingBox.minY..boundingBox.maxY) {
                startColumn = getStartColumn(detectionResult, barcodeColumn, imageRow, leftToRight)
                if (startColumn < 0 || startColumn > boundingBox.maxX) {
                    if (previousStartColumn == -1) {
                        continue
                    }
                    startColumn = previousStartColumn
                }
                val codeword = detectCodeword(
                    image, boundingBox.minX, boundingBox.maxX, leftToRight, startColumn, imageRow, minWidth, maxWidth,
                ) ?: continue
                detectionResultColumn.setCodeword(imageRow, codeword)
                previousStartColumn = startColumn
                minWidth = min(minWidth, codeword.width)
                maxWidth = max(maxWidth, codeword.width)
            }
        }
        return createDecoderResult(detectionResult)
    }

    /** Combines the row indicator columns into a [DetectionResult], or null when no metadata is readable. */
    private fun merge(
        leftRowIndicatorColumn: DetectionResultRowIndicatorColumn?,
        rightRowIndicatorColumn: DetectionResultRowIndicatorColumn?,
    ): DetectionResult? {
        if (leftRowIndicatorColumn == null && rightRowIndicatorColumn == null) {
            return null
        }
        val barcodeMetadata = getBarcodeMetadata(leftRowIndicatorColumn, rightRowIndicatorColumn) ?: return null
        val boundingBox = BoundingBox.merge(
            adjustBoundingBox(leftRowIndicatorColumn),
            adjustBoundingBox(rightRowIndicatorColumn),
        )
        return DetectionResult(barcodeMetadata, boundingBox)
    }

    /** Extends a row indicator column's box by the rows its row heights say are missing. */
    private fun adjustBoundingBox(rowIndicatorColumn: DetectionResultRowIndicatorColumn?): BoundingBox? {
        if (rowIndicatorColumn == null) {
            return null
        }
        val rowHeights = rowIndicatorColumn.getRowHeights() ?: return null
        val maxRowHeight = getMax(rowHeights)
        var missingStartRows = 0
        for (rowHeight in rowHeights) {
            missingStartRows += maxRowHeight - rowHeight
            if (rowHeight > 0) {
                break
            }
        }
        val codewords = rowIndicatorColumn.codewords
        var row = 0
        while (missingStartRows > 0 && codewords[row] == null) {
            missingStartRows--
            row++
        }
        var missingEndRows = 0
        for (r in rowHeights.indices.reversed()) {
            missingEndRows += maxRowHeight - rowHeights[r]
            if (rowHeights[r] > 0) {
                break
            }
        }
        row = codewords.size - 1
        while (missingEndRows > 0 && codewords[row] == null) {
            missingEndRows--
            row--
        }
        return rowIndicatorColumn.boundingBox.addMissingRows(missingStartRows, missingEndRows, rowIndicatorColumn.isLeft)
    }

    /** Largest of [values], or -1 when empty. */
    private fun getMax(values: IntArray): Int {
        var maxValue = -1
        for (value in values) {
            maxValue = max(maxValue, value)
        }
        return maxValue
    }

    /** Picks the metadata from the left indicator, the right one, or null when they disagree completely. */
    private fun getBarcodeMetadata(
        leftRowIndicatorColumn: DetectionResultRowIndicatorColumn?,
        rightRowIndicatorColumn: DetectionResultRowIndicatorColumn?,
    ): BarcodeMetadata? {
        val leftBarcodeMetadata = leftRowIndicatorColumn?.getBarcodeMetadata()
            ?: return rightRowIndicatorColumn?.getBarcodeMetadata()
        val rightBarcodeMetadata = rightRowIndicatorColumn?.getBarcodeMetadata() ?: return leftBarcodeMetadata
        if (leftBarcodeMetadata.columnCount != rightBarcodeMetadata.columnCount &&
            leftBarcodeMetadata.errorCorrectionLevel != rightBarcodeMetadata.errorCorrectionLevel &&
            leftBarcodeMetadata.rowCount != rightBarcodeMetadata.rowCount
        ) {
            return null
        }
        return leftBarcodeMetadata
    }

    /** Scans a row indicator column up and down from [startPoint]. */
    private fun getRowIndicatorColumn(
        image: BitMatrix,
        boundingBox: BoundingBox,
        startPoint: ResultPoint,
        leftToRight: Boolean,
        minCodewordWidth: Int,
        maxCodewordWidth: Int,
    ): DetectionResultRowIndicatorColumn {
        val rowIndicatorColumn = DetectionResultRowIndicatorColumn(boundingBox, leftToRight)
        for (i in 0 until 2) {
            val increment = if (i == 0) 1 else -1
            var startColumn = startPoint.x.toInt()
            var imageRow = startPoint.y.toInt()
            while (imageRow <= boundingBox.maxY && imageRow >= boundingBox.minY) {
                val codeword = detectCodeword(
                    image, 0, image.width, leftToRight, startColumn, imageRow, minCodewordWidth, maxCodewordWidth,
                )
                if (codeword != null) {
                    rowIndicatorColumn.setCodeword(imageRow, codeword)
                    startColumn = if (leftToRight) codeword.startX else codeword.endX
                }
                imageRow += increment
            }
        }
        return rowIndicatorColumn
    }

    /** Reconciles the symbol length codeword with the count implied by the row indicators. */
    private fun adjustCodewordCount(detectionResult: DetectionResult, barcodeMatrix: Array<Array<BarcodeValue>>) {
        val barcodeMatrix01 = barcodeMatrix[0][1]
        val numberOfCodewords = barcodeMatrix01.value
        val calculatedNumberOfCodewords = detectionResult.barcodeColumnCount *
            detectionResult.barcodeRowCount -
            getNumberOfECCodeWords(detectionResult.barcodeECLevel)
        if (numberOfCodewords.isEmpty()) {
            if (calculatedNumberOfCodewords < 1 || calculatedNumberOfCodewords > PDF417Common.MAX_CODEWORDS_IN_BARCODE) {
                throw NotFoundException.notFoundInstance
            }
            barcodeMatrix01.setValue(calculatedNumberOfCodewords)
        } else if (numberOfCodewords[0] != calculatedNumberOfCodewords &&
            calculatedNumberOfCodewords >= 1 &&
            calculatedNumberOfCodewords <= PDF417Common.MAX_CODEWORDS_IN_BARCODE
        ) {
            barcodeMatrix01.setValue(calculatedNumberOfCodewords)
        }
    }

    /** Collects codewords, erasures and ambiguous cells, then decodes. */
    private fun createDecoderResult(detectionResult: DetectionResult): DecoderResult {
        val barcodeMatrix = createBarcodeMatrix(detectionResult)
        adjustCodewordCount(detectionResult, barcodeMatrix)
        val erasures = ArrayList<Int>()
        val codewords = IntArray(detectionResult.barcodeRowCount * detectionResult.barcodeColumnCount)
        val ambiguousIndexValuesList = ArrayList<IntArray>()
        val ambiguousIndexesList = ArrayList<Int>()
        for (row in 0 until detectionResult.barcodeRowCount) {
            for (column in 0 until detectionResult.barcodeColumnCount) {
                val values = barcodeMatrix[row][column + 1].value
                val codewordIndex = row * detectionResult.barcodeColumnCount + column
                if (values.isEmpty()) {
                    erasures.add(codewordIndex)
                } else if (values.size == 1) {
                    codewords[codewordIndex] = values[0]
                } else {
                    ambiguousIndexesList.add(codewordIndex)
                    ambiguousIndexValuesList.add(values)
                }
            }
        }
        return createDecoderResultFromAmbiguousValues(
            detectionResult.barcodeECLevel,
            codewords,
            PDF417Common.toIntArray(erasures),
            PDF417Common.toIntArray(ambiguousIndexesList),
            ambiguousIndexValuesList.toTypedArray(),
        )
    }

    /**
     * Tries the combinations of ambiguous codeword values (up to 100) until one passes error correction.
     * Throws ChecksumException when none does.
     */
    private fun createDecoderResultFromAmbiguousValues(
        ecLevel: Int,
        codewords: IntArray,
        erasureArray: IntArray,
        ambiguousIndexes: IntArray,
        ambiguousIndexValues: Array<IntArray>,
    ): DecoderResult {
        val ambiguousIndexCount = IntArray(ambiguousIndexes.size)
        var tries = 100
        while (tries-- > 0) {
            for (i in ambiguousIndexCount.indices) {
                codewords[ambiguousIndexes[i]] = ambiguousIndexValues[i][ambiguousIndexCount[i]]
            }
            try {
                return decodeCodewords(codewords, ecLevel, erasureArray)
            } catch (ignored: ChecksumException) {
            }
            if (ambiguousIndexCount.isEmpty()) {
                throw ChecksumException.checksumInstance
            }
            advanceAmbiguousIndexCount(ambiguousIndexCount, ambiguousIndexValues)
        }
        throw ChecksumException.checksumInstance
    }

    /** Steps [ambiguousIndexCount] to the next combination; throws ChecksumException when exhausted. */
    private fun advanceAmbiguousIndexCount(ambiguousIndexCount: IntArray, ambiguousIndexValues: Array<IntArray>) {
        for (i in ambiguousIndexCount.indices) {
            if (ambiguousIndexCount[i] < ambiguousIndexValues[i].size - 1) {
                ambiguousIndexCount[i]++
                return
            }
            ambiguousIndexCount[i] = 0
            if (i == ambiguousIndexCount.size - 1) {
                throw ChecksumException.checksumInstance
            }
        }
    }

    /** Votes every detected codeword into a rows x (columns + 2) matrix of [BarcodeValue]s. */
    private fun createBarcodeMatrix(detectionResult: DetectionResult): Array<Array<BarcodeValue>> {
        val barcodeMatrix = Array(detectionResult.barcodeRowCount) {
            Array(detectionResult.barcodeColumnCount + 2) { BarcodeValue() }
        }
        var column = 0
        for (detectionResultColumn in detectionResult.getDetectionResultColumns()) {
            if (detectionResultColumn != null) {
                for (codeword in detectionResultColumn.codewords) {
                    if (codeword != null) {
                        val rowNumber = codeword.rowNumber
                        if (rowNumber >= 0 && rowNumber < barcodeMatrix.size) {
                            barcodeMatrix[rowNumber][column].setValue(codeword.value)
                        }
                    }
                }
            }
            column++
        }
        return barcodeMatrix
    }

    /** True when [barcodeColumn] is within the indicator-inclusive column range. */
    private fun isValidBarcodeColumn(detectionResult: DetectionResult, barcodeColumn: Int): Boolean =
        barcodeColumn >= 0 && barcodeColumn <= detectionResult.barcodeColumnCount + 1

    /** Estimates where the codeword at ([barcodeColumn], [imageRow]) starts, from already detected neighbours. */
    private fun getStartColumn(
        detectionResult: DetectionResult,
        barcodeColumn: Int,
        imageRow: Int,
        leftToRight: Boolean,
    ): Int {
        val offset = if (leftToRight) 1 else -1
        var codeword: Codeword? = null
        if (isValidBarcodeColumn(detectionResult, barcodeColumn - offset)) {
            codeword = detectionResult.getDetectionResultColumn(barcodeColumn - offset)!!.getCodeword(imageRow)
        }
        if (codeword != null) {
            return if (leftToRight) codeword.endX else codeword.startX
        }
        codeword = detectionResult.getDetectionResultColumn(barcodeColumn)!!.getCodewordNearby(imageRow)
        if (codeword != null) {
            return if (leftToRight) codeword.startX else codeword.endX
        }
        if (isValidBarcodeColumn(detectionResult, barcodeColumn - offset)) {
            codeword = detectionResult.getDetectionResultColumn(barcodeColumn - offset)!!.getCodewordNearby(imageRow)
        }
        if (codeword != null) {
            return if (leftToRight) codeword.endX else codeword.startX
        }
        var skippedColumns = 0
        var column = barcodeColumn
        while (isValidBarcodeColumn(detectionResult, column - offset)) {
            column -= offset
            for (previousRowCodeword in detectionResult.getDetectionResultColumn(column)!!.codewords) {
                if (previousRowCodeword != null) {
                    return (if (leftToRight) previousRowCodeword.endX else previousRowCodeword.startX) +
                        offset * skippedColumns * (previousRowCodeword.endX - previousRowCodeword.startX)
                }
            }
            skippedColumns++
        }
        val boundingBox = detectionResult.boundingBox!!
        return if (leftToRight) boundingBox.minX else boundingBox.maxX
    }

    /** Reads one codeword at [imageRow] from [startColumn], or null when nothing valid is there. */
    private fun detectCodeword(
        image: BitMatrix,
        minColumn: Int,
        maxColumn: Int,
        leftToRight: Boolean,
        startColumn: Int,
        imageRow: Int,
        minCodewordWidth: Int,
        maxCodewordWidth: Int,
    ): Codeword? {
        var start = adjustCodewordStartColumn(image, minColumn, maxColumn, leftToRight, startColumn, imageRow)
        val moduleBitCount = getModuleBitCount(image, minColumn, maxColumn, leftToRight, start, imageRow)
            ?: return null
        val endColumn: Int
        val codewordBitCount = MathUtils.sum(moduleBitCount)
        if (leftToRight) {
            endColumn = start + codewordBitCount
        } else {
            moduleBitCount.reverse()
            endColumn = start
            start = endColumn - codewordBitCount
        }
        if (!checkCodewordSkew(codewordBitCount, minCodewordWidth, maxCodewordWidth)) {
            return null
        }
        val decodedValue = PDF417CodewordDecoder.getDecodedValue(moduleBitCount)
        val codeword = PDF417Common.getCodeword(decodedValue)
        if (codeword == -1) {
            return null
        }
        return Codeword(start, endColumn, getCodewordBucketNumber(decodedValue), codeword)
    }

    /** Counts the eight bar/space run lengths from [startColumn], or null when they do not fit. */
    private fun getModuleBitCount(
        image: BitMatrix,
        minColumn: Int,
        maxColumn: Int,
        leftToRight: Boolean,
        startColumn: Int,
        imageRow: Int,
    ): IntArray? {
        var imageColumn = startColumn
        val moduleBitCount = IntArray(8)
        var moduleNumber = 0
        val increment = if (leftToRight) 1 else -1
        var previousPixelValue = leftToRight
        while ((if (leftToRight) imageColumn < maxColumn else imageColumn >= minColumn) &&
            moduleNumber < moduleBitCount.size
        ) {
            if (image[imageColumn, imageRow] == previousPixelValue) {
                moduleBitCount[moduleNumber]++
                imageColumn += increment
            } else {
                moduleNumber++
                previousPixelValue = !previousPixelValue
            }
        }
        if (moduleNumber == moduleBitCount.size ||
            ((imageColumn == (if (leftToRight) maxColumn else minColumn)) && moduleNumber == moduleBitCount.size - 1)
        ) {
            return moduleBitCount
        }
        return null
    }

    /** Number of EC codewords for [barcodeECLevel]. */
    private fun getNumberOfECCodeWords(barcodeECLevel: Int): Int = 2 shl barcodeECLevel

    /** Moves the start column back over stray dark pixels, by at most [CODEWORD_SKEW_SIZE]. */
    private fun adjustCodewordStartColumn(
        image: BitMatrix,
        minColumn: Int,
        maxColumn: Int,
        leftToRight: Boolean,
        codewordStartColumn: Int,
        imageRow: Int,
    ): Int {
        var correctedStartColumn = codewordStartColumn
        var increment = if (leftToRight) -1 else 1
        var direction = leftToRight
        for (i in 0 until 2) {
            while ((if (direction) correctedStartColumn >= minColumn else correctedStartColumn < maxColumn) &&
                direction == image[correctedStartColumn, imageRow]
            ) {
                if (abs(codewordStartColumn - correctedStartColumn) > CODEWORD_SKEW_SIZE) {
                    return codewordStartColumn
                }
                correctedStartColumn += increment
            }
            increment = -increment
            direction = !direction
        }
        return correctedStartColumn
    }

    /** True when [codewordSize] is within the expected width range plus skew. */
    private fun checkCodewordSkew(codewordSize: Int, minCodewordWidth: Int, maxCodewordWidth: Int): Boolean =
        minCodewordWidth - CODEWORD_SKEW_SIZE <= codewordSize && codewordSize <= maxCodewordWidth + CODEWORD_SKEW_SIZE

    /** Error-corrects and parses [codewords]; throws FormatException or ChecksumException. */
    private fun decodeCodewords(codewords: IntArray, ecLevel: Int, erasures: IntArray): DecoderResult {
        if (codewords.isEmpty()) {
            throw FormatException.formatInstance
        }
        val numECCodewords = 1 shl (ecLevel + 1)
        val correctedErrorsCount = correctErrors(codewords, erasures, numECCodewords)
        verifyCodewordCount(codewords, numECCodewords)
        val decoderResult = DecodedBitStreamParser.decode(codewords, ecLevel.toString())
        decoderResult.errorsCorrected = correctedErrorsCount
        decoderResult.erasures = erasures.size
        return decoderResult
    }

    /** Corrects [codewords] in place and returns the error count; throws ChecksumException on failure. */
    private fun correctErrors(codewords: IntArray, erasures: IntArray?, numECCodewords: Int): Int {
        if (erasures != null &&
            erasures.size > numECCodewords / 2 + MAX_ERRORS ||
            numECCodewords < 0 ||
            numECCodewords > MAX_EC_CODEWORDS
        ) {
            throw ChecksumException.checksumInstance
        }
        return errorCorrection.decode(codewords, numECCodewords, erasures)
    }

    /** Checks the symbol length descriptor, repairing a zero value; throws FormatException when invalid. */
    private fun verifyCodewordCount(codewords: IntArray, numECCodewords: Int) {
        if (codewords.size < 4) {
            throw FormatException.formatInstance
        }
        val numberOfCodewords = codewords[0]
        if (numberOfCodewords > codewords.size) {
            throw FormatException.formatInstance
        }
        if (numberOfCodewords == 0) {
            if (numECCodewords < codewords.size) {
                codewords[0] = codewords.size - numECCodewords
            } else {
                throw FormatException.formatInstance
            }
        }
    }

    /** Converts a symbol's bit pattern into its eight bar/space widths. */
    private fun getBitCountForCodeword(codeword: Int): IntArray {
        val result = IntArray(8)
        var value = codeword
        var previousValue = 0
        var i = result.size - 1
        while (true) {
            if ((value and 0x1) != previousValue) {
                previousValue = value and 0x1
                i--
                if (i < 0) {
                    break
                }
            }
            result[i]++
            value = value shr 1
        }
        return result
    }

    /** Cluster bucket (0, 3 or 6) of a symbol bit pattern. */
    private fun getCodewordBucketNumber(codeword: Int): Int = getCodewordBucketNumber(getBitCountForCodeword(codeword))

    /** Cluster bucket (0, 3 or 6) from bar/space widths. */
    private fun getCodewordBucketNumber(moduleBitCount: IntArray): Int =
        (moduleBitCount[0] - moduleBitCount[2] + moduleBitCount[4] - moduleBitCount[6] + 9) % 9

    /** Renders [barcodeMatrix] as `value(confidence)` cells for debugging. */
    fun toString(barcodeMatrix: Array<Array<BarcodeValue>>): String {
        val sb = StringBuilder()
        for (row in barcodeMatrix.indices) {
            sb.append("Row ").append(DetectionResultColumn.pad(row, 2)).append(": ")
            for (column in barcodeMatrix[row].indices) {
                val barcodeValue = barcodeMatrix[row][column]
                val values = barcodeValue.value
                if (values.isEmpty()) {
                    sb.append("        ")
                } else {
                    sb.append(DetectionResultColumn.pad(values[0], 4)).append('(')
                        .append(DetectionResultColumn.pad(barcodeValue.getConfidence(values[0]) ?: 0, 2)).append(')')
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
