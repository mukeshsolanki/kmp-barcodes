/*
 * Copyright 2010 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType

/** Renders a Code 128 code, honouring the CODE128_COMPACT and FORCE_CODE_SET hints. */
internal class Code128Writer : OneDimensionalCodeWriter() {

    /** Results of minimal lookahead for code C. */
    private enum class CType {
        /** Not a digit or FNC1. */
        UNCODABLE,

        /** A single digit. */
        ONE_DIGIT,

        /** Two consecutive digits. */
        TWO_DIGITS,

        /** The FNC1 escape character. */
        FNC_1,
    }

    /** Only Code 128 is supported. */
    override val supportedWriteFormats: Collection<BarcodeFormat>
        get() = setOf(BarcodeFormat.CODE_128)

    /** Encodes [contents] with the fast (non-minimal) encoder and no forced code set. */
    override fun encode(contents: String): BooleanArray = encode(contents, null)

    /** Encodes [contents], using the minimal encoder when CODE128_COMPACT is true and honouring FORCE_CODE_SET otherwise. */
    override fun encode(contents: String, hints: Map<EncodeHintType, Any?>?): BooleanArray {
        val forcedCodeSet = check(contents, hints)

        val hasCompactionHint = hints != null && hints.containsKey(EncodeHintType.CODE128_COMPACT) &&
            hints[EncodeHintType.CODE128_COMPACT].toString().toBoolean()

        return if (hasCompactionHint) MinimalEncoder().encode(contents) else encodeFast(contents, forcedCodeSet)
    }

    /** Encodes minimally using divide-and-conquer with memoization. */
    private class MinimalEncoder {

        /** Character sets a position can be encoded in. */
        private enum class Charset {
            /** Code set A. */
            A,

            /** Code set B. */
            B,

            /** Code set C. */
            C,

            /** No code set selected yet. */
            NONE,
        }

        /** Latch or shift emitted before a position. */
        private enum class Latch {
            /** Latch to code set A. */
            A,

            /** Latch to code set B. */
            B,

            /** Latch to code set C. */
            C,

            /** Shift to the other of A/B for one character. */
            SHIFT,

            /** Stay in the current code set. */
            NONE,
        }

        /** Memoized minimal cost per code set and position. */
        private lateinit var memoizedCost: Array<IntArray>

        /** Latch chosen for the minimal cost per code set and position. */
        private lateinit var minPath: Array<Array<Latch?>>

        /** Encodes [contents] along the minimal-cost path. */
        fun encode(contents: String): BooleanArray {
            memoizedCost = Array(4) { IntArray(contents.length) }
            minPath = Array(4) { arrayOfNulls<Latch>(contents.length) }

            encode(contents, Charset.NONE, 0)

            val patterns = ArrayList<IntArray>()
            val checkSum = intArrayOf(0)
            val checkWeight = intArrayOf(1)
            val length = contents.length
            var charset = Charset.NONE
            var i = 0
            while (i < length) {
                val latch = minPath[charset.ordinal][i]!!
                when (latch) {
                    Latch.A -> {
                        charset = Charset.A
                        addPattern(patterns, if (i == 0) CODE_START_A else CODE_CODE_A, checkSum, checkWeight, i)
                    }
                    Latch.B -> {
                        charset = Charset.B
                        addPattern(patterns, if (i == 0) CODE_START_B else CODE_CODE_B, checkSum, checkWeight, i)
                    }
                    Latch.C -> {
                        charset = Charset.C
                        addPattern(patterns, if (i == 0) CODE_START_C else CODE_CODE_C, checkSum, checkWeight, i)
                    }
                    Latch.SHIFT -> addPattern(patterns, CODE_SHIFT, checkSum, checkWeight, i)
                    Latch.NONE -> Unit
                }
                if (charset == Charset.C) {
                    if (contents[i] == ESCAPE_FNC_1) {
                        addPattern(patterns, CODE_FNC_1, checkSum, checkWeight, i)
                    } else {
                        addPattern(patterns, contents.substring(i, i + 2).toInt(), checkSum, checkWeight, i)
                        if (i + 1 < length) {
                            i++
                        }
                    }
                } else {
                    var patternIndex = when (contents[i]) {
                        ESCAPE_FNC_1 -> CODE_FNC_1
                        ESCAPE_FNC_2 -> CODE_FNC_2
                        ESCAPE_FNC_3 -> CODE_FNC_3
                        ESCAPE_FNC_4 -> if (isEffectivelyCodeA(charset, latch)) CODE_FNC_4_A else CODE_FNC_4_B
                        else -> contents[i] - ' '
                    }
                    if (isEffectivelyCodeA(charset, latch) && patternIndex < 0) {
                        patternIndex += '`'.code
                    }
                    addPattern(patterns, patternIndex, checkSum, checkWeight, i)
                }
                i++
            }
            return produceResult(patterns, checkSum[0])
        }

