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

/**
 * One decoded codeword with its pixel span, cluster bucket and (once known) row number.
 */
internal class Codeword(
    /** First pixel column of the codeword. */
    val startX: Int,
    /** Pixel column just past the codeword. */
    val endX: Int,
    /** Cluster bucket (0, 3 or 6). */
    val bucket: Int,
    /** Codeword value (0-928). */
    val value: Int,
) {

    /** Barcode row number, or [BARCODE_ROW_UNKNOWN]. */
    var rowNumber: Int = BARCODE_ROW_UNKNOWN

    /** True when [rowNumber] is consistent with [bucket]. */
    fun hasValidRowNumber(): Boolean = isValidRowNumber(rowNumber)

    /** True when [rowNumber] is known and consistent with [bucket]. */
    fun isValidRowNumber(rowNumber: Int): Boolean = rowNumber != BARCODE_ROW_UNKNOWN && bucket == (rowNumber % 3) * 3

    /** Derives [rowNumber] from a row indicator codeword value. */
    fun setRowNumberAsRowIndicatorColumn() {
        rowNumber = (value / 30) * 3 + bucket / 3
    }

    /** Width in pixels. */
    val width: Int get() = endX - startX

    /** Renders as `rowNumber|value`. */
    override fun toString(): String = "$rowNumber|$value"

    /** Holds the unknown row marker. */
    private companion object {
        /** Marker for an unknown row number. */
        const val BARCODE_ROW_UNKNOWN = -1
    }
}
