/*
 * Copyright 2008 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.oned

import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Reader
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import kotlin.math.max

/**
 * Encapsulates functionality and implementation that is common to all families
 * of one-dimensional barcodes.
 */
internal abstract class OneDReader : Reader {

    /** Decodes [image] without any hints. */
    override fun decode(image: BinaryBitmap): Result = decode(image, null)

    /** Decodes [image]; with TRY_HARDER it retries on the image rotated counter-clockwise when rotation is supported. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        try {
            return doDecode(image, hints)
        } catch (nfe: NotFoundException) {
            val tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER)
            if (tryHarder && image.isRotateSupported) {
                return decodeRotated(image.rotateCounterClockwise(), hints)
            }
            throw nfe
        }
    }

    /** Decodes the already rotated image and maps orientation metadata and result points back to the original. */
    private fun decodeRotated(rotatedImage: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        val result = doDecode(rotatedImage, hints)
        val metadata = result.resultMetadata
        var orientation = 270
        if (metadata != null && metadata.containsKey(ResultMetadataType.ORIENTATION)) {
            orientation = (orientation + metadata[ResultMetadataType.ORIENTATION] as Int) % 360
        }
        result.putMetadata(ResultMetadataType.ORIENTATION, orientation)
        val points = result.resultPoints
        if (points != null) {
            val height = rotatedImage.height
            for (i in points.indices) {
                val point = points[i]!!
                points[i] = ResultPoint(height - point.y - 1, point.x)
            }
        }
        return result
    }

    /** No state to reset. */
    override fun reset() {
    }

    /**
     * Scans rows from the middle outward, alternately above and below, trying each row
     * forwards and reversed, and returns the first row that decodes.
     */
    private fun doDecode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        var currentHints = hints
        val width = image.width
        val height = image.height
        var row = BitArray(width)

        val tryHarder = currentHints != null && currentHints.containsKey(DecodeHintType.TRY_HARDER)
        val rowStep = max(1, height shr (if (tryHarder) 8 else 5))
        val maxLines = if (tryHarder) height else 15

        val middle = height / 2
        for (x in 0 until maxLines) {
            val rowStepsAboveOrBelow = (x + 1) / 2
            val isAbove = (x and 0x01) == 0
            val rowNumber = middle + rowStep * (if (isAbove) rowStepsAboveOrBelow else -rowStepsAboveOrBelow)
            if (rowNumber < 0 || rowNumber >= height) {
                break
            }

            row = try {
                image.getBlackRow(rowNumber, row)
            } catch (ignored: NotFoundException) {
                continue
            }

            for (attempt in 0 until 2) {
                if (attempt == 1) {
                    row.reverse()
                    if (currentHints != null && currentHints.containsKey(DecodeHintType.NEED_RESULT_POINT_CALLBACK)) {
                        currentHints = currentHints - DecodeHintType.NEED_RESULT_POINT_CALLBACK
                    }
                }
                try {
                    val result = decodeRow(rowNumber, row, currentHints)
                    if (attempt == 1) {
                        markUpsideDown(result, width)
                    }
                    return result
                } catch (re: ReaderException) {
                }
            }
        }

        throw NotFoundException.notFoundInstance
    }

    /** Records a 180 degree orientation on [result] and flips its first two result points horizontally. */
    private fun markUpsideDown(result: Result, width: Int) {
        result.putMetadata(ResultMetadataType.ORIENTATION, 180)
        val points = result.resultPoints
        if (points != null) {
            val first = points[0]!!
            val second = points[1]!!
            points[0] = ResultPoint(width - first.x - 1, first.y)
            points[1] = ResultPoint(width - second.x - 1, second.y)
        }
    }

    /**
     * Attempts to decode a one-dimensional barcode format given a single row of an image.
     * Throws NotFoundException, ChecksumException or FormatException when the row does not decode.
     */
    abstract fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result

    /** Shared run-length helpers used by all 1D readers. */
    companion object {

        /**
         * Records the size of successive runs of white and black pixels in [row], starting at [start],
         * into [counters]. Throws NotFoundException if the counters cannot be filled.
         */
        fun recordPattern(row: BitArray, start: Int, counters: IntArray) {
            val numCounters = counters.size
            counters.fill(0, 0, numCounters)
            val end = row.size
            if (start >= end) {
                throw NotFoundException.notFoundInstance
            }
            var isWhite = !row.get(start)
            var counterPosition = 0
            var i = start
            while (i < end) {
                if (row.get(i) != isWhite) {
                    counters[counterPosition]++
                } else {
                    if (++counterPosition == numCounters) {
                        break
                    } else {
                        counters[counterPosition] = 1
                        isWhite = !isWhite
                    }
                }
                i++
            }
            if (!(counterPosition == numCounters || (counterPosition == numCounters - 1 && i == end))) {
                throw NotFoundException.notFoundInstance
            }
        }

        /** Like [recordPattern], but first walks backwards from [start] over as many transitions as there are counters. */
        fun recordPatternInReverse(row: BitArray, start: Int, counters: IntArray) {
            var position = start
            var numTransitionsLeft = counters.size
            var last = row.get(position)
            while (position > 0 && numTransitionsLeft >= 0) {
                if (row.get(--position) != last) {
                    numTransitionsLeft--
                    last = !last
                }
            }
            if (numTransitionsLeft >= 0) {
                throw NotFoundException.notFoundInstance
            }
            recordPattern(row, position + 1, counters)
        }

        /**
         * Returns the ratio of the total variance between observed [counters] and the expected [pattern]
         * to the pattern size, or positive infinity when any counter differs by more than [maxIndividualVariance].
         */
        fun patternMatchVariance(counters: IntArray, pattern: IntArray, maxIndividualVariance: Float): Float {
            val numCounters = counters.size
            var total = 0
            var patternLength = 0
            for (i in 0 until numCounters) {
                total += counters[i]
                patternLength += pattern[i]
            }
            if (total < patternLength) {
                return Float.POSITIVE_INFINITY
            }

            val unitBarWidth = total.toFloat() / patternLength
            val maxVariance = maxIndividualVariance * unitBarWidth

            var totalVariance = 0.0f
            for (x in 0 until numCounters) {
                val counter = counters[x]
                val scaledPattern = pattern[x] * unitBarWidth
                val variance = if (counter > scaledPattern) counter - scaledPattern else scaledPattern - counter
                if (variance > maxVariance) {
                    return Float.POSITIVE_INFINITY
                }
                totalVariance += variance
            }
            return totalVariance / total
        }
    }
}
