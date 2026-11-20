/*
 * Copyright 2006 Jeremias Maerki in part, and ZXing Authors in part
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
 */
/*
 * This file has been modified from its original form in Barcode4J.
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.encoder

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.zxing.WriterException
import kotlin.math.abs

/**
 * Top-level class for the logic part of the PDF417 implementation.
 */
internal class PDF417(
    /** True to emit compact PDF417 (no right row indicator, one-module stop line). */
    private var compact: Boolean = false,
) {

    /** The matrix produced by the last [generateBarcodeLogic] call. */
    lateinit var barcodeMatrix: BarcodeMatrix
        private set

    /** Compaction mode used by the high-level encoder. */
    private var compaction: Compaction = Compaction.AUTO

    /** Character encoding for byte compaction, or null for the default (ISO-8859-1). */
    private var encoding: Charset? = null

    /** Minimum number of data columns. */
    private var minCols: Int = 2

    /** Maximum number of data columns. */
    private var maxCols: Int = 30

    /** Maximum number of rows. */
    private var maxRows: Int = 30

    /** Minimum number of rows. */
    private var minRows: Int = 2

    /** Encodes [msg] at [errorCorrectionLevel] into [barcodeMatrix]; throws [WriterException] if it cannot fit. */
    fun generateBarcodeLogic(msg: String, errorCorrectionLevel: Int) {
        val errorCorrectionCodeWords = PDF417ErrorCorrection.getErrorCorrectionCodewordCount(errorCorrectionLevel)
        val highLevel = PDF417HighLevelEncoder.encodeHighLevel(msg, compaction, encoding)
        val sourceCodeWords = highLevel.length
        val dimension = determineDimensions(sourceCodeWords, errorCorrectionCodeWords)
        val cols = dimension[0]
        val rows = dimension[1]
        val pad = getNumberOfPadCodewords(sourceCodeWords, errorCorrectionCodeWords, cols, rows)
        if (sourceCodeWords + errorCorrectionCodeWords + 1 > 929) {
            throw WriterException(
                "Encoded message contains too many code words, message too big (" + msg.length + " bytes)",
            )
        }
        val n = sourceCodeWords + pad + 1
        val sb = StringBuilder(n)
        sb.append(n.toChar())
        sb.append(highLevel)
        for (i in 0 until pad) {
            sb.append(900.toChar())
        }
        val dataCodewords = sb.toString()
        val ec = PDF417ErrorCorrection.generateErrorCorrection(dataCodewords, errorCorrectionLevel)
        val matrix = BarcodeMatrix(rows, cols)
        barcodeMatrix = matrix
        encodeLowLevel(dataCodewords + ec, cols, rows, errorCorrectionLevel, matrix)
    }

    /** Writes every row (start, left indicator, data, right indicator, stop) into [logic]. */
    private fun encodeLowLevel(
        fullCodewords: CharSequence,
        c: Int,
        r: Int,
        errorCorrectionLevel: Int,
        logic: BarcodeMatrix,
    ) {
        var idx = 0
        for (y in 0 until r) {
            val cluster = y % 3
            logic.startRow()
            encodeChar(START_PATTERN, 17, logic.currentRow)
            val left = leftRowIndicator(y, cluster, c, r, errorCorrectionLevel)
            val right = rightRowIndicator(y, cluster, c, r, errorCorrectionLevel)
            encodeChar(CODEWORD_TABLE[cluster][left], 17, logic.currentRow)
            for (x in 0 until c) {
                encodeChar(CODEWORD_TABLE[cluster][fullCodewords[idx].code], 17, logic.currentRow)
                idx++
            }
            if (compact) {
                encodeChar(STOP_PATTERN, 1, logic.currentRow)
            } else {
                encodeChar(CODEWORD_TABLE[cluster][right], 17, logic.currentRow)
                encodeChar(STOP_PATTERN, 18, logic.currentRow)
            }
        }
    }

    /** Picks the columns/rows pair closest to the preferred aspect ratio; throws [WriterException] if none fits. */
    private fun determineDimensions(sourceCodeWords: Int, errorCorrectionCodeWords: Int): IntArray {
        var ratio = 0.0f
        var dimension: IntArray? = null
        for (cols in minCols..maxCols) {
            val rows = calculateNumberOfRows(sourceCodeWords, errorCorrectionCodeWords, cols)
            if (rows < minRows) {
                break
            }
            if (rows > maxRows) {
                continue
            }
            val newRatio = ((17 * cols + 69).toFloat() * DEFAULT_MODULE_WIDTH) / (rows * HEIGHT)
            if (dimension != null && abs(newRatio - PREFERRED_RATIO) > abs(ratio - PREFERRED_RATIO)) {
                continue
            }
            ratio = newRatio
            dimension = intArrayOf(cols, rows)
        }
        if (dimension == null) {
            val rows = calculateNumberOfRows(sourceCodeWords, errorCorrectionCodeWords, minCols)
            if (rows < minRows) {
                dimension = intArrayOf(minCols, minRows)
            }
        }
        return dimension ?: throw WriterException("Unable to fit message in columns")
    }

    /** Sets the allowed column and row ranges. */
    fun setDimensions(maxCols: Int, minCols: Int, maxRows: Int, minRows: Int) {
        this.maxCols = maxCols
        this.minCols = minCols
        this.maxRows = maxRows
        this.minRows = minRows
    }

    /** Sets the compaction mode to use. */
    fun setCompaction(compaction: Compaction) {
        this.compaction = compaction
    }

    /** Enables or disables compact PDF417. */
    fun setCompact(compact: Boolean) {
        this.compact = compact
    }

    /** Sets the character encoding to use for byte compaction. */
    fun setEncoding(encoding: Charset?) {
        this.encoding = encoding
    }

    /** Constants, the codeword table and stateless helpers. */
    private companion object {
        /** The start pattern (17 bits). */
        const val START_PATTERN: Int = 0x1fea8

        /** The stop pattern (18 bits). */
        const val STOP_PATTERN: Int = 0x3fa29

        /** Target width/height ratio of the symbol. */
        const val PREFERRED_RATIO: Float = 3.0f

        /** One module width in mm. */
        const val DEFAULT_MODULE_WIDTH: Float = 0.357f

        /** Row height in mm. */
        const val HEIGHT: Float = 2.0f

        /** The codeword table from Annex A of ISO/IEC 15438:2001(E). */
        val CODEWORD_TABLE: Array<IntArray> = PDF417_ENCODER_CODEWORD_TABLE

        /** Number of rows needed for [m] source and [k] EC codewords in [c] columns (annex Q). */
        fun calculateNumberOfRows(m: Int, k: Int, c: Int): Int {
            var r = ((m + 1 + k) / c) + 1
            if (c * r >= (m + 1 + k + c)) {
                r--
            }
            return r
        }

        /** Number of pad codewords for the given layout (4.9.2). */
        fun getNumberOfPadCodewords(m: Int, k: Int, c: Int, r: Int): Int {
            val n = c * r - k
            return if (n > m + 1) n - m - 1 else 0
        }

        /** Left row indicator codeword for row [y]. */
        fun leftRowIndicator(y: Int, cluster: Int, c: Int, r: Int, errorCorrectionLevel: Int): Int = when (cluster) {
            0 -> (30 * (y / 3)) + ((r - 1) / 3)
            1 -> (30 * (y / 3)) + (errorCorrectionLevel * 3) + ((r - 1) % 3)
            else -> (30 * (y / 3)) + (c - 1)
        }

        /** Right row indicator codeword for row [y]. */
        fun rightRowIndicator(y: Int, cluster: Int, c: Int, r: Int, errorCorrectionLevel: Int): Int = when (cluster) {
            0 -> (30 * (y / 3)) + (c - 1)
            1 -> (30 * (y / 3)) + ((r - 1) / 3)
            else -> (30 * (y / 3)) + (errorCorrectionLevel * 3) + ((r - 1) % 3)
        }

        /** Appends the top [len] bits of [pattern] to [logic] as bars. */
        fun encodeChar(pattern: Int, len: Int, logic: BarcodeRow) {
            var map = 1 shl (len - 1)
            var last = (pattern and map) != 0
            var width = 0
            for (i in 0 until len) {
                val black = (pattern and map) != 0
                if (last == black) {
                    width++
                } else {
                    logic.addBar(last, width)
                    last = black
                    width = 1
                }
                map = map shr 1
            }
            logic.addBar(last, width)
        }
    }
}
