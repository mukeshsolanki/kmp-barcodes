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

package com.mukeshsolanki.barcodes.internal.zxing.aztec.decoder

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.aztec.AztecDetectorResult
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.CharacterSetECI
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonDecoder
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonException

/**
 * Decodes the bits of a located Aztec Code into text.
 */
internal class Decoder {

    /** The detector result currently being decoded. */
    private lateinit var ddata: AztecDetectorResult

    /** Decodes the symbol described by [detectorResult]; throws [FormatException] when it cannot. */
    fun decode(detectorResult: AztecDetectorResult): DecoderResult {
        ddata = detectorResult
        val matrix = detectorResult.bits
        val rawbits = extractBits(matrix)
        val correctedBits = correctBits(rawbits)
        val rawBytes = convertBoolArrayToByteArray(correctedBits.correctBits)
        val result = getEncodedData(correctedBits.correctBits)
        val decoderResult = DecoderResult(rawBytes, result, null, "${correctedBits.ecLevel}%")
        decoderResult.numBits = correctedBits.correctBits.size
        decoderResult.errorsCorrected = correctedBits.errorsCorrected
        return decoderResult
    }

    /** Corrected data bits plus the error-correction statistics. */
    private class CorrectedBitsResult(
        /** The data bits after RS correction and unstuffing. */
        val correctBits: BooleanArray,
        /** Number of codewords RS corrected. */
        val errorsCorrected: Int,
        /** Percentage of codewords used for error correction. */
        val ecLevel: Int,
    )

    /** Performs RS error correction and unstuffing; throws [FormatException] on too many errors. */
    private fun correctBits(rawbits: BooleanArray): CorrectedBitsResult {
        val codewordSize: Int
        val gf: GenericGF
        when {
            ddata.nbLayers <= 2 -> {
                codewordSize = 6
                gf = GenericGF.AZTEC_DATA_6
            }
            ddata.nbLayers <= 8 -> {
                codewordSize = 8
                gf = GenericGF.AZTEC_DATA_8
            }
            ddata.nbLayers <= 22 -> {
                codewordSize = 10
                gf = GenericGF.AZTEC_DATA_10
            }
            else -> {
                codewordSize = 12
                gf = GenericGF.AZTEC_DATA_12
            }
        }

        val numDataCodewords = ddata.nbDatablocks
        val numCodewords = rawbits.size / codewordSize
        if (numCodewords < numDataCodewords) {
            throw FormatException.formatInstance
        }
        var offset = rawbits.size % codewordSize

        val dataWords = IntArray(numCodewords)
        for (i in 0 until numCodewords) {
            dataWords[i] = readCode(rawbits, offset, codewordSize)
            offset += codewordSize
        }

        val errorsCorrected = try {
            ReedSolomonDecoder(gf).decodeWithECCount(dataWords, numCodewords - numDataCodewords)
        } catch (ex: ReedSolomonException) {
            throw FormatException.getFormatInstance(ex)
        }

        val correctedBits = unstuff(dataWords, numDataCodewords, codewordSize)
        val ecLevel = 100 * (numCodewords - numDataCodewords) / numCodewords
        return CorrectedBitsResult(correctedBits, errorsCorrected, ecLevel)
    }

    /** Unpacks the first [numDataCodewords] of [dataWords] into bits, removing the stuffed bits. */
    private fun unstuff(dataWords: IntArray, numDataCodewords: Int, codewordSize: Int): BooleanArray {
        val mask = (1 shl codewordSize) - 1
        var stuffedBits = 0
        for (i in 0 until numDataCodewords) {
            val dataWord = dataWords[i]
            if (dataWord == 0 || dataWord == mask) {
                throw FormatException.formatInstance
            } else if (dataWord == 1 || dataWord == mask - 1) {
                stuffedBits++
            }
        }
        val correctedBits = BooleanArray(numDataCodewords * codewordSize - stuffedBits)
        var index = 0
        for (i in 0 until numDataCodewords) {
            val dataWord = dataWords[i]
            if (dataWord == 1 || dataWord == mask - 1) {
                correctedBits.fill(dataWord > 1, index, index + codewordSize - 1)
                index += codewordSize - 1
            } else {
                for (bit in codewordSize - 1 downTo 0) {
                    correctedBits[index++] = (dataWord and (1 shl bit)) != 0
                }
            }
        }
        return correctedBits
    }

