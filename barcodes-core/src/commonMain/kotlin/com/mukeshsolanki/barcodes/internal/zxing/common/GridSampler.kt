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

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException

/**
 * Samples an image at the points of a perspective-corrected grid to reconstruct a barcode's modules.
 */
internal abstract class GridSampler {

    /**
     * Samples a [dimensionX] x [dimensionY] grid; the transform maps the four "to" points (grid space)
     * onto the four "from" points (image space). Throws [NotFoundException] if sampling leaves the image.
     */
    abstract fun sampleGrid(
        image: BitMatrix,
        dimensionX: Int,
        dimensionY: Int,
        p1ToX: Float,
        p1ToY: Float,
        p2ToX: Float,
        p2ToY: Float,
        p3ToX: Float,
        p3ToY: Float,
        p4ToX: Float,
        p4ToY: Float,
        p1FromX: Float,
        p1FromY: Float,
        p2FromX: Float,
        p2FromY: Float,
        p3FromX: Float,
        p3FromY: Float,
        p4FromX: Float,
        p4FromY: Float,
    ): BitMatrix

    /** Samples a [dimensionX] x [dimensionY] grid from [image] through [transform]. */
    abstract fun sampleGrid(
        image: BitMatrix,
        dimensionX: Int,
        dimensionY: Int,
        transform: PerspectiveTransform,
    ): BitMatrix

    /** Global sampler instance and shared point checks. */
    companion object {
        /** Installed sampler implementation. */
        private var gridSampler: GridSampler = DefaultGridSampler()

        /** Installs [newGridSampler] as the global sampler. */
        fun setGridSampler(newGridSampler: GridSampler) {
            gridSampler = newGridSampler
        }

        /** The current global sampler. */
        val instance: GridSampler
            get() = gridSampler

        /**
         * Checks transformed [points] (x1,y1,...,xn,yn) against [image], nudging endpoints that are
         * less than one pixel outside back inside. Throws [NotFoundException] if an endpoint is outside.
         */
        fun checkAndNudgePoints(image: BitMatrix, points: FloatArray) {
            val width = image.width
            val height = image.height
            var nudged = true
            val maxOffset = points.size - 1
            var offset = 0
            while (offset < maxOffset && nudged) {
                nudged = nudgePoint(points, offset, width, height)
                offset += 2
            }
            nudged = true
            offset = points.size - 2
            while (offset >= 0 && nudged) {
                nudged = nudgePoint(points, offset, width, height)
                offset -= 2
            }
        }

        /** Validates and nudges the point at [offset]; returns true if it was nudged. */
        private fun nudgePoint(points: FloatArray, offset: Int, width: Int, height: Int): Boolean {
            val x = points[offset].toInt()
            val y = points[offset + 1].toInt()
            if (x < -1 || x > width || y < -1 || y > height) {
                throw NotFoundException.notFoundInstance
            }
            var nudged = false
            if (x == -1) {
                points[offset] = 0.0f
                nudged = true
            } else if (x == width) {
                points[offset] = (width - 1).toFloat()
                nudged = true
            }
            if (y == -1) {
                points[offset + 1] = 0.0f
                nudged = true
            } else if (y == height) {
                points[offset + 1] = (height - 1).toFloat()
                nudged = true
            }
            return nudged
        }
    }
}
