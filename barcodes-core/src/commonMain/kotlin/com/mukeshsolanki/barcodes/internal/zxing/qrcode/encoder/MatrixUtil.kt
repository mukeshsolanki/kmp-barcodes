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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder

import com.mukeshsolanki.barcodes.internal.zxing.WriterException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Version

/**
 * Lays out function patterns, format/version info and data bits into a QR [ByteMatrix].
 */
internal object MatrixUtil {

    /** The 7x7 finder (position detection) pattern. */
    private val POSITION_DETECTION_PATTERN = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 1, 1, 1, 0, 1),
        intArrayOf(1, 0, 1, 1, 1, 0, 1),
        intArrayOf(1, 0, 1, 1, 1, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1),
    )

    /** The 5x5 alignment (position adjustment) pattern. */
    private val POSITION_ADJUSTMENT_PATTERN = arrayOf(
        intArrayOf(1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 1),
        intArrayOf(1, 0, 1, 0, 1),
        intArrayOf(1, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1),
    )

    /** Alignment pattern centres per version, -1 = none. From Appendix E Table 1, JIS0510X:2004 (p 71). */
    private val POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE = arrayOf(
        intArrayOf(-1, -1, -1, -1,  -1,  -1,  -1), // Version 1
        intArrayOf( 6, 18, -1, -1,  -1,  -1,  -1), // Version 2
        intArrayOf( 6, 22, -1, -1,  -1,  -1,  -1), // Version 3
        intArrayOf( 6, 26, -1, -1,  -1,  -1,  -1), // Version 4
        intArrayOf( 6, 30, -1, -1,  -1,  -1,  -1), // Version 5
        intArrayOf( 6, 34, -1, -1,  -1,  -1,  -1), // Version 6
        intArrayOf( 6, 22, 38, -1,  -1,  -1,  -1), // Version 7
        intArrayOf( 6, 24, 42, -1,  -1,  -1,  -1), // Version 8
        intArrayOf( 6, 26, 46, -1,  -1,  -1,  -1), // Version 9
        intArrayOf( 6, 28, 50, -1,  -1,  -1,  -1), // Version 10
        intArrayOf( 6, 30, 54, -1,  -1,  -1,  -1), // Version 11
        intArrayOf( 6, 32, 58, -1,  -1,  -1,  -1), // Version 12
        intArrayOf( 6, 34, 62, -1,  -1,  -1,  -1), // Version 13
        intArrayOf( 6, 26, 46, 66,  -1,  -1,  -1), // Version 14
        intArrayOf( 6, 26, 48, 70,  -1,  -1,  -1), // Version 15
        intArrayOf( 6, 26, 50, 74,  -1,  -1,  -1), // Version 16
        intArrayOf( 6, 30, 54, 78,  -1,  -1,  -1), // Version 17
        intArrayOf( 6, 30, 56, 82,  -1,  -1,  -1), // Version 18
        intArrayOf( 6, 30, 58, 86,  -1,  -1,  -1), // Version 19
        intArrayOf( 6, 34, 62, 90,  -1,  -1,  -1), // Version 20
        intArrayOf( 6, 28, 50, 72,  94,  -1,  -1), // Version 21
        intArrayOf( 6, 26, 50, 74,  98,  -1,  -1), // Version 22
        intArrayOf( 6, 30, 54, 78, 102,  -1,  -1), // Version 23
        intArrayOf( 6, 28, 54, 80, 106,  -1,  -1), // Version 24
        intArrayOf( 6, 32, 58, 84, 110,  -1,  -1), // Version 25
        intArrayOf( 6, 30, 58, 86, 114,  -1,  -1), // Version 26
        intArrayOf( 6, 34, 62, 90, 118,  -1,  -1), // Version 27
        intArrayOf( 6, 26, 50, 74,  98, 122,  -1), // Version 28
        intArrayOf( 6, 30, 54, 78, 102, 126,  -1), // Version 29
        intArrayOf( 6, 26, 52, 78, 104, 130,  -1), // Version 30
        intArrayOf( 6, 30, 56, 82, 108, 134,  -1), // Version 31
        intArrayOf( 6, 34, 60, 86, 112, 138,  -1), // Version 32
        intArrayOf( 6, 30, 58, 86, 114, 142,  -1), // Version 33
        intArrayOf( 6, 34, 62, 90, 118, 146,  -1), // Version 34
        intArrayOf( 6, 30, 54, 78, 102, 126, 150), // Version 35
        intArrayOf( 6, 24, 50, 76, 102, 128, 154), // Version 36
        intArrayOf( 6, 28, 54, 80, 106, 132, 158), // Version 37
        intArrayOf( 6, 32, 58, 84, 110, 136, 162), // Version 38
        intArrayOf( 6, 26, 54, 82, 110, 138, 166), // Version 39
        intArrayOf( 6, 30, 58, 86, 114, 142, 170), // Version 40
    )

    /** Type info cell coordinates at the top-left corner. */
    private val TYPE_INFO_COORDINATES = arrayOf(
        intArrayOf(8, 0),
        intArrayOf(8, 1),
        intArrayOf(8, 2),
        intArrayOf(8, 3),
        intArrayOf(8, 4),
        intArrayOf(8, 5),
        intArrayOf(8, 7),
        intArrayOf(8, 8),
        intArrayOf(7, 8),
        intArrayOf(5, 8),
        intArrayOf(4, 8),
        intArrayOf(3, 8),
        intArrayOf(2, 8),
        intArrayOf(1, 8),
        intArrayOf(0, 8),
    )

    /** Version info BCH generator, 1 1111 0010 0101. From Appendix D in JISX0510:2004 (p. 67). */
    private const val VERSION_INFO_POLY = 0x1f25

    /** Type info BCH generator. From Appendix C in JISX0510:2004 (p.65). */
    private const val TYPE_INFO_POLY = 0x537

    /** XOR mask applied to type info. */
    private const val TYPE_INFO_MASK_PATTERN = 0x5412

    /** Sets every cell to -1 (empty). */
    fun clearMatrix(matrix: ByteMatrix) {
        matrix.clear((-1).toByte())
    }

    /** Builds the complete matrix for [dataBits] at [ecLevel], [version] and [maskPattern] into [matrix]. */
    fun buildMatrix(
        dataBits: BitArray,
        ecLevel: ErrorCorrectionLevel,
        version: Version,
        maskPattern: Int,
        matrix: ByteMatrix,
    ) {
        clearMatrix(matrix)
        embedBasicPatterns(version, matrix)
        embedTypeInfo(ecLevel, maskPattern, matrix)
        maybeEmbedVersionInfo(version, matrix)
        embedDataBits(dataBits, maskPattern, matrix)
    }

    /** Embeds finder patterns and separators, the dark dot, alignment patterns and timing patterns. */
    fun embedBasicPatterns(version: Version, matrix: ByteMatrix) {
        embedPositionDetectionPatternsAndSeparators(matrix)
        embedDarkDotAtLeftBottomCorner(matrix)
        maybeEmbedPositionAdjustmentPatterns(version, matrix)
        embedTimingPatterns(matrix)
    }

    /** Embeds the 15 type info bits in both locations. See 8.9 of JISX0510:2004 (p.46). */
    fun embedTypeInfo(ecLevel: ErrorCorrectionLevel, maskPattern: Int, matrix: ByteMatrix) {
        val typeInfoBits = BitArray()
        makeTypeInfoBits(ecLevel, maskPattern, typeInfoBits)
        for (i in 0 until typeInfoBits.size) {
            val bit = typeInfoBits.get(typeInfoBits.size - 1 - i)
            val coordinates = TYPE_INFO_COORDINATES[i]
            val x1 = coordinates[0]
            val y1 = coordinates[1]
            matrix.set(x1, y1, bit)
            val x2: Int
            val y2: Int
            if (i < 8) {
                x2 = matrix.width - i - 1
                y2 = 8
            } else {
                x2 = 8
                y2 = matrix.height - 7 + (i - 8)
            }
            matrix.set(x2, y2, bit)
        }
    }

    /** Embeds the 18 version info bits in both locations when version >= 7. See 8.10 of JISX0510:2004 (p.47). */
    fun maybeEmbedVersionInfo(version: Version, matrix: ByteMatrix) {
        if (version.versionNumber < 7) {
            return
        }
        val versionInfoBits = BitArray()
        makeVersionInfoBits(version, versionInfoBits)
        var bitIndex = 6 * 3 - 1
        for (i in 0 until 6) {
            for (j in 0 until 3) {
                val bit = versionInfoBits.get(bitIndex)
                bitIndex--
                matrix.set(i, matrix.height - 11 + j, bit)
                matrix.set(matrix.height - 11 + j, i, bit)
            }
        }
    }

    /** Places [dataBits] in zig-zag order into empty cells, applying [maskPattern] (-1 = none). See 8.7 of JISX0510:2004. */
    fun embedDataBits(dataBits: BitArray, maskPattern: Int, matrix: ByteMatrix) {
        var bitIndex = 0
        var direction = -1
        var x = matrix.width - 1
        var y = matrix.height - 1
        while (x > 0) {
            if (x == 6) {
                x -= 1
            }
            while (y >= 0 && y < matrix.height) {
                for (i in 0 until 2) {
                    val xx = x - i
                    if (!isEmpty(matrix.get(xx, y).toInt())) {
                        continue
                    }
                    var bit: Boolean
                    if (bitIndex < dataBits.size) {
                        bit = dataBits.get(bitIndex)
                        ++bitIndex
                    } else {
                        bit = false
                    }
                    if (maskPattern != -1 && MaskUtil.getDataMaskBit(maskPattern, xx, y)) {
                        bit = !bit
                    }
                    matrix.set(xx, y, bit)
                }
                y += direction
            }
            direction = -direction
            y += direction
            x -= 2
        }
        if (bitIndex != dataBits.size) {
            throw WriterException("Not all bits consumed: " + bitIndex + '/' + dataBits.size)
        }
    }

    /** Returns the 1-based position of the highest set bit of [value], or 0 when none. */
    fun findMSBSet(value: Int): Int = 32 - value.countLeadingZeroBits()

    /** Computes the BCH code of [value] for generator [poly] by polynomial division. */
    fun calculateBCHCode(value: Int, poly: Int): Int {
        if (poly == 0) {
            throw IllegalArgumentException("0 polynomial")
        }
        val msbSetInPoly = findMSBSet(poly)
        var remainder = value shl (msbSetInPoly - 1)
        while (findMSBSet(remainder) >= msbSetInPoly) {
            remainder = remainder xor (poly shl (findMSBSet(remainder) - msbSetInPoly))
        }
        return remainder
    }

    /** Appends the masked 15-bit type info for [ecLevel] and [maskPattern] to [bits]. See 8.9 of JISX0510:2004 (p.45). */
    fun makeTypeInfoBits(ecLevel: ErrorCorrectionLevel, maskPattern: Int, bits: BitArray) {
        if (!QRCode.isValidMaskPattern(maskPattern)) {
            throw WriterException("Invalid mask pattern")
        }
        val typeInfo = (ecLevel.bits shl 3) or maskPattern
        bits.appendBits(typeInfo, 5)
        val bchCode = calculateBCHCode(typeInfo, TYPE_INFO_POLY)
        bits.appendBits(bchCode, 10)
        val maskBits = BitArray()
        maskBits.appendBits(TYPE_INFO_MASK_PATTERN, 15)
        bits.xor(maskBits)
        if (bits.size != 15) {
            throw WriterException("should not happen but we got: " + bits.size)
        }
    }

    /** Appends the 18-bit version info for [version] to [bits]. See 8.10 of JISX0510:2004 (p.45). */
    fun makeVersionInfoBits(version: Version, bits: BitArray) {
        bits.appendBits(version.versionNumber, 6)
        val bchCode = calculateBCHCode(version.versionNumber, VERSION_INFO_POLY)
        bits.appendBits(bchCode, 12)
        if (bits.size != 18) {
            throw WriterException("should not happen but we got: " + bits.size)
        }
    }

    /** True when [value] marks an empty cell (-1). */
    private fun isEmpty(value: Int): Boolean = value == -1

    /** Embeds alternating timing patterns on row 6 and column 6 where cells are empty. */
    private fun embedTimingPatterns(matrix: ByteMatrix) {
        for (i in 8 until matrix.width - 8) {
            val bit = (i + 1) % 2
            if (isEmpty(matrix.get(i, 6).toInt())) {
                matrix.set(i, 6, bit)
            }
            if (isEmpty(matrix.get(6, i).toInt())) {
                matrix.set(6, i, bit)
            }
        }
    }

    /** Sets the always-dark module at the bottom-left corner. JISX0510:2004 (p.46). */
    private fun embedDarkDotAtLeftBottomCorner(matrix: ByteMatrix) {
        if (matrix.get(8, matrix.height - 8).toInt() == 0) {
            throw WriterException()
        }
        matrix.set(8, matrix.height - 8, 1)
    }

    /** Writes an 8-cell light horizontal separator starting at ([xStart], [yStart]). */
    private fun embedHorizontalSeparationPattern(xStart: Int, yStart: Int, matrix: ByteMatrix) {
        for (x in 0 until 8) {
            if (!isEmpty(matrix.get(xStart + x, yStart).toInt())) {
                throw WriterException()
            }
            matrix.set(xStart + x, yStart, 0)
        }
    }

    /** Writes a 7-cell light vertical separator starting at ([xStart], [yStart]). */
    private fun embedVerticalSeparationPattern(xStart: Int, yStart: Int, matrix: ByteMatrix) {
        for (y in 0 until 7) {
            if (!isEmpty(matrix.get(xStart, yStart + y).toInt())) {
                throw WriterException()
            }
            matrix.set(xStart, yStart + y, 0)
        }
    }

    /** Copies the alignment pattern with its top-left at ([xStart], [yStart]). */
    private fun embedPositionAdjustmentPattern(xStart: Int, yStart: Int, matrix: ByteMatrix) {
        for (y in 0 until 5) {
            val patternY = POSITION_ADJUSTMENT_PATTERN[y]
            for (x in 0 until 5) {
                matrix.set(xStart + x, yStart + y, patternY[x])
            }
        }
    }

    /** Copies the finder pattern with its top-left at ([xStart], [yStart]). */
    private fun embedPositionDetectionPattern(xStart: Int, yStart: Int, matrix: ByteMatrix) {
        for (y in 0 until 7) {
            val patternY = POSITION_DETECTION_PATTERN[y]
            for (x in 0 until 7) {
                matrix.set(xStart + x, yStart + y, patternY[x])
            }
        }
    }

    /** Embeds the three finder patterns and their horizontal and vertical separators. */
    private fun embedPositionDetectionPatternsAndSeparators(matrix: ByteMatrix) {
        val pdpWidth = POSITION_DETECTION_PATTERN[0].size
        embedPositionDetectionPattern(0, 0, matrix)
        embedPositionDetectionPattern(matrix.width - pdpWidth, 0, matrix)
        embedPositionDetectionPattern(0, matrix.width - pdpWidth, matrix)
        val hspWidth = 8
        embedHorizontalSeparationPattern(0, hspWidth - 1, matrix)
        embedHorizontalSeparationPattern(matrix.width - hspWidth, hspWidth - 1, matrix)
        embedHorizontalSeparationPattern(0, matrix.width - hspWidth, matrix)
        val vspSize = 7
        embedVerticalSeparationPattern(vspSize, 0, matrix)
        embedVerticalSeparationPattern(matrix.height - vspSize - 1, 0, matrix)
        embedVerticalSeparationPattern(vspSize, matrix.height - vspSize, matrix)
    }

    /** Embeds alignment patterns at every listed centre pair whose cell is still empty (version >= 2). */
    private fun maybeEmbedPositionAdjustmentPatterns(version: Version, matrix: ByteMatrix) {
        if (version.versionNumber < 2) {
            return
        }
        val index = version.versionNumber - 1
        val coordinates = POSITION_ADJUSTMENT_PATTERN_COORDINATE_TABLE[index]
        for (y in coordinates) {
            if (y >= 0) {
                for (x in coordinates) {
                    if (x >= 0 && isEmpty(matrix.get(x, y).toInt())) {
                        embedPositionAdjustmentPattern(x - 2, y - 2, matrix)
                    }
                }
            }
        }
    }
}
