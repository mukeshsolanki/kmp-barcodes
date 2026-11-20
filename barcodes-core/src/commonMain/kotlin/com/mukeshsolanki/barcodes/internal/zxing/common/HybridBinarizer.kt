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
import kotlin.math.min

/**
 * Local thresholding binarizer: 8x8 pixel blocks thresholded against the average of a 5x5 block
 * neighbourhood. Uses the global histogram approach for 1D rows and for images smaller than 40px.
 */
internal class HybridBinarizer(source: LuminanceSource) : GlobalHistogramBinarizer(source) {

    /** Cached result of [blackMatrix]. */
    private var matrix: BitMatrix? = null

    /** Binarized image, computed once on first access. */
    override val blackMatrix: BitMatrix
        get() {
            matrix?.let { return it }
            val source = luminanceSource
            val width = source.width
            val height = source.height
            val computed = if (width >= MINIMUM_DIMENSION && height >= MINIMUM_DIMENSION) {
                val luminances = source.matrix
                var subWidth = width shr BLOCK_SIZE_POWER
                if ((width and BLOCK_SIZE_MASK) != 0) {
                    subWidth++
                }
                var subHeight = height shr BLOCK_SIZE_POWER
                if ((height and BLOCK_SIZE_MASK) != 0) {
                    subHeight++
                }
                val blackPoints = calculateBlackPoints(luminances, subWidth, subHeight, width, height)
                val newMatrix = BitMatrix(width, height)
                calculateThresholdForBlock(luminances, subWidth, subHeight, width, height, blackPoints, newMatrix)
                newMatrix
            } else {
                super.blackMatrix
            }
            matrix = computed
            return computed
        }

    /** Creates a binarizer of the same kind for [source]. */
    override fun createBinarizer(source: LuminanceSource): Binarizer = HybridBinarizer(source)

    /** Block constants and thresholding helpers. */
    companion object {
        /** Log2 of the block size. */
        private const val BLOCK_SIZE_POWER = 3

        /** Block edge length in pixels. */
        private const val BLOCK_SIZE = 1 shl BLOCK_SIZE_POWER

        /** Mask of the in-block pixel offset. */
        private const val BLOCK_SIZE_MASK = BLOCK_SIZE - 1

        /** Smallest image edge the local approach accepts (5 blocks). */
        private const val MINIMUM_DIMENSION = BLOCK_SIZE * 5

        /** Minimum min/max spread for a block to count as having contrast. */
        private const val MIN_DYNAMIC_RANGE = 24

        /** Thresholds each block against the average black point of its 5x5 block neighbourhood. */
        private fun calculateThresholdForBlock(
            luminances: ByteArray,
            subWidth: Int,
            subHeight: Int,
            width: Int,
            height: Int,
            blackPoints: Array<IntArray>,
            matrix: BitMatrix,
        ) {
            val maxYOffset = height - BLOCK_SIZE
            val maxXOffset = width - BLOCK_SIZE
            for (y in 0 until subHeight) {
                var yoffset = y shl BLOCK_SIZE_POWER
                if (yoffset > maxYOffset) {
                    yoffset = maxYOffset
                }
                val top = cap(y, subHeight - 3)
                for (x in 0 until subWidth) {
                    var xoffset = x shl BLOCK_SIZE_POWER
                    if (xoffset > maxXOffset) {
                        xoffset = maxXOffset
                    }
                    val left = cap(x, subWidth - 3)
                    var sum = 0
                    for (z in -2..2) {
                        val blackRow = blackPoints[top + z]
                        sum += blackRow[left - 2] + blackRow[left - 1] + blackRow[left] + blackRow[left + 1] +
                            blackRow[left + 2]
                    }
                    val average = sum / 25
                    thresholdBlock(luminances, xoffset, yoffset, average, width, matrix)
                }
            }
        }

        /** Clamps [value] to `2..max`. */
        private fun cap(value: Int, max: Int): Int = if (value < 2) 2 else min(value, max)

        /** Sets every pixel of one block whose luminance is at most [threshold]. */
        private fun thresholdBlock(
            luminances: ByteArray,
            xoffset: Int,
            yoffset: Int,
            threshold: Int,
            stride: Int,
            matrix: BitMatrix,
        ) {
            var offset = yoffset * stride + xoffset
            for (y in 0 until BLOCK_SIZE) {
                for (x in 0 until BLOCK_SIZE) {
                    if ((luminances[offset + x].toInt() and 0xFF) <= threshold) {
                        matrix.set(xoffset + x, yoffset + y)
                    }
                }
                offset += stride
            }
        }

        /** Computes one black point per block, borrowing neighbours' estimates for low-contrast blocks. */
        private fun calculateBlackPoints(
            luminances: ByteArray,
            subWidth: Int,
            subHeight: Int,
            width: Int,
            height: Int,
        ): Array<IntArray> {
            val maxYOffset = height - BLOCK_SIZE
            val maxXOffset = width - BLOCK_SIZE
            val blackPoints = Array(subHeight) { IntArray(subWidth) }
            for (y in 0 until subHeight) {
                var yoffset = y shl BLOCK_SIZE_POWER
                if (yoffset > maxYOffset) {
                    yoffset = maxYOffset
                }
                for (x in 0 until subWidth) {
                    var xoffset = x shl BLOCK_SIZE_POWER
                    if (xoffset > maxXOffset) {
                        xoffset = maxXOffset
                    }
                    var sum = 0
                    var min = 0xFF
                    var max = 0
                    var yy = 0
                    var offset = yoffset * width + xoffset
                    while (yy < BLOCK_SIZE) {
                        for (xx in 0 until BLOCK_SIZE) {
                            val pixel = luminances[offset + xx].toInt() and 0xFF
                            sum += pixel
                            if (pixel < min) {
                                min = pixel
                            }
                            if (pixel > max) {
                                max = pixel
                            }
                        }
                        if (max - min > MIN_DYNAMIC_RANGE) {
                            yy++
                            offset += width
                            while (yy < BLOCK_SIZE) {
                                for (xx in 0 until BLOCK_SIZE) {
                                    sum += luminances[offset + xx].toInt() and 0xFF
                                }
                                yy++
                                offset += width
                            }
                        }
                        yy++
                        offset += width
                    }

                    var average = sum shr (BLOCK_SIZE_POWER * 2)
                    if (max - min <= MIN_DYNAMIC_RANGE) {
                        average = min / 2
                        if (y > 0 && x > 0) {
                            val averageNeighborBlackPoint =
                                (blackPoints[y - 1][x] + (2 * blackPoints[y][x - 1]) + blackPoints[y - 1][x - 1]) / 4
                            if (min < averageNeighborBlackPoint) {
                                average = averageNeighborBlackPoint
                            }
                        }
                    }
                    blackPoints[y][x] = average
                }
            }
            return blackPoints
        }
    }
}
