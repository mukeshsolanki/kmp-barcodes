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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.aztec.encoder

import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/**
 * A token that emits a run of raw bytes in Binary Shift mode, with the B/S headers it needs.
 */
internal class BinaryShiftToken(
    previous: Token?,
    /** Index of the first byte of the run in the input text. */
    private val binaryShiftStart: Int,
    /** Number of bytes in the run. */
    private val binaryShiftByteCount: Int,
) : Token(previous) {

    /** Appends the B/S header(s) and every byte of the run. */
    override fun appendTo(bitArray: BitArray, text: ByteArray) {
        val bsbc = binaryShiftByteCount
        for (i in 0 until bsbc) {
            if (i == 0 || (i == 31 && bsbc <= 62)) {
                appendHeader(bitArray, bsbc, i)
            }
            bitArray.appendBits(text[binaryShiftStart + i].toInt(), 8)
        }
    }

    /** Writes the B/S code plus its length field before byte [i] of a run of [bsbc] bytes. */
    private fun appendHeader(bitArray: BitArray, bsbc: Int, i: Int) {
        bitArray.appendBits(31, 5)
        if (bsbc > 62) {
            bitArray.appendBits(bsbc - 31, 16)
        } else if (i == 0) {
            bitArray.appendBits(minOf(bsbc, 31), 5)
        } else {
            bitArray.appendBits(bsbc - 31, 5)
        }
    }
}
