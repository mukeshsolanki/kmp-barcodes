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

import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import kotlin.math.abs

/**
 * Finds the three finder patterns of a QR Code in an image. Not reentrant.
 */
internal class FinderPatternFinder(
    /** Image to search. */
    private val image: BitMatrix,
    /** Optional callback notified of candidate points. */
    private val resultPointCallback: ResultPointCallback? = null,
) {
    /** Candidate finder pattern centres seen so far. */
    private val possibleCenters = ArrayList<FinderPattern>()

    /** True once rows were skipped after two confirmed centres. */
    private var hasSkipped = false

    /** Reusable state counts for cross-checks. */
    private val crossCheckStateCount = IntArray(5)

    /** Scans the image for three finder patterns; throws [NotFoundException] if not found. */
    fun find(hints: Map<DecodeHintType, Any?>?): FinderPatternInfo {
        val tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER)
        val maxI = image.height
        val maxJ = image.width
        var iSkip = (3 * maxI) / (4 * MAX_MODULES)
        if (iSkip < MIN_SKIP || tryHarder) {
            iSkip = MIN_SKIP
        }
        var done = false
        val stateCount = IntArray(5)
        var i = iSkip - 1
        while (i < maxI && !done) {
            doClearCounts(stateCount)
            var currentState = 0
            var j = 0
            while (j < maxJ) {
                if (image.get(j, i)) {
                    if ((currentState and 1) == 1) {
                        currentState++
                    }
                    stateCount[currentState]++
                } else {
                    if ((currentState and 1) == 0) {
                        if (currentState == 4) {
                            if (foundPatternCross(stateCount)) {
                                val confirmed = handlePossibleCenter(stateCount, i, j)
                                if (confirmed) {
                                    iSkip = 2
                                    if (hasSkipped) {
                                        done = haveMultiplyConfirmedCenters()
                                    } else {
                                        val rowSkip = findRowSkip()
                                        if (rowSkip > stateCount[2]) {
                                            i += rowSkip - stateCount[2] - iSkip
                                            j = maxJ - 1
                                        }
                                    }
                                    currentState = 0
                                    doClearCounts(stateCount)
                                } else {
                                    doShiftCounts2(stateCount)
                                    currentState = 3
                                }
                            } else {
                                doShiftCounts2(stateCount)
                                currentState = 3
                            }
                        } else {
                            stateCount[++currentState]++
                        }
                    } else {
                        stateCount[currentState]++
                    }
                }
                j++
            }
            if (foundPatternCross(stateCount)) {
                val confirmed = handlePossibleCenter(stateCount, i, maxJ)
                if (confirmed) {
                    iSkip = stateCount[0]
                    if (hasSkipped) {
                        done = haveMultiplyConfirmedCenters()
                    }
                }
            }
            i += iSkip
        }
        val patternInfo = selectBestPatterns()
        ResultPoint.orderBestPatterns(patternInfo)
        return FinderPatternInfo(patternInfo)
    }

    /** Clears and returns the shared cross-check state counts. */
    private fun getCrossCheckStateCount(): IntArray {
        doClearCounts(crossCheckStateCount)
        return crossCheckStateCount
    }

    /** Checks the 1:1:3:1:1 ratio along the up-left/down-right diagonal through (centerJ, centerI). */
    private fun crossCheckDiagonal(centerI: Int, centerJ: Int): Boolean {
        val stateCount = getCrossCheckStateCount()
        var i = 0
        while (centerI >= i && centerJ >= i && image.get(centerJ - i, centerI - i)) {
            stateCount[2]++
            i++
        }
        if (stateCount[2] == 0) {
            return false
        }
        while (centerI >= i && centerJ >= i && !image.get(centerJ - i, centerI - i)) {
            stateCount[1]++
            i++
        }
        if (stateCount[1] == 0) {
            return false
        }
        while (centerI >= i && centerJ >= i && image.get(centerJ - i, centerI - i)) {
            stateCount[0]++
            i++
        }
        if (stateCount[0] == 0) {
            return false
        }
        val maxI = image.height
        val maxJ = image.width
        i = 1
        while (centerI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, centerI + i)) {
            stateCount[2]++
            i++
        }
        while (centerI + i < maxI && centerJ + i < maxJ && !image.get(centerJ + i, centerI + i)) {
            stateCount[3]++
            i++
        }
        if (stateCount[3] == 0) {
            return false
        }
        while (centerI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, centerI + i)) {
            stateCount[4]++
            i++
        }
        if (stateCount[4] == 0) {
            return false
        }
        return foundPatternDiagonal(stateCount)
    }

    /** Scans vertically through [centerJ] from [startI]; returns the vertical centre or NaN. */
    private fun crossCheckVertical(startI: Int, centerJ: Int, maxCount: Int, originalStateCountTotal: Int): Float {
        val image = this.image
        val maxI = image.height
        val stateCount = getCrossCheckStateCount()
        var i = startI
        while (i >= 0 && image.get(centerJ, i)) {
            stateCount[2]++
            i--
        }
        if (i < 0) {
            return Float.NaN
        }
        while (i >= 0 && !image.get(centerJ, i) && stateCount[1] <= maxCount) {
            stateCount[1]++
            i--
        }
        if (i < 0 || stateCount[1] > maxCount) {
            return Float.NaN
        }
        while (i >= 0 && image.get(centerJ, i) && stateCount[0] <= maxCount) {
            stateCount[0]++
            i--
        }
        if (stateCount[0] > maxCount) {
            return Float.NaN
        }
        i = startI + 1
        while (i < maxI && image.get(centerJ, i)) {
            stateCount[2]++
            i++
        }
        if (i == maxI) {
            return Float.NaN
        }
        while (i < maxI && !image.get(centerJ, i) && stateCount[3] < maxCount) {
            stateCount[3]++
            i++
        }
        if (i == maxI || stateCount[3] >= maxCount) {
            return Float.NaN
        }
        while (i < maxI && image.get(centerJ, i) && stateCount[4] < maxCount) {
            stateCount[4]++
            i++
        }
        if (stateCount[4] >= maxCount) {
            return Float.NaN
        }
        val stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4]
        if (5 * abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
            return Float.NaN
        }
        return if (foundPatternCross(stateCount)) centerFromEnd(stateCount, i) else Float.NaN
    }

    /** Scans horizontally through [centerI] from [startJ]; returns the horizontal centre or NaN. */
    private fun crossCheckHorizontal(startJ: Int, centerI: Int, maxCount: Int, originalStateCountTotal: Int): Float {
        val image = this.image
        val maxJ = image.width
        val stateCount = getCrossCheckStateCount()
        var j = startJ
        while (j >= 0 && image.get(j, centerI)) {
            stateCount[2]++
            j--
        }
        if (j < 0) {
            return Float.NaN
        }
        while (j >= 0 && !image.get(j, centerI) && stateCount[1] <= maxCount) {
            stateCount[1]++
            j--
        }
        if (j < 0 || stateCount[1] > maxCount) {
            return Float.NaN
        }
        while (j >= 0 && image.get(j, centerI) && stateCount[0] <= maxCount) {
            stateCount[0]++
            j--
        }
        if (stateCount[0] > maxCount) {
            return Float.NaN
        }
        j = startJ + 1
        while (j < maxJ && image.get(j, centerI)) {
            stateCount[2]++
            j++
        }
        if (j == maxJ) {
            return Float.NaN
        }
        while (j < maxJ && !image.get(j, centerI) && stateCount[3] < maxCount) {
            stateCount[3]++
            j++
        }
        if (j == maxJ || stateCount[3] >= maxCount) {
            return Float.NaN
        }
        while (j < maxJ && image.get(j, centerI) && stateCount[4] < maxCount) {
            stateCount[4]++
            j++
        }
        if (stateCount[4] >= maxCount) {
            return Float.NaN
        }
        val stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4]
        if (5 * abs(stateCountTotal - originalStateCountTotal) >= originalStateCountTotal) {
            return Float.NaN
        }
        return if (foundPatternCross(stateCount)) centerFromEnd(stateCount, j) else Float.NaN
    }

    /** Cross-checks a horizontal hit vertically, horizontally and diagonally, then records it; true if accepted. */
    private fun handlePossibleCenter(stateCount: IntArray, i: Int, j: Int): Boolean {
        val stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4]
        var centerJ = centerFromEnd(stateCount, j)
        val centerI = crossCheckVertical(i, centerJ.toInt(), stateCount[2], stateCountTotal)
        if (!centerI.isNaN()) {
            centerJ = crossCheckHorizontal(centerJ.toInt(), centerI.toInt(), stateCount[2], stateCountTotal)
            if (!centerJ.isNaN() && crossCheckDiagonal(centerI.toInt(), centerJ.toInt())) {
                recordCenter(stateCountTotal / 7.0f, centerI, centerJ)
                return true
            }
        }
        return false
    }

    /** Merges the centre (centerJ, centerI) into a matching candidate, or adds it as a new one. */
    private fun recordCenter(estimatedModuleSize: Float, centerI: Float, centerJ: Float) {
        for (index in possibleCenters.indices) {
            val center = possibleCenters[index]
            if (center.aboutEquals(estimatedModuleSize, centerI, centerJ)) {
                possibleCenters[index] = center.combineEstimate(centerI, centerJ, estimatedModuleSize)
                return
            }
        }
        val point = FinderPattern(centerJ, centerI, estimatedModuleSize)
        possibleCenters.add(point)
        resultPointCallback?.foundPossibleResultPoint(point)
    }

    /** Returns how many rows can be skipped once two confirmed centres are known, else 0. */
    private fun findRowSkip(): Int {
        val max = possibleCenters.size
        if (max <= 1) {
            return 0
        }
        var firstConfirmedCenter: ResultPoint? = null
        for (center in possibleCenters) {
            if (center.count >= CENTER_QUORUM) {
                if (firstConfirmedCenter == null) {
                    firstConfirmedCenter = center
                } else {
                    hasSkipped = true
                    return (abs(firstConfirmedCenter.x - center.x) - abs(firstConfirmedCenter.y - center.y)).toInt() / 2
                }
            }
        }
        return 0
    }

    /** True when at least 3 centres are confirmed and their module sizes deviate by at most 5%. */
    private fun haveMultiplyConfirmedCenters(): Boolean {
        var confirmedCount = 0
        var totalModuleSize = 0.0f
        val max = possibleCenters.size
        for (pattern in possibleCenters) {
            if (pattern.count >= CENTER_QUORUM) {
                confirmedCount++
                totalModuleSize += pattern.estimatedModuleSize
            }
        }
        if (confirmedCount < 3) {
            return false
        }
        val average = totalModuleSize / max
        var totalDeviation = 0.0f
        for (pattern in possibleCenters) {
            totalDeviation += abs(pattern.estimatedModuleSize - average)
        }
        return totalDeviation <= 0.05f * totalModuleSize
    }

    /** Picks the 3 confirmed candidates of similar size closest to an isosceles right triangle; throws [NotFoundException] otherwise. */
    private fun selectBestPatterns(): Array<FinderPattern> {
        val startSize = possibleCenters.size
        if (startSize < 3) {
            throw NotFoundException.notFoundInstance
        }
        possibleCenters.removeAll { it.count < CENTER_QUORUM }
        possibleCenters.sortWith(moduleComparator)
        var distortion = Double.MAX_VALUE
        val bestPatterns = arrayOfNulls<FinderPattern>(3)
        for (i in 0 until possibleCenters.size - 2) {
            val fpi = possibleCenters[i]
            val minModuleSize = fpi.estimatedModuleSize
            for (j in i + 1 until possibleCenters.size - 1) {
                val fpj = possibleCenters[j]
                val squares0 = squaredDistance(fpi, fpj)
                for (k in j + 1 until possibleCenters.size) {
                    val fpk = possibleCenters[k]
                    val maxModuleSize = fpk.estimatedModuleSize
                    if (maxModuleSize > minModuleSize * 1.4f) {
                        continue
                    }
                    val d = triangleDistortion(squares0, squaredDistance(fpj, fpk), squaredDistance(fpi, fpk))
                    if (d < distortion) {
                        distortion = d
                        bestPatterns[0] = fpi
                        bestPatterns[1] = fpj
                        bestPatterns[2] = fpk
                    }
                }
            }
        }
        if (distortion == Double.MAX_VALUE) {
            throw NotFoundException.notFoundInstance
        }
        return bestPatterns.requireNoNulls()
    }

    /** Constants and pure helpers. */
    companion object {
        /** Times a centre must be seen to count as confirmed. */
        private const val CENTER_QUORUM = 2

        /** Minimum row skip: 1 pixel/module times 3 modules/center. */
        const val MIN_SKIP = 3

        /** Largest module count supported for row skipping (version 20). */
        const val MAX_MODULES = 97

        /** Orders patterns by estimated module size. */
        private val moduleComparator = Comparator<FinderPattern> { center1, center2 ->
            center1.estimatedModuleSize.compareTo(center2.estimatedModuleSize)
        }

        /** Returns the centre of a 1:1:3:1:1 run ending at [end]. */
        private fun centerFromEnd(stateCount: IntArray, end: Int): Float =
            (end - stateCount[4] - stateCount[3]) - stateCount[2] / 2.0f

        /** True when the counts are within 50% of the 1:1:3:1:1 finder ratios. */
        fun foundPatternCross(stateCount: IntArray): Boolean {
            var totalModuleSize = 0
            for (i in 0 until 5) {
                val count = stateCount[i]
                if (count == 0) {
                    return false
                }
                totalModuleSize += count
            }
            if (totalModuleSize < 7) {
                return false
            }
            val moduleSize = totalModuleSize / 7.0f
            val maxVariance = moduleSize / 2.0f
            return abs(moduleSize - stateCount[0]) < maxVariance &&
                abs(moduleSize - stateCount[1]) < maxVariance &&
                abs(3.0f * moduleSize - stateCount[2]) < 3 * maxVariance &&
                abs(moduleSize - stateCount[3]) < maxVariance &&
                abs(moduleSize - stateCount[4]) < maxVariance
        }

        /** True when the counts are within 75% of the 1:1:3:1:1 finder ratios (diagonal check). */
        fun foundPatternDiagonal(stateCount: IntArray): Boolean {
            var totalModuleSize = 0
            for (i in 0 until 5) {
                val count = stateCount[i]
                if (count == 0) {
                    return false
                }
                totalModuleSize += count
            }
            if (totalModuleSize < 7) {
                return false
            }
            val moduleSize = totalModuleSize / 7.0f
            val maxVariance = moduleSize / 1.333f
            return abs(moduleSize - stateCount[0]) < maxVariance &&
                abs(moduleSize - stateCount[1]) < maxVariance &&
                abs(3.0f * moduleSize - stateCount[2]) < 3 * maxVariance &&
                abs(moduleSize - stateCount[3]) < maxVariance &&
                abs(moduleSize - stateCount[4]) < maxVariance
        }

        /** Zeroes [counts]. */
        fun doClearCounts(counts: IntArray) {
            counts.fill(0)
        }

        /** Shifts the counts back by two states after a failed match. */
        fun doShiftCounts2(stateCount: IntArray) {
            stateCount[0] = stateCount[2]
            stateCount[1] = stateCount[3]
            stateCount[2] = stateCount[4]
            stateCount[3] = 1
            stateCount[4] = 0
        }

        /** Returns the squared distance between [a] and [b] in double precision. */
        private fun squaredDistance(a: FinderPattern, b: FinderPattern): Double {
            val x = (a.x - b.x).toDouble()
            val y = (a.y - b.y).toDouble()
            return x * x + y * y
        }

        /** Sorts the three squared sides ascending (zxing's inlined order) and returns |c - 2b| + |c - 2a|. */
        private fun triangleDistortion(squares0: Double, squares1: Double, squares2: Double): Double {
            var a = squares0
            var b = squares1
            var c = squares2
            if (a < b) {
                if (b > c) {
                    if (a < c) {
                        val temp = b
                        b = c
                        c = temp
                    } else {
                        val temp = a
                        a = c
                        c = b
                        b = temp
                    }
                }
            } else {
                if (b < c) {
                    if (a < c) {
                        val temp = a
                        a = b
                        b = temp
                    } else {
                        val temp = a
                        a = b
                        b = c
                        c = temp
                    }
                } else {
                    val temp = a
                    a = c
                    c = temp
                }
            }
            return abs(c - 2 * b) + abs(c - 2 * a)
        }
    }
}
