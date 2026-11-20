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
 * One row of a PDF417 barcode, as module values (1 = black, 0 = white).
 */
internal class BarcodeRow(width: Int) {

    /** Module values of this row. */
    private val row: ByteArray = ByteArray(width)

    /** Position of the next bar to be added. */
    private var currentLocation: Int = 0

    /** Sets the module at [x] to [value]. */
    fun set(x: Int, value: Byte) {
        row[x] = value
    }

    /** Sets the module at [x] to black or white. */
    private fun set(x: Int, black: Boolean) {
        row[x] = (if (black) 1 else 0).toByte()
    }

    /** Appends a bar of [width] modules, black if [black]. */
    fun addBar(black: Boolean, width: Int) {
        for (ii in 0 until width) {
            set(currentLocation++, black)
        }
    }

    /** Returns this row with every module repeated [scale] times. */
    fun getScaledRow(scale: Int): ByteArray {
        val output = ByteArray(row.size * scale)
        for (i in output.indices) {
            output[i] = row[i / scale]
        }
        return output
    }
}
