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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.decoder

import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitSource
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.ECIStringBuilder

/** Decodes Data Matrix codewords back into text across all encodation modes (ISO 16022:2006 5.2). */
internal object DecodedBitStreamParser {

    /** Encodation modes a codeword stream can switch between. */
    private enum class Mode {
        /** Padding reached; decoding stops. */
        PAD_ENCODE,

        /** ASCII encodation. */
        ASCII_ENCODE,

        /** C40 encodation. */
        C40_ENCODE,

        /** Text encodation. */
        TEXT_ENCODE,

        /** ANSI X12 encodation. */
        ANSIX12_ENCODE,

        /** EDIFACT encodation. */
        EDIFACT_ENCODE,

        /** Base 256 encodation. */
        BASE256_ENCODE,

        /** Extended Channel Interpretation designator. */
        ECI_ENCODE,
    }

    /** C40 basic set (Annex C Table C.1); '*' marks the shift placeholders. */
    private val C40_BASIC_SET_CHARS = charArrayOf(
        '*', '*', '*', ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
        'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    )

    /** C40 shift 2 set, shared with Text encodation. */
    private val C40_SHIFT2_SET_CHARS = charArrayOf(
        '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.',
        '/', ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_',
    )

    /** Text basic set (Annex C Table C.2); '*' marks the shift placeholders. */
    private val TEXT_BASIC_SET_CHARS = charArrayOf(
        '*', '*', '*', ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    )

    /** Text shift 2 set, identical to C40's. */
    private val TEXT_SHIFT2_SET_CHARS = C40_SHIFT2_SET_CHARS

    /** Text shift 3 set. */
    private val TEXT_SHIFT3_SET_CHARS = charArrayOf(
        '`', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
        'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '{', '|', '}', '~', 127.toChar(),
    )

    /** Decodes the error-corrected data codewords [bytes] into a [DecoderResult]. */
    fun decode(bytes: ByteArray): DecoderResult {
        val bits = BitSource(bytes)
        val result = ECIStringBuilder(100)
        val resultTrailer = StringBuilder(0)
        val byteSegments = ArrayList<ByteArray>(1)
        var mode = Mode.ASCII_ENCODE
        val fnc1Positions = HashSet<Int>()
        var isECIencoded = false
        do {
            if (mode == Mode.ASCII_ENCODE) {
                mode = decodeAsciiSegment(bits, result, resultTrailer, fnc1Positions)
            } else {
                when (mode) {
                    Mode.C40_ENCODE -> decodeC40Segment(bits, result, fnc1Positions)
                    Mode.TEXT_ENCODE -> decodeTextSegment(bits, result, fnc1Positions)
                    Mode.ANSIX12_ENCODE -> decodeAnsiX12Segment(bits, result)
                    Mode.EDIFACT_ENCODE -> decodeEdifactSegment(bits, result)
                    Mode.BASE256_ENCODE -> decodeBase256Segment(bits, result, byteSegments)
                    Mode.ECI_ENCODE -> {
                        decodeECISegment(bits, result)
                        isECIencoded = true
                    }
                    else -> throw FormatException.formatInstance
                }
                mode = Mode.ASCII_ENCODE
            }
        } while (mode != Mode.PAD_ENCODE && bits.available() > 0)
        if (resultTrailer.isNotEmpty()) {
            result.appendCharacters(resultTrailer)
        }
        return DecoderResult(
            bytes,
            result.toString(),
            if (byteSegments.isEmpty()) null else byteSegments,
            null,
            symbologyModifier(isECIencoded, fnc1Positions),
        )
    }

    /** Derives the AIM symbology modifier from ECI use and the FNC1 positions. */
    private fun symbologyModifier(isECIencoded: Boolean, fnc1Positions: Set<Int>): Int {
        val base = if (isECIencoded) 3 else 0
        return when {
            0 in fnc1Positions || 4 in fnc1Positions -> base + 2
            1 in fnc1Positions || 5 in fnc1Positions -> base + 3
            else -> base + 1
        }
    }

    /** Decodes an ASCII segment (5.2.3, Annex C Table C.2) and returns the next mode. */
    private fun decodeAsciiSegment(
        bits: BitSource,
        result: ECIStringBuilder,
        resultTrailer: StringBuilder,
        fnc1positions: MutableSet<Int>,
    ): Mode {
        var upperShift = false
        do {
            var oneByte = bits.readBits(8)
            if (oneByte == 0) {
                throw FormatException.formatInstance
            } else if (oneByte <= 128) {
                if (upperShift) {
                    oneByte += 128
                }
                result.append((oneByte - 1).toChar())
                return Mode.ASCII_ENCODE
            } else if (oneByte == 129) {
                return Mode.PAD_ENCODE
            } else if (oneByte <= 229) {
                val value = oneByte - 130
                if (value < 10) {
                    result.append('0')
                }
                result.append(value.toString())
            } else {
                when (oneByte) {
                    230 -> return Mode.C40_ENCODE
                    231 -> return Mode.BASE256_ENCODE
                    232 -> {
                        fnc1positions.add(result.length())
                        result.append(29.toChar())
                    }
                    233, 234 -> Unit
                    235 -> upperShift = true
                    236 -> {
                        result.append("[)>\u001E05\u001D")
                        resultTrailer.insert(0, "\u001E\u0004")
                    }
                    237 -> {
                        result.append("[)>\u001E06\u001D")
                        resultTrailer.insert(0, "\u001E\u0004")
                    }
                    238 -> return Mode.ANSIX12_ENCODE
                    239 -> return Mode.TEXT_ENCODE
                    240 -> return Mode.EDIFACT_ENCODE
                    241 -> return Mode.ECI_ENCODE
                    else -> if (oneByte != 254 || bits.available() != 0) {
                        throw FormatException.formatInstance
                    }
                }
            }
        } while (bits.available() > 0)
        return Mode.ASCII_ENCODE
    }

    /** Appends [c], shifted into the upper half when [upperShift] is set. */
    private fun appendShifted(result: ECIStringBuilder, c: Char, upperShift: Boolean) {
        result.append(if (upperShift) c + 128 else c)
    }

    /** Decodes a C40 segment (5.2.5, Annex C Table C.1). */
    private fun decodeC40Segment(bits: BitSource, result: ECIStringBuilder, fnc1positions: MutableSet<Int>) {
        var upperShift = false
        val cValues = IntArray(3)
        var shift = 0

        do {
            if (bits.available() == 8) {
                return
            }
            val firstByte = bits.readBits(8)
            if (firstByte == 254) {
                return
            }

            parseTwoBytes(firstByte, bits.readBits(8), cValues)

            for (i in 0 until 3) {
                val cValue = cValues[i]
                when (shift) {
                    0 -> if (cValue < 3) {
                        shift = cValue + 1
                    } else if (cValue < C40_BASIC_SET_CHARS.size) {
                        appendShifted(result, C40_BASIC_SET_CHARS[cValue], upperShift)
                        upperShift = false
                    } else {
                        throw FormatException.formatInstance
                    }
                    1 -> {
                        appendShifted(result, cValue.toChar(), upperShift)
                        upperShift = false
                        shift = 0
                    }
                    2 -> {
                        upperShift = decodeShift2(cValue, C40_SHIFT2_SET_CHARS, result, fnc1positions, upperShift)
                        shift = 0
                    }
                    3 -> {
                        result.append((cValue + if (upperShift) 224 else 96).toChar())
                        upperShift = false
                        shift = 0
                    }
                    else -> throw FormatException.formatInstance
                }
            }
        } while (bits.available() > 0)
    }

    /** Handles a shift 2 value shared by C40 and Text, returning the new upper-shift state. */
    private fun decodeShift2(
        cValue: Int,
        shift2Set: CharArray,
        result: ECIStringBuilder,
        fnc1positions: MutableSet<Int>,
        upperShift: Boolean,
    ): Boolean {
        if (cValue < shift2Set.size) {
            appendShifted(result, shift2Set[cValue], upperShift)
            return false
        }
        when (cValue) {
            27 -> {
                fnc1positions.add(result.length())
                result.append(29.toChar())
            }
            30 -> return true
            else -> throw FormatException.formatInstance
        }
        return upperShift
    }

    /** Decodes a Text segment (5.2.6, Annex C Table C.2). */
    private fun decodeTextSegment(bits: BitSource, result: ECIStringBuilder, fnc1positions: MutableSet<Int>) {
        var upperShift = false
        val cValues = IntArray(3)
        var shift = 0
        do {
            if (bits.available() == 8) {
                return
            }
            val firstByte = bits.readBits(8)
            if (firstByte == 254) {
                return
            }

            parseTwoBytes(firstByte, bits.readBits(8), cValues)

            for (i in 0 until 3) {
                val cValue = cValues[i]
                when (shift) {
                    0 -> if (cValue < 3) {
                        shift = cValue + 1
                    } else if (cValue < TEXT_BASIC_SET_CHARS.size) {
                        appendShifted(result, TEXT_BASIC_SET_CHARS[cValue], upperShift)
                        upperShift = false
                    } else {
                        throw FormatException.formatInstance
                    }
                    1 -> {
                        appendShifted(result, cValue.toChar(), upperShift)
                        upperShift = false
                        shift = 0
                    }
                    2 -> {
                        upperShift = decodeShift2(cValue, TEXT_SHIFT2_SET_CHARS, result, fnc1positions, upperShift)
                        shift = 0
                    }
                    3 -> if (cValue < TEXT_SHIFT3_SET_CHARS.size) {
                        appendShifted(result, TEXT_SHIFT3_SET_CHARS[cValue], upperShift)
                        upperShift = false
                        shift = 0
                    } else {
                        throw FormatException.formatInstance
                    }
                    else -> throw FormatException.formatInstance
                }
            }
        } while (bits.available() > 0)
    }

    /** Decodes an ANSI X12 segment (5.2.7). */
    private fun decodeAnsiX12Segment(bits: BitSource, result: ECIStringBuilder) {
        val cValues = IntArray(3)
        do {
            if (bits.available() == 8) {
                return
            }
            val firstByte = bits.readBits(8)
            if (firstByte == 254) {
                return
            }

            parseTwoBytes(firstByte, bits.readBits(8), cValues)

            for (i in 0 until 3) {
                val cValue = cValues[i]
                when {
                    cValue == 0 -> result.append('\r')
                    cValue == 1 -> result.append('*')
                    cValue == 2 -> result.append('>')
                    cValue == 3 -> result.append(' ')
                    cValue < 14 -> result.append((cValue + 44).toChar())
                    cValue < 40 -> result.append((cValue + 51).toChar())
                    else -> throw FormatException.formatInstance
                }
            }
        } while (bits.available() > 0)
    }

    /** Splits a 16-bit C40/Text/X12 pair into its three values. */
    private fun parseTwoBytes(firstByte: Int, secondByte: Int, result: IntArray) {
        var fullBitValue = (firstByte shl 8) + secondByte - 1
        var temp = fullBitValue / 1600
        result[0] = temp
        fullBitValue -= temp * 1600
        temp = fullBitValue / 40
        result[1] = temp
        result[2] = fullBitValue - temp * 40
    }

    /** Decodes an EDIFACT segment (5.2.8, Annex C Table C.3). */
    private fun decodeEdifactSegment(bits: BitSource, result: ECIStringBuilder) {
        do {
            if (bits.available() <= 16) {
                return
            }

            for (i in 0 until 4) {
                var edifactValue = bits.readBits(6)

                if (edifactValue == 0x1F) {
                    val bitsLeft = 8 - bits.bitOffset
                    if (bitsLeft != 8) {
                        bits.readBits(bitsLeft)
                    }
                    return
                }

                if ((edifactValue and 0x20) == 0) {
                    edifactValue = edifactValue or 0x40
                }
                result.append(edifactValue.toChar())
            }
        } while (bits.available() > 0)
    }

    /** Decodes a Base 256 segment (5.2.9, Annex B B.2) and records its bytes. */
    private fun decodeBase256Segment(
        bits: BitSource,
        result: ECIStringBuilder,
        byteSegments: MutableCollection<ByteArray>,
    ) {
        var codewordPosition = 1 + bits.byteOffset
        val d1 = unrandomize255State(bits.readBits(8), codewordPosition++)
        val count = when {
            d1 == 0 -> bits.available() / 8
            d1 < 250 -> d1
            else -> 250 * (d1 - 249) + unrandomize255State(bits.readBits(8), codewordPosition++)
        }

        if (count < 0) {
            throw FormatException.formatInstance
        }

        val bytes = ByteArray(count)
        for (i in 0 until count) {
            if (bits.available() < 8) {
                throw FormatException.formatInstance
            }
            bytes[i] = unrandomize255State(bits.readBits(8), codewordPosition++).toByte()
        }
        byteSegments.add(bytes)
        result.append(Charsets.ISO_8859_1.decode(bytes))
    }

    /** Decodes a single-byte character-set ECI designator (ISO 16022:2007 5.4.1). */
    private fun decodeECISegment(bits: BitSource, result: ECIStringBuilder) {
        if (bits.available() < 8) {
            throw FormatException.formatInstance
        }
        val c1 = bits.readBits(8)
        if (c1 <= 127) {
            result.appendECI(c1 - 1)
        }
    }

    /** Reverses the 255-state randomisation of Base 256 codewords (Annex B B.2). */
    private fun unrandomize255State(randomizedBase256Codeword: Int, base256CodewordPosition: Int): Int {
        val pseudoRandomNumber = ((149 * base256CodewordPosition) % 255) + 1
        val tempVariable = randomizedBase256Codeword - pseudoRandomNumber
        return if (tempVariable >= 0) tempVariable else tempVariable + 256
    }
}
