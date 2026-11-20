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
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonEncoder
import kotlin.math.abs

/**
 * Generates Aztec 2D barcodes.
 */
internal object Encoder {

    /** Default minimal percentage of error check words. */
    const val DEFAULT_EC_PERCENT: Int = 33

    /** Layer count meaning "choose the smallest symbol that fits". */
    const val DEFAULT_AZTEC_LAYERS: Int = 0

    /** Maximum number of layers of a full-range symbol. */
    private const val MAX_NB_BITS = 32

    /** Maximum number of layers of a compact symbol. */
    private const val MAX_NB_BITS_COMPACT = 4

    /** Codeword size in bits, indexed by layer count. */
    private val WORD_SIZE = intArrayOf(
        4, 6, 6, 8, 8, 8, 8, 8, 8, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
        12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    )

    /** The chosen symbol size and the data bits stuffed for its word size. */
    private class Layout(
        /** Whether the symbol is compact. */
        val compact: Boolean,
        /** Number of layers. */
        val layers: Int,
        /** Total bits available in all layers. */
        val totalBitsInLayer: Int,
        /** Codeword size in bits. */
        val wordSize: Int,
        /** Data bits after bit stuffing. */
        val stuffedBits: BitArray,
    )

    /**
     * Encodes [data] as an Aztec symbol. Bytes come from [charset] (ISO-8859-1 when null), and an ECI is
     * inserted only when [charset] is non-null.
     */
    fun encode(
        data: String,
        minECCPercent: Int = DEFAULT_EC_PERCENT,
        userSpecifiedLayers: Int = DEFAULT_AZTEC_LAYERS,
        charset: Charset? = null,
    ): AztecCode {
        val bytes = (charset ?: Charsets.ISO_8859_1).encode(data)
        return encode(bytes, minECCPercent, userSpecifiedLayers, charset)
    }

    /** Encodes the binary [data] as an Aztec symbol, marking [charset] with an ECI when it is non-null. */
    fun encode(
        data: ByteArray,
        minECCPercent: Int = DEFAULT_EC_PERCENT,
        userSpecifiedLayers: Int = DEFAULT_AZTEC_LAYERS,
        charset: Charset? = null,
    ): AztecCode {
        val bits = HighLevelEncoder(data, charset).encode()
        val eccBits = bits.size * minECCPercent / 100 + 11
        val totalSizeBits = bits.size + eccBits
        val layout = if (userSpecifiedLayers != DEFAULT_AZTEC_LAYERS) {
            userLayout(bits, eccBits, userSpecifiedLayers)
        } else {
            autoLayout(bits, eccBits, totalSizeBits)
        }
        val compact = layout.compact
        val layers = layout.layers
        val wordSize = layout.wordSize
        val messageBits = generateCheckWords(layout.stuffedBits, layout.totalBitsInLayer, wordSize)
        val messageSizeInWords = layout.stuffedBits.size / wordSize
        val modeMessage = generateModeMessage(compact, layers, messageSizeInWords)

        val baseMatrixSize = (if (compact) 11 else 14) + layers * 4
        val alignmentMap = IntArray(baseMatrixSize)
        val matrixSize: Int
        if (compact) {
            matrixSize = baseMatrixSize
            for (i in alignmentMap.indices) {
                alignmentMap[i] = i
            }
        } else {
            matrixSize = baseMatrixSize + 1 + 2 * ((baseMatrixSize / 2 - 1) / 15)
            val origCenter = baseMatrixSize / 2
            val center = matrixSize / 2
            for (i in 0 until origCenter) {
                val newOffset = i + i / 15
                alignmentMap[origCenter - i - 1] = center - newOffset - 1
                alignmentMap[origCenter + i] = center + newOffset + 1
            }
        }
        val matrix = BitMatrix(matrixSize)
        drawDataBits(matrix, messageBits, alignmentMap, layers, compact, baseMatrixSize)
        drawModeMessage(matrix, compact, matrixSize, modeMessage)
        drawAlignmentMarks(matrix, compact, matrixSize, baseMatrixSize)

        val aztec = AztecCode()
        aztec.isCompact = compact
        aztec.size = matrixSize
        aztec.layers = layers
        aztec.codeWords = messageSizeInWords
        aztec.matrix = matrix
        return aztec
    }

    /** Validates and builds the layout for a user-specified layer count (negative means compact). */
    private fun userLayout(bits: BitArray, eccBits: Int, userSpecifiedLayers: Int): Layout {
        val compact = userSpecifiedLayers < 0
        val layers = abs(userSpecifiedLayers)
        if (layers > (if (compact) MAX_NB_BITS_COMPACT else MAX_NB_BITS)) {
            throw IllegalArgumentException("Illegal value $userSpecifiedLayers for layers")
        }
        val totalBitsInLayer = totalBitsInLayer(layers, compact)
        val wordSize = WORD_SIZE[layers]
        val usableBitsInLayers = totalBitsInLayer - (totalBitsInLayer % wordSize)
        val stuffedBits = stuffBits(bits, wordSize)
        if (stuffedBits.size + eccBits > usableBitsInLayers) {
            throw IllegalArgumentException("Data to large for user specified layer")
        }
        if (compact && stuffedBits.size > wordSize * 64) {
            throw IllegalArgumentException("Data to large for user specified layer")
        }
        return Layout(compact, layers, totalBitsInLayer, wordSize, stuffedBits)
    }

