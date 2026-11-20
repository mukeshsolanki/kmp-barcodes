/*
 * Copyright 2007 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder

import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Reads format info, version and codewords from a sampled QR Code [BitMatrix].
 */
internal class BitMatrixParser(
    /** The sampled modules; must be >= 21 and 1 mod 4 on a side. */
    private val bitMatrix: BitMatrix,
) {
    /** Cached version, once read. */
    private var parsedVersion: Version? = null

    /** Cached format information, once read. */
    private var parsedFormatInfo: FormatInformation? = null

    /** When true, version and format info are read transposed. */
    private var mirror: Boolean = false

    init {
        val dimension = bitMatrix.height
        if (dimension < 21 || (dimension and 0x03) != 1) {
            throw FormatException.formatInstance
        }
    }

    /** Reads format information from either of its two locations; throws [FormatException] if neither parses. */
    fun readFormatInformation(): FormatInformation {
        parsedFormatInfo?.let { return it }
        var formatInfoBits1 = 0
        for (i in 0 until 6) {
            formatInfoBits1 = copyBit(i, 8, formatInfoBits1)
        }
        formatInfoBits1 = copyBit(7, 8, formatInfoBits1)
        formatInfoBits1 = copyBit(8, 8, formatInfoBits1)
        formatInfoBits1 = copyBit(8, 7, formatInfoBits1)
        for (j in 5 downTo 0) {
            formatInfoBits1 = copyBit(8, j, formatInfoBits1)
        }
        val dimension = bitMatrix.height
        var formatInfoBits2 = 0
        val jMin = dimension - 7
        for (j in dimension - 1 downTo jMin) {
            formatInfoBits2 = copyBit(8, j, formatInfoBits2)
        }
        for (i in dimension - 8 until dimension) {
            formatInfoBits2 = copyBit(i, 8, formatInfoBits2)
        }
        val info = FormatInformation.decodeFormatInformation(formatInfoBits1, formatInfoBits2)
        parsedFormatInfo = info
        return info ?: throw FormatException.formatInstance
    }

    /** Reads version information from either of its two locations; throws [FormatException] if neither parses. */
    fun readVersion(): Version {
        parsedVersion?.let { return it }
        val dimension = bitMatrix.height
        val provisionalVersion = (dimension - 17) / 4
        if (provisionalVersion <= 6) {
            return Version.getVersionForNumber(provisionalVersion)
        }
        var versionBits = 0
        val ijMin = dimension - 11
        for (j in 5 downTo 0) {
            for (i in dimension - 9 downTo ijMin) {
                versionBits = copyBit(i, j, versionBits)
            }
        }
        var theParsedVersion = Version.decodeVersionInformation(versionBits)
        if (theParsedVersion != null && theParsedVersion.dimensionForVersion == dimension) {
            parsedVersion = theParsedVersion
            return theParsedVersion
        }
        versionBits = 0
        for (i in 5 downTo 0) {
            for (j in dimension - 9 downTo ijMin) {
                versionBits = copyBit(i, j, versionBits)
            }
        }
        theParsedVersion = Version.decodeVersionInformation(versionBits)
        if (theParsedVersion != null && theParsedVersion.dimensionForVersion == dimension) {
            parsedVersion = theParsedVersion
            return theParsedVersion
        }
        throw FormatException.formatInstance
    }

    /** Shifts [versionBits] left and appends the module at (i, j), transposed when mirrored. */
    private fun copyBit(i: Int, j: Int, versionBits: Int): Int {
        val bit = if (mirror) bitMatrix.get(j, i) else bitMatrix.get(i, j)
        return if (bit) (versionBits shl 1) or 0x1 else versionBits shl 1
    }

    /** Unmasks the matrix and reads its codewords in zig-zag order; throws [FormatException] on a count mismatch. */
    fun readCodewords(): ByteArray {
        val formatInfo = readFormatInformation()
        val version = readVersion()
        val dataMask = DataMask.entries[formatInfo.dataMask.toInt()]
        val dimension = bitMatrix.height
        dataMask.unmaskBitMatrix(bitMatrix, dimension)
        val functionPattern = version.buildFunctionPattern()
        var readingUp = true
        val result = ByteArray(version.totalCodewords)
        var resultOffset = 0
        var currentByte = 0
        var bitsRead = 0
        var j = dimension - 1
        while (j > 0) {
            if (j == 6) {
                j--
            }
            for (count in 0 until dimension) {
                val i = if (readingUp) dimension - 1 - count else count
                for (col in 0 until 2) {
                    if (!functionPattern.get(j - col, i)) {
                        bitsRead++
                        currentByte = currentByte shl 1
                        if (bitMatrix.get(j - col, i)) {
                            currentByte = currentByte or 1
                        }
                        if (bitsRead == 8) {
                            result[resultOffset++] = currentByte.toByte()
                            bitsRead = 0
                            currentByte = 0
                        }
                    }
                }
            }
            readingUp = !readingUp
            j -= 2
        }
        if (resultOffset != version.totalCodewords) {
            throw FormatException.formatInstance
        }
        return result
    }

    /** Re-applies the data mask, restoring the matrix after [readCodewords]. */
    fun remask() {
        val formatInfo = parsedFormatInfo ?: return
        val dataMask = DataMask.entries[formatInfo.dataMask.toInt()]
        val dimension = bitMatrix.height
        dataMask.unmaskBitMatrix(bitMatrix, dimension)
    }

    /** Resets cached info and sets whether version and format info are read mirrored. */
    fun setMirror(mirror: Boolean) {
        parsedVersion = null
        parsedFormatInfo = null
        this.mirror = mirror
    }

    /** Transposes the bit matrix in place for a mirrored reading attempt. */
    fun mirror() {
        for (x in 0 until bitMatrix.width) {
            for (y in x + 1 until bitMatrix.height) {
                if (bitMatrix.get(x, y) != bitMatrix.get(y, x)) {
                    bitMatrix.flip(y, x)
                    bitMatrix.flip(x, y)
                }
            }
        }
    }
}
