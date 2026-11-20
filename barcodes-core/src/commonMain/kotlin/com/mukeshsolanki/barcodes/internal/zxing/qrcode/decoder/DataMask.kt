/*
 * Copyright 2007 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder

import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * The eight QR data masks (ISO 18004:2006 6.8.1). i is the row and j the column.
 */
internal enum class DataMask {
    /** 000: (i + j) mod 2 == 0. */
    DATA_MASK_000 {
        override fun isMasked(i: Int, j: Int): Boolean = ((i + j) and 0x01) == 0
    },

    /** 001: i mod 2 == 0. */
    DATA_MASK_001 {
        override fun isMasked(i: Int, j: Int): Boolean = (i and 0x01) == 0
    },

    /** 010: j mod 3 == 0. */
    DATA_MASK_010 {
        override fun isMasked(i: Int, j: Int): Boolean = j % 3 == 0
    },

    /** 011: (i + j) mod 3 == 0. */
    DATA_MASK_011 {
        override fun isMasked(i: Int, j: Int): Boolean = (i + j) % 3 == 0
    },

    /** 100: (i/2 + j/3) mod 2 == 0. */
    DATA_MASK_100 {
        override fun isMasked(i: Int, j: Int): Boolean = (((i / 2) + (j / 3)) and 0x01) == 0
    },

    /** 101: ij mod 6 == 0. */
    DATA_MASK_101 {
        override fun isMasked(i: Int, j: Int): Boolean = (i * j) % 6 == 0
    },

    /** 110: ij mod 6 < 3. */
    DATA_MASK_110 {
        override fun isMasked(i: Int, j: Int): Boolean = ((i * j) % 6) < 3
    },

    /** 111: (i + j + ij mod 3) mod 2 == 0. */
    DATA_MASK_111 {
        override fun isMasked(i: Int, j: Int): Boolean = ((i + j + ((i * j) % 3)) and 0x01) == 0
    },
    ;

    /** Flips every masked module of the top-left [dimension] x [dimension] region of [bits]. */
    fun unmaskBitMatrix(bits: BitMatrix, dimension: Int) {
        for (i in 0 until dimension) {
            for (j in 0 until dimension) {
                if (isMasked(i, j)) {
                    bits.flip(j, i)
                }
            }
        }
    }

    /** Returns true when the module at row [i], column [j] is masked. */
    abstract fun isMasked(i: Int, j: Int): Boolean
}
