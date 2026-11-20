/*
 * Copyright 2006 Jeremias Maerki in part, and ZXing Authors in part
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
/*
 * This file has been modified from its original form in Barcode4J.
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.WriterException
import com.mukeshsolanki.barcodes.internal.zxing.common.CharacterSetECI
import kotlin.math.min

/**
 * PDF417 high-level encoder following the algorithm described in ISO/IEC 15438:2001(E) annex P.
 * The automatic multi-ECI path of zxing is not ported.
 */
internal object PDF417HighLevelEncoder {

    /** Code for Text compaction. */
    private const val TEXT_COMPACTION = 0

    /** Code for Byte compaction. */
    private const val BYTE_COMPACTION = 1

    /** Code for Numeric compaction. */
    private const val NUMERIC_COMPACTION = 2

    /** Text compaction submode Alpha. */
    private const val SUBMODE_ALPHA = 0

    /** Text compaction submode Lower. */
    private const val SUBMODE_LOWER = 1

    /** Text compaction submode Mixed. */
    private const val SUBMODE_MIXED = 2

    /** Text compaction submode Punctuation. */
    private const val SUBMODE_PUNCTUATION = 3

    /** Mode latch to Text Compaction mode. */
    private const val LATCH_TO_TEXT = 900

    /** Mode latch to Byte Compaction mode (number of characters NOT a multiple of 6). */
    private const val LATCH_TO_BYTE_PADDED = 901

    /** Mode latch to Numeric Compaction mode. */
    private const val LATCH_TO_NUMERIC = 902

    /** Mode shift to Byte Compaction mode. */
    private const val SHIFT_TO_BYTE = 913

    /** Mode latch to Byte Compaction mode (number of characters a multiple of 6). */
    private const val LATCH_TO_BYTE = 924

    /** Identifier for a user defined Extended Channel Interpretation (ECI). */
    private const val ECI_USER_DEFINED = 925

    /** Identifier for a general purpose ECI format. */
    private const val ECI_GENERAL_PURPOSE = 926

    /** Identifier for an ECI of a character set or code page. */
    private const val ECI_CHARSET = 927

    /** Raw code table for text compaction Mixed sub-mode. */
    private val TEXT_MIXED_RAW: ByteArray = byteArrayOf(
        48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 38, 13, 9, 44, 58,
        35, 45, 46, 36, 47, 43, 37, 42, 61, 94, 0, 32, 0, 0, 0,
    )

    /** Raw code table for text compaction Punctuation sub-mode. */
    private val TEXT_PUNCTUATION_RAW: ByteArray = byteArrayOf(
        59, 60, 62, 64, 91, 92, 93, 95, 96, 126, 33, 13, 9, 44, 58,
        10, 45, 46, 36, 47, 34, 124, 42, 40, 41, 63, 123, 125, 39, 0,
    )

    /** Inverse lookup of [TEXT_MIXED_RAW]: ASCII char to Mixed value, or -1. */
    private val MIXED: ByteArray = inverseLookup(TEXT_MIXED_RAW)

    /** Inverse lookup of [TEXT_PUNCTUATION_RAW]: ASCII char to Punctuation value, or -1. */
    private val PUNCTUATION: ByteArray = inverseLookup(TEXT_PUNCTUATION_RAW)

    /** Encoding used when none is specified. */
    private val DEFAULT_ENCODING: Charset = Charsets.ISO_8859_1

    /** Builds a 128-entry ASCII-to-index table from a raw code table, -1 for absent chars. */
    private fun inverseLookup(raw: ByteArray): ByteArray {
        val table = ByteArray(128)
        table.fill(-1)
        for (i in raw.indices) {
            val b = raw[i]
            if (b > 0) {
                table[b.toInt()] = i.toByte()
            }
        }
        return table
    }

    /**
     * Encodes [msg] into codewords (chars 0..928) per annex P; [encoding] null means ISO-8859-1.
     * Throws [WriterException] for empty or unencodable input.
     */
    fun encodeHighLevel(msg: String, compaction: Compaction, encoding: Charset?): String {
        if (msg.isEmpty()) {
            throw WriterException("Empty message not allowed")
        }
        if (encoding == null) {
            requireLatin1(msg)
        }
        val sb = StringBuilder(msg.length)
        val charset = encoding ?: DEFAULT_ENCODING
        if (encoding != null && encoding.name != DEFAULT_ENCODING.name) {
            val eci = CharacterSetECI.getCharacterSetECI(encoding)
            if (eci != null) {
                encodingECI(eci.value, sb)
            }
        }
        val len = msg.length
        when (compaction) {
            Compaction.TEXT -> encodeText(msg, 0, len, sb, SUBMODE_ALPHA)
            Compaction.BYTE -> {
                val msgBytes = charset.encode(msg)
                encodeBinary(msgBytes, 0, msgBytes.size, BYTE_COMPACTION, sb)
            }
            Compaction.NUMERIC -> {
                sb.append(LATCH_TO_NUMERIC.toChar())
                encodeNumeric(msg, 0, len, sb)
            }
            Compaction.AUTO -> encodeAuto(msg, charset, sb)
        }
        return sb.toString()
    }

