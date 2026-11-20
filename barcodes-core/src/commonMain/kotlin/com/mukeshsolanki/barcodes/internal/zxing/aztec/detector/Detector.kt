/*
 * Copyright 2010 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.aztec.detector

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.aztec.AztecDetectorResult
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.GridSampler
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils
import com.mukeshsolanki.barcodes.internal.zxing.common.detector.WhiteRectangleDetector
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonDecoder
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonException
import kotlin.math.floor

/**
 * Detects an Aztec Code in an image, even if it is rotated, skewed or partially obscured.
 */
internal class Detector(
    /** The binarized image to search. */
    private val image: BitMatrix,
) {

    /** Whether the detected symbol is compact. */
    private var compact = false

    /** Number of data layers of the detected symbol. */
    private var nbLayers = 0

    /** Number of data codewords of the detected symbol. */
    private var nbDataBlocks = 0

    /** Number of rings found in the bull's eye. */
    private var nbCenterLayers = 0

    /** Index of the bull's eye corner carrying three orientation marks. */
    private var shift = 0

    /** Detects an Aztec Code; when [isMirror] is true the image is treated as a mirror image. */
    fun detect(isMirror: Boolean = false): AztecDetectorResult {
        val pCenter = getMatrixCenter()
        val bullsEyeCorners = getBullsEyeCorners(pCenter)
        if (isMirror) {
            val temp = bullsEyeCorners[0]
            bullsEyeCorners[0] = bullsEyeCorners[2]
            bullsEyeCorners[2] = temp
        }
        val errorsCorrected = extractParameters(bullsEyeCorners)
        val bits = sampleGrid(
            image,
            bullsEyeCorners[shift % 4],
            bullsEyeCorners[(shift + 1) % 4],
            bullsEyeCorners[(shift + 2) % 4],
            bullsEyeCorners[(shift + 3) % 4],
        )
        val corners = getMatrixCornerPoints(bullsEyeCorners)
        return AztecDetectorResult(bits, corners, compact, nbDataBlocks, nbLayers, errorsCorrected)
    }

    /** Reads layer and data block counts from the ring around the bull's eye; returns errors corrected. */
    private fun extractParameters(bullsEyeCorners: Array<ResultPoint>): Int {
        if (!isValid(bullsEyeCorners[0]) || !isValid(bullsEyeCorners[1]) ||
            !isValid(bullsEyeCorners[2]) || !isValid(bullsEyeCorners[3])
        ) {
            throw NotFoundException.notFoundInstance
        }
        val length = 2 * nbCenterLayers
        val sides = intArrayOf(
            sampleLine(bullsEyeCorners[0], bullsEyeCorners[1], length),
            sampleLine(bullsEyeCorners[1], bullsEyeCorners[2], length),
            sampleLine(bullsEyeCorners[2], bullsEyeCorners[3], length),
            sampleLine(bullsEyeCorners[3], bullsEyeCorners[0], length),
        )
        shift = getRotation(sides, length)

        var parameterData = 0L
        for (i in 0 until 4) {
            val side = sides[(shift + i) % 4]
            if (compact) {
                parameterData = parameterData shl 7
                parameterData += (side shr 1) and 0x7F
            } else {
                parameterData = parameterData shl 10
                parameterData += ((side shr 2) and (0x1f shl 5)) + ((side shr 1) and 0x1F)
            }
        }

        val correctedParam = getCorrectedParameterData(parameterData, compact)
        val correctedData = correctedParam.data
        if (compact) {
            nbLayers = (correctedData shr 6) + 1
            nbDataBlocks = (correctedData and 0x3F) + 1
        } else {
            nbLayers = (correctedData shr 11) + 1
            nbDataBlocks = (correctedData and 0x7FF) + 1
        }
        return correctedParam.errorsCorrected
    }

    /** Finds the corners of the bull's eye around [pCenter] as [topRight, bottomRight, bottomLeft, topLeft]. */
    private fun getBullsEyeCorners(pCenter: Point): Array<ResultPoint> {
        var pina = pCenter
        var pinb = pCenter
        var pinc = pCenter
        var pind = pCenter
        var color = true

        nbCenterLayers = 1
        while (nbCenterLayers < 9) {
            val pouta = getFirstDifferent(pina, color, 1, -1)
            val poutb = getFirstDifferent(pinb, color, 1, 1)
            val poutc = getFirstDifferent(pinc, color, -1, 1)
            val poutd = getFirstDifferent(pind, color, -1, -1)
            if (nbCenterLayers > 2) {
                val q = distance(poutd, pouta) * nbCenterLayers / (distance(pind, pina) * (nbCenterLayers + 2))
                if (q < 0.75 || q > 1.25 || !isWhiteOrBlackRectangle(pouta, poutb, poutc, poutd)) {
                    break
                }
            }
            pina = pouta
            pinb = poutb
            pinc = poutc
            pind = poutd
            color = !color
            nbCenterLayers++
        }

        if (nbCenterLayers != 5 && nbCenterLayers != 7) {
            throw NotFoundException.notFoundInstance
        }
        compact = nbCenterLayers == 5

        val pinax = ResultPoint(pina.x + 0.5f, pina.y - 0.5f)
        val pinbx = ResultPoint(pinb.x + 0.5f, pinb.y + 0.5f)
        val pincx = ResultPoint(pinc.x - 0.5f, pinc.y + 0.5f)
        val pindx = ResultPoint(pind.x - 0.5f, pind.y - 0.5f)
        return expandSquare(arrayOf(pinax, pinbx, pincx, pindx), 2 * nbCenterLayers - 3, 2 * nbCenterLayers)
    }

    /** Finds a candidate center point of an Aztec code in the image. */
    private fun getMatrixCenter(): Point {
        var corners = try {
            WhiteRectangleDetector(image).detect()
        } catch (e: NotFoundException) {
            fallbackCorners(image.width / 2, image.height / 2)
        }
        var cx = centerX(corners)
        var cy = centerY(corners)

        corners = try {
            WhiteRectangleDetector(image, 15, cx, cy).detect()
        } catch (e: NotFoundException) {
            fallbackCorners(cx, cy)
        }
        cx = centerX(corners)
        cy = centerY(corners)
        return Point(cx, cy)
    }

    /** Expands a white square from ([cx], [cy]) when no white rectangle was detected. */
    private fun fallbackCorners(cx: Int, cy: Int): Array<ResultPoint> = arrayOf(
        getFirstDifferent(Point(cx + 7, cy - 7), false, 1, -1).toResultPoint(),
        getFirstDifferent(Point(cx + 7, cy + 7), false, 1, 1).toResultPoint(),
        getFirstDifferent(Point(cx - 7, cy + 7), false, -1, 1).toResultPoint(),
        getFirstDifferent(Point(cx - 7, cy - 7), false, -1, -1).toResultPoint(),
    )

    /** Returns the rounded mean x of corners A, B, C, D, summed in zxing's A+D+B+C order. */
    private fun centerX(p: Array<ResultPoint>): Int = MathUtils.round((p[0].x + p[3].x + p[1].x + p[2].x) / 4.0f)

    /** Returns the rounded mean y of corners A, B, C, D, summed in zxing's A+D+B+C order. */
    private fun centerY(p: Array<ResultPoint>): Int = MathUtils.round((p[0].y + p[3].y + p[1].y + p[2].y) / 4.0f)

    /** Returns the corners of the whole symbol from the bull's eye corners. */
    private fun getMatrixCornerPoints(bullsEyeCorners: Array<ResultPoint>): Array<ResultPoint> =
        expandSquare(bullsEyeCorners, 2 * nbCenterLayers, getDimension())

    /** Samples the symbol grid; the points are the centers of the squares just outside the bull's eye. */
    private fun sampleGrid(
        image: BitMatrix,
        topLeft: ResultPoint,
        topRight: ResultPoint,
        bottomRight: ResultPoint,
        bottomLeft: ResultPoint,
    ): BitMatrix {
        val sampler = GridSampler.instance
        val dimension = getDimension()
        val low = dimension / 2.0f - nbCenterLayers
        val high = dimension / 2.0f + nbCenterLayers
        return sampler.sampleGrid(
            image,
            dimension,
            dimension,
            low, low,
            high, low,
            high, high,
            low, high,
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y,
        )
    }

    /** Samples [size] bits from [p1] (inclusive) towards [p2] (exclusive); the first bit is the high bit. */
    private fun sampleLine(p1: ResultPoint, p2: ResultPoint, size: Int): Int {
        var result = 0
        val d = distance(p1, p2)
        val moduleSize = d / size
        val px = p1.x
        val py = p1.y
        val dx = moduleSize * (p2.x - p1.x) / d
        val dy = moduleSize * (p2.y - p1.y) / d
        for (i in 0 until size) {
            if (image.get(MathUtils.round(px + i * dx), MathUtils.round(py + i * dy))) {
                result = result or (1 shl (size - i - 1))
            }
        }
        return result
    }

    /** Returns true if the border of the rectangle is made of white points only or black points only. */
    private fun isWhiteOrBlackRectangle(p1: Point, p2: Point, p3: Point, p4: Point): Boolean {
        val corr = 3
        val q1 = Point(maxOf(0, p1.x - corr), minOf(image.height - 1, p1.y + corr))
        val q2 = Point(maxOf(0, p2.x - corr), maxOf(0, p2.y - corr))
        val q3 = Point(minOf(image.width - 1, p3.x + corr), maxOf(0, minOf(image.height - 1, p3.y - corr)))
        val q4 = Point(minOf(image.width - 1, p4.x + corr), minOf(image.height - 1, p4.y + corr))

        val cInit = getColor(q4, q1)
        if (cInit == 0) {
            return false
        }
        if (getColor(q1, q2) != cInit) {
            return false
        }
        if (getColor(q2, q3) != cInit) {
            return false
        }
        return getColor(q3, q4) == cInit
    }

    /** Returns 1 if the segment is more than 90% black, -1 if more than 90% white, 0 otherwise. */
    private fun getColor(p1: Point, p2: Point): Int {
        val d = distance(p1, p2)
        if (d == 0.0f) {
            return 0
        }
        val dx = (p2.x - p1.x) / d
        val dy = (p2.y - p1.y) / d
        var error = 0
        var px = p1.x.toFloat()
        var py = p1.y.toFloat()
        val colorModel = image.get(p1.x, p1.y)
        val iMax = floor(d).toInt()
        for (i in 0 until iMax) {
            if (image.get(MathUtils.round(px), MathUtils.round(py)) != colorModel) {
                error++
            }
            px += dx
            py += dy
        }
        val errRatio = error / d
        if (errRatio > 0.1f && errRatio < 0.9f) {
            return 0
        }
        return if ((errRatio <= 0.1f) == colorModel) 1 else -1
    }

    /** Returns the last point of [color] reached from [init] stepping by ([dx], [dy]). */
    private fun getFirstDifferent(init: Point, color: Boolean, dx: Int, dy: Int): Point {
        var x = init.x + dx
        var y = init.y + dy
        while (isValid(x, y) && image.get(x, y) == color) {
            x += dx
            y += dy
        }
        x -= dx
        y -= dy
        while (isValid(x, y) && image.get(x, y) == color) {
            x += dx
        }
        x -= dx
        while (isValid(x, y) && image.get(x, y) == color) {
            y += dy
        }
        y -= dy
        return Point(x, y)
    }

    /** Returns true if ([x], [y]) lies inside the image. */
    private fun isValid(x: Int, y: Int): Boolean = x >= 0 && x < image.width && y >= 0 && y < image.height

    /** Returns true if [point], rounded, lies inside the image. */
    private fun isValid(point: ResultPoint): Boolean {
        val x = MathUtils.round(point.x)
        val y = MathUtils.round(point.y)
        return isValid(x, y)
    }

    /** Returns the side length of the symbol in modules. */
    private fun getDimension(): Int {
        if (compact) {
            return 4 * nbLayers + 11
        }
        return 4 * nbLayers + 2 * ((2 * nbLayers + 6) / 15) + 15
    }

    /** An integer image coordinate. */
    internal class Point(
        /** X coordinate. */
        val x: Int,
        /** Y coordinate. */
        val y: Int,
    ) {
        /** Converts this point to a [ResultPoint]. */
        fun toResultPoint(): ResultPoint = ResultPoint(x.toFloat(), y.toFloat())
    }

    /** RS-corrected parameter data and the number of errors corrected. */
    private class CorrectedParameter(
        /** The data portion of the parameter words. */
        val data: Int,
        /** Number of errors corrected. */
        val errorsCorrected: Int,
    )

    /** Shared tables and static helpers. */
    companion object {
        /** Expected orientation bits for each of the four rotations. */
        private val EXPECTED_CORNER_BITS = intArrayOf(
            0xee0,
            0x1dc,
            0x83b,
            0x707,
        )

        /** Returns which bull's eye corner holds the three orientation marks. */
        private fun getRotation(sides: IntArray, length: Int): Int {
            var cornerBits = 0
            for (side in sides) {
                val t = ((side shr (length - 2)) shl 1) + (side and 1)
                cornerBits = (cornerBits shl 3) + t
            }
            cornerBits = ((cornerBits and 1) shl 11) + (cornerBits shr 1)
            for (shift in 0 until 4) {
                if ((cornerBits xor EXPECTED_CORNER_BITS[shift]).countOneBits() <= 2) {
                    return shift
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /** RS-corrects the 28- or 40-bit [parameterData]; throws [NotFoundException] on too many errors. */
        private fun getCorrectedParameterData(parameterData: Long, compact: Boolean): CorrectedParameter {
            val numCodewords = if (compact) 7 else 10
            val numDataCodewords = if (compact) 2 else 4
            val numECCodewords = numCodewords - numDataCodewords
            val parameterWords = IntArray(numCodewords)
            var data = parameterData
            for (i in numCodewords - 1 downTo 0) {
                parameterWords[i] = data.toInt() and 0xF
                data = data shr 4
            }
            val errorsCorrected = try {
                ReedSolomonDecoder(GenericGF.AZTEC_PARAM).decodeWithECCount(parameterWords, numECCodewords)
            } catch (ignored: ReedSolomonException) {
                throw NotFoundException.notFoundInstance
            }
            var result = 0
            for (i in 0 until numDataCodewords) {
                result = (result shl 4) + parameterWords[i]
            }
            return CorrectedParameter(result, errorsCorrected)
        }

        /** Pushes the square of [cornerPoints] out equally in all directions from [oldSide] to [newSide]. */
        private fun expandSquare(cornerPoints: Array<ResultPoint>, oldSide: Int, newSide: Int): Array<ResultPoint> {
            val ratio = newSide / (2.0f * oldSide)
            var dx = cornerPoints[0].x - cornerPoints[2].x
            var dy = cornerPoints[0].y - cornerPoints[2].y
            var centerx = (cornerPoints[0].x + cornerPoints[2].x) / 2.0f
            var centery = (cornerPoints[0].y + cornerPoints[2].y) / 2.0f

            val result0 = ResultPoint(centerx + ratio * dx, centery + ratio * dy)
            val result2 = ResultPoint(centerx - ratio * dx, centery - ratio * dy)

            dx = cornerPoints[1].x - cornerPoints[3].x
            dy = cornerPoints[1].y - cornerPoints[3].y
            centerx = (cornerPoints[1].x + cornerPoints[3].x) / 2.0f
            centery = (cornerPoints[1].y + cornerPoints[3].y) / 2.0f
            val result1 = ResultPoint(centerx + ratio * dx, centery + ratio * dy)
            val result3 = ResultPoint(centerx - ratio * dx, centery - ratio * dy)

            return arrayOf(result0, result1, result2, result3)
        }

        /** Returns the distance between two integer points. */
        private fun distance(a: Point, b: Point): Float = MathUtils.distance(a.x, a.y, b.x, b.y)

        /** Returns the distance between two result points. */
        private fun distance(a: ResultPoint, b: ResultPoint): Float = MathUtils.distance(a.x, a.y, b.x, b.y)
    }
}
