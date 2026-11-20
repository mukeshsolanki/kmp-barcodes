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

/** C40 encodation: three values per two codewords, upper-case oriented. */
internal open class C40Encoder : Encoder {

    /** Always C40_ENCODATION for this class. */
    override val encodingMode: Int
        get() = HighLevelEncoder.C40_ENCODATION

    /** Encodes triplets until the look-ahead prefers another mode, avoiding a lone value at the end. */
    override fun encode(context: EncoderContext) {
        val buffer = StringBuilder()
        while (context.hasMoreCharacters()) {
            val c = context.currentChar
            context.pos++

            var lastCharSize = encodeChar(c, buffer)

            val unwritten = (buffer.length / 3) * 2

            val curCodewordCount = context.codewordCount + unwritten
            context.updateSymbolInfo(curCodewordCount)
            val available = context.symbolInfo!!.dataCapacity - curCodewordCount

            if (!context.hasMoreCharacters()) {
                val removed = StringBuilder()
                if (buffer.length % 3 == 2 && available != 2) {
                    lastCharSize = backtrackOneCharacter(context, buffer, removed, lastCharSize)
                }
                while (buffer.length % 3 == 1 && (lastCharSize > 3 || available != 1)) {
                    lastCharSize = backtrackOneCharacter(context, buffer, removed, lastCharSize)
                }
                break
            }

            val count = buffer.length
            if (count % 3 == 0) {
                val newMode = HighLevelEncoder.lookAheadTest(context.message, context.pos, encodingMode)
                if (newMode != encodingMode) {
                    context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
                    break
                }
            }
        }
        handleEOD(context, buffer)
    }

    /** Removes the last encoded character from [buffer] and steps back one position. */
    private fun backtrackOneCharacter(
        context: EncoderContext,
        buffer: StringBuilder,
        removed: StringBuilder,
        lastCharSize: Int,
    ): Int {
        val count = buffer.length
        buffer.deleteRange(count - lastCharSize, count)
        context.pos--
        val c = context.currentChar
        val size = encodeChar(c, removed)
        context.resetSymbolInfo()
        return size
    }

    /** Handles end of data: flushes triplets and writes the unlatch when needed. */
    open fun handleEOD(context: EncoderContext, buffer: StringBuilder) {
        val unwritten = (buffer.length / 3) * 2
        val rest = buffer.length % 3

        val curCodewordCount = context.codewordCount + unwritten
        context.updateSymbolInfo(curCodewordCount)
        val available = context.symbolInfo!!.dataCapacity - curCodewordCount

        if (rest == 2) {
            buffer.append('\u0000')
            while (buffer.length >= 3) {
                writeNextTriplet(context, buffer)
            }
            if (context.hasMoreCharacters()) {
                context.writeCodeword(HighLevelEncoder.C40_UNLATCH)
            }
        } else if (available == 1 && rest == 1) {
            while (buffer.length >= 3) {
                writeNextTriplet(context, buffer)
            }
            if (context.hasMoreCharacters()) {
                context.writeCodeword(HighLevelEncoder.C40_UNLATCH)
            }
            context.pos--
        } else if (rest == 0) {
            while (buffer.length >= 3) {
                writeNextTriplet(context, buffer)
            }
            if (available > 0 || context.hasMoreCharacters()) {
                context.writeCodeword(HighLevelEncoder.C40_UNLATCH)
            }
        } else {
            error("Unexpected case. Please report!")
        }
        context.signalEncoderChange(HighLevelEncoder.ASCII_ENCODATION)
    }

    /** Appends the C40 values for [c] to [sb] and returns how many were added. */
    open fun encodeChar(c: Char, sb: StringBuilder): Int {
        if (c == ' ') {
            sb.append('\u0003')
            return 1
        }
        if (c in '0'..'9') {
            sb.append(c - 48 + 4)
            return 1
        }
        if (c in 'A'..'Z') {
            sb.append(c - 65 + 14)
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
        if (c <= '_') {
            sb.append('\u0001')
            sb.append(c - 91 + 22)
            return 2
        }
        if (c.code <= 127) {
            sb.append('\u0002')
            sb.append(c - 96)
            return 2
        }
        sb.append("\u0001\u001e")
        return 2 + encodeChar(c - 128, sb)
    }

    /** Triplet packing shared with Text and X12. */
    companion object {

        /** Writes the first three values of [buffer] as two codewords and removes them. */
        fun writeNextTriplet(context: EncoderContext, buffer: StringBuilder) {
            context.writeCodewords(encodeToCodewords(buffer))
            buffer.deleteRange(0, 3)
        }

        /** Packs three values as (1600 * c1) + (40 * c2) + c3 + 1 into two codewords. */
        private fun encodeToCodewords(sb: CharSequence): String {
            val v = 1600 * sb[0].code + 40 * sb[1].code + sb[2].code + 1
            return charArrayOf((v / 256).toChar(), (v % 256).toChar()).concatToString()
        }
    }
}
