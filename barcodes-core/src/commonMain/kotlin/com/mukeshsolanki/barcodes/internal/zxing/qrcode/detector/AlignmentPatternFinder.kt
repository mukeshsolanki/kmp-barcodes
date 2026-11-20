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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.detector

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import kotlin.math.abs

/**
 * Finds the bottom-right alignment pattern within a region of the image. Not reentrant.
 */
internal class AlignmentPatternFinder(
    /** Image to search. */
    private val image: BitMatrix,
    /** Left column of the search region. */
    private val startX: Int,
    /** Top row of the search region. */
    private val startY: Int,
    /** Width of the search region. */
    private val width: Int,
    /** Height of the search region. */
    private val height: Int,
    /** Estimated module size so far. */
    private val moduleSize: Float,
    /** Optional callback notified of candidate points. */
    private val resultPointCallback: ResultPointCallback?,
) {
    /** Candidates seen once so far. */
    private val possibleCenters = ArrayList<AlignmentPattern>(5)

    /** Reusable state counts for vertical cross-checks. */
    private val crossCheckStateCount = IntArray(3)

    /** Scans outward from the region's middle row; throws [NotFoundException] if nothing is seen. */
    fun find(): AlignmentPattern {
        val startX = this.startX
        val height = this.height
        val maxJ = startX + width
        val middleI = startY + (height / 2)
        val stateCount = IntArray(3)
        for (iGen in 0 until height) {
            val i = middleI + if ((iGen and 0x01) == 0) (iGen + 1) / 2 else -((iGen + 1) / 2)
            stateCount[0] = 0
            stateCount[1] = 0
            stateCount[2] = 0
            var j = startX
            while (j < maxJ && !image.get(j, i)) {
                j++
            }
            var currentState = 0
            while (j < maxJ) {
                if (image.get(j, i)) {
                    if (currentState == 1) {
                        stateCount[1]++
                    } else {
                        if (currentState == 2) {
                            if (foundPatternCross(stateCount)) {
                                val confirmed = handlePossibleCenter(stateCount, i, j)
                                if (confirmed != null) {
                                    return confirmed
                                }
                            }
                            stateCount[0] = stateCount[2]
                            stateCount[1] = 1
                            stateCount[2] = 0
                            currentState = 1
                        } else {
                            stateCount[++currentState]++
                        }
                    }
                } else {
                    if (currentState == 1) {
                        currentState++
                    }
                    stateCount[currentState]++
                }
                j++
            }
            if (foundPatternCross(stateCount)) {
                val confirmed = handlePossibleCenter(stateCount, i, maxJ)
                if (confirmed != null) {
                    return confirmed
                }
            }
        }
        if (possibleCenters.isNotEmpty()) {
            return possibleCenters[0]
        }
        throw NotFoundException.notFoundInstance
    }

    /** True when the black/white/black counts are close enough to 1:1:1 at [moduleSize]. */
    private fun foundPatternCross(stateCount: IntArray): Boolean {
        val moduleSize = this.moduleSize
        val maxVariance = moduleSize / 2.0f
        for (i in 0 until 3) {
            if (abs(moduleSize - stateCount[i]) >= maxVariance) {
                return false
            }
        }
        return true
    }

    /** Scans vertically through [centerJ] from [startI]; returns the vertical centre or NaN. */
    private fun crossCheckVertical(startI: Int, centerJ: Int, maxCount: Int, originalStateCountTotal: Int): Float {
        val image = this.image
        val maxI = image.height
        val stateCount = crossCheckStateCount
        stateCount[0] = 0
        stateCount[1] = 0
        stateCount[2] = 0
        var i = startI
        while (i >= 0 && image.get(centerJ, i) && stateCount[1] <= maxCount) {
            stateCount[1]++
            i--
        }
        if (i < 0 || stateCount[1] > maxCount) {
            return Float.NaN
        }
        while (i >= 0 && !image.get(centerJ, i) && stateCount[0] <= maxCount) {
            stateCount[0]++
            i--
        }
        if (stateCount[0] > maxCount) {
            return Float.NaN
        }
        i = startI + 1
        while (i < maxI && image.get(centerJ, i) && stateCount[1] <= maxCount) {
            stateCount[1]++
            i++
        }
        if (i == maxI || stateCount[1] > maxCount) {
            return Float.NaN
        }
        while (i < maxI && !image.get(centerJ, i) && stateCount[2] <= maxCount) {
            stateCount[2]++
            i++
        }
        if (stateCount[2] > maxCount) {
            return Float.NaN
        }
        val stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2]
        if (5 * abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
            return Float.NaN
        }
        return if (foundPatternCross(stateCount)) centerFromEnd(stateCount, i) else Float.NaN
    }

    /** Cross-checks a horizontal hit; returns a pattern once it has been seen twice, else null. */
    private fun handlePossibleCenter(stateCount: IntArray, i: Int, j: Int): AlignmentPattern? {
        val stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2]
        val centerJ = centerFromEnd(stateCount, j)
        val centerI = crossCheckVertical(i, centerJ.toInt(), 2 * stateCount[1], stateCountTotal)
        if (!centerI.isNaN()) {
            val estimatedModuleSize = (stateCount[0] + stateCount[1] + stateCount[2]) / 3.0f
            for (center in possibleCenters) {
                if (center.aboutEquals(estimatedModuleSize, centerI, centerJ)) {
                    return center.combineEstimate(centerI, centerJ, estimatedModuleSize)
                }
            }
            val point = AlignmentPattern(centerJ, centerI, estimatedModuleSize)
            possibleCenters.add(point)
            resultPointCallback?.foundPossibleResultPoint(point)
        }
        return null
    }

    /** Pure helpers. */
    private companion object {
        /** Returns the centre of a black/white/black run ending at [end]. */
        fun centerFromEnd(stateCount: IntArray, end: Int): Float = (end - stateCount[2]) - stateCount[1] / 2.0f
    }
}
