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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.common

/**
 * A 2D perspective transform between quadrilaterals, after section 3.4.2 of Wolberg's "Digital Image Warping".
 */
internal class PerspectiveTransform private constructor(
    /** Matrix element (1,1). */
    private val a11: Float,
    /** Matrix element (2,1). */
    private val a21: Float,
    /** Matrix element (3,1). */
    private val a31: Float,
    /** Matrix element (1,2). */
    private val a12: Float,
    /** Matrix element (2,2). */
    private val a22: Float,
    /** Matrix element (3,2). */
    private val a32: Float,
    /** Matrix element (1,3). */
    private val a13: Float,
    /** Matrix element (2,3). */
    private val a23: Float,
    /** Matrix element (3,3). */
    private val a33: Float,
) {

    /** Transforms interleaved x,y [points] in place. */
    fun transformPoints(points: FloatArray) {
        val maxI = points.size - 1
        var i = 0
        while (i < maxI) {
            val x = points[i]
            val y = points[i + 1]
            val denominator = a13 * x + a23 * y + a33
            points[i] = (a11 * x + a21 * y + a31) / denominator
            points[i + 1] = (a12 * x + a22 * y + a32) / denominator
            i += 2
        }
    }

    /** Transforms parallel [xValues] / [yValues] in place. */
    fun transformPoints(xValues: FloatArray, yValues: FloatArray) {
        val n = xValues.size
        for (i in 0 until n) {
            val x = xValues[i]
            val y = yValues[i]
            val denominator = a13 * x + a23 * y + a33
            xValues[i] = (a11 * x + a21 * y + a31) / denominator
            yValues[i] = (a12 * x + a22 * y + a32) / denominator
        }
    }

    /** Adjoint (transpose of the cofactor matrix), which serves as the inverse here. */
    internal fun buildAdjoint(): PerspectiveTransform = PerspectiveTransform(
        a22 * a33 - a23 * a32,
        a23 * a31 - a21 * a33,
        a21 * a32 - a22 * a31,
        a13 * a32 - a12 * a33,
        a11 * a33 - a13 * a31,
        a12 * a31 - a11 * a32,
        a12 * a23 - a13 * a22,
        a13 * a21 - a11 * a23,
        a11 * a22 - a12 * a21,
    )

    /** Matrix product of this transform and [other]. */
    internal fun times(other: PerspectiveTransform): PerspectiveTransform = PerspectiveTransform(
        a11 * other.a11 + a21 * other.a12 + a31 * other.a13,
        a11 * other.a21 + a21 * other.a22 + a31 * other.a23,
        a11 * other.a31 + a21 * other.a32 + a31 * other.a33,
        a12 * other.a11 + a22 * other.a12 + a32 * other.a13,
        a12 * other.a21 + a22 * other.a22 + a32 * other.a23,
        a12 * other.a31 + a22 * other.a32 + a32 * other.a33,
        a13 * other.a11 + a23 * other.a12 + a33 * other.a13,
        a13 * other.a21 + a23 * other.a22 + a33 * other.a23,
        a13 * other.a31 + a23 * other.a32 + a33 * other.a33,
    )

    /** Factories for quadrilateral and square mappings. */
    companion object {
        /** Transform mapping quadrilateral (x0..y3) onto quadrilateral (x0p..y3p). */
        fun quadrilateralToQuadrilateral(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
            x0p: Float,
            y0p: Float,
            x1p: Float,
            y1p: Float,
            x2p: Float,
            y2p: Float,
            x3p: Float,
            y3p: Float,
        ): PerspectiveTransform {
            val qToS = quadrilateralToSquare(x0, y0, x1, y1, x2, y2, x3, y3)
            val sToQ = squareToQuadrilateral(x0p, y0p, x1p, y1p, x2p, y2p, x3p, y3p)
            return sToQ.times(qToS)
        }

        /** Transform mapping the unit square onto the quadrilateral (x0..y3). */
        fun squareToQuadrilateral(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
        ): PerspectiveTransform {
            val dx3 = x0 - x1 + x2 - x3
            val dy3 = y0 - y1 + y2 - y3
            if (dx3 == 0.0f && dy3 == 0.0f) {
                return PerspectiveTransform(
                    x1 - x0, x2 - x1, x0,
                    y1 - y0, y2 - y1, y0,
                    0.0f, 0.0f, 1.0f,
                )
            }
            val dx1 = x1 - x2
            val dx2 = x3 - x2
            val dy1 = y1 - y2
            val dy2 = y3 - y2
            val denominator = dx1 * dy2 - dx2 * dy1
            val a13 = (dx3 * dy2 - dx2 * dy3) / denominator
            val a23 = (dx1 * dy3 - dx3 * dy1) / denominator
            return PerspectiveTransform(
                x1 - x0 + a13 * x1, x3 - x0 + a23 * x3, x0,
                y1 - y0 + a13 * y1, y3 - y0 + a23 * y3, y0,
                a13, a23, 1.0f,
            )
        }

        /** Transform mapping the quadrilateral (x0..y3) onto the unit square. */
        fun quadrilateralToSquare(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
        ): PerspectiveTransform = squareToQuadrilateral(x0, y0, x1, y1, x2, y2, x3, y3).buildAdjoint()
    }
}
