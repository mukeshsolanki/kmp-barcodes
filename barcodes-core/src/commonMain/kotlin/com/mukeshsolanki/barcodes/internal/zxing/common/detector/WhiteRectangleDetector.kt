/*
 * Copyright 2010 ZXing authors
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

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Finds a barcode-like rectangular region by growing a box from a centre point until its border
 * is white, tracking the last black points to locate the corners.
 */
internal class WhiteRectangleDetector(
    /** Image to search. */
    private val image: BitMatrix,
    initSize: Int,
    x: Int,
    y: Int,
) {

    /** Image height. */
    private val height: Int = image.height

    /** Image width. */
    private val width: Int = image.width

    /** Initial left edge of the search box. */
    private val leftInit: Int = x - initSize / 2

    /** Initial right edge of the search box. */
    private val rightInit: Int = x + initSize / 2

    /** Initial top edge of the search box. */
    private val upInit: Int = y - initSize / 2

    /** Initial bottom edge of the search box. */
    private val downInit: Int = y + initSize / 2

    init {
        if (upInit < 0 || leftInit < 0 || downInit >= height || rightInit >= width) {
            throw NotFoundException.notFoundInstance
        }
    }

    /** Searches from the image centre with the default initial size. */
    constructor(image: BitMatrix) : this(image, INIT_SIZE, image.width / 2, image.height / 2)

    /**
     * Returns the region's corners: topmost, leftmost, rightmost, bottommost (first/last and
     * second/third are diagonal pairs). Throws [NotFoundException] if no region is found.
     */
    fun detect(): Array<ResultPoint> {
        var left = leftInit
        var right = rightInit
        var up = upInit
        var down = downInit
        var sizeExceeded = false
        var aBlackPointFoundOnBorder = true

        var atLeastOneBlackPointFoundOnRight = false
        var atLeastOneBlackPointFoundOnBottom = false
        var atLeastOneBlackPointFoundOnLeft = false
        var atLeastOneBlackPointFoundOnTop = false

        while (aBlackPointFoundOnBorder) {
            aBlackPointFoundOnBorder = false

            var rightBorderNotWhite = true
            while ((rightBorderNotWhite || !atLeastOneBlackPointFoundOnRight) && right < width) {
                rightBorderNotWhite = containsBlackPoint(up, down, right, false)
                if (rightBorderNotWhite) {
                    right++
                    aBlackPointFoundOnBorder = true
                    atLeastOneBlackPointFoundOnRight = true
                } else if (!atLeastOneBlackPointFoundOnRight) {
                    right++
                }
            }

            if (right >= width) {
                sizeExceeded = true
                break
            }

            var bottomBorderNotWhite = true
            while ((bottomBorderNotWhite || !atLeastOneBlackPointFoundOnBottom) && down < height) {
                bottomBorderNotWhite = containsBlackPoint(left, right, down, true)
                if (bottomBorderNotWhite) {
                    down++
                    aBlackPointFoundOnBorder = true
                    atLeastOneBlackPointFoundOnBottom = true
                } else if (!atLeastOneBlackPointFoundOnBottom) {
                    down++
                }
            }

            if (down >= height) {
                sizeExceeded = true
                break
            }

            var leftBorderNotWhite = true
            while ((leftBorderNotWhite || !atLeastOneBlackPointFoundOnLeft) && left >= 0) {
                leftBorderNotWhite = containsBlackPoint(up, down, left, false)
                if (leftBorderNotWhite) {
                    left--
                    aBlackPointFoundOnBorder = true
                    atLeastOneBlackPointFoundOnLeft = true
                } else if (!atLeastOneBlackPointFoundOnLeft) {
                    left--
                }
            }

            if (left < 0) {
                sizeExceeded = true
                break
            }

            var topBorderNotWhite = true
            while ((topBorderNotWhite || !atLeastOneBlackPointFoundOnTop) && up >= 0) {
                topBorderNotWhite = containsBlackPoint(left, right, up, true)
                if (topBorderNotWhite) {
                    up--
                    aBlackPointFoundOnBorder = true
                    atLeastOneBlackPointFoundOnTop = true
                } else if (!atLeastOneBlackPointFoundOnTop) {
                    up--
                }
            }

            if (up < 0) {
                sizeExceeded = true
                break
            }
        }

        if (sizeExceeded) {
            throw NotFoundException.notFoundInstance
        }

        val maxSize = right - left

        var z: ResultPoint? = null
        var i = 1
        while (z == null && i < maxSize) {
            z = getBlackPointOnSegment(left, down - i, left + i, down)
            i++
        }
        if (z == null) {
            throw NotFoundException.notFoundInstance
        }

        var t: ResultPoint? = null
        i = 1
        while (t == null && i < maxSize) {
            t = getBlackPointOnSegment(left, up + i, left + i, up)
            i++
        }
        if (t == null) {
            throw NotFoundException.notFoundInstance
        }

        var x: ResultPoint? = null
        i = 1
        while (x == null && i < maxSize) {
            x = getBlackPointOnSegment(right, up + i, right - i, up)
            i++
        }
        if (x == null) {
            throw NotFoundException.notFoundInstance
        }

        var y: ResultPoint? = null
        i = 1
        while (y == null && i < maxSize) {
            y = getBlackPointOnSegment(right, down - i, right - i, down)
            i++
        }
        if (y == null) {
            throw NotFoundException.notFoundInstance
        }

        return centerEdges(y, z, x, t)
    }

    /** First black pixel on the segment from ([aX], [aY]) towards ([bX], [bY]), or null. */
    private fun getBlackPointOnSegment(aX: Int, aY: Int, bX: Int, bY: Int): ResultPoint? {
        val fromX = aX.toFloat()
        val fromY = aY.toFloat()
        val dist = MathUtils.round(MathUtils.distance(fromX, fromY, bX.toFloat(), bY.toFloat()))
        val xStep = (bX - fromX) / dist
        val yStep = (bY - fromY) / dist
        for (i in 0 until dist) {
            val x = MathUtils.round(fromX + i * xStep)
            val y = MathUtils.round(fromY + i * yStep)
            if (image.get(x, y)) {
                return ResultPoint(x.toFloat(), y.toFloat())
            }
        }
        return null
    }

    /**
     * Moves the bottommost [y], leftmost [z], rightmost [x] and topmost [t] points one pixel towards
     * the centre, returning them as top, left, right, bottom.
     */
    private fun centerEdges(y: ResultPoint, z: ResultPoint, x: ResultPoint, t: ResultPoint): Array<ResultPoint> {
        val yi = y.x
        val yj = y.y
        val zi = z.x
        val zj = z.y
        val xi = x.x
        val xj = x.y
        val ti = t.x
        val tj = t.y

        return if (yi < width / 2.0f) {
            arrayOf(
                ResultPoint(ti - CORR, tj + CORR),
                ResultPoint(zi + CORR, zj + CORR),
                ResultPoint(xi - CORR, xj - CORR),
                ResultPoint(yi + CORR, yj - CORR),
            )
        } else {
            arrayOf(
                ResultPoint(ti + CORR, tj + CORR),
                ResultPoint(zi + CORR, zj - CORR),
                ResultPoint(xi - CORR, xj + CORR),
                ResultPoint(yi - CORR, yj - CORR),
            )
        }
    }

    /** True if the segment [a]..[b] at [fixed] (a row if [horizontal], else a column) has a black pixel. */
    private fun containsBlackPoint(a: Int, b: Int, fixed: Int, horizontal: Boolean): Boolean {
        if (horizontal) {
            for (x in a..b) {
                if (image.get(x, fixed)) {
                    return true
                }
            }
        } else {
            for (y in a..b) {
                if (image.get(fixed, y)) {
                    return true
                }
            }
        }
        return false
    }

    /** Detector constants. */
    companion object {
        /** Default initial search box size. */
        private const val INIT_SIZE = 10

        /** Pixel correction applied when centring edges. */
        private const val CORR = 1
    }
}
