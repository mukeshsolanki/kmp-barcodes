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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.common

import kotlin.math.min

/**
 * Reads bits at a time from a byte sequence, first byte first, most significant bit first.
 */
internal class BitSource(
    /** Bytes to read from. */
    private val bytes: ByteArray,
) {

    /** Index of the next bit in the current byte to be read by [readBits]. */
    var bitOffset: Int = 0
        private set

    /** Index of the next byte to be read by [readBits]. */
    var byteOffset: Int = 0
        private set

    /** Reads [numBits] (1..32) bits and returns them as the least significant bits of an int. */
    fun readBits(numBits: Int): Int {
        if (numBits < 1 || numBits > 32 || numBits > available()) {
            throw IllegalArgumentException(numBits.toString())
        }
        var remaining = numBits
        var result = 0
        if (bitOffset > 0) {
            val bitsLeft = 8 - bitOffset
            val toRead = min(remaining, bitsLeft)
            val bitsToNotRead = bitsLeft - toRead
            val mask = (0xFF shr (8 - toRead)) shl bitsToNotRead
            result = (bytes[byteOffset].toInt() and mask) shr bitsToNotRead
            remaining -= toRead
            bitOffset += toRead
            if (bitOffset == 8) {
                bitOffset = 0
                byteOffset++
            }
        }
        if (remaining > 0) {
            while (remaining >= 8) {
                result = (result shl 8) or (bytes[byteOffset].toInt() and 0xFF)
                byteOffset++
                remaining -= 8
            }
            if (remaining > 0) {
                val bitsToNotRead = 8 - remaining
                val mask = (0xFF shr bitsToNotRead) shl bitsToNotRead
                result = (result shl remaining) or ((bytes[byteOffset].toInt() and mask) shr bitsToNotRead)
                bitOffset += remaining
            }
        }
        return result
    }

    /** Number of bits that can still be read. */
    fun available(): Int = 8 * (bytes.size - byteOffset) - bitOffset
}
