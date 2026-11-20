package com.mukeshsolanki.barcodes

/** Characters escaped with a backslash in vCard and iCalendar text values. */
private const val VCARD_SPECIALS = "\\;,"

/** Mask keeping the unsigned value of a byte. */
private const val BYTE_MASK = 0xFF

/** Radix of percent-encoded byte values. */
private const val HEX_RADIX = 16

/** Prefixes every character in [specials] with a backslash. */
internal fun String.escape(specials: String): String = buildString {
    for (c in this@escape) {
        if (c in specials) append('\\')
        append(c)
    }
}

/** vCard/iCalendar text escaping: `\ ; ,` backslashed and newlines as `\n`. */
internal fun String.vEscape(): String = replace("\r\n", "\n").escape(VCARD_SPECIALS).replace("\n", "\\n")

/** RFC 3986 percent-encoding of UTF-8 bytes, keeping only unreserved characters. */
internal fun String.percentEncode(): String = buildString {
    for (byte in this@percentEncode.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) {
            append(c)
        } else {
            append('%').append((byte.toInt() and BYTE_MASK).toString(HEX_RADIX).uppercase().padStart(2, '0'))
        }
    }
}
