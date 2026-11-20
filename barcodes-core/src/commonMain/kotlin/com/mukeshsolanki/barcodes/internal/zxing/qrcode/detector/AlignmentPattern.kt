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
 * An alignment pattern: one of the smaller square patterns in all but version 1 QR Codes.
 */
internal class AlignmentPattern(
    posX: Float,
    posY: Float,
    /** Estimated module size in pixels. */
    private val estimatedModuleSize: Float,
) : ResultPoint(posX, posY) {

    /** True when this pattern is at nearly the same centre (i = row, j = column) with nearly the same size. */
    fun aboutEquals(moduleSize: Float, i: Float, j: Float): Boolean {
        if (abs(i - y) <= moduleSize && abs(j - x) <= moduleSize) {
            val moduleSizeDiff = abs(moduleSize - estimatedModuleSize)
            return moduleSizeDiff <= 1.0f || moduleSizeDiff <= estimatedModuleSize
        }
        return false
    }

    /** Returns a new pattern averaging this estimate with a new one at (j, i) of [newModuleSize]. */
    fun combineEstimate(i: Float, j: Float, newModuleSize: Float): AlignmentPattern {
        val combinedX = (x + j) / 2.0f
        val combinedY = (y + i) / 2.0f
        val combinedModuleSize = (estimatedModuleSize + newModuleSize) / 2.0f
        return AlignmentPattern(combinedX, combinedY, combinedModuleSize)
    }
}
