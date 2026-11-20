/*
 * Copyright 2008 ZXing authors
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
import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray

/**
 * Encapsulates functionality and implementation that is common to UPC and EAN families
 * of one-dimensional barcodes.
 */
internal abstract class UPCEANReader protected constructor() : OneDReader() {

    /** Reused buffer for the decoded digits of a row. */
    private val decodeRowStringBuffer = StringBuilder(20)

    /** Reader for optional 2 and 5 digit add-on extensions. */
    private val extensionReader = UPCEANExtensionSupport()

    /** Lookup of GS1 prefixes to country identifiers. */
    private val eanManSupport = EANManufacturerOrgSupport()

    /** Decodes [row] after locating its start guard pattern. */
    override fun decodeRow(rowNumber: Int, row: BitArray, hints: Map<DecodeHintType, Any?>?): Result =
        decodeRow(rowNumber, row, findStartGuardPattern(row), hints)

    /**
     * Like [decodeRow], but the caller supplies [startGuardRange], the start/end columns of the
     * opening start pattern, so it can be computed once and reused across implementations.
     */
    open fun decodeRow(
        rowNumber: Int,
        row: BitArray,
        startGuardRange: IntArray,
        hints: Map<DecodeHintType, Any?>?,
    ): Result {
        val resultPointCallback = hints?.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK) as ResultPointCallback?
        var symbologyIdentifier = 0

        resultPointCallback?.foundPossibleResultPoint(
            ResultPoint((startGuardRange[0] + startGuardRange[1]) / 2.0f, rowNumber.toFloat()),
        )

        val result = decodeRowStringBuffer
        result.setLength(0)
        val endStart = decodeMiddle(row, startGuardRange, result)

        resultPointCallback?.foundPossibleResultPoint(ResultPoint(endStart.toFloat(), rowNumber.toFloat()))

        val endRange = decodeEnd(row, endStart)

        resultPointCallback?.foundPossibleResultPoint(
            ResultPoint((endRange[0] + endRange[1]) / 2.0f, rowNumber.toFloat()),
        )

        val end = endRange[1]
        val quietEnd = end + (end - endRange[0])
        if (quietEnd >= row.size || !row.isRange(end, quietEnd, false)) {
            throw NotFoundException.notFoundInstance
        }

        val resultString = result.toString()
        if (resultString.length < 8) {
            throw FormatException.formatInstance
        }
        if (!checkChecksum(resultString)) {
            throw ChecksumException.checksumInstance
        }

        val left = (startGuardRange[1] + startGuardRange[0]) / 2.0f
        val right = (endRange[1] + endRange[0]) / 2.0f
        val format = barcodeFormat
        val decodeResult = Result(
            resultString,
            null,
            arrayOf(
                ResultPoint(left, rowNumber.toFloat()),
                ResultPoint(right, rowNumber.toFloat()),
            ),
            format,
        )

        val extensionLength = decodeExtension(decodeResult, rowNumber, row, endRange[1])

        val allowedExtensions = hints?.get(DecodeHintType.ALLOWED_EAN_EXTENSIONS) as IntArray?
        if (allowedExtensions != null) {
            var valid = false
            for (length in allowedExtensions) {
                if (extensionLength == length) {
                    valid = true
                    break
                }
            }
            if (!valid) {
                throw NotFoundException.notFoundInstance
            }
        }

        if (format == BarcodeFormat.EAN_13 || format == BarcodeFormat.UPC_A) {
            val countryID = eanManSupport.lookupCountryIdentifier(resultString)
            if (countryID != null) {
                decodeResult.putMetadata(ResultMetadataType.POSSIBLE_COUNTRY, countryID)
            }
        }
        if (format == BarcodeFormat.EAN_8) {
            symbologyIdentifier = 4
        }

