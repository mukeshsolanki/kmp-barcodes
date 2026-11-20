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

/** Converts luminance data to 1-bit data; implementations trade speed against thresholding quality. */
internal abstract class Binarizer protected constructor(
    /** The luminance source being binarized. */
    val luminanceSource: LuminanceSource,
) {
    /** Converts row [y] to 1-bit data, reusing [row] when possible; throws [NotFoundException] when it can't. */
    abstract fun getBlackRow(y: Int, row: BitArray?): BitArray

    /** Converts the whole image to 1-bit data; throws [NotFoundException] when it can't. Expensive: call sparingly. */
    abstract val blackMatrix: BitMatrix

    /** Creates a pristine binarizer of the same type over [source]. */
    abstract fun createBinarizer(source: LuminanceSource): Binarizer

    /** Width of the source. */
    val width: Int get() = luminanceSource.width

    /** Height of the source. */
    val height: Int get() = luminanceSource.height
}
