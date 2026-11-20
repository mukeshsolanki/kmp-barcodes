/*
 * Copyright 2012 ZXing authors
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

/** Simply encapsulates a width and height. */
internal class Dimension(
    /** The width; never negative. */
    val width: Int,
    /** The height; never negative. */
    val height: Int,
) {
    init {
        require(width >= 0 && height >= 0)
    }

    /** Equal when both dimensions match. */
    override fun equals(other: Any?): Boolean = other is Dimension && width == other.width && height == other.height

    /** zxing's hash: `width * 32713 + height`. */
    override fun hashCode(): Int = width * 32713 + height

    /** Formats as `WxH`. */
    override fun toString(): String = "${width}x$height"
}
