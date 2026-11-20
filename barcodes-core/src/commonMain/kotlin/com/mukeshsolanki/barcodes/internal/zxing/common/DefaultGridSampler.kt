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
 * Default [GridSampler] that samples one point at the centre of each module.
 */
internal class DefaultGridSampler : GridSampler() {

    /** Builds the quadrilateral transform and samples through it. */
    override fun sampleGrid(
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
    ): BitMatrix {
        val transform = PerspectiveTransform.quadrilateralToQuadrilateral(
            p1ToX, p1ToY, p2ToX, p2ToY, p3ToX, p3ToY, p4ToX, p4ToY,
            p1FromX, p1FromY, p2FromX, p2FromY, p3FromX, p3FromY, p4FromX, p4FromY,
        )
        return sampleGrid(image, dimensionX, dimensionY, transform)
    }

    /** Samples each module centre through [transform]; a twisted transform reading out of bounds is NotFound. */
    override fun sampleGrid(
        image: BitMatrix,
        dimensionX: Int,
        dimensionY: Int,
        transform: PerspectiveTransform,
    ): BitMatrix {
        if (dimensionX <= 0 || dimensionY <= 0) {
            throw NotFoundException.notFoundInstance
        }
        val bits = BitMatrix(dimensionX, dimensionY)
        val points = FloatArray(2 * dimensionX)
        for (y in 0 until dimensionY) {
            val max = points.size
            val iValue = y + 0.5f
            for (x in 0 until max step 2) {
                points[x] = (x / 2).toFloat() + 0.5f
                points[x + 1] = iValue
            }
            transform.transformPoints(points)
            checkAndNudgePoints(image, points)
            try {
                for (x in 0 until max step 2) {
                    if (image.get(points[x].toInt(), points[x + 1].toInt())) {
                        bits.set(x / 2, y)
                    }
                }
            } catch (outOfBounds: IndexOutOfBoundsException) {
                throw NotFoundException.notFoundInstance
            }
        }
        return bits
    }
}
