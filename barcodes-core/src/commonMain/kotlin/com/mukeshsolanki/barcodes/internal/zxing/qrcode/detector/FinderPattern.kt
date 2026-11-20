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

import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import kotlin.math.abs

/**
 * A finder pattern (one of the three corner squares), with a count of how often it was seen.
 */
internal class FinderPattern private constructor(
    posX: Float,
    posY: Float,
    /** Estimated module size in pixels. */
    val estimatedModuleSize: Float,
    /** Number of times this pattern has been observed. */
    val count: Int,
) : ResultPoint(posX, posY) {

    /** Creates a pattern seen once. */
    constructor(posX: Float, posY: Float, estimatedModuleSize: Float) : this(posX, posY, estimatedModuleSize, 1)

    /** True when this pattern is at nearly the same centre (i = row, j = column) with nearly the same size. */
    fun aboutEquals(moduleSize: Float, i: Float, j: Float): Boolean {
        if (abs(i - y) <= moduleSize && abs(j - x) <= moduleSize) {
            val moduleSizeDiff = abs(moduleSize - estimatedModuleSize)
            return moduleSizeDiff <= 1.0f || moduleSizeDiff <= estimatedModuleSize
        }
        return false
    }

    /** Returns a new pattern with a count-weighted average of this estimate and a new one at (j, i). */
    fun combineEstimate(i: Float, j: Float, newModuleSize: Float): FinderPattern {
        val combinedCount = count + 1
        val combinedX = (count * x + j) / combinedCount
        val combinedY = (count * y + i) / combinedCount
        val combinedModuleSize = (count * estimatedModuleSize + newModuleSize) / combinedCount
        return FinderPattern(combinedX, combinedY, combinedModuleSize, combinedCount)
    }
}