    /** Throws [WriterException] when [msg] holds a char above U+00FF and no encoding was given. */
    private fun requireLatin1(msg: String) {
        for (i in msg.indices) {
            if (msg[i].code > 255) {
                throw WriterException(
                    "Non-encodable character detected. Consider specifying EncodeHintType.CHARACTER_SET.",
                )
            }
        }
    }

    /** Default mode selection: numeric runs of 13+ digits, text runs of 5+ chars, bytes otherwise. */
    private fun encodeAuto(msg: String, encoding: Charset, sb: StringBuilder) {
        val len = msg.length
        var p = 0
        var textSubMode = SUBMODE_ALPHA
        var encodingMode = TEXT_COMPACTION
        while (p < len) {
            val n = determineConsecutiveDigitCount(msg, p)
            if (n >= 13) {
                sb.append(LATCH_TO_NUMERIC.toChar())
                encodingMode = NUMERIC_COMPACTION
                textSubMode = SUBMODE_ALPHA
                encodeNumeric(msg, p, n, sb)
                p += n
                continue
            }
            val t = determineConsecutiveTextCount(msg, p)
            if (t >= 5 || n == len) {
                if (encodingMode != TEXT_COMPACTION) {
                    sb.append(LATCH_TO_TEXT.toChar())
                    encodingMode = TEXT_COMPACTION
                    textSubMode = SUBMODE_ALPHA
                }
                textSubMode = encodeText(msg, p, t, sb, textSubMode)
                p += t
                continue
            }
            var b = determineConsecutiveBinaryCount(msg, p, encoding)
            if (b == 0) {
                b = 1
            }
            val bytes = encoding.encode(msg.substring(p, p + b))
            if (bytes.size == 1 && encodingMode == TEXT_COMPACTION) {
                encodeBinary(bytes, 0, 1, TEXT_COMPACTION, sb)
            } else {
                encodeBinary(bytes, 0, bytes.size, encodingMode, sb)
                encodingMode = BYTE_COMPACTION
                textSubMode = SUBMODE_ALPHA
            }
            p += b
        }
    }

    /**
     * Encodes [count] chars of [msg] from [startpos] with Text Compaction (4.4.2), starting in
     * [initialSubmode], and returns the submode it ends in.
     */
    private fun encodeText(msg: String, startpos: Int, count: Int, sb: StringBuilder, initialSubmode: Int): Int {
        val tmp = StringBuilder(count)
        var submode = initialSubmode
        var idx = 0
        while (true) {
            val ch = msg[startpos + idx]
            val nextIsPunctuation = submode == SUBMODE_MIXED &&
                startpos + idx + 1 < count &&
                isPunctuation(msg[startpos + idx + 1])
            val newSubmode = encodeTextChar(ch, submode, nextIsPunctuation, tmp)
            if (newSubmode != submode) {
                submode = newSubmode
                continue
            }
            idx++
            if (idx >= count) {
                break
            }
        }
        packTextValues(tmp, sb)
        return submode
    }

    /**
     * Appends the text values for [ch] in [submode] to [tmp]. Returns a different submode when it only
     * appended a latch (the char must then be encoded again), or [submode] when [ch] was consumed.
     */
    private fun encodeTextChar(ch: Char, submode: Int, nextIsPunctuation: Boolean, tmp: StringBuilder): Int =
        when (submode) {
            SUBMODE_ALPHA -> encodeAlpha(ch, tmp)
            SUBMODE_LOWER -> encodeLower(ch, tmp)
            SUBMODE_MIXED -> encodeMixed(ch, nextIsPunctuation, tmp)
            else -> encodePunctuation(ch, tmp)
        }

    /** Text step in submode Alpha. */
    private fun encodeAlpha(ch: Char, tmp: StringBuilder): Int {
        when {
            isAlphaUpper(ch) -> tmp.append(if (ch == ' ') 26.toChar() else (ch.code - 65).toChar())
            isAlphaLower(ch) -> {
                tmp.append(27.toChar())
                return SUBMODE_LOWER
            }
            isMixed(ch) -> {
                tmp.append(28.toChar())
                return SUBMODE_MIXED
            }
            else -> appendPunctuationShift(ch, tmp)
        }
        return SUBMODE_ALPHA
    }