        /** Returns whether [contents] at [position] can be encoded in [charset]. */
        private fun canEncode(contents: CharSequence, charset: Charset, position: Int): Boolean {
            val c = contents[position]
            return when (charset) {
                Charset.A -> c == ESCAPE_FNC_1 ||
                    c == ESCAPE_FNC_2 ||
                    c == ESCAPE_FNC_3 ||
                    c == ESCAPE_FNC_4 ||
                    A.indexOf(c) >= 0
                Charset.B -> c == ESCAPE_FNC_1 ||
                    c == ESCAPE_FNC_2 ||
                    c == ESCAPE_FNC_3 ||
                    c == ESCAPE_FNC_4 ||
                    B.indexOf(c) >= 0
                Charset.C -> c == ESCAPE_FNC_1 ||
                    (position + 1 < contents.length && isDigit(c) && isDigit(contents[position + 1]))
                Charset.NONE -> false
            }
        }

        /** Returns the minimal cost of encoding [contents] from [position] when currently in [charset], memoizing the path. */
        private fun encode(contents: CharSequence, charset: Charset, position: Int): Int {
            val mCost = memoizedCost[charset.ordinal][position]
            if (mCost > 0) {
                return mCost
            }

            var minCost = Int.MAX_VALUE
            var minLatch = Latch.NONE
            val atEnd = position + 1 >= contents.length

            val sets = arrayOf(Charset.A, Charset.B)
            for (i in 0..1) {
                if (canEncode(contents, sets[i], position)) {
                    var cost = 1
                    var latch = Latch.NONE
                    if (charset != sets[i]) {
                        cost++
                        latch = Latch.valueOf(sets[i].name)
                    }
                    if (!atEnd) {
                        cost += encode(contents, sets[i], position + 1)
                    }
                    if (cost < minCost) {
                        minCost = cost
                        minLatch = latch
                    }
                    cost = 1
                    if (charset == sets[(i + 1) % 2]) {
                        cost++
                        latch = Latch.SHIFT
                        if (!atEnd) {
                            cost += encode(contents, charset, position + 1)
                        }
                        if (cost < minCost) {
                            minCost = cost
                            minLatch = latch
                        }
                    }
                }
            }
            if (canEncode(contents, Charset.C, position)) {
                var cost = 1
                var latch = Latch.NONE
                if (charset != Charset.C) {
                    cost++
                    latch = Latch.C
                }
                val advance = if (contents[position] == ESCAPE_FNC_1) 1 else 2
                if (position + advance < contents.length) {
                    cost += encode(contents, Charset.C, position + advance)
                }
                if (cost < minCost) {
                    minCost = cost
                    minLatch = latch
                }
            }
            if (minCost == Int.MAX_VALUE) {
                throw IllegalArgumentException("Bad character in input")
            }
            memoizedCost[charset.ordinal][position] = minCost
            minPath[charset.ordinal][position] = minLatch
            return minCost
        }

        /** Code set tables and pattern helpers for the minimal encoder. */
        private companion object {

            /** Characters encodable in code set A. */
            const val A = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_\u0000\u0001\u0002" +
                "\u0003\u0004\u0005\u0006\u0007\u0008\u0009\n\u000B\u000C\r\u000E\u000F\u0010\u0011" +
                "\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F" +
                "ÿ"

            /** Characters encodable in code set B. */
            const val B = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqr" +
                "stuvwxyz{|}~\u007Fÿ"

            /** Shift code value. */
            const val CODE_SHIFT = 98

            /** True when the character at a position is read as code set A: in A without a shift, or in B with one. */
            fun isEffectivelyCodeA(charset: Charset, latch: Latch): Boolean =
                charset == Charset.A && latch != Latch.SHIFT || charset == Charset.B && latch == Latch.SHIFT

            /** Appends the pattern for [patternIndex] and folds it into the running [checkSum] and [checkWeight]. */
            fun addPattern(
                patterns: MutableList<IntArray>,
                patternIndex: Int,
                checkSum: IntArray,
                checkWeight: IntArray,
                position: Int,
            ) {
                patterns.add(Code128Reader.CODE_PATTERNS[patternIndex])
                if (position != 0) {
                    checkWeight[0]++
                }
                checkSum[0] += patternIndex * checkWeight[0]
            }

            /** Returns whether [c] is an ASCII digit. */
            fun isDigit(c: Char): Boolean = c in '0'..'9'
        }
    }

