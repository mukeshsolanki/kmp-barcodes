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

/** EDIFACT encodation: four 6-bit values per three codewords. */
internal class EdifactEncoder : Encoder {

    /** Always EDIFACT_ENCODATION. */
    override val encodingMode: Int
        get() = HighLevelEncoder.EDIFACT_ENCODATION

    /** Encodes quadruples until the look-ahead prefers another mode, then unlatches. */
    override fun encode(context: EncoderContext) {
        val buffer = StringBuilder()
        while (context.hasMoreCharacters()) {
            val c = context.currentChar
            encodeChar(c, buffer)
            context.pos++

            val count = buffer.length
            if (count >= 4) {
                context.writeCodewords(encodeToCodewords(buffer))
                buffer.deleteRange(0, 4)

                val newMode = HighLevelEncoder.lookAheadTest(context.message, context.pos, encodingMode)
                if (newMode != encodingMode) {
                    context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
                    break
                }
            }
        }
        buffer.append(31.toChar())
        handleEOD(context, buffer)
    }

    /** EDIFACT helpers. */
    private companion object {

        /** Handles end of data, always signalling a return to ASCII. */
        private fun handleEOD(context: EncoderContext, buffer: CharSequence) {
            try {
                writeEndOfData(context, buffer)
            } finally {
                context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
            }
        }

        /** Writes the remaining values plus unlatch, or rewinds them to ASCII when that is cheaper. */
        private fun writeEndOfData(context: EncoderContext, buffer: CharSequence) {
            val count = buffer.length
            if (count == 0) {
                return
            }
            if (count == 1 && endsWithoutUnlatch(context)) {
                return
            }

            check(count <= 4) { "Count must not exceed 4" }
            val restChars = count - 1
            val encoded = encodeToCodewords(buffer)
            val endOfSymbolReached = !context.hasMoreCharacters()
            var restInAscii = endOfSymbolReached && restChars <= 2

            if (restChars <= 2) {
                context.updateSymbolInfo(context.codewordCount + restChars)
                val available = context.symbolInfo!!.dataCapacity - context.codewordCount
                if (available >= 3) {
                    restInAscii = false
                    context.updateSymbolInfo(context.codewordCount + encoded.length)
                }
            }

            if (restInAscii) {
                context.resetSymbolInfo()
                context.pos -= restChars
            } else {
                context.writeCodewords(encoded)
            }
        }

        /** True when only the unlatch is left and the symbol can end without writing it. */
        private fun endsWithoutUnlatch(context: EncoderContext): Boolean {
            context.updateSymbolInfo()
            var available = context.symbolInfo!!.dataCapacity - context.codewordCount
            val remaining = context.remainingCharacters
            if (remaining > available) {
                context.updateSymbolInfo(context.codewordCount + 1)
                available = context.symbolInfo!!.dataCapacity - context.codewordCount
            }
            return remaining <= available && available <= 2
        }

        /** Appends the 6-bit EDIFACT value of [c]; throws for characters EDIFACT can't encode. */
        private fun encodeChar(c: Char, sb: StringBuilder) {
            when (c) {
                in ' '..'?' -> sb.append(c)
                in '@'..'^' -> sb.append(c - 64)
                else -> HighLevelEncoder.illegalCharacter()
            }
        }

        /** Packs up to four 6-bit values into up to three codewords. */
        private fun encodeToCodewords(sb: CharSequence): String {
            val len = sb.length
            check(len != 0) { "StringBuilder must not be empty" }
            val c1 = sb[0].code
            val c2 = if (len >= 2) sb[1].code else 0
            val c3 = if (len >= 3) sb[2].code else 0
            val c4 = if (len >= 4) sb[3].code else 0

            val v = (c1 shl 18) + (c2 shl 12) + (c3 shl 6) + c4
            val res = StringBuilder(3)
            res.append(((v shr 16) and 255).toChar())
            if (len >= 2) {
                res.append(((v shr 8) and 255).toChar())
            }
            if (len >= 3) {
                res.append((v and 255).toChar())
            }
            return res.toString()
        }
    }
}
