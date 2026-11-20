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

import kotlin.math.ceil
import kotlin.math.min

/** Data Matrix ECC 200 high-level encoder (ISO/IEC 16022:2000 Annex P and S). */
internal object HighLevelEncoder {

    /** Padding codeword. */
    private const val PAD: Char = '\u0081'

    /** Latch to C40 encodation. */
    const val LATCH_TO_C40: Char = '\u00E6'

    /** Latch to Base 256 encodation. */
    const val LATCH_TO_BASE256: Char = '\u00E7'

    /** Upper Shift to extended ASCII. */
    const val UPPER_SHIFT: Char = '\u00EB'

    /** 05 Macro codeword. */
    private const val MACRO_05: Char = '\u00EC'

    /** 06 Macro codeword. */
    private const val MACRO_06: Char = '\u00ED'

    /** Latch to ANSI X12 encodation. */
    const val LATCH_TO_ANSIX12: Char = '\u00EE'

    /** Latch to Text encodation. */
    const val LATCH_TO_TEXT: Char = '\u00EF'

    /** Latch to EDIFACT encodation. */
    const val LATCH_TO_EDIFACT: Char = '\u00F0'

    /** Unlatch from C40 encodation. */
    const val C40_UNLATCH: Char = '\u00FE'

    /** Unlatch from X12 encodation. */
    const val X12_UNLATCH: Char = '\u00FE'

    /** 05 Macro header. */
    const val MACRO_05_HEADER: String = "[)>\u001E05\u001D"

    /** 06 Macro header. */
    const val MACRO_06_HEADER: String = "[)>\u001E06\u001D"

    /** Macro trailer. */
    const val MACRO_TRAILER: String = "\u001E\u0004"

    /** ASCII encodation mode. */
    const val ASCII_ENCODATION: Int = 0

    /** C40 encodation mode. */
    const val C40_ENCODATION: Int = 1

    /** Text encodation mode. */
    const val TEXT_ENCODATION: Int = 2

    /** ANSI X12 encodation mode. */
    const val X12_ENCODATION: Int = 3

    /** EDIFACT encodation mode. */
    const val EDIFACT_ENCODATION: Int = 4

    /** Base 256 encodation mode. */
    const val BASE256_ENCODATION: Int = 5

    /** Returns the 253-state randomised pad codeword for 1-based [codewordPosition]. */
    private fun randomize253State(codewordPosition: Int): Char {
        val pseudoRandom = ((149 * codewordPosition) % 253) + 1
        val tempVariable = PAD.code + pseudoRandom
        return (if (tempVariable <= 254) tempVariable else tempVariable - 254).toChar()
    }

    /** Encodes [msg] into data codewords (chars 0..255) padded to the chosen symbol of [shape]. */
    fun encodeHighLevel(msg: String, shape: SymbolShapeHint = SymbolShapeHint.FORCE_NONE): String {
        val encoders = arrayOf(
            ASCIIEncoder(), C40Encoder(), TextEncoder(),
            X12Encoder(), EdifactEncoder(), Base256Encoder(),
        )

        val context = EncoderContext(msg)
        context.setSymbolShape(shape)

        if (msg.startsWith(MACRO_05_HEADER) && msg.endsWith(MACRO_TRAILER)) {
            context.writeCodeword(MACRO_05)
            context.setSkipAtEnd(2)
            context.pos += MACRO_05_HEADER.length
        } else if (msg.startsWith(MACRO_06_HEADER) && msg.endsWith(MACRO_TRAILER)) {
            context.writeCodeword(MACRO_06)
            context.setSkipAtEnd(2)
            context.pos += MACRO_06_HEADER.length
        }

        var encodingMode = ASCII_ENCODATION

        while (context.hasMoreCharacters()) {
            encoders[encodingMode].encode(context)
            if (context.newEncoding >= 0) {
                encodingMode = context.newEncoding
                context.resetEncoderSignal()
            }
        }
        val len = context.codewordCount
        context.updateSymbolInfo()
        val capacity = context.symbolInfo!!.dataCapacity
        if (len < capacity &&
            encodingMode != ASCII_ENCODATION &&
            encodingMode != BASE256_ENCODATION &&
            encodingMode != EDIFACT_ENCODATION
        ) {
            context.writeCodeword('þ')
        }
        val codewords = context.codewords
        if (codewords.length < capacity) {
            codewords.append(PAD)
        }
        while (codewords.length < capacity) {
            codewords.append(randomize253State(codewords.length + 1))
        }

        return context.codewords.toString()
    }

