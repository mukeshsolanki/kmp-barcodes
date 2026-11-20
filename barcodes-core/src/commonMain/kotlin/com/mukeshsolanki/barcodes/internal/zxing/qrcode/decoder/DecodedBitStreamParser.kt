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

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitSource
import com.mukeshsolanki.barcodes.internal.zxing.common.CharacterSetECI
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.StringUtils

/**
 * Decodes the bit stream of a QR Code's data codewords back into text.
 * See ISO 18004:2006, 6.4.3 - 6.4.7.
 */
internal object DecodedBitStreamParser {

    /** The alphanumeric mode character set. See ISO 18004:2006, 6.4.4 Table 5. */
    private val ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:".toCharArray()

    /** Hanzi subset indicator for GB2312; such segments throw because GB2312 is not bundled. */
    private const val GB2312_SUBSET = 1

    /** Parses [bytes] for [version] and [ecLevel]; throws [FormatException] on malformed data. */
    fun decode(
        bytes: ByteArray,
        version: Version,
        ecLevel: ErrorCorrectionLevel?,
        hints: Map<DecodeHintType, Any?>?,
    ): DecoderResult {
        val bits = BitSource(bytes)
        val result = StringBuilder(50)
        val byteSegments = ArrayList<ByteArray>(1)
        var symbolSequence = -1
        var parityData = -1
        val symbologyModifier = try {
            var currentCharacterSetECI: CharacterSetECI? = null
            var fc1InEffect = false
            var hasFNC1first = false
            var hasFNC1second = false
            var mode: Mode
            do {
                mode = if (bits.available() < 4) Mode.TERMINATOR else Mode.forBits(bits.readBits(4))
                when (mode) {
                    Mode.TERMINATOR -> Unit
                    Mode.FNC1_FIRST_POSITION -> {
                        hasFNC1first = true
                        fc1InEffect = true
                    }
                    Mode.FNC1_SECOND_POSITION -> {
                        hasFNC1second = true
                        fc1InEffect = true
                    }
                    Mode.STRUCTURED_APPEND -> {
                        if (bits.available() < 16) {
                            throw FormatException.formatInstance
                        }
                        symbolSequence = bits.readBits(8)
                        parityData = bits.readBits(8)
                    }
                    Mode.ECI -> {
                        val value = parseECIValue(bits)
                        currentCharacterSetECI = CharacterSetECI.getCharacterSetECIByValue(value)
                            ?: throw FormatException.formatInstance
                    }
                    Mode.HANZI -> {
                        val subset = bits.readBits(4)
                        bits.readBits(mode.getCharacterCountBits(version))
                        if (subset == GB2312_SUBSET) {
                            throw FormatException.formatInstance
                        }
                    }
                    else -> {
                        val count = bits.readBits(mode.getCharacterCountBits(version))
                        when (mode) {
                            Mode.NUMERIC -> decodeNumericSegment(bits, result, count)
                            Mode.ALPHANUMERIC -> decodeAlphanumericSegment(bits, result, count, fc1InEffect)
                            Mode.BYTE -> decodeByteSegment(bits, result, count, currentCharacterSetECI, byteSegments, hints)
                            Mode.KANJI -> decodeKanjiSegment(bits, result, count)
                            else -> throw FormatException.formatInstance
                        }
                    }
                }
            } while (mode != Mode.TERMINATOR)
            symbologyModifierFor(currentCharacterSetECI != null, hasFNC1first, hasFNC1second)
        } catch (iae: IllegalArgumentException) {
            throw FormatException.formatInstance
        }
        return DecoderResult(
            bytes,
            result.toString(),
            if (byteSegments.isEmpty()) null else byteSegments,
            ecLevel?.toString(),
            symbolSequence,
            parityData,
            symbologyModifier,
        )
    }

    /** Returns the AIM symbology modifier for the ECI and FNC1 flags seen while parsing. */
    private fun symbologyModifierFor(hasECI: Boolean, hasFNC1first: Boolean, hasFNC1second: Boolean): Int =
        if (hasECI) {
            if (hasFNC1first) 4 else if (hasFNC1second) 6 else 2
        } else {
            if (hasFNC1first) 3 else if (hasFNC1second) 5 else 1
        }

    /** Decodes [count] 13-bit Kanji characters via Shift_JIS into [result]. */
    private fun decodeKanjiSegment(bits: BitSource, result: StringBuilder, count: Int) {
        if (count * 13 > bits.available()) {
            throw FormatException.formatInstance
        }
        val buffer = ByteArray(2 * count)
        var offset = 0
        var remaining = count
        while (remaining > 0) {
            val twoBytes = bits.readBits(13)
            var assembledTwoBytes = ((twoBytes / 0x0C0) shl 8) or (twoBytes % 0x0C0)
            assembledTwoBytes += if (assembledTwoBytes < 0x01F00) 0x08140 else 0x0C140
            buffer[offset] = (assembledTwoBytes shr 8).toByte()
            buffer[offset + 1] = assembledTwoBytes.toByte()
            offset += 2
            remaining--
        }
        result.append(Charsets.SHIFT_JIS.decode(buffer))
    }

