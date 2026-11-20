/*
 * Copyright 2009 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.ECIStringBuilder
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417ResultMetadata

/**
 * Decodes PDF417 data codewords (text, byte and numeric compaction, ECIs and Macro PDF417) into text.
 */
internal object DecodedBitStreamParser {

    /** Text compaction sub-modes. */
    private enum class Mode {
        /** Uppercase letters. */
        ALPHA,

        /** Lowercase letters. */
        LOWER,

        /** Digits and some punctuation. */
        MIXED,

        /** Punctuation. */
        PUNCT,

        /** One uppercase letter, then back. */
        ALPHA_SHIFT,

        /** One punctuation char, then back. */
        PUNCT_SHIFT,
    }

    /** Latch to Text Compaction. */
    private const val TEXT_COMPACTION_MODE_LATCH = 900

    /** Latch to Byte Compaction (count not a multiple of 6). */
    private const val BYTE_COMPACTION_MODE_LATCH = 901

    /** Latch to Numeric Compaction. */
    private const val NUMERIC_COMPACTION_MODE_LATCH = 902

    /** Latch to Byte Compaction (count a multiple of 6). */
    private const val BYTE_COMPACTION_MODE_LATCH_6 = 924

    /** User defined ECI. */
    private const val ECI_USER_DEFINED = 925

    /** General purpose ECI. */
    private const val ECI_GENERAL_PURPOSE = 926

    /** Character set ECI. */
    private const val ECI_CHARSET = 927

    /** Start of a Macro PDF417 control block. */
    private const val BEGIN_MACRO_PDF417_CONTROL_BLOCK = 928

    /** Start of a Macro PDF417 optional field. */
    private const val BEGIN_MACRO_PDF417_OPTIONAL_FIELD = 923

    /** Macro PDF417 terminator. */
    private const val MACRO_PDF417_TERMINATOR = 922

    /** Shift to Byte Compaction for one codeword. */
    private const val MODE_SHIFT_TO_BYTE_COMPACTION_MODE = 913

    /** Maximum codewords in one numeric group. */
    private const val MAX_NUMERIC_CODEWORDS = 15

    /** Macro optional field: file name. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_FILE_NAME = 0

    /** Macro optional field: segment count. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_SEGMENT_COUNT = 1

    /** Macro optional field: time stamp. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_TIME_STAMP = 2

    /** Macro optional field: sender. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_SENDER = 3

    /** Macro optional field: addressee. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_ADDRESSEE = 4

    /** Macro optional field: file size. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_FILE_SIZE = 5

    /** Macro optional field: checksum. */
    private const val MACRO_PDF417_OPTIONAL_FIELD_CHECKSUM = 6

    /** Punctuation latch. */
    private const val PL = 25

    /** Lower latch. */
    private const val LL = 27

    /** Alpha shift. */
    private const val AS = 27

    /** Mixed latch. */
    private const val ML = 28

    /** Alpha latch. */
    private const val AL = 28

    /** Punctuation shift. */
    private const val PS = 29

    /** Punctuation-to-alpha latch. */
    private const val PAL = 29

    /** Punctuation sub-mode characters. */
    private val PUNCT_CHARS: CharArray = ";<>@[\\]_`~!\r\t,:\n-.$/\"|*()?{}'".toCharArray()

    /** Mixed sub-mode characters. */
    private val MIXED_CHARS: CharArray = "0123456789&\r\t,:#-.$/+%*=^".toCharArray()

    /** Codewords in a Macro PDF417 segment index. */
    private const val NUMBER_OF_SEQUENCE_CODEWORDS = 2

