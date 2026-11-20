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
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DetectorResult
import com.mukeshsolanki.barcodes.internal.zxing.common.GridSampler
import com.mukeshsolanki.barcodes.internal.zxing.common.PerspectiveTransform
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Version
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detects a QR Code in an image, even if rotated, skewed or partially obscured.
 */
internal class Detector(
    /** The binarized image to search. */
    private val image: BitMatrix,
) {
    /** Callback taken from the hints of the current [detect] call. */
    private var resultPointCallback: ResultPointCallback? = null

    /** Detects a QR Code without hints. */
    fun detect(): DetectorResult = detect(null)

    /** Detects a QR Code; throws [NotFoundException] or [FormatException] when none can be sampled. */
    fun detect(hints: Map<DecodeHintType, Any?>?): DetectorResult {
        resultPointCallback = hints?.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK) as ResultPointCallback?
        val finder = FinderPatternFinder(image, resultPointCallback)
        val info = finder.find(hints)
        return processFinderPatternInfo(info)
    }

    /** Locates the alignment pattern, then samples the grid defined by [info]. */
    fun processFinderPatternInfo(info: FinderPatternInfo): DetectorResult {
        val topLeft = info.topLeft
        val topRight = info.topRight
        val bottomLeft = info.bottomLeft
        val moduleSize = calculateModuleSize(topLeft, topRight, bottomLeft)
        if (moduleSize < 1.0f) {
            throw NotFoundException.notFoundInstance
        }
        val dimension = computeDimension(topLeft, topRight, bottomLeft, moduleSize)
        val provisionalVersion = Version.getProvisionalVersionForDimension(dimension)
        val modulesBetweenFPCenters = provisionalVersion.dimensionForVersion - 7
        var alignmentPattern: AlignmentPattern? = null
        if (provisionalVersion.alignmentPatternCenters.isNotEmpty()) {
            val bottomRightX = topRight.x - topLeft.x + bottomLeft.x
            val bottomRightY = topRight.y - topLeft.y + bottomLeft.y
            val correctionToTopLeft = 1.0f - 3.0f / modulesBetweenFPCenters
            val estAlignmentX = (topLeft.x + correctionToTopLeft * (bottomRightX - topLeft.x)).toInt()
            val estAlignmentY = (topLeft.y + correctionToTopLeft * (bottomRightY - topLeft.y)).toInt()
            alignmentPattern = findAlignmentWithGrowingRadius(moduleSize, estAlignmentX, estAlignmentY)
        }
        val transform = createTransform(topLeft, topRight, bottomLeft, alignmentPattern, dimension)
        val bits = sampleGrid(image, transform, dimension)
        val points: Array<ResultPoint> = if (alignmentPattern == null) {
            arrayOf(bottomLeft, topLeft, topRight)
        } else {
            arrayOf(bottomLeft, topLeft, topRight, alignmentPattern)
        }
        return DetectorResult(bits, points)
    }

    /** Searches for the alignment pattern with allowance factors 4, 8 and 16; null when never found. */
    private fun findAlignmentWithGrowingRadius(moduleSize: Float, estAlignmentX: Int, estAlignmentY: Int): AlignmentPattern? {
        var i = 4
        while (i <= 16) {
            try {
                return findAlignmentInRegion(moduleSize, estAlignmentX, estAlignmentY, i.toFloat())
            } catch (re: NotFoundException) {
                i = i shl 1
            }
        }
        return null
    }

    /** Averages the module size estimated along the top-left to top-right and top-left to bottom-left axes. */
    fun calculateModuleSize(topLeft: ResultPoint, topRight: ResultPoint, bottomLeft: ResultPoint): Float =
        (calculateModuleSizeOneWay(topLeft, topRight) + calculateModuleSizeOneWay(topLeft, bottomLeft)) / 2.0f

    /** Estimates module size from the finder widths measured along the axis between two patterns. */
    private fun calculateModuleSizeOneWay(pattern: ResultPoint, otherPattern: ResultPoint): Float {
        val moduleSizeEst1 = sizeOfBlackWhiteBlackRunBothWays(
            pattern.x.toInt(),
            pattern.y.toInt(),
            otherPattern.x.toInt(),
            otherPattern.y.toInt(),
        )
        val moduleSizeEst2 = sizeOfBlackWhiteBlackRunBothWays(
            otherPattern.x.toInt(),
            otherPattern.y.toInt(),
            pattern.x.toInt(),
            pattern.y.toInt(),
        )
        if (moduleSizeEst1.isNaN()) {
            return moduleSizeEst2 / 7.0f
        }
        if (moduleSizeEst2.isNaN()) {
            return moduleSizeEst1 / 7.0f
        }
        return (moduleSizeEst1 + moduleSizeEst2) / 14.0f
    }

    /** Measures a finder's black-white-black width towards (toX, toY) and the opposite way, clipped to the image. */
    private fun sizeOfBlackWhiteBlackRunBothWays(fromX: Int, fromY: Int, toX: Int, toY: Int): Float {
        var result = sizeOfBlackWhiteBlackRun(fromX, fromY, toX, toY)
        var scale = 1.0f
        var otherToX = fromX - (toX - fromX)
        if (otherToX < 0) {
            scale = fromX / (fromX - otherToX).toFloat()
            otherToX = 0
        } else if (otherToX >= image.width) {
            scale = (image.width - 1 - fromX) / (otherToX - fromX).toFloat()
            otherToX = image.width - 1
        }
        var otherToY = (fromY - (toY - fromY) * scale).toInt()
        scale = 1.0f
        if (otherToY < 0) {
            scale = fromY / (fromY - otherToY).toFloat()
            otherToY = 0
        } else if (otherToY >= image.height) {
            scale = (image.height - 1 - fromY) / (otherToY - fromY).toFloat()
            otherToY = image.height - 1
        }
        otherToX = (fromX + (otherToX - fromX) * scale).toInt()
        result += sizeOfBlackWhiteBlackRun(fromX, fromY, otherToX, otherToY)
        return result - 1.0f
    }

    /** Traces a Bresenham line from a black start until black-white-black ends; returns its length or NaN. */
    private fun sizeOfBlackWhiteBlackRun(startFromX: Int, startFromY: Int, startToX: Int, startToY: Int): Float {
        val steep = abs(startToY - startFromY) > abs(startToX - startFromX)
        val fromX = if (steep) startFromY else startFromX
        val fromY = if (steep) startFromX else startFromY
        val toX = if (steep) startToY else startToX
        val toY = if (steep) startToX else startToY
        val dx = abs(toX - fromX)
        val dy = abs(toY - fromY)
        var error = -dx / 2
        val xstep = if (fromX < toX) 1 else -1
        val ystep = if (fromY < toY) 1 else -1
        var state = 0
        val xLimit = toX + xstep
        var x = fromX
        var y = fromY
        while (x != xLimit) {
            val realX = if (steep) y else x
            val realY = if (steep) x else y
            if ((state == 1) == image.get(realX, realY)) {
                if (state == 2) {
                    return MathUtils.distance(x, y, fromX, fromY)
                }
                state++
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
        if (state == 2) {
            return MathUtils.distance(toX + xstep, toY, fromX, fromY)
        }
        return Float.NaN
    }

    /** Looks for the alignment pattern within allowanceFactor modules of the estimate; throws [NotFoundException]. */
    fun findAlignmentInRegion(
        overallEstModuleSize: Float,
        estAlignmentX: Int,
        estAlignmentY: Int,
        allowanceFactor: Float,
    ): AlignmentPattern {
        val allowance = (allowanceFactor * overallEstModuleSize).toInt()
        val alignmentAreaLeftX = max(0, estAlignmentX - allowance)
        val alignmentAreaRightX = min(image.width - 1, estAlignmentX + allowance)
        if (alignmentAreaRightX - alignmentAreaLeftX < overallEstModuleSize * 3) {
            throw NotFoundException.notFoundInstance
        }
        val alignmentAreaTopY = max(0, estAlignmentY - allowance)
        val alignmentAreaBottomY = min(image.height - 1, estAlignmentY + allowance)
        if (alignmentAreaBottomY - alignmentAreaTopY < overallEstModuleSize * 3) {
            throw NotFoundException.notFoundInstance
        }
        val alignmentFinder = AlignmentPatternFinder(
            image,
            alignmentAreaLeftX,
            alignmentAreaTopY,
            alignmentAreaRightX - alignmentAreaLeftX,
            alignmentAreaBottomY - alignmentAreaTopY,
            overallEstModuleSize,
            resultPointCallback,
        )
        return alignmentFinder.find()
    }

    /** Grid geometry helpers. */
    private companion object {
        /** Builds the module-space to image-space transform from the three finders and optional alignment pattern. */
        fun createTransform(
            topLeft: ResultPoint,
            topRight: ResultPoint,
            bottomLeft: ResultPoint,
            alignmentPattern: ResultPoint?,
            dimension: Int,
        ): PerspectiveTransform {
            val dimMinusThree = dimension - 3.5f
            val bottomRightX: Float
            val bottomRightY: Float
            val sourceBottomRightX: Float
            val sourceBottomRightY: Float
            if (alignmentPattern != null) {
                bottomRightX = alignmentPattern.x
                bottomRightY = alignmentPattern.y
                sourceBottomRightX = dimMinusThree - 3.0f
                sourceBottomRightY = sourceBottomRightX
            } else {
                bottomRightX = (topRight.x - topLeft.x) + bottomLeft.x
                bottomRightY = (topRight.y - topLeft.y) + bottomLeft.y
                sourceBottomRightX = dimMinusThree
                sourceBottomRightY = dimMinusThree
            }
            return PerspectiveTransform.quadrilateralToQuadrilateral(
                3.5f,
                3.5f,
                dimMinusThree,
                3.5f,
                sourceBottomRightX,
                sourceBottomRightY,
                3.5f,
                dimMinusThree,
                topLeft.x,
                topLeft.y,
                topRight.x,
                topRight.y,
                bottomRightX,
                bottomRightY,
                bottomLeft.x,
                bottomLeft.y,
            )
        }

        /** Samples a [dimension] x [dimension] module grid from [image] through [transform]. */
        fun sampleGrid(image: BitMatrix, transform: PerspectiveTransform, dimension: Int): BitMatrix =
            GridSampler.instance.sampleGrid(image, dimension, dimension, transform)

        /** Computes the symbol side in modules from finder distances, snapped to 1 mod 4; throws [NotFoundException]. */
        fun computeDimension(
            topLeft: ResultPoint,
            topRight: ResultPoint,
            bottomLeft: ResultPoint,
            moduleSize: Float,
        ): Int {
            val tltrCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, topRight) / moduleSize)
            val tlblCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, bottomLeft) / moduleSize)
            val dimension = ((tltrCentersDimension + tlblCentersDimension) / 2) + 7
            return when (dimension and 0x03) {
                0 -> dimension + 1
                2 -> dimension - 1
                3 -> throw NotFoundException.notFoundInstance
                else -> dimension
            }
        }
    }
}