    /** Picks the encodation to use from [startpos], refusing X12/EDIFACT when the next chars aren't native. */
    fun lookAheadTest(msg: CharSequence, startpos: Int, currentMode: Int): Int {
        val newMode = lookAheadTestIntern(msg, startpos, currentMode)
        if (currentMode == X12_ENCODATION && newMode == X12_ENCODATION) {
            val endpos = min(startpos + 3, msg.length)
            for (i in startpos until endpos) {
                if (!isNativeX12(msg[i])) {
                    return ASCII_ENCODATION
                }
            }
        } else if (currentMode == EDIFACT_ENCODATION && newMode == EDIFACT_ENCODATION) {
            val endpos = min(startpos + 4, msg.length)
            for (i in startpos until endpos) {
                if (!isNativeEDIFACT(msg[i])) {
                    return ASCII_ENCODATION
                }
            }
        }
        return newMode
    }

    /** Annex P look-ahead: estimates the cheapest encodation for the rest of [msg]. */
    fun lookAheadTestIntern(msg: CharSequence, startpos: Int, currentMode: Int): Int {
        if (startpos >= msg.length) {
            return currentMode
        }
        val charCounts: FloatArray
        if (currentMode == ASCII_ENCODATION) {
            charCounts = floatArrayOf(0f, 1f, 1f, 1f, 1f, 1.25f)
        } else {
            charCounts = floatArrayOf(1f, 2f, 2f, 2f, 2f, 2.25f)
            charCounts[currentMode] = 0f
        }

        var charsProcessed = 0
        val mins = IntArray(6)
        val intCharCounts = IntArray(6)
        while (true) {
            if (startpos + charsProcessed == msg.length) {
                return modeAtEndOfData(charCounts, intCharCounts, mins)
            }

            val c = msg[startpos + charsProcessed]
            charsProcessed++

            addCharCosts(c, charCounts)

            if (charsProcessed >= 4) {
                mins.fill(0)
                intCharCounts.fill(0)
                findMinimums(charCounts, intCharCounts, Int.MAX_VALUE, mins)
                val mode = modeAfterFourChars(msg, startpos + charsProcessed, intCharCounts)
                if (mode >= 0) {
                    return mode
                }
            }
        }
    }

    /** Step K: chooses the mode once the end of the message is reached. */
    private fun modeAtEndOfData(charCounts: FloatArray, intCharCounts: IntArray, mins: IntArray): Int {
        mins.fill(0)
        intCharCounts.fill(0)
        val min = findMinimums(charCounts, intCharCounts, Int.MAX_VALUE, mins)
        val minCount = mins.sum()

        if (intCharCounts[ASCII_ENCODATION] == min) {
            return ASCII_ENCODATION
        }
        if (minCount == 1) {
            if (mins[BASE256_ENCODATION] > 0) {
                return BASE256_ENCODATION
            }
            if (mins[EDIFACT_ENCODATION] > 0) {
                return EDIFACT_ENCODATION
            }
            if (mins[TEXT_ENCODATION] > 0) {
                return TEXT_ENCODATION
            }
            if (mins[X12_ENCODATION] > 0) {
                return X12_ENCODATION
            }
        }
        return C40_ENCODATION
    }

    /** Steps L to Q (zxing's special Base 256 check is always false): adds the cost of [c] in every encodation to [charCounts]. */
    private fun addCharCosts(c: Char, charCounts: FloatArray) {
        if (isDigit(c)) {
            charCounts[ASCII_ENCODATION] += 0.5f
        } else if (isExtendedASCII(c)) {
            charCounts[ASCII_ENCODATION] = ceil(charCounts[ASCII_ENCODATION])
            charCounts[ASCII_ENCODATION] += 2.0f
        } else {
            charCounts[ASCII_ENCODATION] = ceil(charCounts[ASCII_ENCODATION])
            charCounts[ASCII_ENCODATION]++
        }

        if (isNativeC40(c)) {
            charCounts[C40_ENCODATION] += 2.0f / 3.0f
        } else if (isExtendedASCII(c)) {
            charCounts[C40_ENCODATION] += 8.0f / 3.0f
        } else {
            charCounts[C40_ENCODATION] += 4.0f / 3.0f
        }

        if (isNativeText(c)) {
            charCounts[TEXT_ENCODATION] += 2.0f / 3.0f
        } else if (isExtendedASCII(c)) {
            charCounts[TEXT_ENCODATION] += 8.0f / 3.0f
        } else {
            charCounts[TEXT_ENCODATION] += 4.0f / 3.0f
        }

        if (isNativeX12(c)) {
            charCounts[X12_ENCODATION] += 2.0f / 3.0f
        } else if (isExtendedASCII(c)) {
            charCounts[X12_ENCODATION] += 13.0f / 3.0f
        } else {
            charCounts[X12_ENCODATION] += 10.0f / 3.0f
        }

        if (isNativeEDIFACT(c)) {
            charCounts[EDIFACT_ENCODATION] += 3.0f / 4.0f
        } else if (isExtendedASCII(c)) {
            charCounts[EDIFACT_ENCODATION] += 17.0f / 4.0f
        } else {
            charCounts[EDIFACT_ENCODATION] += 13.0f / 4.0f
        }

        charCounts[BASE256_ENCODATION]++
    }