    /** Decodes [codewords] (index 0 is the length descriptor); throws [FormatException] when malformed. */
    fun decode(codewords: IntArray, ecLevel: String): DecoderResult {
        val result = ECIStringBuilder(codewords.size * 2)
        var codeIndex = textCompaction(codewords, 1, result)
        val resultMetadata = PDF417ResultMetadata()
        while (codeIndex < codewords[0]) {
            val code = codewords[codeIndex++]
            when (code) {
                TEXT_COMPACTION_MODE_LATCH -> codeIndex = textCompaction(codewords, codeIndex, result)
                BYTE_COMPACTION_MODE_LATCH, BYTE_COMPACTION_MODE_LATCH_6 ->
                    codeIndex = byteCompaction(code, codewords, codeIndex, result)
                MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(codewords[codeIndex++].toChar())
                NUMERIC_COMPACTION_MODE_LATCH -> codeIndex = numericCompaction(codewords, codeIndex, result)
                ECI_CHARSET -> result.appendECI(codewords[codeIndex++])
                ECI_GENERAL_PURPOSE -> codeIndex += 2
                ECI_USER_DEFINED -> codeIndex++
                BEGIN_MACRO_PDF417_CONTROL_BLOCK -> codeIndex = decodeMacroBlock(codewords, codeIndex, resultMetadata)
                BEGIN_MACRO_PDF417_OPTIONAL_FIELD, MACRO_PDF417_TERMINATOR -> throw FormatException.formatInstance
                else -> codeIndex = textCompaction(codewords, codeIndex - 1, result)
            }
        }
        if (result.isEmpty && resultMetadata.fileId == null) {
            throw FormatException.formatInstance
        }
        val decoderResult = DecoderResult(null, result.toString(), null, ecLevel)
        decoderResult.other = resultMetadata
        return decoderResult
    }

    /** Parses a Macro PDF417 control block into [resultMetadata] and returns the next code index. */
    fun decodeMacroBlock(codewords: IntArray, startIndex: Int, resultMetadata: PDF417ResultMetadata): Int {
        var codeIndex = startIndex
        if (codeIndex + NUMBER_OF_SEQUENCE_CODEWORDS > codewords[0]) {
            throw FormatException.formatInstance
        }
        val segmentIndexArray = IntArray(NUMBER_OF_SEQUENCE_CODEWORDS)
        for (i in 0 until NUMBER_OF_SEQUENCE_CODEWORDS) {
            segmentIndexArray[i] = codewords[codeIndex]
            codeIndex++
        }
        val segmentIndexString = decodeBase900toBase10(segmentIndexArray, NUMBER_OF_SEQUENCE_CODEWORDS)
        resultMetadata.segmentIndex = if (segmentIndexString.isEmpty()) 0 else parseIntOrFormatError(segmentIndexString)
        val fileId = StringBuilder()
        while (codeIndex < codewords[0] &&
            codeIndex < codewords.size &&
            codewords[codeIndex] != MACRO_PDF417_TERMINATOR &&
            codewords[codeIndex] != BEGIN_MACRO_PDF417_OPTIONAL_FIELD
        ) {
            fileId.append(codewords[codeIndex].toString().padStart(3, '0'))
            codeIndex++
        }
        if (fileId.isEmpty()) {
            throw FormatException.formatInstance
        }
        resultMetadata.fileId = fileId.toString()
        var optionalFieldsStart = -1
        if (codewords[codeIndex] == BEGIN_MACRO_PDF417_OPTIONAL_FIELD) {
            optionalFieldsStart = codeIndex + 1
        }
        while (codeIndex < codewords[0]) {
            when (codewords[codeIndex]) {
                BEGIN_MACRO_PDF417_OPTIONAL_FIELD -> {
                    codeIndex++
                    codeIndex = decodeOptionalField(codewords, codeIndex, resultMetadata)
                }
                MACRO_PDF417_TERMINATOR -> {
                    codeIndex++
                    resultMetadata.isLastSegment = true
                }
                else -> throw FormatException.formatInstance
            }
        }
        if (optionalFieldsStart != -1) {
            var optionalFieldsLength = codeIndex - optionalFieldsStart
            if (resultMetadata.isLastSegment) {
                optionalFieldsLength--
            }
            if (optionalFieldsLength > 0) {
                resultMetadata.optionalData =
                    codewords.copyOfRange(optionalFieldsStart, optionalFieldsStart + optionalFieldsLength)
            }
        }
        return codeIndex
    }

