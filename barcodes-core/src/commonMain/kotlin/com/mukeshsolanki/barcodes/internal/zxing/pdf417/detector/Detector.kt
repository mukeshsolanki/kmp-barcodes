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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.detector

import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import kotlin.math.abs
import kotlin.math.max

/**
 * Detects a PDF417 code in an image, even if rotated, skewed or partially obscured.
 */
internal object Detector {

    /** Result indexes for the start pattern corners. */
    private val INDEXES_START_PATTERN: IntArray = intArrayOf(0, 4, 1, 5)

    /** Result indexes for the stop pattern corners. */
    private val INDEXES_STOP_PATTERN: IntArray = intArrayOf(6, 2, 7, 3)

    /** Maximum average variance for a guard pattern match. */
    private const val MAX_AVG_VARIANCE: Float = 0.42f

    /** Maximum variance of a single bar for a guard pattern match. */
    private const val MAX_INDIVIDUAL_VARIANCE: Float = 0.8f

    /** Minimum stop pattern height as a fraction of the start pattern height. */
    private const val MAX_STOP_PATTERN_HEIGHT_VARIANCE: Float = 0.5f

    /** Start pattern bar/space widths: 11111111 0 1 0 1 0 1 000. */
    private val START_PATTERN: IntArray = intArrayOf(8, 1, 1, 1, 1, 1, 1, 3)

    /** Stop pattern bar/space widths: 1111111 0 1 000 1 0 1 00 1. */
    private val STOP_PATTERN: IntArray = intArrayOf(7, 1, 1, 3, 1, 1, 1, 2, 1)

    /** Pixels a pattern start may shift left to include leading black pixels. */
    private const val MAX_PIXEL_DRIFT = 3

    /** Maximum pattern drift between consecutive rows of the same symbol. */
    private const val MAX_PATTERN_DRIFT = 5

    /** Rows without the pattern tolerated before a symbol is considered to end. */
    private const val SKIPPED_ROW_COUNT_MAX = 25

    /** Row step while scanning for patterns. */
    private const val ROW_STEP = 5

    /** Minimum symbol height in pixels. */
    private const val BARCODE_MIN_HEIGHT = 10

    /** Rotations tried, in order. */
    private val ROTATIONS: IntArray = intArrayOf(0, 180, 270, 90)

    /**
     * Detects PDF417 codes in [image], trying 0, 180, 270 and 90 degree rotations; at most one unless [multiple].
     * Throws NotFoundException when the image cannot be binarized.
     */
    fun detect(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?, multiple: Boolean): PDF417DetectorResult {
        val originalMatrix = image.blackMatrix
        for (rotation in ROTATIONS) {
            val bitMatrix = applyRotation(originalMatrix, rotation)
            val barcodeCoordinates = detect(multiple, bitMatrix)
            if (barcodeCoordinates.isNotEmpty()) {
                return PDF417DetectorResult(bitMatrix, barcodeCoordinates, rotation)
            }
        }
        return PDF417DetectorResult(originalMatrix, ArrayList(), 0)
    }

    /** Returns [matrix] rotated by [rotation] degrees (a copy unless no rotation is needed). */
    private fun applyRotation(matrix: BitMatrix, rotation: Int): BitMatrix {
        if (rotation % 360 == 0) {
            return matrix
        }
        val newMatrix = matrix.clone()
        newMatrix.rotate(rotation)
        return newMatrix
    }

    /** Finds symbol vertices in [bitMatrix] without rotating it. */
    private fun detect(multiple: Boolean, bitMatrix: BitMatrix): List<Array<ResultPoint?>> {
        val barcodeCoordinates = ArrayList<Array<ResultPoint?>>()
        var row = 0
        var column = 0
        var foundBarcodeInRow = false
        while (row < bitMatrix.height) {
            val vertices = findVertices(bitMatrix, row, column)
            if (vertices[0] == null && vertices[3] == null) {
                if (!foundBarcodeInRow) {
                    break
                }
                foundBarcodeInRow = false
                column = 0
                row = lowestRow(row, barcodeCoordinates) + ROW_STEP
                continue
            }
            foundBarcodeInRow = true
            barcodeCoordinates.add(vertices)
            if (!multiple) {
                break
            }
            val next = vertices[2] ?: vertices[4]!!
            column = next.x.toInt()
            row = next.y.toInt()
        }
        return barcodeCoordinates
    }

