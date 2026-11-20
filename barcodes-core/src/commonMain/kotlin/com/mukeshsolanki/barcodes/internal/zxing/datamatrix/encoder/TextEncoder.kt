/*
 * Copyright 2006-2007 Jeremias Maerki.
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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder

/** Text encodation: like C40 but lower-case oriented. */
internal class TextEncoder : C40Encoder() {

    /** Always TEXT_ENCODATION. */
    override val encodingMode: Int
        get() = HighLevelEncoder.TEXT_ENCODATION

    /** Appends the Text values for [c] to [sb] and returns how many were added. */
    override fun encodeChar(c: Char, sb: StringBuilder): Int {
        if (c == ' ') {
            sb.append('\u0003')
            return 1
        }
        if (c in '0'..'9') {
            sb.append(c - 48 + 4)
            return 1
        }
        if (c in 'a'..'z') {
            sb.append(c - 97 + 14)
            return 1
        }
        if (c < ' ') {
            sb.append('\u0000')
            sb.append(c)
            return 2
        }
        if (c <= '/') {
            sb.append('\u0001')
            sb.append(c - 33)
            return 2
        }
        if (c <= '@') {
            sb.append('\u0001')
            sb.append(c - 58 + 15)
            return 2
        }
        if (c in '['..'_') {
            sb.append('\u0001')
            sb.append(c - 91 + 22)
            return 2
        }
        if (c == '`') {
            sb.append('\u0002')
            sb.append('\u0000')
            return 2
        }
        if (c <= 'Z') {
            sb.append('\u0002')
            sb.append(c - 65 + 1)
            return 2
        }
        if (c.code <= 127) {
            sb.append('\u0002')
            sb.append(c - 123 + 27)
            return 2
        }
        sb.append("\u0001\u001e")
        return 2 + encodeChar(c - 128, sb)
    }
}
