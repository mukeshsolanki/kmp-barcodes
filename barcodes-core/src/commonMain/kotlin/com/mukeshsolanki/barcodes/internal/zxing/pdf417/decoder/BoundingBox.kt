/*
 * Copyright 2013 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import kotlin.math.max
import kotlin.math.min

/**
 * The image region holding a PDF417 symbol, defined by its four corners.
 */
internal class BoundingBox {

    /** The image the box refers to. */
    private val image: BitMatrix

    /** Top-left corner. */
    val topLeft: ResultPoint

    /** Bottom-left corner. */
    val bottomLeft: ResultPoint

    /** Top-right corner. */
    val topRight: ResultPoint

    /** Bottom-right corner. */
    val bottomRight: ResultPoint

    /** Smallest x of the box. */
    val minX: Int

    /** Largest x of the box. */
    val maxX: Int

    /** Smallest y of the box. */
    val minY: Int

    /** Largest y of the box. */
    val maxY: Int

    /**
     * Builds a box from up to four corners; a missing side is extended to the image edge.
     * Throws [NotFoundException] when both sides are missing.
     */
    constructor(
        image: BitMatrix,
        topLeft: ResultPoint?,
        bottomLeft: ResultPoint?,
        topRight: ResultPoint?,
        bottomRight: ResultPoint?,
    ) {
        val leftUnspecified = topLeft == null || bottomLeft == null
        val rightUnspecified = topRight == null || bottomRight == null
        if (leftUnspecified && rightUnspecified) {
            throw NotFoundException.notFoundInstance
        }
        this.image = image
        if (leftUnspecified) {
            this.topRight = topRight!!
            this.bottomRight = bottomRight!!
            this.topLeft = ResultPoint(0f, topRight.y)
            this.bottomLeft = ResultPoint(0f, bottomRight.y)
        } else if (rightUnspecified) {
            this.topLeft = topLeft!!
            this.bottomLeft = bottomLeft!!
            this.topRight = ResultPoint((image.width - 1).toFloat(), topLeft.y)
            this.bottomRight = ResultPoint((image.width - 1).toFloat(), bottomLeft.y)
        } else {
            this.topLeft = topLeft!!
            this.bottomLeft = bottomLeft!!
            this.topRight = topRight!!
            this.bottomRight = bottomRight!!
        }
        minX = min(this.topLeft.x, this.bottomLeft.x).toInt()
        maxX = max(this.topRight.x, this.bottomRight.x).toInt()
        minY = min(this.topLeft.y, this.topRight.y).toInt()
        maxY = max(this.bottomLeft.y, this.bottomRight.y).toInt()
    }

    /** Copies [boundingBox]. */
    constructor(boundingBox: BoundingBox) {
        image = boundingBox.image
        topLeft = boundingBox.topLeft
        bottomLeft = boundingBox.bottomLeft
        topRight = boundingBox.topRight
        bottomRight = boundingBox.bottomRight
        minX = boundingBox.minX
        maxX = boundingBox.maxX
        minY = boundingBox.minY
        maxY = boundingBox.maxY
    }

    /** Returns a box extended by [missingStartRows] above and [missingEndRows] below on the left or right side. */
    fun addMissingRows(missingStartRows: Int, missingEndRows: Int, isLeft: Boolean): BoundingBox {
        var newTopLeft = topLeft
        var newBottomLeft = bottomLeft
        var newTopRight = topRight
        var newBottomRight = bottomRight
        if (missingStartRows > 0) {
            val top = if (isLeft) topLeft else topRight
            val newMinY = max(top.y.toInt() - missingStartRows, 0)
            val newTop = ResultPoint(top.x, newMinY.toFloat())
            if (isLeft) {
                newTopLeft = newTop
            } else {
                newTopRight = newTop
            }
        }
        if (missingEndRows > 0) {
            val bottom = if (isLeft) bottomLeft else bottomRight
            val newMaxY = min(bottom.y.toInt() + missingEndRows, image.height - 1)
            val newBottom = ResultPoint(bottom.x, newMaxY.toFloat())
            if (isLeft) {
                newBottomLeft = newBottom
            } else {
                newBottomRight = newBottom
            }
        }
        return BoundingBox(image, newTopLeft, newBottomLeft, newTopRight, newBottomRight)
    }

    /** Holds the merge helper. */
    companion object {
        /** Merges the left side of [leftBox] with the right side of [rightBox]; either may be null. */
        fun merge(leftBox: BoundingBox?, rightBox: BoundingBox?): BoundingBox? {
            if (leftBox == null) {
                return rightBox
            }
            if (rightBox == null) {
                return leftBox
            }
            return BoundingBox(leftBox.image, leftBox.topLeft, leftBox.bottomLeft, rightBox.topRight, rightBox.bottomRight)
        }
    }
}