    /** Reads the raw bits of every data layer out of [matrix]. */
    private fun extractBits(matrix: BitMatrix): BooleanArray {
        val compact = ddata.isCompact
        val layers = ddata.nbLayers
        val baseMatrixSize = (if (compact) 11 else 14) + layers * 4
        val alignmentMap = IntArray(baseMatrixSize)
        val rawbits = BooleanArray(totalBitsInLayer(layers, compact))

        if (compact) {
            for (i in alignmentMap.indices) {
                alignmentMap[i] = i
            }
        } else {
            val matrixSize = baseMatrixSize + 1 + 2 * ((baseMatrixSize / 2 - 1) / 15)
            val origCenter = baseMatrixSize / 2
            val center = matrixSize / 2
            for (i in 0 until origCenter) {
                val newOffset = i + i / 15
                alignmentMap[origCenter - i - 1] = center - newOffset - 1
                alignmentMap[origCenter + i] = center + newOffset + 1
            }
        }
        var rowOffset = 0
        for (i in 0 until layers) {
            val rowSize = (layers - i) * 4 + (if (compact) 9 else 12)
            val low = i * 2
            val high = baseMatrixSize - 1 - low
            for (j in 0 until rowSize) {
                val columnOffset = j * 2
                for (k in 0 until 2) {
                    rawbits[rowOffset + columnOffset + k] =
                        matrix.get(alignmentMap[low + k], alignmentMap[low + j])
                    rawbits[rowOffset + 2 * rowSize + columnOffset + k] =
                        matrix.get(alignmentMap[low + j], alignmentMap[high - k])
                    rawbits[rowOffset + 4 * rowSize + columnOffset + k] =
                        matrix.get(alignmentMap[high - k], alignmentMap[high - j])
                    rawbits[rowOffset + 6 * rowSize + columnOffset + k] =
                        matrix.get(alignmentMap[high - j], alignmentMap[low + k])
                }
            }
            rowOffset += rowSize * 8
        }
        return rawbits
    }

