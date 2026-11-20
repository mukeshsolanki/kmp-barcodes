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
 * A node in the immutable, backwards-linked list of output tokens built by the high-level encoder.
 */
internal abstract class Token(
    /** The token emitted before this one, or null for the head of the list. */
    val previous: Token?,
) {

    /** Returns a new token that appends [bitCount] bits of [value] after this one. */
    fun add(value: Int, bitCount: Int): Token = SimpleToken(this, value, bitCount)

    /** Returns a new token that appends a binary shift of [byteCount] bytes starting at [start]. */
    fun addBinaryShift(start: Int, byteCount: Int): Token = BinaryShiftToken(this, start, byteCount)

    /** Writes this token's bits to [bitArray], reading raw bytes from [text] when needed. */
    abstract fun appendTo(bitArray: BitArray, text: ByteArray)

    /** Shared statics. */
    companion object {
        /** The empty head token every encoding starts from. */
        val EMPTY: Token = SimpleToken(null, 0, 0)
    }
}