    /** Text step in submode Lower. */
    private fun encodeLower(ch: Char, tmp: StringBuilder): Int {
        when {
            isAlphaLower(ch) -> tmp.append(if (ch == ' ') 26.toChar() else (ch.code - 97).toChar())
            isAlphaUpper(ch) -> {
                tmp.append(27.toChar())
                tmp.append((ch.code - 65).toChar())
            }
            isMixed(ch) -> {
                tmp.append(28.toChar())
                return SUBMODE_MIXED
            }
            else -> appendPunctuationShift(ch, tmp)
        }
        return SUBMODE_LOWER
    }

    /** Text step in submode Mixed. */
    private fun encodeMixed(ch: Char, nextIsPunctuation: Boolean, tmp: StringBuilder): Int {
        when {
            isMixed(ch) -> tmp.append(MIXED[ch.code].toInt().toChar())
            isAlphaUpper(ch) -> {
                tmp.append(28.toChar())
                return SUBMODE_ALPHA
            }
            isAlphaLower(ch) -> {
                tmp.append(27.toChar())
                return SUBMODE_LOWER
            }
            nextIsPunctuation -> {
                tmp.append(25.toChar())
                return SUBMODE_PUNCTUATION
            }
            else -> appendPunctuationShift(ch, tmp)
        }
        return SUBMODE_MIXED
    }

    /** Text step in submode Punctuation. */
    private fun encodePunctuation(ch: Char, tmp: StringBuilder): Int {
        if (isPunctuation(ch)) {
            tmp.append(PUNCTUATION[ch.code].toInt().toChar())
            return SUBMODE_PUNCTUATION
        }
        tmp.append(29.toChar())
        return SUBMODE_ALPHA
    }

    /** Appends a punctuation shift (ps) followed by the punctuation value of [ch]. */
    private fun appendPunctuationShift(ch: Char, tmp: StringBuilder) {
        tmp.append(29.toChar())
        tmp.append(PUNCTUATION[ch.code].toInt().toChar())
    }

    /** Packs pairs of text values into codewords (`h * 30 + l`), padding an odd tail with ps. */
    private fun packTextValues(tmp: StringBuilder, sb: StringBuilder) {
        var h = '\u0000'
        val len = tmp.length
        for (i in 0 until len) {
            val odd = (i % 2) != 0
            if (odd) {
                h = ((h.code * 30) + tmp[i].code).toChar()
                sb.append(h)
            } else {
                h = tmp[i]
            }
        }
        if ((len % 2) != 0) {
            sb.append(((h.code * 30) + 29).toChar())
        }
    }

    /**
     * Encodes [count] bytes from [startpos] with Byte Compaction (4.4.3), starting from [startmode].
     */
    private fun encodeBinary(bytes: ByteArray, startpos: Int, count: Int, startmode: Int, sb: StringBuilder) {
        if (count == 1 && startmode == TEXT_COMPACTION) {
            sb.append(SHIFT_TO_BYTE.toChar())
        } else if ((count % 6) == 0) {
            sb.append(LATCH_TO_BYTE.toChar())
        } else {
            sb.append(LATCH_TO_BYTE_PADDED.toChar())
        }
        var idx = startpos
        if (count >= 6) {
            val chars = CharArray(5)
            while ((startpos + count - idx) >= 6) {
                var t = 0L
                for (i in 0 until 6) {
                    t = t shl 8
                    t += (bytes[idx + i].toInt() and 0xff).toLong()
                }
                for (i in 0 until 5) {
                    chars[i] = (t % 900).toInt().toChar()
                    t /= 900
                }
                for (i in chars.indices.reversed()) {
                    sb.append(chars[i])
                }
                idx += 6
            }
        }
        for (i in idx until startpos + count) {
            val ch = bytes[i].toInt() and 0xff
            sb.append(ch.toChar())
        }
    }

    /** Encodes [count] digits of [msg] from [startpos] with Numeric Compaction, 44 digits per group. */
    private fun encodeNumeric(msg: String, startpos: Int, count: Int, sb: StringBuilder) {
        var idx = 0
        val tmp = StringBuilder(count / 3 + 1)
        while (idx < count) {
            tmp.setLength(0)
            val len = min(44, count - idx)
            val digits = decimalDigits(msg, startpos + idx, len)
            appendBase900(digits, tmp)
            for (i in tmp.length - 1 downTo 0) {
                sb.append(tmp[i])
            }
            idx += len
        }
    }

