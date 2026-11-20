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
package com.mukeshsolanki.barcodes.internal.zxing

import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/** The 1-bit bitmap that readers decode, backed by a [Binarizer]. */
internal class BinaryBitmap(
    /** Binarizer producing the bits. */
    private val binarizer: Binarizer,
) {
    /** Lazily computed, cached 2D bits. */
    private var matrix: BitMatrix? = null

    /** Width of the bitmap. */
    val width: Int get() = binarizer.width

    /** Height of the bitmap. */
    val height: Int get() = binarizer.height

    /** Converts row [y] to 1-bit data, reusing [row] when possible; throws [NotFoundException] when it can't. */
    fun getBlackRow(y: Int, row: BitArray?): BitArray = binarizer.getBlackRow(y, row)

    /** The 2D bits, computed on first access and cached; throws [NotFoundException] when it can't. */
    val blackMatrix: BitMatrix
        get() = matrix ?: binarizer.blackMatrix.also { matrix = it }

    /** Whether this bitmap can be cropped. */
    val isCropSupported: Boolean get() = binarizer.luminanceSource.isCropSupported

    /** Returns a new bitmap over the given rectangle; only callable when [isCropSupported]. */
    fun crop(left: Int, top: Int, width: Int, height: Int): BinaryBitmap {
        val newSource = binarizer.luminanceSource.crop(left, top, width, height)
        return BinaryBitmap(binarizer.createBinarizer(newSource))
    }

    /** Whether this bitmap supports counter-clockwise rotation. */
    val isRotateSupported: Boolean get() = binarizer.luminanceSource.isRotateSupported

    /** Returns a new bitmap rotated 90 degrees counter-clockwise; only callable when [isRotateSupported]. */
    fun rotateCounterClockwise(): BinaryBitmap {
        val newSource = binarizer.luminanceSource.rotateCounterClockwise()
        return BinaryBitmap(binarizer.createBinarizer(newSource))
    }

    /** Returns a new bitmap rotated 45 degrees counter-clockwise; only callable when [isRotateSupported]. */
    fun rotateCounterClockwise45(): BinaryBitmap {
        val newSource = binarizer.luminanceSource.rotateCounterClockwise45()
        return BinaryBitmap(binarizer.createBinarizer(newSource))
    }

    /** Renders the black matrix, or an empty string when it can't be computed. */
    override fun toString(): String = try {
        blackMatrix.toString()
    } catch (e: NotFoundException) {
        ""
    }
}