    /** Returns the lowest bottom row of the symbols found so far, at least [row]. */
    private fun lowestRow(row: Int, barcodeCoordinates: List<Array<ResultPoint?>>): Int {
        var result = row
        for (barcodeCoordinate in barcodeCoordinates) {
            val bottomLeft = barcodeCoordinate[1]
            if (bottomLeft != null) {
                result = max(result.toFloat(), bottomLeft.y).toInt()
            }
            val bottomRight = barcodeCoordinate[3]
            if (bottomRight != null) {
                result = max(result, bottomRight.y.toInt())
            }
        }
        return result
    }

    /**
     * Locates the symbol and codeword-area vertices using the start and stop patterns:
     * 0/1/2/3 = top-left, bottom-left, top-right, bottom-right of the symbol; 4..7 the same for the codeword area.
     */
    private fun findVertices(matrix: BitMatrix, startRow: Int, startColumn: Int): Array<ResultPoint?> {
        val height = matrix.height
        val width = matrix.width
        val result = arrayOfNulls<ResultPoint>(8)
        var row = startRow
        var column = startColumn
        var minHeight = BARCODE_MIN_HEIGHT
        copyToResult(
            result,
            findRowsWithPattern(matrix, height, width, row, column, minHeight, START_PATTERN),
            INDEXES_START_PATTERN,
        )
        val startTop = result[4]
        if (startTop != null) {
            column = startTop.x.toInt()
            row = startTop.y.toInt()
            val startBottom = result[5]
            if (startBottom != null) {
                val endRow = startBottom.y.toInt()
                val startPatternHeight = endRow - row
                minHeight = max(startPatternHeight * MAX_STOP_PATTERN_HEIGHT_VARIANCE, BARCODE_MIN_HEIGHT.toFloat()).toInt()
            }
        }
        copyToResult(
            result,
            findRowsWithPattern(matrix, height, width, row, column, minHeight, STOP_PATTERN),
            INDEXES_STOP_PATTERN,
        )
        return result
    }

    /** Copies [tmpResult] into [result] at [destinationIndexes]. */
    private fun copyToResult(result: Array<ResultPoint?>, tmpResult: Array<ResultPoint?>, destinationIndexes: IntArray) {
        for (i in destinationIndexes.indices) {
            result[destinationIndexes[i]] = tmpResult[i]
        }
    }

