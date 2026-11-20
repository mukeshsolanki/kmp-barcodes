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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.detector

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DetectorResult
import com.mukeshsolanki.barcodes.internal.zxing.common.GridSampler
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.WhiteRectangleDetector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Locates a Data Matrix code in an image, even when rotated, skewed or partly obscured. */
internal class Detector(
    /** Binarized image to search. */
    private val image: BitMatrix,
) {

    /** Finds the white rectangle surrounding the candidate symbol. */
    private val rectangleDetector = WhiteRectangleDetector(image)

    /** Detects a Data Matrix code and samples its module grid, or throws [NotFoundException]. */
    fun detect(): DetectorResult {
        val cornerPoints = rectangleDetector.detect()

        var points = detectSolid1(cornerPoints)
        points = detectSolid2(points)
        points[3] = correctTopRight(points) ?: throw NotFoundException.notFoundInstance
        points = shiftToModuleCenter(points)

        val topLeft = points[0]
        val bottomLeft = points[1]
        val bottomRight = points[2]
        val topRight = points[3]

        var dimensionTop = transitionsBetween(topLeft, topRight) + 1
        var dimensionRight = transitionsBetween(bottomRight, topRight) + 1
        if ((dimensionTop and 0x01) == 1) {
            dimensionTop += 1
        }
        if ((dimensionRight and 0x01) == 1) {
            dimensionRight += 1
        }

        if (4 * dimensionTop < 6 * dimensionRight && 4 * dimensionRight < 6 * dimensionTop) {
            dimensionTop = max(dimensionTop, dimensionRight)
            dimensionRight = dimensionTop
        }

        val bits = sampleGrid(image, topLeft, bottomLeft, bottomRight, topRight, dimensionTop, dimensionRight)

        return DetectorResult(bits, arrayOf(topLeft, bottomLeft, bottomRight, topRight))
    }

    /** Orders the corners so that the side with the fewest transitions (a solid edge) is B-C. */
    private fun detectSolid1(cornerPoints: Array<ResultPoint>): Array<ResultPoint> {
        val pointA = cornerPoints[0]
        val pointB = cornerPoints[1]
        val pointC = cornerPoints[3]
        val pointD = cornerPoints[2]

        val trAB = transitionsBetween(pointA, pointB)
        val trBC = transitionsBetween(pointB, pointC)
        val trCD = transitionsBetween(pointC, pointD)
        val trDA = transitionsBetween(pointD, pointA)

        var min = trAB
        var points = arrayOf(pointD, pointA, pointB, pointC)
        if (min > trBC) {
            min = trBC
            points = arrayOf(pointA, pointB, pointC, pointD)
        }
        if (min > trCD) {
            min = trCD
            points = arrayOf(pointB, pointC, pointD, pointA)
        }
        if (min > trDA) {
            points = arrayOf(pointC, pointD, pointA, pointB)
        }

        return points
    }

    /** Finds the second solid side adjacent to the first and reorders the corners to A-B-C. */
    private fun detectSolid2(points: Array<ResultPoint>): Array<ResultPoint> {
        val pointA = points[0]
        val pointB = points[1]
        val pointC = points[2]
        val pointD = points[3]

        val tr = transitionsBetween(pointA, pointD)
        val pointBs = shiftPoint(pointB, pointC, (tr + 1) * 4)
        val pointCs = shiftPoint(pointC, pointB, (tr + 1) * 4)
        val trBA = transitionsBetween(pointBs, pointA)
        val trCD = transitionsBetween(pointCs, pointD)

        if (trBA < trCD) {
            points[0] = pointA
            points[1] = pointB
            points[2] = pointC
            points[3] = pointD
        } else {
            points[0] = pointB
            points[1] = pointC
            points[2] = pointD
            points[3] = pointA
        }

        return points
    }

    /** Estimates the top-right corner of the white top-right module, or null when no candidate is valid. */
    private fun correctTopRight(points: Array<ResultPoint>): ResultPoint? {
        val pointA = points[0]
        val pointB = points[1]
        val pointC = points[2]
        val pointD = points[3]

        var trTop = transitionsBetween(pointA, pointD)
        var trRight = transitionsBetween(pointB, pointD)
        val pointAs = shiftPoint(pointA, pointB, (trRight + 1) * 4)
        val pointCs = shiftPoint(pointC, pointB, (trTop + 1) * 4)

        trTop = transitionsBetween(pointAs, pointD)
        trRight = transitionsBetween(pointCs, pointD)

        val candidate1 = ResultPoint(
            pointD.x + (pointC.x - pointB.x) / (trTop + 1),
            pointD.y + (pointC.y - pointB.y) / (trTop + 1),
        )
        val candidate2 = ResultPoint(
            pointD.x + (pointA.x - pointB.x) / (trRight + 1),
            pointD.y + (pointA.y - pointB.y) / (trRight + 1),
        )

        if (!isValid(candidate1)) {
            return if (isValid(candidate2)) candidate2 else null
        }
        if (!isValid(candidate2)) {
            return candidate1
        }

        val sumc1 = transitionsBetween(pointAs, candidate1) + transitionsBetween(pointCs, candidate1)
        val sumc2 = transitionsBetween(pointAs, candidate2) + transitionsBetween(pointCs, candidate2)

        return if (sumc1 > sumc2) candidate1 else candidate2
    }

    /** Moves the edge points onto the centres of the corner modules. */
    private fun shiftToModuleCenter(points: Array<ResultPoint>): Array<ResultPoint> {
        var pointA = points[0]
        var pointB = points[1]
        var pointC = points[2]
        var pointD = points[3]

        var dimH = transitionsBetween(pointA, pointD) + 1
        var dimV = transitionsBetween(pointC, pointD) + 1

        var pointAs = shiftPoint(pointA, pointB, dimV * 4)
        var pointCs = shiftPoint(pointC, pointB, dimH * 4)

        dimH = transitionsBetween(pointAs, pointD) + 1
        dimV = transitionsBetween(pointCs, pointD) + 1
        if ((dimH and 0x01) == 1) {
            dimH += 1
        }
        if ((dimV and 0x01) == 1) {
            dimV += 1
        }

        val centerX = (pointA.x + pointB.x + pointC.x + pointD.x) / 4
        val centerY = (pointA.y + pointB.y + pointC.y + pointD.y) / 4
        pointA = moveAway(pointA, centerX, centerY)
        pointB = moveAway(pointB, centerX, centerY)
        pointC = moveAway(pointC, centerX, centerY)
        pointD = moveAway(pointD, centerX, centerY)

        pointAs = shiftPoint(pointA, pointB, dimV * 4)
        pointAs = shiftPoint(pointAs, pointD, dimH * 4)
        var pointBs = shiftPoint(pointB, pointA, dimV * 4)
        pointBs = shiftPoint(pointBs, pointC, dimH * 4)
        pointCs = shiftPoint(pointC, pointD, dimV * 4)
        pointCs = shiftPoint(pointCs, pointB, dimH * 4)
        var pointDs = shiftPoint(pointD, pointC, dimV * 4)
        pointDs = shiftPoint(pointDs, pointA, dimH * 4)

        return arrayOf(pointAs, pointBs, pointCs, pointDs)
    }

    /** True when [p] lies inside the image (y strictly below the top row, as in zxing). */
    private fun isValid(p: ResultPoint): Boolean =
        p.x >= 0 && p.x <= image.width - 1 && p.y > 0 && p.y <= image.height - 1

    /** Counts black/white transitions between two points with a Bresenham walk. */
    private fun transitionsBetween(from: ResultPoint, to: ResultPoint): Int {
        var fromX = from.x.toInt()
        var fromY = from.y.toInt()
        var toX = to.x.toInt()
        var toY = min(image.height - 1, to.y.toInt())

        val steep = abs(toY - fromY) > abs(toX - fromX)
        if (steep) {
            var temp = fromX
            fromX = fromY
            fromY = temp
            temp = toX
            toX = toY
            toY = temp
        }

        val dx = abs(toX - fromX)
        val dy = abs(toY - fromY)
        var error = -dx / 2
        val ystep = if (fromY < toY) 1 else -1
        val xstep = if (fromX < toX) 1 else -1
        var transitions = 0
        var inBlack = image.get(if (steep) fromY else fromX, if (steep) fromX else fromY)
        var x = fromX
        var y = fromY
        while (x != toX) {
            val isBlack = image.get(if (steep) y else x, if (steep) x else y)
            if (isBlack != inBlack) {
                transitions++
                inBlack = isBlack
            }
            error += dy
            if (error > 0) {
                if (y == toY) {
                    break
                }
                y += ystep
                error -= dx
            }
            x += xstep
        }
        return transitions
    }

    /** Point geometry and grid sampling helpers. */
    private companion object {

        /** Moves [point] 1/(div+1) of the way towards [to]. */
        private fun shiftPoint(point: ResultPoint, to: ResultPoint, div: Int): ResultPoint {
            val x = (to.x - point.x) / (div + 1)
            val y = (to.y - point.y) / (div + 1)
            return ResultPoint(point.x + x, point.y + y)
        }

        /** Moves [point] one pixel further from the centre ([fromX], [fromY]) on each axis. */
        private fun moveAway(point: ResultPoint, fromX: Float, fromY: Float): ResultPoint {
            val x = if (point.x < fromX) point.x - 1 else point.x + 1
            val y = if (point.y < fromY) point.y - 1 else point.y + 1
            return ResultPoint(x, y)
        }

        /** Samples a [dimensionX] x [dimensionY] grid between the four located corners. */
        private fun sampleGrid(
            image: BitMatrix,
            topLeft: ResultPoint,
            bottomLeft: ResultPoint,
            bottomRight: ResultPoint,
            topRight: ResultPoint,
            dimensionX: Int,
            dimensionY: Int,
        ): BitMatrix = GridSampler.instance.sampleGrid(
            image,
            dimensionX,
            dimensionY,
            0.5f,
            0.5f,
            dimensionX - 0.5f,
            0.5f,
            dimensionX - 0.5f,
            dimensionY - 0.5f,
            0.5f,
            dimensionY - 0.5f,
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRight.x,
            bottomRight.y,
            bottomLeft.x,
            bottomLeft.y,
        )
    }
}
