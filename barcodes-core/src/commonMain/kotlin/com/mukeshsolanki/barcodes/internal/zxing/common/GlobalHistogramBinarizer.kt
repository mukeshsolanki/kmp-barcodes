/*
 * Copyright 2009 ZXing authors
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

import com.mukeshsolanki.barcodes.internal.zxing.Binarizer
import com.mukeshsolanki.barcodes.internal.zxing.LuminanceSource
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException

/**
 * Binarizer using a single global black point from a luminance histogram. Cheap, but cannot
 * handle shadows or gradients; [HybridBinarizer] is preferred for 2D codes.
 */
internal open class GlobalHistogramBinarizer(source: LuminanceSource) : Binarizer(source) {

    /** Reusable row luminance buffer. */
    private var luminances: ByteArray = EMPTY

    /** Reusable histogram buckets. */
    private val buckets: IntArray = IntArray(LUMINANCE_BUCKETS)

    /** Binarizes row [y] with a -1 4 -1 sharpening filter, for 1D readers. */
    override fun getBlackRow(y: Int, row: BitArray?): BitArray {
        val source = luminanceSource
        val width = source.width
        val result = if (row == null || row.size < width) BitArray(width) else row.also { it.clear() }

        initArrays(width)
        val localLuminances = source.getRow(y, luminances)
        val localBuckets = buckets
        for (x in 0 until width) {
            localBuckets[(localLuminances[x].toInt() and 0xff) shr LUMINANCE_SHIFT]++
        }
        val blackPoint = estimateBlackPoint(localBuckets)

        if (width < 3) {
            for (x in 0 until width) {
                if ((localLuminances[x].toInt() and 0xff) < blackPoint) {
                    result.set(x)
                }
            }
        } else {
            var left = localLuminances[0].toInt() and 0xff
            var center = localLuminances[1].toInt() and 0xff
            for (x in 1 until width - 1) {
                val right = localLuminances[x + 1].toInt() and 0xff
                if (((center * 4) - left - right) / 2 < blackPoint) {
                    result.set(x)
                }
                left = center
                center = right
            }
        }
        return result
    }

    /** Binarizes the whole image with a black point estimated from four sampled rows, for 2D readers. */
    override val blackMatrix: BitMatrix
        get() {
            val source = luminanceSource
            val width = source.width
            val height = source.height
            val matrix = BitMatrix(width, height)

            initArrays(width)
            val localBuckets = buckets
            for (y in 1 until 5) {
                val row = height * y / 5
                val localLuminances = source.getRow(row, luminances)
                val right = (width * 4) / 5
                for (x in width / 5 until right) {
                    val pixel = localLuminances[x].toInt() and 0xff
                    localBuckets[pixel shr LUMINANCE_SHIFT]++
                }
            }
            val blackPoint = estimateBlackPoint(localBuckets)

            val localLuminances = source.matrix
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    val pixel = localLuminances[offset + x].toInt() and 0xff
                    if (pixel < blackPoint) {
                        matrix.set(x, y)
                    }
                }
            }
            return matrix
        }

    /** Creates a binarizer of the same kind for [source]. */
    override fun createBinarizer(source: LuminanceSource): Binarizer = GlobalHistogramBinarizer(source)

    /** Ensures the row buffer holds [luminanceSize] bytes and clears the histogram. */
    private fun initArrays(luminanceSize: Int) {
        if (luminances.size < luminanceSize) {
            luminances = ByteArray(luminanceSize)
        }
        buckets.fill(0)
    }

    /** Histogram constants and black point estimation. */
    companion object {
        /** Bits of luminance kept per histogram bucket. */
        private const val LUMINANCE_BITS = 5

        /** Shift that maps a luminance byte to its bucket. */
        private const val LUMINANCE_SHIFT = 8 - LUMINANCE_BITS

        /** Number of histogram buckets. */
        private const val LUMINANCE_BUCKETS = 1 shl LUMINANCE_BITS

        /** Initial empty luminance buffer. */
        private val EMPTY = ByteArray(0)

        /** Picks a black point in the valley between the two histogram peaks; throws [NotFoundException] on low contrast. */
        private fun estimateBlackPoint(buckets: IntArray): Int {
            val numBuckets = buckets.size
            var maxBucketCount = 0
            var firstPeak = 0
            var firstPeakSize = 0
            for (x in 0 until numBuckets) {
                if (buckets[x] > firstPeakSize) {
                    firstPeak = x
                    firstPeakSize = buckets[x]
                }
                if (buckets[x] > maxBucketCount) {
                    maxBucketCount = buckets[x]
                }
            }

            var secondPeak = 0
            var secondPeakScore = 0
            for (x in 0 until numBuckets) {
                val distanceToBiggest = x - firstPeak
                val score = buckets[x] * distanceToBiggest * distanceToBiggest
                if (score > secondPeakScore) {
                    secondPeak = x
                    secondPeakScore = score
                }
            }

            if (firstPeak > secondPeak) {
                val temp = firstPeak
                firstPeak = secondPeak
                secondPeak = temp
            }

            if (secondPeak - firstPeak <= numBuckets / 16) {
                throw NotFoundException.notFoundInstance
            }

            var bestValley = secondPeak - 1
            var bestValleyScore = -1
            for (x in secondPeak - 1 downTo firstPeak + 1) {
                val fromFirst = x - firstPeak
                val score = fromFirst * fromFirst * (secondPeak - x) * (maxBucketCount - buckets[x])
                if (score > bestValleyScore) {
                    bestValley = x
                    bestValleyScore = score
                }
            }

            return bestValley shl LUMINANCE_SHIFT
        }
    }
}