    /** Code values, escape characters and the fast encoder. */
    companion object {

        /** Start in code set A. */
        private const val CODE_START_A = 103

        /** Start in code set B. */
        private const val CODE_START_B = 104

        /** Start in code set C. */
        private const val CODE_START_C = 105

        /** Switch to code set A. */
        private const val CODE_CODE_A = 101

        /** Switch to code set B. */
        private const val CODE_CODE_B = 100

        /** Switch to code set C. */
        private const val CODE_CODE_C = 99

        /** Stop code. */
        private const val CODE_STOP = 106

        /** Input escape for FNC1. */
        private const val ESCAPE_FNC_1 = 'ñ'

        /** Input escape for FNC2. */
        private const val ESCAPE_FNC_2 = 'ò'

        /** Input escape for FNC3. */
        private const val ESCAPE_FNC_3 = 'ó'

        /** Input escape for FNC4. */
        private const val ESCAPE_FNC_4 = 'ô'

        /** FNC1 code (code sets A, B and C). */
        private const val CODE_FNC_1 = 102

        /** FNC2 code (code sets A and B). */
        private const val CODE_FNC_2 = 97

        /** FNC3 code (code sets A and B). */
        private const val CODE_FNC_3 = 96

        /** FNC4 code in code set A. */
        private const val CODE_FNC_4_A = 101

        /** FNC4 code in code set B. */
        private const val CODE_FNC_4_B = 100

        /**
         * Validates [contents] against ASCII and any FORCE_CODE_SET hint. Returns the forced code set,
         * or -1 when none is forced; throws IllegalArgumentException on invalid input.
         */
        private fun check(contents: String, hints: Map<EncodeHintType, Any?>?): Int {
            var forcedCodeSet = -1
            if (hints != null && hints.containsKey(EncodeHintType.FORCE_CODE_SET)) {
                val codeSetHint = hints[EncodeHintType.FORCE_CODE_SET].toString()
                forcedCodeSet = when (codeSetHint) {
                    "A" -> CODE_CODE_A
                    "B" -> CODE_CODE_B
                    "C" -> CODE_CODE_C
                    else -> throw IllegalArgumentException("Unsupported code set hint: $codeSetHint")
                }
            }

            for (c in contents) {
                val isEscape = c == ESCAPE_FNC_1 || c == ESCAPE_FNC_2 || c == ESCAPE_FNC_3 || c == ESCAPE_FNC_4
                if (!isEscape && c.code > 127) {
                    throw IllegalArgumentException("Bad character in input")
                }
                when (forcedCodeSet) {
                    CODE_CODE_A -> if (c.code in 96..127) {
                        throw IllegalArgumentException("Bad character in input for forced code set A")
                    }
                    CODE_CODE_B -> if (c.code < 32) {
                        throw IllegalArgumentException("Bad character in input for forced code set B")
                    }
                    CODE_CODE_C -> if (c.code < 48 || c.code in 58..127 ||
                        c == ESCAPE_FNC_2 || c == ESCAPE_FNC_3 || c == ESCAPE_FNC_4
                    ) {
                        throw IllegalArgumentException("Bad character in input for forced code set C")
                    }
                }
            }
            return forcedCodeSet
        }

        /** Encodes [contents] greedily with lookahead, or entirely in [forcedCodeSet] when it is not -1. */
        private fun encodeFast(contents: String, forcedCodeSet: Int): BooleanArray {
            val length = contents.length
            val patterns = ArrayList<IntArray>()
            var checkSum = 0
            var checkWeight = 1
            var codeSet = 0
            var position = 0

            while (position < length) {
                val newCodeSet = if (forcedCodeSet == -1) chooseCode(contents, position, codeSet) else forcedCodeSet

                val patternIndex: Int
                if (newCodeSet == codeSet) {
                    patternIndex = when (contents[position]) {
                        ESCAPE_FNC_1 -> CODE_FNC_1
                        ESCAPE_FNC_2 -> CODE_FNC_2
                        ESCAPE_FNC_3 -> CODE_FNC_3
                        ESCAPE_FNC_4 -> if (codeSet == CODE_CODE_A) CODE_FNC_4_A else CODE_FNC_4_B
                        else -> when (codeSet) {
                            CODE_CODE_A -> {
                                var index = contents[position] - ' '
                                if (index < 0) {
                                    index += '`'.code
                                }
                                index
                            }
                            CODE_CODE_B -> contents[position] - ' '
                            else -> {
                                if (position + 1 == length) {
                                    throw IllegalArgumentException("Bad number of characters for digit only encoding.")
                                }
                                val index = contents.substring(position, position + 2).toInt()
                                position++
                                index
                            }
                        }
                    }
                    position++
                } else {
                    patternIndex = if (codeSet == 0) {
                        when (newCodeSet) {
                            CODE_CODE_A -> CODE_START_A
                            CODE_CODE_B -> CODE_START_B
                            else -> CODE_START_C
                        }
                    } else {
                        newCodeSet
                    }
                    codeSet = newCodeSet
                }

                patterns.add(Code128Reader.CODE_PATTERNS[patternIndex])

                checkSum += patternIndex * checkWeight
                if (position != 0) {
                    checkWeight++
                }
            }
            return produceResult(patterns, checkSum)
        }

        /** Appends the check symbol and stop code to [patterns] and renders them to horizontal modules. */
        fun produceResult(patterns: MutableList<IntArray>, checkSum: Int): BooleanArray {
            val checkValue = checkSum % 103
            if (checkValue < 0) {
                throw IllegalArgumentException("Unable to compute a valid input checksum")
            }
            patterns.add(Code128Reader.CODE_PATTERNS[checkValue])

            patterns.add(Code128Reader.CODE_PATTERNS[CODE_STOP])

            var codeWidth = 0
            for (pattern in patterns) {
                for (width in pattern) {
                    codeWidth += width
                }
            }

            val result = BooleanArray(codeWidth)
            var pos = 0
            for (pattern in patterns) {
                pos += OneDimensionalCodeWriter.appendPattern(result, pos, pattern, true)
            }

            return result
        }

        /** Classifies the character(s) of [value] at [start] for code C lookahead. */
        private fun findCType(value: CharSequence, start: Int): CType {
            val last = value.length
            if (start >= last) {
                return CType.UNCODABLE
            }
            var c = value[start]
            if (c == ESCAPE_FNC_1) {
                return CType.FNC_1
            }
            if (c < '0' || c > '9') {
                return CType.UNCODABLE
            }
            if (start + 1 >= last) {
                return CType.ONE_DIGIT
            }
            c = value[start + 1]
            if (c < '0' || c > '9') {
                return CType.ONE_DIGIT
            }
            return CType.TWO_DIGITS
        }

        /** Chooses the code set for [value] at [start] given the current [oldCode]. */
        private fun chooseCode(value: CharSequence, start: Int, oldCode: Int): Int {
            var lookahead = findCType(value, start)
            if (lookahead == CType.ONE_DIGIT) {
                if (oldCode == CODE_CODE_A) {
                    return CODE_CODE_A
                }
                return CODE_CODE_B
            }
            if (lookahead == CType.UNCODABLE) {
                if (start < value.length) {
                    val c = value[start]
                    if (c < ' ' || (oldCode == CODE_CODE_A && (c < '`' || (c >= ESCAPE_FNC_1 && c <= ESCAPE_FNC_4)))) {
                        return CODE_CODE_A
                    }
                }
                return CODE_CODE_B
            }
            if (oldCode == CODE_CODE_A && lookahead == CType.FNC_1) {
                return CODE_CODE_A
            }
            if (oldCode == CODE_CODE_C) {
                return CODE_CODE_C
            }
            if (oldCode == CODE_CODE_B) {
                if (lookahead == CType.FNC_1) {
                    return CODE_CODE_B
                }
                lookahead = findCType(value, start + 2)
                if (lookahead == CType.UNCODABLE || lookahead == CType.ONE_DIGIT) {
                    return CODE_CODE_B
                }
                if (lookahead == CType.FNC_1) {
                    lookahead = findCType(value, start + 3)
                    return if (lookahead == CType.TWO_DIGITS) CODE_CODE_C else CODE_CODE_B
                }
                var index = start + 4
                lookahead = findCType(value, index)
                while (lookahead == CType.TWO_DIGITS) {
                    index += 2
                    lookahead = findCType(value, index)
                }
                if (lookahead == CType.ONE_DIGIT) {
                    return CODE_CODE_B
                }
                return CODE_CODE_C
            }
            if (lookahead == CType.FNC_1) {
                lookahead = findCType(value, start + 1)
            }
            if (lookahead == CType.TWO_DIGITS) {
                return CODE_CODE_C
            }
            return CODE_CODE_B
        }
    }
}
