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

/** Base 256 encodation: raw bytes behind a length field, 255-state randomised. */
internal class Base256Encoder : Encoder {

    /** Always BASE256_ENCODATION. */
    override val encodingMode: Int
        get() = HighLevelEncoder.BASE256_ENCODATION

    /** Encodes bytes until the look-ahead prefers another mode, then writes length and data. */
    override fun encode(context: EncoderContext) {
        val buffer = StringBuilder()
        buffer.append('\u0000')
        while (context.hasMoreCharacters()) {
            val c = context.currentChar
            buffer.append(c)

            context.pos++

            val newMode = HighLevelEncoder.lookAheadTest(context.message, context.pos, encodingMode)
            if (newMode != encodingMode) {
                context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
                break
            }
        }
        val dataCount = buffer.length - 1
        val lengthFieldSize = 1
        val currentSize = context.codewordCount + dataCount + lengthFieldSize
        context.updateSymbolInfo(currentSize)
        val mustPad = context.symbolInfo!!.dataCapacity - currentSize > 0
        if (context.hasMoreCharacters() || mustPad) {
            if (dataCount <= 249) {
                buffer[0] = dataCount.toChar()
            } else if (dataCount <= 1555) {
                buffer[0] = (dataCount / 250 + 249).toChar()
                buffer.insert(1, (dataCount % 250).toChar())
            } else {
                error("Message length not in valid ranges: $dataCount")
            }
        }
        for (i in 0 until buffer.length) {
            context.writeCodeword(randomize255State(buffer[i], context.codewordCount + 1))
        }
    }

    /** Randomisation helper. */
    private companion object {

        /** Applies the 255-state randomisation for 1-based [codewordPosition]. */
        private fun randomize255State(ch: Char, codewordPosition: Int): Char {
            val pseudoRandom = ((149 * codewordPosition) % 255) + 1
            val tempVariable = ch.code + pseudoRandom
            return (if (tempVariable <= 255) tempVariable else tempVariable - 256).toChar()
        }
    }
}
