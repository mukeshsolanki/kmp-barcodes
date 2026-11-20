/*
 * Copyright 2011 ZXing authors
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

/** Renders a Codabar code. */
internal class CodaBarWriter : OneDimensionalCodeWriter() {

    /** Only Codabar is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.CODABAR)

    /** Encodes [contents], adding default A guards when it has no valid start/end characters. */
    override fun encode(contents: String): BooleanArray {
        val text = withGuards(contents)

        var resultLength = 20
        for (i in 1 until text.length - 1) {
            val c = text[i]
            if (c.isDigit() || c == '-' || c == '$') {
                resultLength += 9
            } else if (CodaBarReader.arrayContains(CHARS_WHICH_ARE_TEN_LENGTH_EACH_AFTER_DECODED, c)) {
                resultLength += 10
            } else {
                throw IllegalArgumentException("Cannot encode a character in the contents")
            }
        }
        resultLength += text.length - 1

        val result = BooleanArray(resultLength)
        var position = 0
        for (index in text.indices) {
            var c = text[index].uppercaseChar()
            if (index == 0 || index == text.length - 1) {
                c = toStandardGuard(c)
            }
            var code = 0
            for (i in CodaBarReader.ALPHABET.indices) {
                if (c == CodaBarReader.ALPHABET[i]) {
                    code = CodaBarReader.CHARACTER_ENCODINGS[i]
                    break
                }
            }
            var color = true
            var counter = 0
            var bit = 0
            while (bit < 7) {
                result[position] = color
                position++
                if (((code shr (6 - bit)) and 1) == 0 || counter == 1) {
                    color = !color
                    bit++
                    counter = 0
                } else {
                    counter++
                }
            }
            if (index < text.length - 1) {
                result[position] = false
                position++
            }
        }
        return result
    }

    /** Guard tables and helpers. */
    private companion object {

        /** Standard start/end characters. */
        val START_END_CHARS: CharArray = charArrayOf('A', 'B', 'C', 'D')

        /** Alternate start/end characters, mapped to A-D. */
        val ALT_START_END_CHARS: CharArray = charArrayOf('T', 'N', '*', 'E')

        /** Characters that decode to 10 modules each. */
        val CHARS_WHICH_ARE_TEN_LENGTH_EACH_AFTER_DECODED: CharArray = charArrayOf('/', ':', '+', '.')

        /** Guard added when the contents have none. */
        val DEFAULT_GUARD: Char = START_END_CHARS[0]

        /** Returns [contents] with default guards added when needed; throws if its guards are mismatched. */
        fun withGuards(contents: String): String {
            if (contents.length < 2) {
                return "$DEFAULT_GUARD$contents$DEFAULT_GUARD"
            }
            val firstChar = contents[0].uppercaseChar()
            val lastChar = contents[contents.length - 1].uppercaseChar()
            val startsNormal = CodaBarReader.arrayContains(START_END_CHARS, firstChar)
            val endsNormal = CodaBarReader.arrayContains(START_END_CHARS, lastChar)
            val startsAlt = CodaBarReader.arrayContains(ALT_START_END_CHARS, firstChar)
            val endsAlt = CodaBarReader.arrayContains(ALT_START_END_CHARS, lastChar)
            if (startsNormal) {
                if (!endsNormal) {
                    throw IllegalArgumentException("Invalid start/end guards")
                }
                return contents
            }
            if (startsAlt) {
                if (!endsAlt) {
                    throw IllegalArgumentException("Invalid start/end guards")
                }
                return contents
            }
            if (endsNormal || endsAlt) {
                throw IllegalArgumentException("Invalid start/end guards")
            }
            return "$DEFAULT_GUARD$contents$DEFAULT_GUARD"
        }

        /** Maps an alternate guard character to its standard A-D equivalent. */
        fun toStandardGuard(c: Char): Char = when (c) {
            'T' -> 'A'
            'N' -> 'B'
            '*' -> 'C'
            'E' -> 'D'
            else -> c
        }
    }
}
