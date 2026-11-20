package com.mukeshsolanki.barcodes.internal.charset

/**
 * A minimal, platform-free replacement for `java.nio.charset.Charset`
 * with JDK `getBytes`/`String(bytes, cs)` semantics.
 */
internal abstract class Charset(
    /** Canonical charset name, e.g. `UTF-8`. */
    val name: String,
) {
    /** Encodes [s]; each unencodable char (or surrogate pair) becomes `?` (0x3F), like Java `getBytes`. */
    abstract fun encode(s: String): ByteArray

    /** Decodes [length] bytes of [bytes] from [offset]; malformed input becomes U+FFFD. */
    abstract fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String

    /** Returns true when every char of [s] is encodable without replacement. */
    abstract fun canEncode(s: String): Boolean

    /** Returns the canonical name, matching `java.nio.charset.Charset.toString()` closely enough for messages. */
    override fun toString(): String = name
}

/** Single-byte charsets mapping chars `0..maxChar` to themselves: ISO-8859-1 and US-ASCII. */
internal class SingleByteCharset(
    name: String,
    /** Highest char value encodable as one byte. */
    private val maxChar: Int,
) : Charset(name) {

    /** Encodes each code point to one byte, writing `?` for unencodable ones (a surrogate pair yields one `?`). */
    override fun encode(s: String): ByteArray {
        val out = ByteArray(s.length)
        var size = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            i += if (isPairAt(s, i)) 2 else 1
            out[size++] = if (c.code <= maxChar) c.code.toByte() else QUESTION_MARK
        }
        return out.copyOf(size)
    }

    /** Decodes each byte to one char; bytes above [maxChar] become U+FFFD. */
    override fun decode(bytes: ByteArray, offset: Int, length: Int): String {
        val chars = CharArray(length)
        for (i in 0 until length) {
            val b = bytes[offset + i].toInt() and 0xFF
            chars[i] = if (b <= maxChar) b.toChar() else REPLACEMENT
        }
        return chars.concatToString()
    }

    /** Returns true when no char of [s] exceeds [maxChar]. */
    override fun canEncode(s: String): Boolean = s.all { it.code <= maxChar }
}

/** UTF-8 backed by the Kotlin stdlib codec. */
internal object Utf8Charset : Charset("UTF-8") {

    /** Encodes [s] as UTF-8, writing `?` for each unpaired surrogate like Java `getBytes(UTF_8)`. */
    override fun encode(s: String): ByteArray =
        (if (canEncode(s)) s else replaceLoneSurrogates(s)).encodeToByteArray()

    /** Returns [s] with every unpaired surrogate replaced by `?`, keeping valid pairs intact. */
    private fun replaceLoneSurrogates(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            when {
                isPairAt(s, i) -> out.append(s[i]).append(s[i + 1]).also { i += 2 }
                s[i].isSurrogate() -> out.append('?').also { i++ }
                else -> out.append(s[i]).also { i++ }
            }
        }
        return out.toString()
    }

    /** Decodes UTF-8, replacing malformed sequences with U+FFFD. */
    override fun decode(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.decodeToString(offset, offset + length)

    /** Returns true unless [s] contains an unpaired surrogate. */
    override fun canEncode(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            when {
                isPairAt(s, i) -> i += 2
                s[i].isSurrogate() -> return false
                else -> i++
            }
        }
        return true
    }
}

/** The byte written for an unencodable char. */
internal const val QUESTION_MARK: Byte = 0x3F

/** The char produced for undecodable bytes. */
internal const val REPLACEMENT: Char = '�'

/** Returns true when [s] holds a valid high/low surrogate pair starting at [index]. */
internal fun isPairAt(s: String, index: Int): Boolean =
    s[index].isHighSurrogate() && index + 1 < s.length && s[index + 1].isLowSurrogate()
