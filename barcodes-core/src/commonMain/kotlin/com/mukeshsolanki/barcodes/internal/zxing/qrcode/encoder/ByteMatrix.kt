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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder

/**
 * A byte grid holding -1 (unset), 0 (light) or 1 (dark) per module.
 */
internal class ByteMatrix(
    /** Width in modules. */
    val width: Int,
    /** Height in modules. */
    val height: Int,
) {
    /** Row-major storage; array[y][x] is point (x, y). */
    val array: Array<ByteArray> = Array(height) { ByteArray(width) }

    /** Returns the value at ([x], [y]). */
    fun get(x: Int, y: Int): Byte = array[y][x]

    /** Sets ([x], [y]) to [value]. */
    fun set(x: Int, y: Int, value: Byte) {
        array[y][x] = value
    }

    /** Sets ([x], [y]) to [value] truncated to a byte. */
    fun set(x: Int, y: Int, value: Int) {
        array[y][x] = value.toByte()
    }

    /** Sets ([x], [y]) to 1 when [value] is true, else 0. */
    fun set(x: Int, y: Int, value: Boolean) {
        array[y][x] = (if (value) 1 else 0).toByte()
    }

    /** Fills every cell with [value]. */
    fun clear(value: Byte) {
        for (aByte in array) {
            aByte.fill(value)
        }
    }

    /** Renders the matrix as " 0"/" 1"/"  " cells, one row per line. */
    override fun toString(): String {
        val result = StringBuilder(2 * width * height + 2)
        for (y in 0 until height) {
            val bytesY = array[y]
            for (x in 0 until width) {
                result.append(
                    when (bytesY[x].toInt()) {
                        0 -> " 0"
                        1 -> " 1"
                        else -> "  "
                    },
                )
            }
            result.append('\n')
        }
        return result.toString()
    }
}
