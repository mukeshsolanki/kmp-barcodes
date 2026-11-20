/*
 * Copyright 2015 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.oned

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat

/** Renders a Code 93 code. */
internal class Code93Writer : OneDimensionalCodeWriter() {

    /** Only Code 93 is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.CODE_93)

    /** Encodes [contents] (plain text, converted to extended encoding here) to horizontal modules. */
    override fun encode(contents: String): BooleanArray {
        var text = convertToExtended(contents)
        val length = text.length
        if (length > 80) {
            throw IllegalArgumentException(
                "Requested contents should be less than 80 digits long after converting to extended encoding, but got $length",
            )
        }

        val codeWidth = (text.length + 2 + 2) * 9 + 1

        val result = BooleanArray(codeWidth)

        var pos = appendPattern(result, 0, Code93Reader.ASTERISK_ENCODING)

        for (i in 0 until length) {
            val indexInString = Code93Reader.ALPHABET_STRING.indexOf(text[i])
            pos += appendPattern(result, pos, Code93Reader.CHARACTER_ENCODINGS[indexInString])
        }

        val check1 = computeChecksumIndex(text, 20)
        pos += appendPattern(result, pos, Code93Reader.CHARACTER_ENCODINGS[check1])

        text += Code93Reader.ALPHABET_STRING[check1]

        val check2 = computeChecksumIndex(text, 15)
        pos += appendPattern(result, pos, Code93Reader.CHARACTER_ENCODINGS[check2])

        pos += appendPattern(result, pos, Code93Reader.ASTERISK_ENCODING)

        result[pos] = true

        return result
    }

    /** Pattern and checksum helpers. */
    companion object {

        /** Writes the 9 module pattern [a] into [target] at [pos]; returns 9. */
        private fun appendPattern(target: BooleanArray, pos: Int, a: Int): Int {
            for (i in 0 until 9) {
                val temp = a and (1 shl (8 - i))
                target[pos + i] = temp != 0
            }
            return 9
        }

        /** Computes the weighted modulo 47 checksum index of [contents] with weights cycling up to [maxWeight]. */
        private fun computeChecksumIndex(contents: String, maxWeight: Int): Int {
            var weight = 1
            var total = 0

            for (i in contents.length - 1 downTo 0) {
                val indexInString = Code93Reader.ALPHABET_STRING.indexOf(contents[i])
                total += indexInString * weight
                if (++weight > maxWeight) {
                    weight = 1
                }
            }
            return total % 47
        }

        /** Converts [contents] to Code 93 extended encoding, using a-d for the ($) (%) (/) (+) shift characters. */
        fun convertToExtended(contents: String): String {
            val length = contents.length
            val extendedContent = StringBuilder(length * 2)
            for (i in 0 until length) {
                appendExtended(extendedContent, contents[i])
            }
            return extendedContent.toString()
        }

        /** Appends the extended encoding of [character] to [extendedContent]; throws for characters above 127. */
        private fun appendExtended(extendedContent: StringBuilder, character: Char) {
            val code = character.code
            if (code == 0) {
                extendedContent.append("bU")
            } else if (code <= 26) {
                extendedContent.append('a')
                extendedContent.append('A' + (code - 1))
            } else if (code <= 31) {
                extendedContent.append('b')
                extendedContent.append('A' + (code - 27))
            } else if (character == ' ' || character == '$' || character == '%' || character == '+') {
                extendedContent.append(character)
            } else if (character <= ',') {
                extendedContent.append('c')
                extendedContent.append('A' + (character - '!'))
            } else if (character <= '9') {
                extendedContent.append(character)
            } else if (character == ':') {
                extendedContent.append("cZ")
            } else if (character <= '?') {
                extendedContent.append('b')
                extendedContent.append('F' + (character - ';'))
            } else if (character == '@') {
                extendedContent.append("bV")
            } else if (character <= 'Z') {
                extendedContent.append(character)
            } else if (character <= '_') {
                extendedContent.append('b')
                extendedContent.append('K' + (character - '['))
            } else if (character == '`') {
                extendedContent.append("bW")
            } else if (character <= 'z') {
                extendedContent.append('d')
                extendedContent.append('A' + (character - 'a'))
            } else if (code <= 127) {
                extendedContent.append('b')
                extendedContent.append('P' + (character - '{'))
            } else {
                throw IllegalArgumentException("Requested content contains a non-encodable character")
            }
        }
    }
}
