/*
 * Copyright 2010 ZXing authors
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

/** Renders a Code 39 code, switching to full ASCII extended mode when needed. */
internal class Code39Writer : OneDimensionalCodeWriter() {

    /** Only Code 39 is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.CODE_39)

    /** Encodes up to 80 characters (after any extended mode conversion) to horizontal modules. */
    override fun encode(contents: String): BooleanArray {
        var text = contents
        var length = text.length
        if (length > 80) {
            throw IllegalArgumentException(
                "Requested contents should be less than 80 digits long, but got $length",
            )
        }

        for (i in 0 until length) {
            val indexInString = Code39Reader.ALPHABET_STRING.indexOf(text[i])
            if (indexInString < 0) {
                text = tryToConvertToExtendedMode(text)
                length = text.length
                if (length > 80) {
                    throw IllegalArgumentException(
                        "Requested contents should be less than 80 digits long, but got $length (extended full ASCII mode)",
                    )
                }
                break
            }
        }

        val widths = IntArray(9)
        val codeWidth = 24 + 1 + (13 * length)
        val result = BooleanArray(codeWidth)
        toIntArray(Code39Reader.ASTERISK_ENCODING, widths)
        var pos = appendPattern(result, 0, widths, true)
        val narrowWhite = intArrayOf(1)
        pos += appendPattern(result, pos, narrowWhite, false)
        for (i in 0 until length) {
            val indexInString = Code39Reader.ALPHABET_STRING.indexOf(text[i])
            toIntArray(Code39Reader.CHARACTER_ENCODINGS[indexInString], widths)
            pos += appendPattern(result, pos, widths, true)
            pos += appendPattern(result, pos, narrowWhite, false)
        }
        toIntArray(Code39Reader.ASTERISK_ENCODING, widths)
        appendPattern(result, pos, widths, true)
        return result
    }

    /** Conversion helpers. */
    private companion object {

        /** Expands the 9 bit wide/narrow pattern [a] into module widths (1 or 2) in [toReturn]. */
        fun toIntArray(a: Int, toReturn: IntArray) {
            for (i in 0 until 9) {
                val temp = a and (1 shl (8 - i))
                toReturn[i] = if (temp == 0) 1 else 2
            }
        }

        /** Rewrites [contents] into full ASCII Code 39 shift sequences; throws for characters above 127. */
        fun tryToConvertToExtendedMode(contents: String): String {
            val length = contents.length
            val extendedContent = StringBuilder()
            for (i in 0 until length) {
                val character = contents[i]
                when (character) {
                    '\u0000' -> extendedContent.append("%U")
                    ' ', '-', '.' -> extendedContent.append(character)
                    '@' -> extendedContent.append("%V")
                    '`' -> extendedContent.append("%W")
                    else -> appendExtended(extendedContent, character)
                }
            }
            return extendedContent.toString()
        }

        /** Appends the extended mode shift pair for [character] to [extendedContent]. */
        fun appendExtended(extendedContent: StringBuilder, character: Char) {
            val code = character.code
            if (code <= 26) {
                extendedContent.append('$')
                extendedContent.append('A' + (code - 1))
            } else if (character < ' ') {
                extendedContent.append('%')
                extendedContent.append('A' + (code - 27))
            } else if (character <= ',' || character == '/' || character == ':') {
                extendedContent.append('/')
                extendedContent.append('A' + (code - 33))
            } else if (character <= '9') {
                extendedContent.append('0' + (code - 48))
            } else if (character <= '?') {
                extendedContent.append('%')
                extendedContent.append('F' + (code - 59))
            } else if (character <= 'Z') {
                extendedContent.append('A' + (code - 65))
            } else if (character <= '_') {
                extendedContent.append('%')
                extendedContent.append('K' + (code - 91))
            } else if (character <= 'z') {
                extendedContent.append('+')
                extendedContent.append('A' + (code - 97))
            } else if (code <= 127) {
                extendedContent.append('%')
                extendedContent.append('P' + (code - 123))
            } else {
                throw IllegalArgumentException("Requested content contains a non-encodable character")
            }
        }
    }
}
