/*
 * Copyright 2022 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.common

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.FormatException

/**
 * Converts a sequence of ECIs and bytes into a string. ECIs without a supported charset decode as ISO-8859-1.
 */
internal class ECIStringBuilder private constructor(
    /** Pending bytes (one char per byte) in [currentCharset]. */
    private var currentBytes: StringBuilder,
) {

    /** Decoded text so far, or null if nothing has been flushed. */
    private var result: StringBuilder? = null

    /** Charset of the pending bytes. */
    private var currentCharset: Charset = Charsets.ISO_8859_1

    /** Creates an empty builder. */
    constructor() : this(StringBuilder())

    /** Creates an empty builder with room for [initialCapacity] bytes. */
    constructor(initialCapacity: Int) : this(StringBuilder(initialCapacity))

    /** Appends the lowest byte of [value]. */
    fun append(value: Char) {
        currentBytes.append((value.code and 0xff).toChar())
    }

    /** Appends [value] as a byte. */
    fun append(value: Byte) {
        currentBytes.append((value.toInt() and 0xff).toChar())
    }

    /** Appends the characters of [value] as byte values. */
    fun append(value: String) {
        currentBytes.append(value)
    }

    /** Appends the decimal string of [value]. */
    fun append(value: Int) {
        append(value.toString())
    }

    /** Switches to the charset of ECI [value]; throws [FormatException] on an invalid ECI. */
    fun appendECI(value: Int) {
        encodeCurrentBytesIfAny()
        val characterSetECI = CharacterSetECI.getCharacterSetECIByValue(value)
            ?: throw FormatException.formatInstance
        currentCharset = characterSetECI.charset ?: Charsets.ISO_8859_1
    }

    /** Decodes pending bytes with [currentCharset] and moves them into [result]. */
    private fun encodeCurrentBytesIfAny() {
        if (currentBytes.isEmpty()) {
            return
        }
        val decoded: CharSequence = if (currentCharset == Charsets.ISO_8859_1) {
            currentBytes
        } else {
            currentCharset.decode(Charsets.ISO_8859_1.encode(currentBytes.toString()))
        }
        val existing = result
        if (existing == null) {
            result = if (decoded is StringBuilder) decoded else StringBuilder(decoded)
        } else {
            existing.append(decoded)
        }
        currentBytes = StringBuilder()
    }

    /** Appends the characters of [value] as text, unlike the byte-oriented appends. */
    fun appendCharacters(value: CharSequence) {
        encodeCurrentBytesIfAny()
        val existing = result ?: StringBuilder().also { result = it }
        existing.append(value)
    }

    /** Length of the decoded string; prefer [isEmpty]. */
    fun length(): Int = toString().length

    /** True iff nothing has been appended. */
    val isEmpty: Boolean
        get() = currentBytes.isEmpty() && (result?.isEmpty() ?: true)

    /** Flushes pending bytes and returns the decoded text. */
    override fun toString(): String {
        encodeCurrentBytesIfAny()
        return result?.toString() ?: ""
    }
}