    /** Step R: returns the decided mode after four or more characters, or -1 to keep looking. */
    private fun modeAfterFourChars(msg: CharSequence, nextPos: Int, counts: IntArray): Int {
        val ascii = counts[ASCII_ENCODATION]
        val c40 = counts[C40_ENCODATION]
        val text = counts[TEXT_ENCODATION]
        val x12 = counts[X12_ENCODATION]
        val edifact = counts[EDIFACT_ENCODATION]
        val base256 = counts[BASE256_ENCODATION]
        if (ascii < min(base256, c40, text, x12, edifact)) {
            return ASCII_ENCODATION
        }
        if (base256 < ascii || base256 + 1 < min(c40, text, x12, edifact)) {
            return BASE256_ENCODATION
        }
        if (edifact + 1 < min(base256, c40, text, x12, ascii)) {
            return EDIFACT_ENCODATION
        }
        if (text + 1 < min(base256, c40, edifact, x12, ascii)) {
            return TEXT_ENCODATION
        }
        if (x12 + 1 < min(base256, c40, edifact, text, ascii)) {
            return X12_ENCODATION
        }
        if (c40 + 1 < min(ascii, base256, edifact, text)) {
            if (c40 < x12) {
                return C40_ENCODATION
            }
            if (c40 == x12) {
                return c40OrX12(msg, nextPos + 1)
            }
        }
        return -1
    }

    /** Breaks a C40/X12 tie: X12 when an X12 terminator follows a run of native X12 characters. */
    private fun c40OrX12(msg: CharSequence, start: Int): Int {
        var p = start
        while (p < msg.length) {
            val tc = msg[p]
            if (isX12TermSep(tc)) {
                return X12_ENCODATION
            }
            if (!isNativeX12(tc)) {
                break
            }
            p++
        }
        return C40_ENCODATION
    }

    /** Minimum of five ints. */
    private fun min(f1: Int, f2: Int, f3: Int, f4: Int, f5: Int): Int = min(min(f1, f2, f3, f4), f5)

    /** Minimum of four ints. */
    private fun min(f1: Int, f2: Int, f3: Int, f4: Int): Int = min(f1, min(f2, min(f3, f4)))

    /** Rounds [charCounts] up into [intCharCounts], marks the minimal modes in [mins] and returns the minimum. */
    private fun findMinimums(charCounts: FloatArray, intCharCounts: IntArray, min: Int, mins: IntArray): Int {
        var result = min
        for (i in 0 until 6) {
            val current = ceil(charCounts[i]).toInt()
            intCharCounts[i] = current
            if (result > current) {
                result = current
                mins.fill(0)
            }
            if (result == current) {
                mins[i]++
            }
        }
        return result
    }

    /** True for '0'..'9'. */
    fun isDigit(ch: Char): Boolean = ch in '0'..'9'

    /** True for chars 128..255. */
    fun isExtendedASCII(ch: Char): Boolean = ch.code in 128..255

    /** True for chars C40 encodes in its basic set. */
    fun isNativeC40(ch: Char): Boolean = ch == ' ' || ch in '0'..'9' || ch in 'A'..'Z'

    /** True for chars Text encodes in its basic set. */
    fun isNativeText(ch: Char): Boolean = ch == ' ' || ch in '0'..'9' || ch in 'a'..'z'

    /** True for chars X12 can encode. */
    fun isNativeX12(ch: Char): Boolean = isX12TermSep(ch) || ch == ' ' || ch in '0'..'9' || ch in 'A'..'Z'

    /** True for the X12 terminator and separators CR, '*' and '>'. */
    private fun isX12TermSep(ch: Char): Boolean = ch == '\r' || ch == '*' || ch == '>'

    /** True for chars EDIFACT can encode. */
    fun isNativeEDIFACT(ch: Char): Boolean = ch in ' '..'^'

    /** Number of consecutive digits in [msg] starting at [startpos]. */
    fun determineConsecutiveDigitCount(msg: CharSequence, startpos: Int): Int {
        var idx = startpos
        while (idx < msg.length && isDigit(msg[idx])) {
            idx++
        }
        return idx - startpos
    }

    /** Throws for a character the current encodation cannot represent; the character is not echoed. */
    fun illegalCharacter(): Nothing {
        throw IllegalArgumentException("Illegal character for the current Data Matrix encodation")
    }
}
