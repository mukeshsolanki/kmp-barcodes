/*
 * Copyright 2008 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder

import kotlin.math.abs

/**
 * Mask penalty rules (JISX0510:2004 / ISO 18004 6.8.2.1) and mask bit evaluation.
 */
internal object MaskUtil {

    /** Rule 1 weight. */
    private const val N1 = 3

    /** Rule 2 weight. */
    private const val N2 = 3

    /** Rule 3 weight. */
    private const val N3 = 40

    /** Rule 4 weight. */
    private const val N4 = 10

    /** Rule 1: penalises runs of 5+ same-coloured cells in rows and columns. */
    fun applyMaskPenaltyRule1(matrix: ByteMatrix): Int =
        applyMaskPenaltyRule1Internal(matrix, true) + applyMaskPenaltyRule1Internal(matrix, false)

    /** Rule 2: penalises every 2x2 block of one colour. */
    fun applyMaskPenaltyRule2(matrix: ByteMatrix): Int {
        var penalty = 0
        val array = matrix.array
        val width = matrix.width
        val height = matrix.height
        for (y in 0 until height - 1) {
            val arrayY = array[y]
            for (x in 0 until width - 1) {
                val value = arrayY[x]
                if (value == arrayY[x + 1] && value == array[y + 1][x] && value == array[y + 1][x + 1]) {
                    penalty++
                }
            }
        }
        return N2 * penalty
    }

    /** Rule 3: penalises 1:1:3:1:1 finder-like runs with 4 light cells on either side. */
    fun applyMaskPenaltyRule3(matrix: ByteMatrix): Int {
        var numPenalties = 0
        val array = matrix.array
        val width = matrix.width
        val height = matrix.height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val arrayY = array[y]
                if (x + 6 < width &&
                    arrayY[x].toInt() == 1 &&
                    arrayY[x + 1].toInt() == 0 &&
                    arrayY[x + 2].toInt() == 1 &&
                    arrayY[x + 3].toInt() == 1 &&
                    arrayY[x + 4].toInt() == 1 &&
                    arrayY[x + 5].toInt() == 0 &&
                    arrayY[x + 6].toInt() == 1 &&
                    (isWhiteHorizontal(arrayY, x - 4, x) || isWhiteHorizontal(arrayY, x + 7, x + 11))
                ) {
                    numPenalties++
                }
                if (y + 6 < height &&
                    array[y][x].toInt() == 1 &&
                    array[y + 1][x].toInt() == 0 &&
                    array[y + 2][x].toInt() == 1 &&
                    array[y + 3][x].toInt() == 1 &&
                    array[y + 4][x].toInt() == 1 &&
                    array[y + 5][x].toInt() == 0 &&
                    array[y + 6][x].toInt() == 1 &&
                    (isWhiteVertical(array, x, y - 4, y) || isWhiteVertical(array, x, y + 7, y + 11))
                ) {
                    numPenalties++
                }
            }
        }
        return numPenalties * N3
    }

    /** True when cells [from] until [to] of [rowArray] are in range and none is dark. */
    private fun isWhiteHorizontal(rowArray: ByteArray, from: Int, to: Int): Boolean {
        if (from < 0 || rowArray.size < to) {
            return false
        }
        for (i in from until to) {
            if (rowArray[i].toInt() == 1) {
                return false
            }
        }
        return true
    }

    /** True when rows [from] until [to] of column [col] are in range and none is dark. */
    private fun isWhiteVertical(array: Array<ByteArray>, col: Int, from: Int, to: Int): Boolean {
        if (from < 0 || array.size < to) {
            return false
        }
        for (i in from until to) {
            if (array[i][col].toInt() == 1) {
                return false
            }
        }
        return true
    }

    /** Rule 4: penalises 10 points per 5% the dark ratio strays from 50%. */
    fun applyMaskPenaltyRule4(matrix: ByteMatrix): Int {
        var numDarkCells = 0
        val array = matrix.array
        val width = matrix.width
        val height = matrix.height
        for (y in 0 until height) {
            val arrayY = array[y]
            for (x in 0 until width) {
                if (arrayY[x].toInt() == 1) {
                    numDarkCells++
                }
            }
        }
        val numTotalCells = matrix.height * matrix.width
        val fivePercentVariances = abs(numDarkCells * 2 - numTotalCells) * 10 / numTotalCells
        return fivePercentVariances * N4
    }

    /** Returns the mask bit of [maskPattern] at ([x], [y]); throws [IllegalArgumentException] for invalid patterns. */
    fun getDataMaskBit(maskPattern: Int, x: Int, y: Int): Boolean {
        val intermediate: Int = when (maskPattern) {
            0 -> (y + x) and 0x1
            1 -> y and 0x1
            2 -> x % 3
            3 -> (y + x) % 3
            4 -> ((y / 2) + (x / 3)) and 0x1
            5 -> {
                val temp = y * x
                (temp and 0x1) + (temp % 3)
            }
            6 -> {
                val temp = y * x
                ((temp and 0x1) + (temp % 3)) and 0x1
            }
            7 -> {
                val temp = y * x
                ((temp % 3) + ((y + x) and 0x1)) and 0x1
            }
            else -> throw IllegalArgumentException("Invalid mask pattern: $maskPattern")
        }
        return intermediate == 0
    }

    /** Rule 1 for one direction: rows when [isHorizontal], else columns. */
    private fun applyMaskPenaltyRule1Internal(matrix: ByteMatrix, isHorizontal: Boolean): Int {
        var penalty = 0
        val iLimit = if (isHorizontal) matrix.height else matrix.width
        val jLimit = if (isHorizontal) matrix.width else matrix.height
        val array = matrix.array
        for (i in 0 until iLimit) {
            var numSameBitCells = 0
            var prevBit = -1
            for (j in 0 until jLimit) {
                val bit = if (isHorizontal) array[i][j].toInt() else array[j][i].toInt()
                if (bit == prevBit) {
                    numSameBitCells++
                } else {
                    if (numSameBitCells >= 5) {
                        penalty += N1 + (numSameBitCells - 5)
                    }
                    numSameBitCells = 1
                    prevBit = bit
                }
            }
            if (numSameBitCells >= 5) {
                penalty += N1 + (numSameBitCells - 5)
            }
        }
        return penalty
    }
}
