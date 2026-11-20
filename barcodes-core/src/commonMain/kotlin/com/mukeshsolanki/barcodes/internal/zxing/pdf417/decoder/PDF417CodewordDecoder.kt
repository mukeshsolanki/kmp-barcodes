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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

import com.mukeshsolanki.barcodes.internal.zxing.common.detector.MathUtils
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Common

/**
 * Maps the eight bar/space widths of a scanned codeword to a PDF417 symbol.
 */
internal object PDF417CodewordDecoder {

    /** Normalised bar/space width ratios for every entry of the symbol table. */
    private val RATIOS_TABLE: Array<FloatArray> = buildRatiosTable()

    /** Pre-computes [RATIOS_TABLE]. */
    private fun buildRatiosTable(): Array<FloatArray> {
        val table = Array(PDF417Common.SYMBOL_TABLE.size) { FloatArray(PDF417Common.BARS_IN_MODULE) }
        for (i in PDF417Common.SYMBOL_TABLE.indices) {
            var currentSymbol = PDF417Common.SYMBOL_TABLE[i]
            var currentBit = currentSymbol and 0x1
            for (j in 0 until PDF417Common.BARS_IN_MODULE) {
                var size = 0.0f
                while ((currentSymbol and 0x1) == currentBit) {
                    size += 1.0f
                    currentSymbol = currentSymbol shr 1
                }
                currentBit = currentSymbol and 0x1
                table[i][PDF417Common.BARS_IN_MODULE - j - 1] = size / PDF417Common.MODULES_IN_CODEWORD
            }
        }
        return table
    }

    /** Returns the symbol for [moduleBitCount], falling back to the closest match by width ratios. */
    fun getDecodedValue(moduleBitCount: IntArray): Int {
        val decodedValue = getDecodedCodewordValue(sampleBitCounts(moduleBitCount))
        if (decodedValue != -1) {
            return decodedValue
        }
        return getClosestDecodedValue(moduleBitCount)
    }

    /** Resamples [moduleBitCount] to 17 modules. */
    private fun sampleBitCounts(moduleBitCount: IntArray): IntArray {
        val bitCountSum = MathUtils.sum(moduleBitCount).toFloat()
        val result = IntArray(PDF417Common.BARS_IN_MODULE)
        var bitCountIndex = 0
        var sumPreviousBits = 0
        for (i in 0 until PDF417Common.MODULES_IN_CODEWORD) {
            val sampleIndex = bitCountSum / (2 * PDF417Common.MODULES_IN_CODEWORD) +
                (i * bitCountSum) / PDF417Common.MODULES_IN_CODEWORD
            if ((sumPreviousBits + moduleBitCount[bitCountIndex]).toFloat() <= sampleIndex) {
                sumPreviousBits += moduleBitCount[bitCountIndex]
                bitCountIndex++
            }
            result[bitCountIndex]++
        }
        return result
    }

    /** Returns the symbol value for exact module counts, or -1 when it is not a valid symbol. */
    private fun getDecodedCodewordValue(moduleBitCount: IntArray): Int {
        val decodedValue = getBitValue(moduleBitCount)
        return if (PDF417Common.getCodeword(decodedValue) == -1) -1 else decodedValue
    }

    /** Builds the bit pattern described by the module counts (bars = 1). */
    private fun getBitValue(moduleBitCount: IntArray): Int {
        var result = 0L
        for (i in moduleBitCount.indices) {
            for (bit in 0 until moduleBitCount[i]) {
                result = (result shl 1) or (if (i % 2 == 0) 1L else 0L)
            }
        }
        return result.toInt()
    }

    /** Returns the symbol whose width ratios are closest (least squares) to [moduleBitCount], or -1. */
    private fun getClosestDecodedValue(moduleBitCount: IntArray): Int {
        val bitCountSum = MathUtils.sum(moduleBitCount)
        val bitCountRatios = FloatArray(PDF417Common.BARS_IN_MODULE)
        if (bitCountSum > 1) {
            for (i in bitCountRatios.indices) {
                bitCountRatios[i] = moduleBitCount[i] / bitCountSum.toFloat()
            }
        }
        var bestMatchError = Float.MAX_VALUE
        var bestMatch = -1
        for (j in RATIOS_TABLE.indices) {
            var error = 0.0f
            val ratioTableRow = RATIOS_TABLE[j]
            for (k in 0 until PDF417Common.BARS_IN_MODULE) {
                val diff = ratioTableRow[k] - bitCountRatios[k]
                error += diff * diff
                if (error >= bestMatchError) {
                    break
                }
            }
            if (error < bestMatchError) {
                bestMatchError = error
                bestMatch = PDF417Common.SYMBOL_TABLE[j]
            }
        }
        return bestMatch
    }
}
