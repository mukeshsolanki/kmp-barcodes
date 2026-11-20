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
 * A token holding a fixed value of a fixed bit width.
 */
internal class SimpleToken(previous: Token?, value: Int, bitCount: Int) : Token(previous) {

    /** The value to emit, truncated to 16 bits like zxing. */
    private val value: Short = value.toShort()

    /** The number of bits of [value] to emit, truncated to 16 bits like zxing. */
    private val bitCount: Short = bitCount.toShort()

    /** Appends [value] using [bitCount] bits. */
    override fun appendTo(bitArray: BitArray, text: ByteArray) {
        bitArray.appendBits(value.toInt(), bitCount.toInt())
    }
}