    /**
     * Picks the smallest symbol that fits, trying Compact1..Compact4 then Normal4..Normal32, and
     * re-stuffing the bits only when the word size changes.
     */
    private fun autoLayout(bits: BitArray, eccBits: Int, totalSizeBits: Int): Layout {
        var wordSize = 0
        var stuffedBits = BitArray()
        for (i in 0..MAX_NB_BITS) {
            val compact = i <= 3
            val layers = if (compact) i + 1 else i
            val totalBitsInLayer = totalBitsInLayer(layers, compact)
            if (totalSizeBits > totalBitsInLayer) {
                continue
            }
            if (wordSize != WORD_SIZE[layers]) {
                wordSize = WORD_SIZE[layers]
                stuffedBits = stuffBits(bits, wordSize)
            }
            val usableBitsInLayers = totalBitsInLayer - (totalBitsInLayer % wordSize)
            if (compact && stuffedBits.size > wordSize * 64) {
                continue
            }
            if (stuffedBits.size + eccBits <= usableBitsInLayers) {
                return Layout(compact, layers, totalBitsInLayer, wordSize, stuffedBits)
            }
        }
        throw IllegalArgumentException("Data too large for an Aztec code")
    }

    /** Draws the data layers from [messageBits] into [matrix], spiralling inwards-out per zxing. */
    private fun drawDataBits(
        matrix: BitMatrix,
        messageBits: BitArray,
        alignmentMap: IntArray,
        layers: Int,
        compact: Boolean,
        baseMatrixSize: Int,
    ) {
        var rowOffset = 0
        for (i in 0 until layers) {
            val rowSize = (layers - i) * 4 + (if (compact) 9 else 12)
            for (j in 0 until rowSize) {
                val columnOffset = j * 2
                for (k in 0 until 2) {
                    if (messageBits.get(rowOffset + columnOffset + k)) {
                        matrix.set(alignmentMap[i * 2 + k], alignmentMap[i * 2 + j])
                    }
                    if (messageBits.get(rowOffset + rowSize * 2 + columnOffset + k)) {
                        matrix.set(alignmentMap[i * 2 + j], alignmentMap[baseMatrixSize - 1 - i * 2 - k])
                    }
                    if (messageBits.get(rowOffset + rowSize * 4 + columnOffset + k)) {
                        matrix.set(
                            alignmentMap[baseMatrixSize - 1 - i * 2 - k],
                            alignmentMap[baseMatrixSize - 1 - i * 2 - j],
                        )
                    }
                    if (messageBits.get(rowOffset + rowSize * 6 + columnOffset + k)) {
                        matrix.set(alignmentMap[baseMatrixSize - 1 - i * 2 - j], alignmentMap[i * 2 + k])
                    }
                }
            }
            rowOffset += rowSize * 8
        }
    }

    /** Draws the bull's eye and, for full-range symbols, the reference grid lines. */
    private fun drawAlignmentMarks(matrix: BitMatrix, compact: Boolean, matrixSize: Int, baseMatrixSize: Int) {
        if (compact) {
            drawBullsEye(matrix, matrixSize / 2, 5)
            return
        }
        drawBullsEye(matrix, matrixSize / 2, 7)
        var i = 0
        var j = 0
        while (i < baseMatrixSize / 2 - 1) {
            for (k in ((matrixSize / 2) and 1) until matrixSize step 2) {
                matrix.set(matrixSize / 2 - j, k)
                matrix.set(matrixSize / 2 + j, k)
                matrix.set(k, matrixSize / 2 - j)
                matrix.set(k, matrixSize / 2 + j)
            }
            i += 15
            j += 16
        }
    }

    /** Draws a bull's eye of [size] rings around [center], plus its orientation marks. */
    private fun drawBullsEye(matrix: BitMatrix, center: Int, size: Int) {
        for (i in 0 until size step 2) {
            for (j in center - i..center + i) {
                matrix.set(j, center - i)
                matrix.set(j, center + i)
                matrix.set(center - i, j)
                matrix.set(center + i, j)
            }
        }
        matrix.set(center - size, center - size)
        matrix.set(center - size + 1, center - size)
        matrix.set(center - size, center - size + 1)
        matrix.set(center + size, center - size)
        matrix.set(center + size, center - size + 1)
        matrix.set(center + size, center + size - 1)
    }