    /** Shared tables and static helpers. */
    companion object {
        /** The mode tables of the Aztec high-level encoding. */
        private enum class Table {
            /** Upper case letters. */
            UPPER,

            /** Lower case letters. */
            LOWER,

            /** Control and miscellaneous characters. */
            MIXED,

            /** Digits and a little punctuation. */
            DIGIT,

            /** Punctuation. */
            PUNCT,

            /** Raw bytes. */
            BINARY,
        }

        /** Codes of the UPPER table. */
        private val UPPER_TABLE = arrayOf(
            "CTRL_PS", " ", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
            "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "CTRL_LL", "CTRL_ML", "CTRL_DL", "CTRL_BS",
        )

        /** Codes of the LOWER table. */
        private val LOWER_TABLE = arrayOf(
            "CTRL_PS", " ", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "CTRL_US", "CTRL_ML", "CTRL_DL", "CTRL_BS",
        )

        /** Codes of the MIXED table. */
        private val MIXED_TABLE = arrayOf(
            "CTRL_PS", " ", "\u0001", "\u0002", "\u0003", "\u0004", "\u0005", "\u0006", "\u0007", "\b", "\t", "\n",
            "\u000b", "\u000c", "\r", "\u001b", "\u001c", "\u001d", "\u001e", "\u001f", "@", "\\", "^", "_",
            "`", "|", "~", "\u007f", "CTRL_LL", "CTRL_UL", "CTRL_PL", "CTRL_BS",
        )

        /** Codes of the PUNCT table. */
        private val PUNCT_TABLE = arrayOf(
            "FLG(n)", "\r", "\r\n", ". ", ", ", ": ", "!", "\"", "#", "\$", "%", "&", "'", "(", ")",
            "*", "+", ",", "-", ".", "/", ":", ";", "<", "=", ">", "?", "[", "]", "{", "}", "CTRL_UL",
        )

        /** Codes of the DIGIT table. */
        private val DIGIT_TABLE = arrayOf(
            "CTRL_PS", " ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ",", ".", "CTRL_UL", "CTRL_US",
        )

        /** Charset assumed until an ECI says otherwise. */
        private val DEFAULT_ENCODING: Charset = Charsets.ISO_8859_1

        /** Decodes already-corrected bits; used for testing the high-level encoder. */
        fun highLevelDecode(correctedBits: BooleanArray): String = getEncodedData(correctedBits)

        /** Mutable state of a single high-level decode pass. */
        private class DecodeState(
            /** The corrected bits being read. */
            val bits: BooleanArray,
        ) {
            /** Read position in [bits]. */
            var index: Int = 0

            /** Table most recently latched to. */
            var latchTable: Table = Table.UPPER

            /** Table to use for the next read. */
            var shiftTable: Table = Table.UPPER

            /** Decoded text flushed so far. */
            val result: StringBuilder = StringBuilder((bits.size - 5) / 4)

            /** Bytes pending decode with [encoding]. */
            val decodedBytes: MutableList<Byte> = ArrayList()

            /** Charset for [decodedBytes]. */
            var encoding: Charset = DEFAULT_ENCODING

            /** Number of bits not yet read. */
            val remaining: Int get() = bits.size - index

            /** Reads [length] bits as an unsigned code and advances. */
            fun read(length: Int): Int {
                val code = readCode(bits, index, length)
                index += length
                return code
            }

            /** Decodes [decodedBytes] with [encoding] into [result] and clears them. */
            fun flush() {
                result.append(encoding.decode(decodedBytes.toByteArray()))
                decodedBytes.clear()
            }
        }

        /** Returns the string encoded in the Aztec code bits. */
        private fun getEncodedData(correctedBits: BooleanArray): String {
            val state = DecodeState(correctedBits)
            while (state.index < correctedBits.size) {
                val keepGoing = if (state.shiftTable == Table.BINARY) {
                    readBinary(state)
                } else {
                    readCharacter(state)
                }
                if (!keepGoing) {
                    break
                }
            }
            state.flush()
            return state.result.toString()
        }

        /** Reads one Binary Shift run; returns false when the input ended early. */
        private fun readBinary(state: DecodeState): Boolean {
            if (state.remaining < 5) {
                return false
            }
            var length = state.read(5)
            if (length == 0) {
                if (state.remaining < 11) {
                    return false
                }
                length = state.read(11) + 31
            }
            for (charCount in 0 until length) {
                if (state.remaining < 8) {
                    state.index = state.bits.size
                    break
                }
                state.decodedBytes.add(state.read(8).toByte())
            }
            state.shiftTable = state.latchTable
            return true
        }

        /** Reads one code from the current table; returns false when the input ended early. */
        private fun readCharacter(state: DecodeState): Boolean {
            val size = if (state.shiftTable == Table.DIGIT) 4 else 5
            if (state.remaining < size) {
                return false
            }
            val code = state.read(size)
            val str = getCharacter(state.shiftTable, code)
            if ("FLG(n)" == str) {
                if (state.remaining < 3) {
                    return false
                }
                readFlg(state, state.read(3))
                state.shiftTable = state.latchTable
            } else if (str.startsWith("CTRL_")) {
                state.latchTable = state.shiftTable
                state.shiftTable = getTable(str[5])
                if (str[6] == 'L') {
                    state.latchTable = state.shiftTable
                }
            } else {
                for (c in str) {
                    state.decodedBytes.add(c.code.toByte())
                }
                state.shiftTable = state.latchTable
            }
            return true
        }

        /** Handles FLG([n]): FNC1 for 0, an ECI designator for 1-6; 7 is illegal. */
        private fun readFlg(state: DecodeState, n: Int) {
            state.flush()
            when (n) {
                0 -> state.result.append(Char(29))
                7 -> throw FormatException.formatInstance
                else -> {
                    if (state.remaining < 4 * n) {
                        return
                    }
                    var eci = 0
                    repeat(n) {
                        val nextDigit = state.read(4)
                        if (nextDigit < 2 || nextDigit > 11) {
                            throw FormatException.formatInstance
                        }
                        eci = eci * 10 + (nextDigit - 2)
                    }
                    val charsetECI = CharacterSetECI.getCharacterSetECIByValue(eci)
                        ?: throw FormatException.formatInstance
                    state.encoding = charsetECI.charset ?: DEFAULT_ENCODING
                }
            }
        }

        /** Returns the table named by the control character [t]. */
        private fun getTable(t: Char): Table = when (t) {
            'L' -> Table.LOWER
            'P' -> Table.PUNCT
            'M' -> Table.MIXED
            'D' -> Table.DIGIT
            'B' -> Table.BINARY
            else -> Table.UPPER
        }

        /** Returns the character (or string) for [code] in [table]. */
        private fun getCharacter(table: Table, code: Int): String = when (table) {
            Table.UPPER -> UPPER_TABLE[code]
            Table.LOWER -> LOWER_TABLE[code]
            Table.MIXED -> MIXED_TABLE[code]
            Table.PUNCT -> PUNCT_TABLE[code]
            Table.DIGIT -> DIGIT_TABLE[code]
            Table.BINARY -> throw IllegalStateException("Bad table")
        }

        /** Reads a code of [length] bits starting at [startIndex]. */
        private fun readCode(rawbits: BooleanArray, startIndex: Int, length: Int): Int {
            var res = 0
            for (i in startIndex until startIndex + length) {
                res = res shl 1
                if (rawbits[i]) {
                    res = res or 0x01
                }
            }
            return res
        }

        /** Reads 8 bits starting at [startIndex], padding with zeros past the end. */
        private fun readByte(rawbits: BooleanArray, startIndex: Int): Byte {
            val n = rawbits.size - startIndex
            if (n >= 8) {
                return readCode(rawbits, startIndex, 8).toByte()
            }
            return (readCode(rawbits, startIndex, n) shl (8 - n)).toByte()
        }

        /** Packs a bit array into bytes, most significant bit first. */
        fun convertBoolArrayToByteArray(boolArr: BooleanArray): ByteArray {
            val byteArr = ByteArray((boolArr.size + 7) / 8)
            for (i in byteArr.indices) {
                byteArr[i] = readByte(boolArr, 8 * i)
            }
            return byteArr
        }

        /** Returns the number of bits held by [layers] layers of a compact or full-range symbol. */
        private fun totalBitsInLayer(layers: Int, compact: Boolean): Int =
            ((if (compact) 88 else 112) + 16 * layers) * layers
    }
}
