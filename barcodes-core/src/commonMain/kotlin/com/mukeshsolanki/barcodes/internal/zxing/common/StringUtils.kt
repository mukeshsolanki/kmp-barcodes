/*
 * Copyright (C) 2010 ZXing authors
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

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType

/**
 * Common string-related functions. The platform default encoding is taken to be UTF-8, and
 * charsets kmp-barcodes cannot transcode (UTF-16, unknown hint names) fall back to ISO-8859-1.
 */
internal object StringUtils {

    /** Encoding assumed when no other guess fits. */
    private val PLATFORM_DEFAULT_ENCODING: Charset = Charsets.UTF_8

    /** The Shift_JIS charset. */
    val SHIFT_JIS_CHARSET: Charset = Charsets.SHIFT_JIS

    /** GB2312 is not supported by kmp-barcodes, so this is always null. */
    val GB2312_CHARSET: Charset? = null

    /** True when the platform default is a Japanese encoding; never the case with a UTF-8 default. */
    private const val ASSUME_SHIFT_JIS: Boolean = false

    /** Legacy Shift_JIS encoding name. */
    const val SHIFT_JIS: String = "SJIS"

    /** Legacy GB2312 encoding name. */
    const val GB2312: String = "GB2312"

    /** Guesses the encoding of [bytes]: "SJIS", "UTF8", "ISO8859_1", or the guessed charset's name. */
    fun guessEncoding(bytes: ByteArray, hints: Map<DecodeHintType, Any?>?): String {
        val c = guessCharset(bytes, hints)
        if (c == SHIFT_JIS_CHARSET) {
            return "SJIS"
        }
        if (c == Charsets.UTF_8) {
            return "UTF8"
        }
        if (c == Charsets.ISO_8859_1) {
            return "ISO8859_1"
        }
        return c.name
    }

    /** Guesses the charset of [bytes] among Shift_JIS, UTF-8 and ISO-8859-1, honouring a CHARACTER_SET hint. */
    fun guessCharset(bytes: ByteArray, hints: Map<DecodeHintType, Any?>?): Charset {
        if (hints != null && hints.containsKey(DecodeHintType.CHARACTER_SET)) {
            return Charsets.forName(hints[DecodeHintType.CHARACTER_SET].toString()) ?: Charsets.ISO_8859_1
        }
        if (hasUtf16Bom(bytes)) {
            return Charsets.ISO_8859_1
        }
        val length = bytes.size
        var canBeISO88591 = true
        var canBeShiftJIS = true
        var canBeUTF8 = true
        var utf8BytesLeft = 0
        var utf2BytesChars = 0
        var utf3BytesChars = 0
        var utf4BytesChars = 0
        var sjisBytesLeft = 0
        var sjisKatakanaChars = 0
        var sjisCurKatakanaWordLength = 0
        var sjisCurDoubleBytesWordLength = 0
        var sjisMaxKatakanaWordLength = 0
        var sjisMaxDoubleBytesWordLength = 0
        var isoHighOther = 0

        val utf8bom = bytes.size > 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()

        var i = 0
        while (i < length && (canBeISO88591 || canBeShiftJIS || canBeUTF8)) {
            val value = bytes[i].toInt() and 0xFF

            if (canBeUTF8) {
                if (utf8BytesLeft > 0) {
                    if ((value and 0x80) == 0) {
                        canBeUTF8 = false
                    } else {
                        utf8BytesLeft--
                    }
                } else if ((value and 0x80) != 0) {
                    if ((value and 0x40) == 0) {
                        canBeUTF8 = false
                    } else {
                        utf8BytesLeft++
                        if ((value and 0x20) == 0) {
                            utf2BytesChars++
                        } else {
                            utf8BytesLeft++
                            if ((value and 0x10) == 0) {
                                utf3BytesChars++
                            } else {
                                utf8BytesLeft++
                                if ((value and 0x08) == 0) {
                                    utf4BytesChars++
                                } else {
                                    canBeUTF8 = false
                                }
                            }
                        }
                    }
                }
            }

            if (canBeISO88591) {
                if (value > 0x7F && value < 0xA0) {
                    canBeISO88591 = false
                } else if (value > 0x9F && (value < 0xC0 || value == 0xD7 || value == 0xF7)) {
                    isoHighOther++
                }
            }

            if (canBeShiftJIS) {
                if (sjisBytesLeft > 0) {
                    if (value < 0x40 || value == 0x7F || value > 0xFC) {
                        canBeShiftJIS = false
                    } else {
                        sjisBytesLeft--
                    }
                } else if (value == 0x80 || value == 0xA0 || value > 0xEF) {
                    canBeShiftJIS = false
                } else if (value > 0xA0 && value < 0xE0) {
                    sjisKatakanaChars++
                    sjisCurDoubleBytesWordLength = 0
                    sjisCurKatakanaWordLength++
                    if (sjisCurKatakanaWordLength > sjisMaxKatakanaWordLength) {
                        sjisMaxKatakanaWordLength = sjisCurKatakanaWordLength
                    }
                } else if (value > 0x7F) {
                    sjisBytesLeft++
                    sjisCurKatakanaWordLength = 0
                    sjisCurDoubleBytesWordLength++
                    if (sjisCurDoubleBytesWordLength > sjisMaxDoubleBytesWordLength) {
                        sjisMaxDoubleBytesWordLength = sjisCurDoubleBytesWordLength
                    }
                } else {
                    sjisCurKatakanaWordLength = 0
                    sjisCurDoubleBytesWordLength = 0
                }
            }
            i++
        }

        if (canBeUTF8 && utf8BytesLeft > 0) {
            canBeUTF8 = false
        }
        if (canBeShiftJIS && sjisBytesLeft > 0) {
            canBeShiftJIS = false
        }

        if (canBeUTF8 && (utf8bom || utf2BytesChars + utf3BytesChars + utf4BytesChars > 0)) {
            return Charsets.UTF_8
        }
        if (canBeShiftJIS &&
            (ASSUME_SHIFT_JIS || sjisMaxKatakanaWordLength >= 3 || sjisMaxDoubleBytesWordLength >= 3)
        ) {
            return SHIFT_JIS_CHARSET
        }
        if (canBeISO88591 && canBeShiftJIS) {
            return if ((sjisMaxKatakanaWordLength == 2 && sjisKatakanaChars == 2) || isoHighOther * 10 >= length) {
                SHIFT_JIS_CHARSET
            } else {
                Charsets.ISO_8859_1
            }
        }
        if (canBeISO88591) {
            return Charsets.ISO_8859_1
        }
        if (canBeShiftJIS) {
            return SHIFT_JIS_CHARSET
        }
        if (canBeUTF8) {
            return Charsets.UTF_8
        }
        return PLATFORM_DEFAULT_ENCODING
    }

    /** True when [bytes] starts with a UTF-16 byte order mark (zxing would decode it as UTF-16). */
    private fun hasUtf16Bom(bytes: ByteArray): Boolean =
        bytes.size > 2 &&
            ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
}