    /** Finds the top and bottom rows containing [pattern]; all null when shorter than [minHeight]. */
    private fun findRowsWithPattern(
        matrix: BitMatrix,
        height: Int,
        width: Int,
        firstRow: Int,
        startColumn: Int,
        minHeight: Int,
        pattern: IntArray,
    ): Array<ResultPoint?> {
        val result = arrayOfNulls<ResultPoint>(4)
        var found = false
        val counters = IntArray(pattern.size)
        var startRow = firstRow
        while (startRow < height) {
            var loc = findGuardPattern(matrix, startColumn, startRow, width, pattern, counters)
            if (loc != null) {
                while (startRow > 0) {
                    val previousRowLoc = findGuardPattern(matrix, startColumn, --startRow, width, pattern, counters)
                    if (previousRowLoc != null) {
                        loc = previousRowLoc
                    } else {
                        startRow++
                        break
                    }
                }
                result[0] = ResultPoint(loc!![0].toFloat(), startRow.toFloat())
                result[1] = ResultPoint(loc[1].toFloat(), startRow.toFloat())
                found = true
                break
            }
            startRow += ROW_STEP
        }
        var stopRow = startRow + 1
        if (found) {
            var skippedRowCount = 0
            var previousRowLoc = intArrayOf(result[0]!!.x.toInt(), result[1]!!.x.toInt())
            while (stopRow < height) {
                val loc = findGuardPattern(matrix, previousRowLoc[0], stopRow, width, pattern, counters)
                if (loc != null &&
                    abs(previousRowLoc[0] - loc[0]) < MAX_PATTERN_DRIFT &&
                    abs(previousRowLoc[1] - loc[1]) < MAX_PATTERN_DRIFT
                ) {
                    previousRowLoc = loc
                    skippedRowCount = 0
                } else if (skippedRowCount > SKIPPED_ROW_COUNT_MAX) {
                    break
                } else {
                    skippedRowCount++
                }
                stopRow++
            }
            stopRow -= skippedRowCount + 1
            result[2] = ResultPoint(previousRowLoc[0].toFloat(), stopRow.toFloat())
            result[3] = ResultPoint(previousRowLoc[1].toFloat(), stopRow.toFloat())
        }
        if (stopRow - startRow < minHeight) {
            result.fill(null)
        }
        return result
    }

    /**
     * Searches [row] from [column] over [width] pixels for [pattern], reusing [counters].
     * Returns the start/end x of the guard pattern, or null.
     */
    private fun findGuardPattern(
        matrix: BitMatrix,
        column: Int,
        row: Int,
        width: Int,
        pattern: IntArray,
        counters: IntArray,
    ): IntArray? {
        counters.fill(0)
        var patternStart = column
        var pixelDrift = 0
        while (matrix[patternStart, row] && patternStart > 0 && pixelDrift++ < MAX_PIXEL_DRIFT) {
            patternStart--
        }
        var x = patternStart
        var counterPosition = 0
        val patternLength = pattern.size
        var isWhite = false
        while (x < width) {
            val pixel = matrix[x, row]
            if (pixel != isWhite) {
                counters[counterPosition]++
            } else {
                if (counterPosition == patternLength - 1) {
                    if (patternMatchVariance(counters, pattern) < MAX_AVG_VARIANCE) {
                        return intArrayOf(patternStart, x)
                    }
                    patternStart += counters[0] + counters[1]
                    counters.copyInto(counters, 0, 2, 2 + counterPosition - 1)
                    counters[counterPosition - 1] = 0
                    counters[counterPosition] = 0
                    counterPosition--
                } else {
                    counterPosition++
                }
                counters[counterPosition] = 1
                isWhite = !isWhite
            }
            x++
        }
        if (counterPosition == patternLength - 1 && patternMatchVariance(counters, pattern) < MAX_AVG_VARIANCE) {
            return intArrayOf(patternStart, x - 1)
        }
        return null
    }

    /**
     * Ratio of the total variance between [counters] and the expected [pattern] proportions to the total width;
     * positive infinity when the run is too small or a single bar deviates too much.
     */
    private fun patternMatchVariance(counters: IntArray, pattern: IntArray): Float {
        val numCounters = counters.size
        var total = 0
        var patternLength = 0
        for (i in 0 until numCounters) {
            total += counters[i]
            patternLength += pattern[i]
        }
        if (total < patternLength) {
            return Float.POSITIVE_INFINITY
        }
        val unitBarWidth = total.toFloat() / patternLength
        val maxIndividualVariance = MAX_INDIVIDUAL_VARIANCE * unitBarWidth
        var totalVariance = 0.0f
        for (x in 0 until numCounters) {
            val counter = counters[x]
            val scaledPattern = pattern[x] * unitBarWidth
            val variance = if (counter > scaledPattern) counter - scaledPattern else scaledPattern - counter
            if (variance > maxIndividualVariance) {
                return Float.POSITIVE_INFINITY
            }
            totalVariance += variance
        }
        return totalVariance / total
    }
}
