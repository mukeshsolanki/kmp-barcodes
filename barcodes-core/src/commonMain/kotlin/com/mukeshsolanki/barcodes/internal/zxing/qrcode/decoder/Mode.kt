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
 * QR Code data modes. See ISO 18004:2006, 6.4.1, Tables 2 and 3.
 */
internal enum class Mode(
    /** Character count bit widths for versions 1-9, 10-26 and 27-40. */
    private val characterCountBitsForVersions: IntArray,
    /** The four-bit mode indicator. */
    val bits: Int,
) {
    /** End of data; not really a mode. */
    TERMINATOR(intArrayOf(0, 0, 0), 0x00),

    /** Digits 0-9. */
    NUMERIC(intArrayOf(10, 12, 14), 0x01),

    /** The 45-character alphanumeric set. */
    ALPHANUMERIC(intArrayOf(9, 11, 13), 0x02),

    /** Structured append header; not supported. */
    STRUCTURED_APPEND(intArrayOf(0, 0, 0), 0x03),

    /** 8-bit bytes. */
    BYTE(intArrayOf(8, 16, 16), 0x04),

    /** ECI designator; character counts don't apply. */
    ECI(intArrayOf(0, 0, 0), 0x07),

    /** Shift_JIS double-byte Kanji. */
    KANJI(intArrayOf(8, 10, 12), 0x08),

    /** FNC1 in first position (GS1). */
    FNC1_FIRST_POSITION(intArrayOf(0, 0, 0), 0x05),

    /** FNC1 in second position (AIM). */
    FNC1_SECOND_POSITION(intArrayOf(0, 0, 0), 0x09),

    /** GB2312 Hanzi, see GBT 18284-2000. */
    HANZI(intArrayOf(8, 10, 12), 0x0D),
    ;

    /** Returns the number of bits used to encode the character count in this mode for [version]. */
    fun getCharacterCountBits(version: Version): Int {
        val number = version.versionNumber
        val offset = when {
            number <= 9 -> 0
            number <= 26 -> 1
            else -> 2
        }
        return characterCountBitsForVersions[offset]
    }

    /** Lookup from mode indicator bits to mode. */
    companion object {
        /** Returns the mode for the four-bit indicator [bits]; throws [IllegalArgumentException] if unknown. */
        fun forBits(bits: Int): Mode = when (bits) {
            0x0 -> TERMINATOR
            0x1 -> NUMERIC
            0x2 -> ALPHANUMERIC
            0x3 -> STRUCTURED_APPEND
            0x4 -> BYTE
            0x5 -> FNC1_FIRST_POSITION
            0x7 -> ECI
            0x8 -> KANJI
            0x9 -> FNC1_SECOND_POSITION
            0xD -> HANZI
            else -> throw IllegalArgumentException()
        }
    }
}
