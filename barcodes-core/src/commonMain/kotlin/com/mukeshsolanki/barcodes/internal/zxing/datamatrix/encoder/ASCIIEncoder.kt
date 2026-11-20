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

/** ASCII encodation: digit pairs, single characters and upper-shifted extended ASCII. */
internal class ASCIIEncoder : Encoder {

    /** Always ASCII_ENCODATION. */
    override val encodingMode: Int
        get() = HighLevelEncoder.ASCII_ENCODATION

    /** Encodes one digit pair or one character, or latches to a better mode. */
    override fun encode(context: EncoderContext) {
        val n = HighLevelEncoder.determineConsecutiveDigitCount(context.message, context.pos)
        if (n >= 2) {
            context.writeCodeword(encodeASCIIDigits(context.message[context.pos], context.message[context.pos + 1]))
            context.pos += 2
            return
        }
        val c = context.currentChar
        val newMode = HighLevelEncoder.lookAheadTest(context.message, context.pos, encodingMode)
        if (newMode != encodingMode) {
            context.writeCodeword(latchFor(newMode))
            context.signalEncoderChange(newMode)
        } else if (HighLevelEncoder.isExtendedASCII(c)) {
            context.writeCodeword(HighLevelEncoder.UPPER_SHIFT)
            context.writeCodeword(c - 128 + 1)
            context.pos++
        } else {
            context.writeCodeword(c + 1)
            context.pos++
        }
    }

    /** Helpers for ASCII codewords. */
    private companion object {

        /** Returns the latch codeword that switches from ASCII to [mode]. */
        private fun latchFor(mode: Int): Char = when (mode) {
            HighLevelEncoder.BASE256_ENCODATION -> HighLevelEncoder.LATCH_TO_BASE256
            HighLevelEncoder.C40_ENCODATION -> HighLevelEncoder.LATCH_TO_C40
            HighLevelEncoder.X12_ENCODATION -> HighLevelEncoder.LATCH_TO_ANSIX12
            HighLevelEncoder.TEXT_ENCODATION -> HighLevelEncoder.LATCH_TO_TEXT
            HighLevelEncoder.EDIFACT_ENCODATION -> HighLevelEncoder.LATCH_TO_EDIFACT
            else -> error("Illegal mode: $mode")
        }

        /** Encodes two digits as one codeword (value + 130). */
        private fun encodeASCIIDigits(digit1: Char, digit2: Char): Char {
            require(HighLevelEncoder.isDigit(digit1) && HighLevelEncoder.isDigit(digit2)) { "not digits" }
            val num = (digit1 - '0') * 10 + (digit2 - '0')
            return (num + 130).toChar()
        }
    }
}
