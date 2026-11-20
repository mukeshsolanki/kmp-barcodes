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

/**
 * The four QR Code error correction levels. See ISO 18004:2006, 6.5.1.
 */
internal enum class ErrorCorrectionLevel(
    /** The two-bit value encoding this level in format information. */
    val bits: Int,
) {
    /** L = ~7% correction. */
    L(0x01),

    /** M = ~15% correction. */
    M(0x00),

    /** Q = ~25% correction. */
    Q(0x03),

    /** H = ~30% correction. */
    H(0x02),
    ;

    /** Lookup from encoded bits to level. */
    companion object {
        /** Levels indexed by their encoded two-bit value. */
        private val FOR_BITS = arrayOf(M, L, H, Q)

        /** Returns the level encoded by [bits]; throws [IllegalArgumentException] outside 0..3. */
        fun forBits(bits: Int): ErrorCorrectionLevel {
            if (bits < 0 || bits >= FOR_BITS.size) {
                throw IllegalArgumentException()
            }
            return FOR_BITS[bits]
        }
    }
}
