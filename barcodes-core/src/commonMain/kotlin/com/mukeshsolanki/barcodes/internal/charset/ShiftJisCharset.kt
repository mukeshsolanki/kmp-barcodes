package com.mukeshsolanki.barcodes.internal.charset

import com.mukeshsolanki.barcodes.internal.charset.generated.ShiftJisTable

/** Shift_JIS driven by the JDK-generated [ShiftJisTable]. */
internal object ShiftJisCharset : Charset("Shift_JIS") {

    /** Marker for "no mapping" in the lookup arrays. */
    private const val UNMAPPED = -1

    /** Highest code written as a single byte. */
    private const val MAX_SINGLE_BYTE_CODE = 0xFF

    /** Bits in one byte, the shift between the lead and trail byte of a double-byte code. */
    private const val BITS_PER_BYTE = 8

    /** First lead byte of the lower double-byte range. */
    private const val LOW_LEAD_START = 0x81

    /** Last lead byte of the lower double-byte range. */
    private const val LOW_LEAD_END = 0x9F

    /** First lead byte of the upper double-byte range. */
    private const val HIGH_LEAD_START = 0xE0

    /** Last lead byte of the upper double-byte range. */
    private const val HIGH_LEAD_END = 0xFC

    /** Char code to Shift_JIS code (one byte when <= 0xFF), or [UNMAPPED]. */
    private val encodeTable: IntArray by lazy { buildEncodeTable() }

    /** Shift_JIS code to char code, or [UNMAPPED]. */
    private val decodeTable: IntArray by lazy { buildDecodeTable() }

    /** Encodes [s]; unmappable chars (a surrogate pair counts as one) become `?`. */
    override fun encode(s: String): ByteArray {
        val out = ByteArray(s.length * 2)
        var size = 0
        var i = 0
        while (i < s.length) {
            val code = if (isPairAt(s, i)) UNMAPPED else encodeTable[s[i].code]
            i += if (isPairAt(s, i)) 2 else 1
            size = writeCode(out, size, code)
        }
        return out.copyOf(size)
    }

    /**
     * Decodes Shift_JIS; an unmapped lead byte or single byte becomes U+FFFD and consumes one byte,
     * as the JDK does.
     */
    override fun decode(bytes: ByteArray, offset: Int, length: Int): String {
        val end = offset + length
        val out = StringBuilder(length)
        var i = offset
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            val pair = if (isLeadByte(b) && i + 1 < end) {
                decodeTable[(b shl BITS_PER_BYTE) or (bytes[i + 1].toInt() and 0xFF)]
            } else {
                UNMAPPED
            }
            val single = if (isLeadByte(b)) UNMAPPED else decodeTable[b]
            when {
                pair != UNMAPPED -> out.append(pair.toChar()).also { i += 2 }
                single != UNMAPPED -> out.append(single.toChar()).also { i++ }
                else -> out.append(REPLACEMENT).also { i++ }
            }
        }
        return out.toString()
    }

    /** Returns true when every char of [s] has a Shift_JIS mapping. */
    override fun canEncode(s: String): Boolean = s.all { encodeTable[it.code] != UNMAPPED }

    /** Writes [code] (`?` when unmapped) into [out] at [index] and returns the next index. */
    private fun writeCode(out: ByteArray, index: Int, code: Int): Int = when {
        code == UNMAPPED -> {
            out[index] = QUESTION_MARK
            index + 1
        }
        code <= MAX_SINGLE_BYTE_CODE -> {
            out[index] = code.toByte()
            index + 1
        }
        else -> {
            out[index] = (code shr BITS_PER_BYTE).toByte()
            out[index + 1] = code.toByte()
            index + 2
        }
    }

    /** Returns true for Shift_JIS double-byte lead bytes. */
    private fun isLeadByte(b: Int): Boolean = b in LOW_LEAD_START..LOW_LEAD_END || b in HIGH_LEAD_START..HIGH_LEAD_END

    /** Builds the char-to-code array from the round-trip and encode-only tables. */
    private fun buildEncodeTable(): IntArray {
        val table = IntArray(0x10000) { UNMAPPED }
        ShiftJisTable.CHARS.forEachIndexed { i, c -> table[c.code] = ShiftJisTable.CODES[i].code }
        ShiftJisTable.ENCODE_ONLY_CHARS.forEachIndexed { i, c ->
            table[c.code] = ShiftJisTable.ENCODE_ONLY_CODES[i].code
        }
        return table
    }

    /** Builds the code-to-char array from the round-trip table. */
    private fun buildDecodeTable(): IntArray {
        val table = IntArray(0x10000) { UNMAPPED }
        ShiftJisTable.CODES.forEachIndexed { i, code -> table[code.code] = ShiftJisTable.CHARS[i].code }
        return table
    }
}
