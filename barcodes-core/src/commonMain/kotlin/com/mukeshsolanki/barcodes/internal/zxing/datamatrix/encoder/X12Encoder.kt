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

/** ANSI X12 encodation: upper case, digits, space, CR, '*' and '>'. */
internal class X12Encoder : C40Encoder() {

    /** Always X12_ENCODATION. */
    override val encodingMode: Int
        get() = HighLevelEncoder.X12_ENCODATION

    /** Encodes triplets until the look-ahead prefers another mode. */
    override fun encode(context: EncoderContext) {
        val buffer = StringBuilder()
        while (context.hasMoreCharacters()) {
            val c = context.currentChar
            context.pos++

            encodeChar(c, buffer)

            val count = buffer.length
            if (count % 3 == 0) {
                C40Encoder.writeNextTriplet(context, buffer)

                val newMode = HighLevelEncoder.lookAheadTest(context.message, context.pos, encodingMode)
                if (newMode != encodingMode) {
                    context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
                    break
                }
            }
        }
        handleEOD(context, buffer)
    }

    /** Appends the X12 value for [c] to [sb]; throws for characters X12 can't encode. */
    override fun encodeChar(c: Char, sb: StringBuilder): Int {
        when {
            c == '\r' -> sb.append('\u0000')
            c == '*' -> sb.append('\u0001')
            c == '>' -> sb.append('\u0002')
            c == ' ' -> sb.append('\u0003')
            c in '0'..'9' -> sb.append(c - 48 + 4)
            c in 'A'..'Z' -> sb.append(c - 65 + 14)
            else -> HighLevelEncoder.illegalCharacter()
        }
        return 1
    }

    /** Rewinds unflushed characters to ASCII and writes the unlatch when needed. */
    override fun handleEOD(context: EncoderContext, buffer: StringBuilder) {
        context.updateSymbolInfo()
        val available = context.symbolInfo!!.dataCapacity - context.codewordCount
        val count = buffer.length
        context.pos -= count
        if (context.remainingCharacters > 1 || available > 1 || context.remainingCharacters != available) {
            context.writeCodeword(HighLevelEncoder.X12_UNLATCH)
        }
        if (context.newEncoding < 0) {
            context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
        }
    }
}