        decodeResult.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]E$symbologyIdentifier")

        return decodeResult
    }

    /**
     * Tries to read an add-on extension after [rowOffset] and merges it into [decodeResult].
     * Returns the extension length, or 0 when none was read.
     */
    private fun decodeExtension(decodeResult: Result, rowNumber: Int, row: BitArray, rowOffset: Int): Int {
        var extensionLength = 0
        try {
            val extensionResult = extensionReader.decodeRow(rowNumber, row, rowOffset)
            decodeResult.putMetadata(ResultMetadataType.UPC_EAN_EXTENSION, extensionResult.text)
            extensionResult.resultMetadata?.let { decodeResult.putAllMetadata(it) }
            extensionResult.resultPoints?.let { decodeResult.addResultPoints(it) }
            extensionLength = extensionResult.text.length
        } catch (re: ReaderException) {
        }
        return extensionLength
    }

    /** Returns whether [s] passes this format's checksum; throws FormatException on non-digits. */
    open fun checkChecksum(s: String): Boolean = checkStandardUPCEANChecksum(s)

    /** Finds the end guard pattern starting at [endStart]. */
    open fun decodeEnd(row: BitArray, endStart: Int): IntArray =
        findGuardPattern(row, endStart, false, START_END_PATTERN)

    /** The 1D format this decoder reads. */
    abstract val barcodeFormat: BarcodeFormat

    /**
     * Decodes the portion of a barcode between the start and end guard patterns into [resultString].
     * Returns the horizontal offset of the first pixel after the decoded middle.
     */
    abstract fun decodeMiddle(row: BitArray, startRange: IntArray, resultString: StringBuilder): Int

    /** UPC/EAN patterns and guard/digit helpers shared by the UPC/EAN readers, writers and extensions. */
    companion object {

        /** Maximum average variance accepted for a pattern match. */
        private const val MAX_AVG_VARIANCE = 0.48f

        /** Maximum variance any single counter may have in a pattern match. */
        private const val MAX_INDIVIDUAL_VARIANCE = 0.7f

        /** Start/end guard pattern. */
        val START_END_PATTERN: IntArray = intArrayOf(1, 1, 1)

        /** Pattern marking the middle of a UPC/EAN pattern, separating the two halves. */
        val MIDDLE_PATTERN: IntArray = intArrayOf(1, 1, 1, 1, 1)

        /** End guard pattern. */
        val END_PATTERN: IntArray = intArrayOf(1, 1, 1, 1, 1, 1)

        /** "Odd", or "L" patterns used to encode UPC/EAN digits. */
        val L_PATTERNS: Array<IntArray> = arrayOf(
            intArrayOf(3, 2, 1, 1),
            intArrayOf(2, 2, 2, 1),
            intArrayOf(2, 1, 2, 2),
            intArrayOf(1, 4, 1, 1),
            intArrayOf(1, 1, 3, 2),
            intArrayOf(1, 2, 3, 1),
            intArrayOf(1, 1, 1, 4),
            intArrayOf(1, 3, 1, 2),
            intArrayOf(1, 2, 1, 3),
            intArrayOf(3, 1, 1, 2),
        )

        /** The L patterns followed by the reversed "even", or "G" patterns used to encode UPC/EAN digits. */
        val L_AND_G_PATTERNS: Array<IntArray> = Array(20) { i ->
            if (i < 10) L_PATTERNS[i] else L_PATTERNS[i - 10].reversedArray()
        }

        /** Finds a start guard pattern in [row] that is preceded by a quiet zone at least as wide as itself. */
        fun findStartGuardPattern(row: BitArray): IntArray {
            var nextStart = 0
            val counters = IntArray(START_END_PATTERN.size)
            while (true) {
                counters.fill(0, 0, START_END_PATTERN.size)
                val startRange = findGuardPattern(row, nextStart, false, START_END_PATTERN, counters)
                val start = startRange[0]
                nextStart = startRange[1]
                val quietStart = start - (nextStart - start)
                if (quietStart >= 0 && row.isRange(quietStart, start, false)) {
                    return startRange
                }
            }
        }

        /** Returns true iff the digit string [s] passes the UPC/EAN checksum; throws FormatException on non-digits. */
        fun checkStandardUPCEANChecksum(s: CharSequence): Boolean {
            val length = s.length
            if (length == 0) {
                return false
            }
            val check = s[length - 1].digitToIntOrNull() ?: -1
            return getStandardUPCEANChecksum(s.subSequence(0, length - 1)) == check
        }

        /** Computes the UPC/EAN check digit of [s]; throws FormatException on non-digits. */
        fun getStandardUPCEANChecksum(s: CharSequence): Int {
            val length = s.length
            var sum = 0
            var i = length - 1
            while (i >= 0) {
                val digit = s[i] - '0'
                if (digit < 0 || digit > 9) {
                    throw FormatException.formatInstance
                }
                sum += digit
                i -= 2
            }
            sum *= 3
            i = length - 2
            while (i >= 0) {
                val digit = s[i] - '0'
                if (digit < 0 || digit > 9) {
                    throw FormatException.formatInstance
                }
                sum += digit
                i -= 2
            }
            return (1000 - sum) % 10
        }

        /** Finds [pattern] in [row] from [rowOffset]; [whiteFirst] means the pattern starts with a white run. */
        fun findGuardPattern(row: BitArray, rowOffset: Int, whiteFirst: Boolean, pattern: IntArray): IntArray =
            findGuardPattern(row, rowOffset, whiteFirst, pattern, IntArray(pattern.size))

        /**
         * Finds [pattern] in [row] from [rowOffset] reusing [counters]. Returns the start/end offsets
         * of the guard pattern; throws NotFoundException if it is not found.
         */
        private fun findGuardPattern(
            row: BitArray,
            rowOffset: Int,
            whiteFirst: Boolean,
            pattern: IntArray,
            counters: IntArray,
        ): IntArray {
            val width = row.size
            val offset = if (whiteFirst) row.getNextUnset(rowOffset) else row.getNextSet(rowOffset)
            var counterPosition = 0
            var patternStart = offset
            val patternLength = pattern.size
            var isWhite = whiteFirst
            for (x in offset until width) {
                if (row.get(x) != isWhite) {
                    counters[counterPosition]++
                } else {
                    if (counterPosition == patternLength - 1) {
                        if (OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE) < MAX_AVG_VARIANCE) {
                            return intArrayOf(patternStart, x)
                        }
                        patternStart += counters[0] + counters[1]
                        counters.copyInto(counters, 0, 2, counterPosition + 1)
                        counters[counterPosition - 1] = 0
                        counters[counterPosition] = 0
                        counterPosition--
                    } else {
                        counterPosition++
                    }
                    counters[counterPosition] = 1
                    isWhite = !isWhite
                }
            }
            throw NotFoundException.notFoundInstance
        }

        /**
         * Decodes a single UPC/EAN digit at [rowOffset] using [patterns], recording runs into [counters].
         * Returns the index of the best matching pattern; throws NotFoundException if none match.
         */
        fun decodeDigit(row: BitArray, counters: IntArray, rowOffset: Int, patterns: Array<IntArray>): Int {
            OneDReader.recordPattern(row, rowOffset, counters)
            var bestVariance = MAX_AVG_VARIANCE
            var bestMatch = -1
            val max = patterns.size
            for (i in 0 until max) {
                val pattern = patterns[i]
                val variance = OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE)
                if (variance < bestVariance) {
                    bestVariance = variance
                    bestMatch = i
                }
            }
            if (bestMatch >= 0) {
                return bestMatch
            } else {
                throw NotFoundException.notFoundInstance
            }
        }
    }
}