    /**
     * Returns `"1" + msg[start, start + len)` as decimal digit values, most significant first.
     * Throws [NumberFormatException] for a non-digit, as zxing's BigInteger parse does.
     */
    private fun decimalDigits(msg: String, start: Int, len: Int): IntArray {
        val digits = IntArray(len + 1)
        digits[0] = 1
        for (i in 0 until len) {
            digits[i + 1] = msg[start + i].digitToIntOrNull() ?: throw NumberFormatException("Not a decimal digit")
        }
        return digits
    }

    /** Appends the base-900 digits of the decimal number [digits] to [out], least significant first. */
    private fun appendBase900(digits: IntArray, out: StringBuilder) {
        var start = 0
        do {
            var remainder = 0
            for (i in start until digits.size) {
                val current = remainder * 10 + digits[i]
                digits[i] = current / 900
                remainder = current % 900
            }
            out.append(remainder.toChar())
            while (start < digits.size && digits[start] == 0) {
                start++
            }
        } while (start < digits.size)
    }

    /** True for '0'..'9'. */
    private fun isDigit(ch: Char): Boolean = ch in '0'..'9'

    /** True for space and 'A'..'Z'. */
    private fun isAlphaUpper(ch: Char): Boolean = ch == ' ' || ch in 'A'..'Z'

    /** True for space and 'a'..'z'. */
    private fun isAlphaLower(ch: Char): Boolean = ch == ' ' || ch in 'a'..'z'

    /** True when [ch] is in the Mixed sub-mode table. */
    private fun isMixed(ch: Char): Boolean = MIXED[ch.code].toInt() != -1

    /** True when [ch] is in the Punctuation sub-mode table. */
    private fun isPunctuation(ch: Char): Boolean = PUNCTUATION[ch.code].toInt() != -1

    /** True for chars encodable with Text Compaction. */
    private fun isText(ch: Char): Boolean = ch == '\t' || ch == '\n' || ch == '\r' || (ch.code in 32..126)

    /** Number of consecutive digits from [startpos]. */
    private fun determineConsecutiveDigitCount(msg: String, startpos: Int): Int {
        var count = 0
        val len = msg.length
        var idx = startpos
        while (idx < len && isDigit(msg[idx])) {
            count++
            idx++
        }
        return count
    }

    /** Number of consecutive chars from [startpos] encodable with Text Compaction. */
    private fun determineConsecutiveTextCount(msg: String, startpos: Int): Int {
        val len = msg.length
        var idx = startpos
        while (idx < len) {
            var numericCount = 0
            while (numericCount < 13 && idx < len && isDigit(msg[idx])) {
                numericCount++
                idx++
            }
            if (numericCount >= 13) {
                return idx - startpos - numericCount
            }
            if (numericCount > 0) {
                continue
            }
            if (!isText(msg[idx])) {
                break
            }
            idx++
        }
        return idx - startpos
    }

    /**
     * Number of consecutive chars from [startpos] to encode with Byte Compaction (until a 13+ digit run).
     * Throws [WriterException] when a char cannot be encoded in [encoding].
     */
    private fun determineConsecutiveBinaryCount(msg: String, startpos: Int, encoding: Charset): Int {
        val len = msg.length
        var idx = startpos
        while (idx < len) {
            var numericCount = 0
            var i = idx
            while (numericCount < 13 && isDigit(msg[i])) {
                numericCount++
                i = idx + numericCount
                if (i >= len) {
                    break
                }
            }
            if (numericCount >= 13) {
                return idx - startpos
            }
            if (!encoding.canEncode(msg[idx].toString())) {
                throw WriterException("Non-encodable character detected")
            }
            idx++
        }
        return idx - startpos
    }

    /** Appends the ECI designator codewords for [eci]; throws [WriterException] when out of range. */
    private fun encodingECI(eci: Int, sb: StringBuilder) {
        if (eci in 0 until 900) {
            sb.append(ECI_CHARSET.toChar())
            sb.append(eci.toChar())
        } else if (eci < 810900) {
            sb.append(ECI_GENERAL_PURPOSE.toChar())
            sb.append((eci / 900 - 1).toChar())
            sb.append((eci % 900).toChar())
        } else if (eci < 811800) {
            sb.append(ECI_USER_DEFINED.toChar())
            sb.append((810900 - eci).toChar())
        } else {
            throw WriterException("ECI number not in valid range from 0..811799, but was $eci")
        }
    }
}
