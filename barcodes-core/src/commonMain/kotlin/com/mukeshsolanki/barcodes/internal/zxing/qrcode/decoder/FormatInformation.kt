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
 * A QR Code's format information: the data mask and error correction level.
 */
internal class FormatInformation private constructor(formatInfo: Int) {

    /** The error correction level from bits 3-4. */
    val errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.forBits((formatInfo shr 3) and 0x03)

    /** The data mask index from the bottom 3 bits. */
    val dataMask: Byte = (formatInfo and 0x07).toByte()

    /** Hashes the level ordinal and data mask like zxing. */
    override fun hashCode(): Int = (errorCorrectionLevel.ordinal shl 3) or dataMask.toInt()

    /** Equal when level and data mask match. */
    override fun equals(other: Any?): Boolean {
        if (other !is FormatInformation) {
            return false
        }
        return errorCorrectionLevel == other.errorCorrectionLevel && dataMask == other.dataMask
    }

    /** Format information decoding tables and helpers. */
    companion object {
        /** XOR mask applied to format information. */
        private const val FORMAT_INFO_MASK_QR = 0x5412

        /** Masked format info to unmasked 5-bit value. See ISO 18004:2006, Annex C, Table C.1. */
        private val FORMAT_INFO_DECODE_LOOKUP = arrayOf(
            intArrayOf(0x5412, 0x00),
            intArrayOf(0x5125, 0x01),
            intArrayOf(0x5E7C, 0x02),
            intArrayOf(0x5B4B, 0x03),
            intArrayOf(0x45F9, 0x04),
            intArrayOf(0x40CE, 0x05),
            intArrayOf(0x4F97, 0x06),
            intArrayOf(0x4AA0, 0x07),
            intArrayOf(0x77C4, 0x08),
            intArrayOf(0x72F3, 0x09),
            intArrayOf(0x7DAA, 0x0A),
            intArrayOf(0x789D, 0x0B),
            intArrayOf(0x662F, 0x0C),
            intArrayOf(0x6318, 0x0D),
            intArrayOf(0x6C41, 0x0E),
            intArrayOf(0x6976, 0x0F),
            intArrayOf(0x1689, 0x10),
            intArrayOf(0x13BE, 0x11),
            intArrayOf(0x1CE7, 0x12),
            intArrayOf(0x19D0, 0x13),
            intArrayOf(0x0762, 0x14),
            intArrayOf(0x0255, 0x15),
            intArrayOf(0x0D0C, 0x16),
            intArrayOf(0x083B, 0x17),
            intArrayOf(0x355F, 0x18),
            intArrayOf(0x3068, 0x19),
            intArrayOf(0x3F31, 0x1A),
            intArrayOf(0x3A06, 0x1B),
            intArrayOf(0x24B4, 0x1C),
            intArrayOf(0x2183, 0x1D),
            intArrayOf(0x2EDA, 0x1E),
            intArrayOf(0x2BED, 0x1F),
        )

        /** Returns the Hamming distance between [a] and [b]. */
        fun numBitsDiffering(a: Int, b: Int): Int = (a xor b).countOneBits()

        /** Decodes two masked copies of format info, retrying unmasked; null when neither matches. */
        fun decodeFormatInformation(maskedFormatInfo1: Int, maskedFormatInfo2: Int): FormatInformation? =
            doDecodeFormatInformation(maskedFormatInfo1, maskedFormatInfo2)
                ?: doDecodeFormatInformation(
                    maskedFormatInfo1 xor FORMAT_INFO_MASK_QR,
                    maskedFormatInfo2 xor FORMAT_INFO_MASK_QR,
                )

        /** Finds the table entry closest to either input, accepting up to 3 differing bits. */
        private fun doDecodeFormatInformation(maskedFormatInfo1: Int, maskedFormatInfo2: Int): FormatInformation? {
            var bestDifference = Int.MAX_VALUE
            var bestFormatInfo = 0
            for (decodeInfo in FORMAT_INFO_DECODE_LOOKUP) {
                val targetInfo = decodeInfo[0]
                if (targetInfo == maskedFormatInfo1 || targetInfo == maskedFormatInfo2) {
                    return FormatInformation(decodeInfo[1])
                }
                var bitsDifference = numBitsDiffering(maskedFormatInfo1, targetInfo)
                if (bitsDifference < bestDifference) {
                    bestFormatInfo = decodeInfo[1]
                    bestDifference = bitsDifference
                }
                if (maskedFormatInfo1 != maskedFormatInfo2) {
                    bitsDifference = numBitsDiffering(maskedFormatInfo2, targetInfo)
                    if (bitsDifference < bestDifference) {
                        bestFormatInfo = decodeInfo[1]
                        bestDifference = bitsDifference
                    }
                }
            }
            if (bestDifference <= 3) {
                return FormatInformation(bestFormatInfo)
            }
            return null
        }
    }
}
