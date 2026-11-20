/*
 * Copyright 2011 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder

/**
 * Holds all of the module rows of a PDF417 barcode.
 */
internal class BarcodeMatrix(
    /** Number of rows. */
    private val height: Int,
    /** Number of data columns. */
    width: Int,
) {

    /** The rows, each wide enough for start, stop, row indicators and data. */
    private val rows: Array<BarcodeRow> = Array(height) { BarcodeRow((width + 4) * 17 + 1) }

    /** Index of the row being written. */
    private var currentRowIndex: Int = -1

    /** Sets the module at ([x], [y]) to [value]. */
    fun set(x: Int, y: Int, value: Byte) {
        rows[y].set(x, value)
    }

    /** Advances to the next row. */
    fun startRow() {
        ++currentRowIndex
    }

    /** The row currently being written. */
    val currentRow: BarcodeRow get() = rows[currentRowIndex]

    /** The unscaled matrix, bottom row first as zxing returns it. */
    val matrix: Array<ByteArray> get() = getScaledMatrix(1, 1)

    /** Returns the matrix scaled by [xScale] horizontally and [yScale] vertically, rows in reverse order. */
    fun getScaledMatrix(xScale: Int, yScale: Int): Array<ByteArray> {
        val yMax = height * yScale
        return Array(yMax) { k -> rows[(yMax - k - 1) / yScale].getScaledRow(xScale) }
    }
}
