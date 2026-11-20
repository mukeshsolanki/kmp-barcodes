package com.mukeshsolanki.barcodes.internal.charset

/** The four supported charsets and a JDK-style name lookup. */
internal object Charsets {

    /** ISO-8859-1 (Latin-1). */
    val ISO_8859_1: Charset = SingleByteCharset("ISO-8859-1", 0xFF)

    /** UTF-8. */
    val UTF_8: Charset = Utf8Charset

    /** US-ASCII. */
    val US_ASCII: Charset = SingleByteCharset("US-ASCII", 0x7F)

    /** Shift_JIS, backed by the generated JDK table. */
    val SHIFT_JIS: Charset = ShiftJisCharset

    /** Upper-cased aliases to charsets. */
    private val byAlias: Map<String, Charset> by lazy {
        mapOf(
            "ISO-8859-1" to ISO_8859_1,
            "ISO8859_1" to ISO_8859_1,
            "ISO_8859_1" to ISO_8859_1,
            "LATIN1" to ISO_8859_1,
            "UTF-8" to UTF_8,
            "UTF8" to UTF_8,
            "US-ASCII" to US_ASCII,
            "ASCII" to US_ASCII,
            "SHIFT_JIS" to SHIFT_JIS,
            "SJIS" to SHIFT_JIS,
        )
    }

    /** Looks up a charset by [name] case-insensitively; returns null for unsupported names. */
    fun forName(name: String): Charset? = byAlias[name.uppercase()]
}
