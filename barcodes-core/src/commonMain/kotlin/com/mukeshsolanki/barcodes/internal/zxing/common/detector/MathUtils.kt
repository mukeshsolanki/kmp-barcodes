/*
 * Copyright 2012 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.common.detector

import kotlin.math.sqrt

/**
 * General math-related and numeric utility functions.
 */
internal object MathUtils {

    /** Rounds to the nearest int, halves away from zero (-2.5 rounds to -3). */
    fun round(d: Float): Int = (d + (if (d < 0.0f) -0.5f else 0.5f)).toInt()

    /** Euclidean distance between ([aX], [aY]) and ([bX], [bY]). */
    fun distance(aX: Float, aY: Float, bX: Float, bY: Float): Float {
        val xDiff = (aX - bX).toDouble()
        val yDiff = (aY - bY).toDouble()
        return sqrt(xDiff * xDiff + yDiff * yDiff).toFloat()
    }

    /** Euclidean distance between integer points ([aX], [aY]) and ([bX], [bY]). */
    fun distance(aX: Int, aY: Int, bX: Int, bY: Int): Float {
        val xDiff = (aX - bX).toDouble()
        val yDiff = (aY - bY).toDouble()
        return sqrt(xDiff * xDiff + yDiff * yDiff).toFloat()
    }

    /** Sum of the values in [array]. */
    fun sum(array: IntArray): Int {
        var count = 0
        for (a in array) {
            count += a
        }
        return count
    }
}
