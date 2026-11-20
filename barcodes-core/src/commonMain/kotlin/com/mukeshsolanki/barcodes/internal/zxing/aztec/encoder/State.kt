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

import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/**
 * All information about a sequence necessary to generate the current output. A state is immutable.
 */
internal class State private constructor(
    /** The list of tokens output so far; in Binary Shift mode it does not yet include those bytes. */
    val token: Token,
    /** The current mode, or the mode to return to after Binary Shift. */
    val mode: Int,
    /** If non-zero, the number of most recent bytes to output in Binary Shift mode. */
    val binaryShiftByteCount: Int,
    /** The total number of bits generated, including Binary Shift. */
    val bitCount: Int,
) {

    /** The B/S header cost for the current binary shift run. */
    private val binaryShiftCost: Int = calculateBinaryShiftCost(binaryShiftByteCount)

    /** Appends FLG(n): FNC1 when [eci] is negative, otherwise the ECI designator [eci]. */
    fun appendFLGn(eci: Int): State {
        val result = shiftAndAppend(HighLevelEncoder.MODE_PUNCT, 0)
        var token = result.token
        var bitsAdded = 3
        if (eci < 0) {
            token = token.add(0, 3)
        } else if (eci > 999999) {
            throw IllegalArgumentException("ECI code must be between 0 and 999999")
        } else {
            val eciDigits = eci.toString()
            token = token.add(eciDigits.length, 3)
            for (eciDigit in eciDigits) {
                token = token.add(eciDigit - '0' + 2, 4)
            }
            bitsAdded += eciDigits.length * 4
        }
        return State(token, mode, 0, bitCount + bitsAdded)
    }

    /** Returns this state with a latch to [mode] (possibly the current one) followed by [value]. */
    fun latchAndAppend(mode: Int, value: Int): State {
        var bitCount = this.bitCount
        var token = this.token
        if (mode != this.mode) {
            val latch = HighLevelEncoder.LATCH_TABLE[this.mode][mode]
            token = token.add(latch and 0xFFFF, latch shr 16)
            bitCount += latch shr 16
        }
        val latchModeBitCount = if (mode == HighLevelEncoder.MODE_DIGIT) 4 else 5
        token = token.add(value, latchModeBitCount)
        return State(token, mode, 0, bitCount + latchModeBitCount)
    }

    /** Returns this state with a temporary shift to [mode] to output the single [value]. */
    fun shiftAndAppend(mode: Int, value: Int): State {
        var token = this.token
        val thisModeBitCount = if (this.mode == HighLevelEncoder.MODE_DIGIT) 4 else 5
        token = token.add(HighLevelEncoder.SHIFT_TABLE[this.mode][mode], thisModeBitCount)
        token = token.add(value, 5)
        return State(token, this.mode, 0, this.bitCount + thisModeBitCount + 5)
    }

    /** Returns this state with the character at [index] output in Binary Shift mode. */
    fun addBinaryShiftChar(index: Int): State {
        var token = this.token
        var mode = this.mode
        var bitCount = this.bitCount
        if (this.mode == HighLevelEncoder.MODE_PUNCT || this.mode == HighLevelEncoder.MODE_DIGIT) {
            val latch = HighLevelEncoder.LATCH_TABLE[mode][HighLevelEncoder.MODE_UPPER]
            token = token.add(latch and 0xFFFF, latch shr 16)
            bitCount += latch shr 16
            mode = HighLevelEncoder.MODE_UPPER
        }
        val deltaBitCount = when (binaryShiftByteCount) {
            0, 31 -> 18
            62 -> 9
            else -> 8
        }
        var result = State(token, mode, binaryShiftByteCount + 1, bitCount + deltaBitCount)
        if (result.binaryShiftByteCount == 2047 + 31) {
            result = result.endBinaryShift(index + 1)
        }
        return result
    }

    /** Returns the state identical to this one, but no longer in Binary Shift mode. */
    fun endBinaryShift(index: Int): State {
        if (binaryShiftByteCount == 0) {
            return this
        }
        val token = this.token.addBinaryShift(index - binaryShiftByteCount, binaryShiftByteCount)
        return State(token, mode, 0, this.bitCount)
    }

    /** Returns true if this state is better than or equal to [other] under all possible circumstances. */
    fun isBetterThanOrEqualTo(other: State): Boolean {
        var newModeBitCount = this.bitCount + (HighLevelEncoder.LATCH_TABLE[this.mode][other.mode] shr 16)
        if (this.binaryShiftByteCount < other.binaryShiftByteCount) {
            newModeBitCount += other.binaryShiftCost - this.binaryShiftCost
        } else if (this.binaryShiftByteCount > other.binaryShiftByteCount && other.binaryShiftByteCount > 0) {
            newModeBitCount += 10
        }
        return newModeBitCount <= other.bitCount
    }

    /** Converts the token list of this state, in forward order, into a [BitArray] over [text]. */
    fun toBitArray(text: ByteArray): BitArray {
        val symbols = ArrayList<Token>()
        var token: Token? = endBinaryShift(text.size).token
        while (token != null) {
            symbols.add(token)
            token = token.previous
        }
        val bitArray = BitArray()
        for (i in symbols.indices.reversed()) {
            symbols[i].appendTo(bitArray, text)
        }
        return bitArray
    }

    /** Shared statics. */
    companion object {
        /** The state every encoding starts from: UPPER mode, no output. */
        val INITIAL_STATE: State = State(Token.EMPTY, HighLevelEncoder.MODE_UPPER, 0, 0)

        /** Returns the bit cost of the B/S header(s) needed for [binaryShiftByteCount] bytes. */
        private fun calculateBinaryShiftCost(binaryShiftByteCount: Int): Int = when {
            binaryShiftByteCount > 62 -> 21
            binaryShiftByteCount > 31 -> 20
            binaryShiftByteCount > 0 -> 10
            else -> 0
        }
    }
}