    /** Builds the RS-protected mode message holding the layer count and data word count. */
    fun generateModeMessage(compact: Boolean, layers: Int, messageSizeInWords: Int): BitArray {
        val modeMessage = BitArray()
        return if (compact) {
            modeMessage.appendBits(layers - 1, 2)
            modeMessage.appendBits(messageSizeInWords - 1, 6)
            generateCheckWords(modeMessage, 28, 4)
        } else {
            modeMessage.appendBits(layers - 1, 5)
            modeMessage.appendBits(messageSizeInWords - 1, 11)
            generateCheckWords(modeMessage, 40, 4)
        }
    }

    /** Draws the mode message around the bull's eye. */
    private fun drawModeMessage(matrix: BitMatrix, compact: Boolean, matrixSize: Int, modeMessage: BitArray) {
        val center = matrixSize / 2
        if (compact) {
            for (i in 0 until 7) {
                val offset = center - 3 + i
                if (modeMessage.get(i)) {
                    matrix.set(offset, center - 5)
                }
                if (modeMessage.get(i + 7)) {
                    matrix.set(center + 5, offset)
                }
                if (modeMessage.get(20 - i)) {
                    matrix.set(offset, center + 5)
                }
                if (modeMessage.get(27 - i)) {
                    matrix.set(center - 5, offset)
                }
            }
        } else {
            for (i in 0 until 10) {
                val offset = center - 5 + i + i / 5
                if (modeMessage.get(i)) {
                    matrix.set(offset, center - 7)
                }
                if (modeMessage.get(i + 10)) {
                    matrix.set(center + 7, offset)
                }
                if (modeMessage.get(29 - i)) {
                    matrix.set(offset, center + 7)
                }
                if (modeMessage.get(39 - i)) {
                    matrix.set(center - 7, offset)
                }
            }
        }
    }

    /** Appends RS check words to [bitArray] so it fills [totalBits], left-padded to a word boundary. */
    private fun generateCheckWords(bitArray: BitArray, totalBits: Int, wordSize: Int): BitArray {
        val messageSizeInWords = bitArray.size / wordSize
        val rs = ReedSolomonEncoder(getGF(wordSize))
        val totalWords = totalBits / wordSize
        val messageWords = bitsToWords(bitArray, wordSize, totalWords)
        rs.encode(messageWords, totalWords - messageSizeInWords)
        val startPad = totalBits % wordSize
        val messageBits = BitArray()
        messageBits.appendBits(0, startPad)
        for (messageWord in messageWords) {
            messageBits.appendBits(messageWord, wordSize)
        }
        return messageBits
    }

    /** Packs [stuffedBits] into [totalWords] words of [wordSize] bits, most significant bit first. */
    private fun bitsToWords(stuffedBits: BitArray, wordSize: Int, totalWords: Int): IntArray {
        val message = IntArray(totalWords)
        val n = stuffedBits.size / wordSize
        for (i in 0 until n) {
            var value = 0
            for (j in 0 until wordSize) {
                value = value or if (stuffedBits.get(i * wordSize + j)) (1 shl (wordSize - j - 1)) else 0
            }
            message[i] = value
        }
        return message
    }

    /** Returns the Galois field used for codewords of [wordSize] bits. */
    private fun getGF(wordSize: Int): GenericGF = when (wordSize) {
        4 -> GenericGF.AZTEC_PARAM
        6 -> GenericGF.AZTEC_DATA_6
        8 -> GenericGF.AZTEC_DATA_8
        10 -> GenericGF.AZTEC_DATA_10
        12 -> GenericGF.AZTEC_DATA_12
        else -> throw IllegalArgumentException("Unsupported word size $wordSize")
    }

    /** Splits [bits] into [wordSize]-bit words, stuffing a bit into any all-zero or all-one word. */
    fun stuffBits(bits: BitArray, wordSize: Int): BitArray {
        val out = BitArray()
        val n = bits.size
        val mask = (1 shl wordSize) - 2
        var i = 0
        while (i < n) {
            val word = readStuffingWord(bits, i, n, wordSize)
            i += when {
                (word and mask) == mask -> {
                    out.appendBits(word and mask, wordSize)
                    wordSize - 1
                }
                (word and mask) == 0 -> {
                    out.appendBits(word or 1, wordSize)
                    wordSize - 1
                }
                else -> {
                    out.appendBits(word, wordSize)
                    wordSize
                }
            }
        }
        return out
    }

    /** Reads the [wordSize]-bit word at [start], treating bits past [n] as ones. */
    private fun readStuffingWord(bits: BitArray, start: Int, n: Int, wordSize: Int): Int {
        var word = 0
        for (j in 0 until wordSize) {
            if (start + j >= n || bits.get(start + j)) {
                word = word or (1 shl (wordSize - 1 - j))
            }
        }
        return word
    }

    /** Returns the number of bits held by [layers] layers of a compact or full-range symbol. */
    private fun totalBitsInLayer(layers: Int, compact: Boolean): Int =
        ((if (compact) 88 else 112) + 16 * layers) * layers
}