    /** Parses the optional field whose designator is at [codeIndex] and returns the next code index. */
    private fun decodeOptionalField(codewords: IntArray, codeIndex: Int, resultMetadata: PDF417ResultMetadata): Int {
        val field = ECIStringBuilder()
        return when (codewords[codeIndex]) {
            MACRO_PDF417_OPTIONAL_FIELD_FILE_NAME -> textCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.fileName = field.toString()
            }
            MACRO_PDF417_OPTIONAL_FIELD_SENDER -> textCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.sender = field.toString()
            }
            MACRO_PDF417_OPTIONAL_FIELD_ADDRESSEE -> textCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.addressee = field.toString()
            }
            MACRO_PDF417_OPTIONAL_FIELD_SEGMENT_COUNT -> numericCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.segmentCount = parseIntOrFormatError(field.toString())
            }
            MACRO_PDF417_OPTIONAL_FIELD_TIME_STAMP -> numericCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.timestamp = parseLongOrFormatError(field.toString())
            }
            MACRO_PDF417_OPTIONAL_FIELD_CHECKSUM -> numericCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.checksum = parseIntOrFormatError(field.toString())
            }
            MACRO_PDF417_OPTIONAL_FIELD_FILE_SIZE -> numericCompaction(codewords, codeIndex + 1, field).also {
                resultMetadata.fileSize = parseLongOrFormatError(field.toString())
            }
            else -> throw FormatException.formatInstance
        }
    }

    /** Parses a decimal Int, throwing [FormatException] when it is not one. */
    private fun parseIntOrFormatError(value: String): Int = value.toIntOrNull() ?: throw FormatException.formatInstance

    /** Parses a decimal Long, throwing [FormatException] when it is not one. */
    private fun parseLongOrFormatError(value: String): Long =
        value.toLongOrNull() ?: throw FormatException.formatInstance

    /**
     * Text Compaction (5.4.1.5): collects two values per codeword until a mode change, decodes them into
     * [result] and returns the next code index.
     */
    private fun textCompaction(codewords: IntArray, startIndex: Int, result: ECIStringBuilder): Int {
        var codeIndex = startIndex
        var textCompactionData = IntArray((codewords[0] - codeIndex) * 2)
        var byteCompactionData = IntArray((codewords[0] - codeIndex) * 2)
        var index = 0
        var end = false
        var subMode = Mode.ALPHA
        while ((codeIndex < codewords[0]) && !end) {
            var code = codewords[codeIndex++]
            if (code < TEXT_COMPACTION_MODE_LATCH) {
                textCompactionData[index] = code / 30
                textCompactionData[index + 1] = code % 30
                index += 2
                continue
            }
            when (code) {
                TEXT_COMPACTION_MODE_LATCH -> textCompactionData[index++] = TEXT_COMPACTION_MODE_LATCH
                BYTE_COMPACTION_MODE_LATCH,
                BYTE_COMPACTION_MODE_LATCH_6,
                NUMERIC_COMPACTION_MODE_LATCH,
                BEGIN_MACRO_PDF417_CONTROL_BLOCK,
                BEGIN_MACRO_PDF417_OPTIONAL_FIELD,
                MACRO_PDF417_TERMINATOR,
                -> {
                    codeIndex--
                    end = true
                }
                MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> {
                    textCompactionData[index] = MODE_SHIFT_TO_BYTE_COMPACTION_MODE
                    code = codewords[codeIndex++]
                    byteCompactionData[index] = code
                    index++
                }
                ECI_CHARSET -> {
                    subMode = decodeTextCompaction(textCompactionData, byteCompactionData, index, result, subMode)
                    result.appendECI(codewords[codeIndex++])
                    if (codeIndex > codewords[0]) {
                        throw FormatException.formatInstance
                    }
                    textCompactionData = IntArray((codewords[0] - codeIndex) * 2)
                    byteCompactionData = IntArray((codewords[0] - codeIndex) * 2)
                    index = 0
                }
            }
        }
        decodeTextCompaction(textCompactionData, byteCompactionData, index, result, subMode)
        return codeIndex
    }

    /**
     * Decodes [length] text compaction values (5.4.2.2/5.4.2.3) into [result], starting in [startMode],
     * and returns the latched sub-mode it ends in.
     */
    private fun decodeTextCompaction(
        textCompactionData: IntArray,
        byteCompactionData: IntArray,
        length: Int,
        result: ECIStringBuilder,
        startMode: Mode,
    ): Mode {
        var subMode = startMode
        var priorToShiftMode = startMode
        var latchedMode = startMode
        var i = 0
        while (i < length) {
            val subModeCh = textCompactionData[i]
            var ch = '\u0000'
            when (subMode) {
                Mode.ALPHA -> if (subModeCh < 26) {
                    ch = ('A'.code + subModeCh).toChar()
                } else {
                    when (subModeCh) {
                        26 -> ch = ' '
                        LL -> {
                            subMode = Mode.LOWER
                            latchedMode = subMode
                        }
                        ML -> {
                            subMode = Mode.MIXED
                            latchedMode = subMode
                        }
                        PS -> {
                            priorToShiftMode = subMode
                            subMode = Mode.PUNCT_SHIFT
                        }
                        MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(byteCompactionData[i].toChar())
                        TEXT_COMPACTION_MODE_LATCH -> {
                            subMode = Mode.ALPHA
                            latchedMode = subMode
                        }
                    }
                }

                Mode.LOWER -> if (subModeCh < 26) {
                    ch = ('a'.code + subModeCh).toChar()
                } else {
                    when (subModeCh) {
                        26 -> ch = ' '
                        AS -> {
                            priorToShiftMode = subMode
                            subMode = Mode.ALPHA_SHIFT
                        }
                        ML -> {
                            subMode = Mode.MIXED
                            latchedMode = subMode
                        }
                        PS -> {
                            priorToShiftMode = subMode
                            subMode = Mode.PUNCT_SHIFT
                        }
                        MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(byteCompactionData[i].toChar())
                        TEXT_COMPACTION_MODE_LATCH -> {
                            subMode = Mode.ALPHA
                            latchedMode = subMode
                        }
                    }
                }

                Mode.MIXED -> if (subModeCh < PL) {
                    ch = MIXED_CHARS[subModeCh]
                } else {
                    when (subModeCh) {
                        PL -> {
                            subMode = Mode.PUNCT
                            latchedMode = subMode
                        }
                        26 -> ch = ' '
                        LL -> {
                            subMode = Mode.LOWER
                            latchedMode = subMode
                        }
                        AL, TEXT_COMPACTION_MODE_LATCH -> {
                            subMode = Mode.ALPHA
                            latchedMode = subMode
                        }
                        PS -> {
                            priorToShiftMode = subMode
                            subMode = Mode.PUNCT_SHIFT
                        }
                        MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(byteCompactionData[i].toChar())
                    }
                }

                Mode.PUNCT -> if (subModeCh < PAL) {
                    ch = PUNCT_CHARS[subModeCh]
                } else {
                    when (subModeCh) {
                        PAL, TEXT_COMPACTION_MODE_LATCH -> {
                            subMode = Mode.ALPHA
                            latchedMode = subMode
                        }
                        MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(byteCompactionData[i].toChar())
                    }
                }

                Mode.ALPHA_SHIFT -> {
                    subMode = priorToShiftMode
                    if (subModeCh < 26) {
                        ch = ('A'.code + subModeCh).toChar()
                    } else {
                        when (subModeCh) {
                            26 -> ch = ' '
                            TEXT_COMPACTION_MODE_LATCH -> subMode = Mode.ALPHA
                        }
                    }
                }

                Mode.PUNCT_SHIFT -> {
                    subMode = priorToShiftMode
                    if (subModeCh < PAL) {
                        ch = PUNCT_CHARS[subModeCh]
                    } else {
                        when (subModeCh) {
                            PAL, TEXT_COMPACTION_MODE_LATCH -> subMode = Mode.ALPHA
                            MODE_SHIFT_TO_BYTE_COMPACTION_MODE -> result.append(byteCompactionData[i].toChar())
                        }
                    }
                }
            }
            if (ch != '\u0000') {
                result.append(ch)
            }
            i++
        }
        return latchedMode
    }

    /**
     * Byte Compaction (5.4.3): five codewords encode six bytes; returns the next code index.
     * [mode] is the latch codeword, 901 or 924.
     */
    private fun byteCompaction(mode: Int, codewords: IntArray, startIndex: Int, result: ECIStringBuilder): Int {
        var codeIndex = startIndex
        var end = false
        while (codeIndex < codewords[0] && !end) {
            while (codeIndex < codewords[0] && codewords[codeIndex] == ECI_CHARSET) {
                result.appendECI(codewords[++codeIndex])
                codeIndex++
            }
            if (codeIndex >= codewords[0] || codewords[codeIndex] >= TEXT_COMPACTION_MODE_LATCH) {
                end = true
                continue
            }
            var value = 0L
            var count = 0
            do {
                value = 900 * value + codewords[codeIndex++]
                count++
            } while (count < 5 && codeIndex < codewords[0] && codewords[codeIndex] < TEXT_COMPACTION_MODE_LATCH)
            if (count == 5 && (mode == BYTE_COMPACTION_MODE_LATCH_6 ||
                    codeIndex < codewords[0] && codewords[codeIndex] < TEXT_COMPACTION_MODE_LATCH)
            ) {
                for (i in 0 until 6) {
                    result.append((value shr (8 * (5 - i))).toByte())
                }
            } else {
                codeIndex -= count
                while ((codeIndex < codewords[0]) && !end) {
                    val code = codewords[codeIndex++]
                    if (code < TEXT_COMPACTION_MODE_LATCH) {
                        result.append(code.toByte())
                    } else if (code == ECI_CHARSET) {
                        result.appendECI(codewords[codeIndex++])
                    } else {
                        codeIndex--
                        end = true
                    }
                }
            }
        }
        return codeIndex
    }

    /** Numeric Compaction (5.4.4): groups of up to 15 codewords become decimal digits; returns the next index. */
    private fun numericCompaction(codewords: IntArray, startIndex: Int, result: ECIStringBuilder): Int {
        var codeIndex = startIndex
        var count = 0
        var end = false
        val numericCodewords = IntArray(MAX_NUMERIC_CODEWORDS)
        while (codeIndex < codewords[0] && !end) {
            val code = codewords[codeIndex++]
            if (codeIndex == codewords[0]) {
                end = true
            }
            if (code < TEXT_COMPACTION_MODE_LATCH) {
                numericCodewords[count] = code
                count++
            } else if (endsNumericCompaction(code)) {
                codeIndex--
                end = true
            }
            if ((count % MAX_NUMERIC_CODEWORDS == 0 || code == NUMERIC_COMPACTION_MODE_LATCH || end) && count > 0) {
                result.append(decodeBase900toBase10(numericCodewords, count))
                count = 0
            }
        }
        return codeIndex
    }

    /** True for the mode codewords that terminate Numeric Compaction. */
    private fun endsNumericCompaction(code: Int): Boolean = when (code) {
        TEXT_COMPACTION_MODE_LATCH,
        BYTE_COMPACTION_MODE_LATCH,
        BYTE_COMPACTION_MODE_LATCH_6,
        BEGIN_MACRO_PDF417_CONTROL_BLOCK,
        BEGIN_MACRO_PDF417_OPTIONAL_FIELD,
        MACRO_PDF417_TERMINATOR,
        ECI_CHARSET,
        -> true
        else -> false
    }

    /**
     * Converts [count] base-900 [codewords] to their decimal string without the leading "1" marker
     * (e.g. 1, 624, 434, 632, 282, 200 -> "000213298174000"). Throws [FormatException] when the
     * value does not start with 1.
     */
    private fun decodeBase900toBase10(codewords: IntArray, count: Int): String {
        val resultString = base900ToDecimal(codewords, count)
        if (resultString[0] != '1') {
            throw FormatException.formatInstance
        }
        return resultString.substring(1)
    }

    /** Evaluates `sum(codewords[i] * 900^(count - i - 1))` as a decimal string, without BigInteger. */
    private fun base900ToDecimal(codewords: IntArray, count: Int): String {
        val digits = IntArray(count * 3 + 4)
        var size = 1
        for (i in 0 until count) {
            var carry = codewords[i]
            for (j in 0 until size) {
                val v = digits[j] * 900 + carry
                digits[j] = v % 10
                carry = v / 10
            }
            while (carry > 0) {
                digits[size++] = carry % 10
                carry /= 10
            }
        }
        while (size > 1 && digits[size - 1] == 0) {
            size--
        }
        val sb = StringBuilder(size)
        for (j in size - 1 downTo 0) {
            sb.append(('0'.code + digits[j]).toChar())
        }
        return sb.toString()
    }
}
