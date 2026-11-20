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
package com.mukeshsolanki.barcodes.internal.zxing

import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils

/** A point of interest in an image containing a barcode, such as a finder pattern or a corner. */
internal open class ResultPoint(
    /** The x coordinate. */
    val x: Float,
    /** The y coordinate. */
    val y: Float,
) {
    /** Equal when both coordinates are exactly equal. */
    final override fun equals(other: Any?): Boolean = other is ResultPoint && x == other.x && y == other.y

    /** zxing's hash over the raw float bits. */
    final override fun hashCode(): Int = 31 * x.toBits() + y.toBits()

    /** Formats as `(x,y)`. */
    final override fun toString(): String = "($x,$y)"

    /** Static helpers shared by the detectors. */
    companion object {
        /** Orders three points as [A, B, C] so AB < AC, BC < AC and the angle between BC and BA is under 180 degrees. */
        fun <T : ResultPoint> orderBestPatterns(patterns: Array<T>) {
            val zeroOneDistance = distance(patterns[0], patterns[1])
            val oneTwoDistance = distance(patterns[1], patterns[2])
            val zeroTwoDistance = distance(patterns[0], patterns[2])
            var pointA: T
            val pointB: T
            var pointC: T
            if (oneTwoDistance >= zeroOneDistance && oneTwoDistance >= zeroTwoDistance) {
                pointB = patterns[0]
                pointA = patterns[1]
                pointC = patterns[2]
            } else if (zeroTwoDistance >= oneTwoDistance && zeroTwoDistance >= zeroOneDistance) {
                pointB = patterns[1]
                pointA = patterns[0]
                pointC = patterns[2]
            } else {
                pointB = patterns[2]
                pointA = patterns[0]
                pointC = patterns[1]
            }
            if (crossProductZ(pointA, pointB, pointC) < 0.0f) {
                val temp = pointA
                pointA = pointC
                pointC = temp
            }
            patterns[0] = pointA
            patterns[1] = pointB
            patterns[2] = pointC
        }

        /** Returns the Euclidean distance between [pattern1] and [pattern2]. */
        fun distance(pattern1: ResultPoint, pattern2: ResultPoint): Float =
            MathUtils.distance(pattern1.x, pattern1.y, pattern2.x, pattern2.y)

        /** Returns the z component of the cross product between vectors BC and BA. */
        fun crossProductZ(pointA: ResultPoint, pointB: ResultPoint, pointC: ResultPoint): Float {
            val bX = pointB.x
            val bY = pointB.y
            return ((pointC.x - bX) * (pointA.y - bY)) - ((pointC.y - bY) * (pointA.x - bX))
        }
    }
}
