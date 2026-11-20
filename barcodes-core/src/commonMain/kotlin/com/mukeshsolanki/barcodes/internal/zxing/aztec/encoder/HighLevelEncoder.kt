/*
 * Copyright 2013 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.aztec.encoder

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.common.CharacterSetECI

/**
 * Produces nearly optimal encodings of text into the first level of Aztec encoding, using a dynamic
 * algorithm that keeps the set of optimal states for each prefix of the input.
 */
internal class HighLevelEncoder(
    /** The bytes to encode. */
    private val text: ByteArray,
    /** Charset to announce with an ECI designator, or null for none. */
    private val charset: Charset? = null,
) {

    /** Returns the text represented by this encoder encoded as a [BitArray]. */
    fun encode(): BitArray {
        var initialState = State.INITIAL_STATE
        if (charset != null) {
            val eci = CharacterSetECI.getCharacterSetECI(charset)
                ?: throw IllegalArgumentException("No ECI code for character set " + charset.name)
            initialState = initialState.appendFLGn(eci.value)
        }
        var states: Collection<State> = listOf(initialState)
        var index = 0
        while (index < text.size) {
            val pairCode = pairCodeAt(index)
            if (pairCode > 0) {
                states = updateStateListForPair(states, index, pairCode)
                index++
            } else {
                states = updateStateListForChar(states, index)
            }
            index++
        }
        val minState = states.minBy { it.bitCount }
        return minState.toBitArray(text)
    }

    /** Returns the PUNCT pair code (2..5) starting at [index], or 0 when there is no pair. */
    private fun pairCodeAt(index: Int): Int {
        val nextChar = if (index + 1 < text.size) text[index + 1].toInt() else 0
        return when (text[index].toInt()) {
            '\r'.code -> if (nextChar == '\n'.code) 2 else 0
            '.'.code -> if (nextChar == ' '.code) 3 else 0
            ','.code -> if (nextChar == ' '.code) 4 else 0
            ':'.code -> if (nextChar == ' '.code) 5 else 0
            else -> 0
        }
    }

    /** Updates every state in [states] for the character at [index], then drops non-optimal states. */
    private fun updateStateListForChar(states: Iterable<State>, index: Int): Collection<State> {
        val result = ArrayList<State>()
        for (state in states) {
            updateStateForChar(state, index, result)
        }
        return simplifyStates(result)
    }

    /** Adds to [result] every useful way of extending [state] with the character at [index]. */
    private fun updateStateForChar(state: State, index: Int, result: MutableCollection<State>) {
        val ch = text[index].toInt() and 0xFF
        val charInCurrentTable = CHAR_MAP[state.mode][ch] > 0
        var stateNoBinary: State? = null
        for (mode in 0..MODE_PUNCT) {
            val charInMode = CHAR_MAP[mode][ch]
            if (charInMode > 0) {
                val noBinary = stateNoBinary ?: state.endBinaryShift(index)
                stateNoBinary = noBinary
                if (!charInCurrentTable || mode == state.mode || mode == MODE_DIGIT) {
                    result.add(noBinary.latchAndAppend(mode, charInMode))
                }
                if (!charInCurrentTable && SHIFT_TABLE[state.mode][mode] >= 0) {
                    result.add(noBinary.shiftAndAppend(mode, charInMode))
                }
            }
        }
        if (state.binaryShiftByteCount > 0 || CHAR_MAP[state.mode][ch] == 0) {
            result.add(state.addBinaryShiftChar(index))
        }
    }

    /** Shared tables and static helpers. */
    companion object {
        /** UPPER mode, 5 bits per code. */
        const val MODE_UPPER: Int = 0

        /** LOWER mode, 5 bits per code. */
        const val MODE_LOWER: Int = 1

        /** DIGIT mode, 4 bits per code. */
        const val MODE_DIGIT: Int = 2

        /** MIXED mode, 5 bits per code. */
        const val MODE_MIXED: Int = 3

        /** PUNCT mode, 5 bits per code. */
        const val MODE_PUNCT: Int = 4

        /**
         * The optimal way to latch from one mode to another: the high half-word is the bit count, the
         * low half-word holds the bits.
         */
        val LATCH_TABLE: Array<IntArray> = arrayOf(
            intArrayOf(
                0,
                (5 shl 16) + 28,
                (5 shl 16) + 30,
                (5 shl 16) + 29,
                (10 shl 16) + (29 shl 5) + 30,
            ),
            intArrayOf(
                (9 shl 16) + (30 shl 4) + 14,
                0,
                (5 shl 16) + 30,
                (5 shl 16) + 29,
                (10 shl 16) + (29 shl 5) + 30,
            ),
            intArrayOf(
                (4 shl 16) + 14,
                (9 shl 16) + (14 shl 5) + 28,
                0,
                (9 shl 16) + (14 shl 5) + 29,
                (14 shl 16) + (14 shl 10) + (29 shl 5) + 30,
            ),
            intArrayOf(
                (5 shl 16) + 29,
                (5 shl 16) + 28,
                (10 shl 16) + (29 shl 5) + 30,
                0,
                (5 shl 16) + 30,
            ),
            intArrayOf(
                (5 shl 16) + 31,
                (10 shl 16) + (31 shl 5) + 28,
                (10 shl 16) + (31 shl 5) + 30,
                (10 shl 16) + (31 shl 5) + 29,
                0,
            ),
        )

        /** Characters of MIXED mode, indexed by their code. */
        private val MIXED_TABLE: IntArray = intArrayOf(
            0, ' '.code, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 27, 28, 29, 30, 31, '@'.code, '\\'.code, '^'.code,
            '_'.code, '`'.code, '|'.code, '~'.code, 127,
        )

        /** Characters of PUNCT mode, indexed by their code (zxing's table, including its duplicate `'`). */
        private val PUNCT_TABLE: IntArray = intArrayOf(
            0, '\r'.code, 0, 0, 0, 0, '!'.code, '\''.code, '#'.code, '$'.code, '%'.code, '&'.code, '\''.code,
            '('.code, ')'.code, '*'.code, '+'.code, ','.code, '-'.code, '.'.code, '/'.code, ':'.code, ';'.code,
            '<'.code, '='.code, '>'.code, '?'.code, '['.code, ']'.code, '{'.code, '}'.code,
        )

        /** Reverse mapping from [mode][char] to the code of that character in that mode; 0 means none. */
        private val CHAR_MAP: Array<IntArray> = buildCharMap()

        /** Shift codes available from each mode to each other mode; -1 means no shift exists. */
        val SHIFT_TABLE: Array<IntArray> = buildShiftTable()

        /** Builds [CHAR_MAP] exactly as zxing's static initializer does. */
        private fun buildCharMap(): Array<IntArray> {
            val map = Array(5) { IntArray(256) }
            map[MODE_UPPER][' '.code] = 1
            for (c in 'A'..'Z') {
                map[MODE_UPPER][c.code] = c - 'A' + 2
            }
            map[MODE_LOWER][' '.code] = 1
            for (c in 'a'..'z') {
                map[MODE_LOWER][c.code] = c - 'a' + 2
            }
            map[MODE_DIGIT][' '.code] = 1
            for (c in '0'..'9') {
                map[MODE_DIGIT][c.code] = c - '0' + 2
            }
            map[MODE_DIGIT][','.code] = 12
            map[MODE_DIGIT]['.'.code] = 13
            for (i in MIXED_TABLE.indices) {
                map[MODE_MIXED][MIXED_TABLE[i]] = i
            }
            for (i in PUNCT_TABLE.indices) {
                if (PUNCT_TABLE[i] > 0) {
                    map[MODE_PUNCT][PUNCT_TABLE[i]] = i
                }
            }
            return map
        }

        /** Builds [SHIFT_TABLE] exactly as zxing's static initializer does. */
        private fun buildShiftTable(): Array<IntArray> {
            val table = Array(6) { IntArray(6) { -1 } }
            table[MODE_UPPER][MODE_PUNCT] = 0
            table[MODE_LOWER][MODE_PUNCT] = 0
            table[MODE_LOWER][MODE_UPPER] = 28
            table[MODE_MIXED][MODE_PUNCT] = 0
            table[MODE_DIGIT][MODE_PUNCT] = 0
            table[MODE_DIGIT][MODE_UPPER] = 15
            return table
        }

        /** Updates every state in [states] for the PUNCT pair at [index], then drops non-optimal states. */
        private fun updateStateListForPair(states: Iterable<State>, index: Int, pairCode: Int): Collection<State> {
            val result = ArrayList<State>()
            for (state in states) {
                updateStateForPair(state, index, pairCode, result)
            }
            return simplifyStates(result)
        }

        /** Adds to [result] every useful way of extending [state] with the PUNCT pair [pairCode]. */
        private fun updateStateForPair(state: State, index: Int, pairCode: Int, result: MutableCollection<State>) {
            val stateNoBinary = state.endBinaryShift(index)
            result.add(stateNoBinary.latchAndAppend(MODE_PUNCT, pairCode))
            if (state.mode != MODE_PUNCT) {
                result.add(stateNoBinary.shiftAndAppend(MODE_PUNCT, pairCode))
            }
            if (pairCode == 3 || pairCode == 4) {
                val digitState = stateNoBinary
                    .latchAndAppend(MODE_DIGIT, 16 - pairCode)
                    .latchAndAppend(MODE_DIGIT, 1)
                result.add(digitState)
            }
            if (state.binaryShiftByteCount > 0) {
                result.add(state.addBinaryShiftChar(index).addBinaryShiftChar(index + 1))
            }
        }

        /** Removes every state that another state is at least as good as, keeping zxing's ordering. */
        private fun simplifyStates(states: Iterable<State>): Collection<State> {
            val result = ArrayDeque<State>()
            for (newState in states) {
                if (isDominated(newState, result)) {
                    continue
                }
                result.addFirst(newState)
            }
            return result
        }

        /**
         * Walks [result] like zxing: returns true as soon as an old state beats [newState], removing
         * along the way every old state that [newState] beats.
         */
        private fun isDominated(newState: State, result: MutableList<State>): Boolean {
            val iterator = result.iterator()
            while (iterator.hasNext()) {
                val oldState = iterator.next()
                if (oldState.isBetterThanOrEqualTo(newState)) {
                    return true
                }
                if (newState.isBetterThanOrEqualTo(oldState)) {
                    iterator.remove()
                }
            }
            return false
        }
    }
}