    /** Decodes [count] bytes using the active ECI charset or a guessed one, recording the raw segment. */
    private fun decodeByteSegment(
        bits: BitSource,
        result: StringBuilder,
        count: Int,
        currentCharacterSetECI: CharacterSetECI?,
        byteSegments: MutableCollection<ByteArray>,
        hints: Map<DecodeHintType, Any?>?,
    ) {
        if (8 * count > bits.available()) {
            throw FormatException.formatInstance
        }
        val readBytes = ByteArray(count)
        for (i in 0 until count) {
            readBytes[i] = bits.readBits(8).toByte()
        }
        val encoding: Charset = if (currentCharacterSetECI == null) {
            StringUtils.guessCharset(readBytes, hints)
        } else {
            currentCharacterSetECI.charset ?: Charsets.ISO_8859_1
        }
        result.append(encoding.decode(readBytes))
        byteSegments.add(readBytes)
    }

    /** Maps a 0-44 value to its alphanumeric character; throws [FormatException] when out of range. */
    private fun toAlphaNumericChar(value: Int): Char {
        if (value >= ALPHANUMERIC_CHARS.size) {
            throw FormatException.formatInstance
        }
        return ALPHANUMERIC_CHARS[value]
    }

    /** Decodes [count] alphanumeric characters, applying FNC1 '%' rules when [fc1InEffect]. */
    private fun decodeAlphanumericSegment(bits: BitSource, result: StringBuilder, count: Int, fc1InEffect: Boolean) {
        val start = result.length
        var remaining = count
        while (remaining > 1) {
            if (bits.available() < 11) {
                throw FormatException.formatInstance
            }
            val nextTwoCharsBits = bits.readBits(11)
            result.append(toAlphaNumericChar(nextTwoCharsBits / 45))
            result.append(toAlphaNumericChar(nextTwoCharsBits % 45))
            remaining -= 2
        }
        if (remaining == 1) {
            if (bits.available() < 6) {
                throw FormatException.formatInstance
            }
            result.append(toAlphaNumericChar(bits.readBits(6)))
        }
        if (fc1InEffect) {
            applyFnc1PercentRules(result, start)
        }
    }

    /** Renders "%%" as '%' and a lone '%' as the GS separator 0x1D from [start] on. See 6.4.8.1-2. */
    private fun applyFnc1PercentRules(result: StringBuilder, start: Int) {
        var i = start
        while (i < result.length) {
            if (result[i] == '%') {
                if (i < result.length - 1 && result[i + 1] == '%') {
                    result.deleteAt(i + 1)
                } else {
                    result[i] = '\u001D'
                }
            }
            i++
        }
    }

    /** Decodes [count] digits packed three per 10 bits into [result]. */
    private fun decodeNumericSegment(bits: BitSource, result: StringBuilder, count: Int) {
        var remaining = count
        while (remaining >= 3) {
            if (bits.available() < 10) {
                throw FormatException.formatInstance
            }
            val threeDigitsBits = bits.readBits(10)
            if (threeDigitsBits >= 1000) {
                throw FormatException.formatInstance
            }
            result.append(toAlphaNumericChar(threeDigitsBits / 100))
            result.append(toAlphaNumericChar((threeDigitsBits / 10) % 10))
            result.append(toAlphaNumericChar(threeDigitsBits % 10))
            remaining -= 3
        }
        if (remaining == 2) {
            if (bits.available() < 7) {
                throw FormatException.formatInstance
            }
            val twoDigitsBits = bits.readBits(7)
            if (twoDigitsBits >= 100) {
                throw FormatException.formatInstance
            }
            result.append(toAlphaNumericChar(twoDigitsBits / 10))
            result.append(toAlphaNumericChar(twoDigitsBits % 10))
        } else if (remaining == 1) {
            if (bits.available() < 4) {
                throw FormatException.formatInstance
            }
            val digitBits = bits.readBits(4)
            if (digitBits >= 10) {
                throw FormatException.formatInstance
            }
            result.append(toAlphaNumericChar(digitBits))
        }
    }

    /** Reads a 1-3 byte ECI designator value; throws [FormatException] on an invalid prefix. */
    private fun parseECIValue(bits: BitSource): Int {
        val firstByte = bits.readBits(8)
        if ((firstByte and 0x80) == 0) {
            return firstByte and 0x7F
        }
        if ((firstByte and 0xC0) == 0x80) {
            val secondByte = bits.readBits(8)
            return ((firstByte and 0x3F) shl 8) or secondByte
        }
        if ((firstByte and 0xE0) == 0xC0) {
            val secondThirdBytes = bits.readBits(16)
            return ((firstByte and 0x1F) shl 16) or secondThirdBytes
        }
        throw FormatException.formatInstance
    }
}
