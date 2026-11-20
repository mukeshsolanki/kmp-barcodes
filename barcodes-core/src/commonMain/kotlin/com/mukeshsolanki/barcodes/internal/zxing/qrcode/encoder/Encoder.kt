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

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.EncodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.WriterException
import com.mukeshsolanki.barcodes.internal.zxing.common.BitArray
import com.mukeshsolanki.barcodes.internal.zxing.common.CharacterSetECI
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.GenericGF
import com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon.ReedSolomonEncoder
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Mode
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Version
import kotlin.math.max

/**
 * Encodes text into a [QRCode] using a single mode segment, like zxing's non-compact path.
 * The QR_COMPACT, QR_VERSION and GS1_FORMAT hints are ignored.
 */
internal object Encoder {

    /** Alphanumeric code per ASCII value, -1 = not encodable. Table 5 of JISX0510:2004 (p.19). */
    private val ALPHANUMERIC_TABLE = intArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 0x00-0x0f
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 0x10-0x1f
        36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43, // 0x20-0x2f
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 44, -1, -1, -1, -1, -1, // 0x30-0x3f
        -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, // 0x40-0x4f
        25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1, // 0x50-0x5f
    )

    /** Byte mode charset used when no CHARACTER_SET hint is given. */
    val DEFAULT_BYTE_MODE_ENCODING: Charset = Charsets.ISO_8859_1

    /** Sums the four mask penalty rules. See Table 21 of JISX0510:2004 (p.45). */
    private fun calculateMaskPenalty(matrix: ByteMatrix): Int =
        MaskUtil.applyMaskPenaltyRule1(matrix) +
            MaskUtil.applyMaskPenaltyRule2(matrix) +
            MaskUtil.applyMaskPenaltyRule3(matrix) +
            MaskUtil.applyMaskPenaltyRule4(matrix)

    /** Encodes [content] at [ecLevel] without hints; throws [WriterException] if it cannot be encoded. */
    fun encode(content: String, ecLevel: ErrorCorrectionLevel): QRCode = encode(content, ecLevel, null)

    /** Encodes [content] at [ecLevel] honouring CHARACTER_SET and QR_MASK_PATTERN; throws [WriterException] on failure. */
    fun encode(content: String, ecLevel: ErrorCorrectionLevel, hints: Map<EncodeHintType, Any?>?): QRCode {
        val hasEncodingHint = hints != null && hints.containsKey(EncodeHintType.CHARACTER_SET)
        val encoding = if (hasEncodingHint) {
            Charsets.forName(hints!![EncodeHintType.CHARACTER_SET].toString()) ?: DEFAULT_BYTE_MODE_ENCODING
        } else {
            DEFAULT_BYTE_MODE_ENCODING
        }
        val mode = chooseMode(content, encoding)
        val headerBits = BitArray()
        if (mode == Mode.BYTE && hasEncodingHint) {
            CharacterSetECI.getCharacterSetECI(encoding)?.let { appendECI(it, headerBits) }
        }
        appendModeInfo(mode, headerBits)
        val dataBits = BitArray()
        appendBytes(content, mode, dataBits, encoding)
        val version = recommendVersion(ecLevel, mode, headerBits, dataBits)
        val headerAndDataBits = BitArray()
        headerAndDataBits.appendBitArray(headerBits)
        val numLetters = if (mode == Mode.BYTE) dataBits.sizeInBytes else content.length
        appendLengthInfo(numLetters, version, mode, headerAndDataBits)
        headerAndDataBits.appendBitArray(dataBits)

        val ecBlocks = version.getECBlocksForLevel(ecLevel)
        val numDataBytes = version.totalCodewords - ecBlocks.totalECCodewords
        terminateBits(numDataBytes, headerAndDataBits)
        val finalBits = interleaveWithECBytes(
            headerAndDataBits,
            version.totalCodewords,
            numDataBytes,
            ecBlocks.numBlocks,
        )
        val qrCode = QRCode()
        qrCode.ecLevel = ecLevel
        qrCode.mode = mode
        qrCode.version = version
        val dimension = version.dimensionForVersion
        val matrix = ByteMatrix(dimension, dimension)
        var maskPattern = hintedMaskPattern(hints)
        if (maskPattern == -1) {
            maskPattern = chooseMaskPattern(finalBits, ecLevel, version, matrix)
        }
        qrCode.maskPattern = maskPattern
        MatrixUtil.buildMatrix(finalBits, ecLevel, version, maskPattern, matrix)
        qrCode.matrix = matrix
        return qrCode
    }

    /** Returns the QR_MASK_PATTERN hint when it is a valid 0..7 pattern, else -1. */
    private fun hintedMaskPattern(hints: Map<EncodeHintType, Any?>?): Int {
        if (hints == null || !hints.containsKey(EncodeHintType.QR_MASK_PATTERN)) {
            return -1
        }
        val hintMaskPattern = hints[EncodeHintType.QR_MASK_PATTERN].toString().toInt()
        return if (QRCode.isValidMaskPattern(hintMaskPattern)) hintMaskPattern else -1
    }

    /** Picks the smallest version that fits, guessing with version 1 first; throws [WriterException] if none fits. */
    private fun recommendVersion(
        ecLevel: ErrorCorrectionLevel,
        mode: Mode,
        headerBits: BitArray,
        dataBits: BitArray,
    ): Version {
        val provisionalBitsNeeded = calculateBitsNeeded(mode, headerBits, dataBits, Version.getVersionForNumber(1))
        val provisionalVersion = chooseVersion(provisionalBitsNeeded, ecLevel)
        val bitsNeeded = calculateBitsNeeded(mode, headerBits, dataBits, provisionalVersion)
        return chooseVersion(bitsNeeded, ecLevel)
    }

    /** Returns header + character count + data bit length for [version]. */
    private fun calculateBitsNeeded(mode: Mode, headerBits: BitArray, dataBits: BitArray, version: Version): Int =
        headerBits.size + mode.getCharacterCountBits(version) + dataBits.size

    /** Returns the alphanumeric code of character [code], or -1 when it has none. */
    fun getAlphanumericCode(code: Int): Int {
        if (code < ALPHANUMERIC_TABLE.size) {
            return ALPHANUMERIC_TABLE[code]
        }
        return -1
    }

    /** Chooses the mode for [content] with no charset hint. */
    fun chooseMode(content: String): Mode = chooseMode(content, null)

    /** Chooses KANJI for all-double-byte Shift_JIS content, else the narrowest of NUMERIC, ALPHANUMERIC, BYTE. */
    private fun chooseMode(content: String, encoding: Charset?): Mode {
        if (encoding != null && encoding.name == Charsets.SHIFT_JIS.name && isOnlyDoubleByteKanji(content)) {
            return Mode.KANJI
        }
        var hasNumeric = false
        var hasAlphanumeric = false
        for (c in content) {
            if (c in '0'..'9') {
                hasNumeric = true
            } else if (getAlphanumericCode(c.code) != -1) {
                hasAlphanumeric = true
            } else {
                return Mode.BYTE
            }
        }
        if (hasAlphanumeric) {
            return Mode.ALPHANUMERIC
        }
        if (hasNumeric) {
            return Mode.NUMERIC
        }
        return Mode.BYTE
    }

    /** True when [content] encodes in Shift_JIS to lead bytes 0x81-0x9F or 0xE0-0xEB pairs only. */
    fun isOnlyDoubleByteKanji(content: String): Boolean {
        val bytes = Charsets.SHIFT_JIS.encode(content)
        val length = bytes.size
        if (length % 2 != 0) {
            return false
        }
        var i = 0
        while (i < length) {
            val byte1 = bytes[i].toInt() and 0xFF
            if ((byte1 < 0x81 || byte1 > 0x9F) && (byte1 < 0xE0 || byte1 > 0xEB)) {
                return false
            }
            i += 2
        }
        return true
    }

    /** Builds the matrix with each of the 8 masks and returns the first with the lowest penalty. */
    private fun chooseMaskPattern(
        bits: BitArray,
        ecLevel: ErrorCorrectionLevel,
        version: Version,
        matrix: ByteMatrix,
    ): Int {
        var minPenalty = Int.MAX_VALUE
        var bestMaskPattern = -1
        for (maskPattern in 0 until QRCode.NUM_MASK_PATTERNS) {
            MatrixUtil.buildMatrix(bits, ecLevel, version, maskPattern, matrix)
            val penalty = calculateMaskPenalty(matrix)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMaskPattern = maskPattern
            }
        }
        return bestMaskPattern
    }

    /** Returns the first version 1..40 that fits [numInputBits] at [ecLevel]; throws [WriterException] if none. */
    private fun chooseVersion(numInputBits: Int, ecLevel: ErrorCorrectionLevel): Version {
        for (versionNum in 1..40) {
            val version = Version.getVersionForNumber(versionNum)
            if (willFit(numInputBits, version, ecLevel)) {
                return version
            }
        }
        throw WriterException("Data too big")
    }

    /** True when [numInputBits] fit in the data capacity of [version] at [ecLevel]. */
    fun willFit(numInputBits: Int, version: Version, ecLevel: ErrorCorrectionLevel): Boolean {
        val numBytes = version.totalCodewords
        val ecBlocks = version.getECBlocksForLevel(ecLevel)
        val numEcBytes = ecBlocks.totalECCodewords
        val numDataBytes = numBytes - numEcBytes
        val totalInputBytes = (numInputBits + 7) / 8
        return numDataBytes >= totalInputBytes
    }

    /** Appends the terminator, byte-alignment zeros and 0xEC/0x11 padding. See 8.4.8-8.4.9 of JISX0510:2004 (p.24). */
    fun terminateBits(numDataBytes: Int, bits: BitArray) {
        val capacity = numDataBytes * 8
        if (bits.size > capacity) {
            throw WriterException("data bits cannot fit in the QR Code" + bits.size + " > " + capacity)
        }
        var i = 0
        while (i < 4 && bits.size < capacity) {
            bits.appendBit(false)
            ++i
        }
        val numBitsInLastByte = bits.size and 0x07
        if (numBitsInLastByte > 0) {
            for (j in numBitsInLastByte until 8) {
                bits.appendBit(false)
            }
        }
        val numPaddingBytes = numDataBytes - bits.sizeInBytes
        for (j in 0 until numPaddingBytes) {
            bits.appendBits(if ((j and 0x01) == 0) 0xEC else 0x11, 8)
        }
        if (bits.size != capacity) {
            throw WriterException("Bits size does not equal capacity")
        }
    }

    /** Writes the data and EC byte counts for RS block [blockID] into the single-element out arrays. See table 12 in 8.5.1 of JISX0510:2004 (p.30). */
    fun getNumDataBytesAndNumECBytesForBlockID(
        numTotalBytes: Int,
        numDataBytes: Int,
        numRSBlocks: Int,
        blockID: Int,
        numDataBytesInBlock: IntArray,
        numECBytesInBlock: IntArray,
    ) {
        if (blockID >= numRSBlocks) {
            throw WriterException("Block ID too large")
        }
        val numRsBlocksInGroup2 = numTotalBytes % numRSBlocks
        val numRsBlocksInGroup1 = numRSBlocks - numRsBlocksInGroup2
        val numTotalBytesInGroup1 = numTotalBytes / numRSBlocks
        val numTotalBytesInGroup2 = numTotalBytesInGroup1 + 1
        val numDataBytesInGroup1 = numDataBytes / numRSBlocks
        val numDataBytesInGroup2 = numDataBytesInGroup1 + 1
        val numEcBytesInGroup1 = numTotalBytesInGroup1 - numDataBytesInGroup1
        val numEcBytesInGroup2 = numTotalBytesInGroup2 - numDataBytesInGroup2
        if (numEcBytesInGroup1 != numEcBytesInGroup2) {
            throw WriterException("EC bytes mismatch")
        }
        if (numRSBlocks != numRsBlocksInGroup1 + numRsBlocksInGroup2) {
            throw WriterException("RS blocks mismatch")
        }
        if (numTotalBytes !=
            ((numDataBytesInGroup1 + numEcBytesInGroup1) * numRsBlocksInGroup1) +
            ((numDataBytesInGroup2 + numEcBytesInGroup2) * numRsBlocksInGroup2)
        ) {
            throw WriterException("Total bytes mismatch")
        }
        if (blockID < numRsBlocksInGroup1) {
            numDataBytesInBlock[0] = numDataBytesInGroup1
            numECBytesInBlock[0] = numEcBytesInGroup1
        } else {
            numDataBytesInBlock[0] = numDataBytesInGroup2
            numECBytesInBlock[0] = numEcBytesInGroup2
        }
    }

    /** Splits [bits] into RS blocks, adds EC bytes and interleaves them. See 8.6 of JISX0510:2004 (p.37). */
    fun interleaveWithECBytes(bits: BitArray, numTotalBytes: Int, numDataBytes: Int, numRSBlocks: Int): BitArray {
        if (bits.sizeInBytes != numDataBytes) {
            throw WriterException("Number of bits and data bytes does not match")
        }
        var dataBytesOffset = 0
        var maxNumDataBytes = 0
        var maxNumEcBytes = 0
        val blocks = ArrayList<BlockPair>(numRSBlocks)
        for (i in 0 until numRSBlocks) {
            val numDataBytesInBlock = IntArray(1)
            val numEcBytesInBlock = IntArray(1)
            getNumDataBytesAndNumECBytesForBlockID(
                numTotalBytes,
                numDataBytes,
                numRSBlocks,
                i,
                numDataBytesInBlock,
                numEcBytesInBlock,
            )
            val size = numDataBytesInBlock[0]
            val dataBytes = ByteArray(size)
            bits.toBytes(8 * dataBytesOffset, dataBytes, 0, size)
            val ecBytes = generateECBytes(dataBytes, numEcBytesInBlock[0])
            blocks.add(BlockPair(dataBytes, ecBytes))
            maxNumDataBytes = max(maxNumDataBytes, size)
            maxNumEcBytes = max(maxNumEcBytes, ecBytes.size)
            dataBytesOffset += numDataBytesInBlock[0]
        }
        if (numDataBytes != dataBytesOffset) {
            throw WriterException("Data bytes does not match offset")
        }
        val result = BitArray()
        for (i in 0 until maxNumDataBytes) {
            for (block in blocks) {
                val dataBytes = block.dataBytes
                if (i < dataBytes.size) {
                    result.appendBits(dataBytes[i].toInt(), 8)
                }
            }
        }
        for (i in 0 until maxNumEcBytes) {
            for (block in blocks) {
                val ecBytes = block.errorCorrectionBytes
                if (i < ecBytes.size) {
                    result.appendBits(ecBytes[i].toInt(), 8)
                }
            }
        }
        if (numTotalBytes != result.sizeInBytes) {
            throw WriterException("Interleaving error: " + numTotalBytes + " and " + result.sizeInBytes + " differ.")
        }
        return result
    }

    /** Computes [numEcBytesInBlock] Reed-Solomon EC bytes over [dataBytes] in the QR field. */
    fun generateECBytes(dataBytes: ByteArray, numEcBytesInBlock: Int): ByteArray {
        val numDataBytes = dataBytes.size
        val toEncode = IntArray(numDataBytes + numEcBytesInBlock)
        for (i in 0 until numDataBytes) {
            toEncode[i] = dataBytes[i].toInt() and 0xFF
        }
        ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256).encode(toEncode, numEcBytesInBlock)
        val ecBytes = ByteArray(numEcBytesInBlock)
        for (i in 0 until numEcBytesInBlock) {
            ecBytes[i] = toEncode[numDataBytes + i].toByte()
        }
        return ecBytes
    }

    /** Appends the 4-bit mode indicator of [mode]. */
    fun appendModeInfo(mode: Mode, bits: BitArray) {
        bits.appendBits(mode.bits, 4)
    }

    /** Appends the character count [numLetters]; throws [WriterException] if it exceeds the count field. */
    fun appendLengthInfo(numLetters: Int, version: Version, mode: Mode, bits: BitArray) {
        val numBits = mode.getCharacterCountBits(version)
        if (numLetters >= (1 shl numBits)) {
            throw WriterException(numLetters.toString() + " is bigger than " + ((1 shl numBits) - 1))
        }
        bits.appendBits(numLetters, numBits)
    }

    /** Appends [content] encoded in [mode]; [encoding] applies to BYTE mode only. */
    fun appendBytes(content: String, mode: Mode, bits: BitArray, encoding: Charset) {
        when (mode) {
            Mode.NUMERIC -> appendNumericBytes(content, bits)
            Mode.ALPHANUMERIC -> appendAlphanumericBytes(content, bits)
            Mode.BYTE -> append8BitBytes(content, bits, encoding)
            Mode.KANJI -> appendKanjiBytes(content, bits)
            else -> throw WriterException("Invalid mode: $mode")
        }
    }

    /** Appends digits three per 10 bits, then 7 or 4 bits for the remainder. */
    fun appendNumericBytes(content: CharSequence, bits: BitArray) {
        val length = content.length
        var i = 0
        while (i < length) {
            val num1 = content[i] - '0'
            if (i + 2 < length) {
                val num2 = content[i + 1] - '0'
                val num3 = content[i + 2] - '0'
                bits.appendBits(num1 * 100 + num2 * 10 + num3, 10)
                i += 3
            } else if (i + 1 < length) {
                val num2 = content[i + 1] - '0'
                bits.appendBits(num1 * 10 + num2, 7)
                i += 2
            } else {
                bits.appendBits(num1, 4)
                i++
            }
        }
    }

    /** Appends alphanumeric pairs in 11 bits and a trailing character in 6; throws [WriterException] on invalid chars. */
    fun appendAlphanumericBytes(content: CharSequence, bits: BitArray) {
        val length = content.length
        var i = 0
        while (i < length) {
            val code1 = getAlphanumericCode(content[i].code)
            if (code1 == -1) {
                throw WriterException()
            }
            if (i + 1 < length) {
                val code2 = getAlphanumericCode(content[i + 1].code)
                if (code2 == -1) {
                    throw WriterException()
                }
                bits.appendBits(code1 * 45 + code2, 11)
                i += 2
            } else {
                bits.appendBits(code1, 6)
                i++
            }
        }
    }

    /** Appends [content] encoded with [encoding] (unencodable chars become '?') as 8-bit bytes. */
    fun append8BitBytes(content: String, bits: BitArray, encoding: Charset) {
        val bytes = encoding.encode(content)
        for (b in bytes) {
            bits.appendBits(b.toInt(), 8)
        }
    }

    /** Appends Shift_JIS double-byte characters in 13 bits each; throws [WriterException] on invalid sequences. */
    fun appendKanjiBytes(content: String, bits: BitArray) {
        val bytes = Charsets.SHIFT_JIS.encode(content)
        if (bytes.size % 2 != 0) {
            throw WriterException("Kanji byte size not even")
        }
        val maxI = bytes.size - 1
        var i = 0
        while (i < maxI) {
            val byte1 = bytes[i].toInt() and 0xFF
            val byte2 = bytes[i + 1].toInt() and 0xFF
            val code = (byte1 shl 8) or byte2
            var subtracted = -1
            if (code in 0x8140..0x9ffc) {
                subtracted = code - 0x8140
            } else if (code in 0xe040..0xebbf) {
                subtracted = code - 0xc140
            }
            if (subtracted == -1) {
                throw WriterException("Invalid byte sequence")
            }
            val encoded = ((subtracted shr 8) * 0xc0) + (subtracted and 0xff)
            bits.appendBits(encoded, 13)
            i += 2
        }
    }

    /** Appends an ECI header with an 8-bit designator (correct for values up to 127). */
    private fun appendECI(eci: CharacterSetECI, bits: BitArray) {
        bits.appendBits(Mode.ECI.bits, 4)
        bits.appendBits(eci.value, 8)
    }
}
