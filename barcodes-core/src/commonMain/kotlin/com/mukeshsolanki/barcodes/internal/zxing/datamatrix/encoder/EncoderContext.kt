/*
 * Copyright 2006-2007 Jeremias Maerki.
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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder

import com.mukeshsolanki.barcodes.internal.charset.Charsets

/** Mutable state shared by the encodation modes while one message is encoded. */
internal class EncoderContext(msg: String) {

    /** The message as ISO-8859-1 byte values, one char per byte. */
    val message: String = toIsoBytes(msg)

    /** Requested symbol shape. */
    private var shape: SymbolShapeHint = SymbolShapeHint.FORCE_NONE

    /** Codewords written so far; each char holds a value 0..255. */
    val codewords: StringBuilder = StringBuilder(msg.length)

    /** Index of the next character of [message] to encode. */
    var pos: Int = 0

    /** Mode requested by the current encoder, or -1 for no change. */
    var newEncoding: Int = -1
        private set

    /** Smallest symbol known to fit the codewords so far, or null before the first lookup. */
    var symbolInfo: SymbolInfo? = null
        private set

    /** Trailing characters (a macro trailer) that are not encoded. */
    private var skipAtEnd: Int = 0

    /** Sets the requested symbol shape. */
    fun setSymbolShape(shape: SymbolShapeHint) {
        this.shape = shape
    }

    /** Excludes the last [count] characters from encoding. */
    fun setSkipAtEnd(count: Int) {
        skipAtEnd = count
    }

    /** The character at [pos]. */
    val currentChar: Char
        get() = message[pos]

    /** Appends several codewords. */
    fun writeCodewords(codewords: String) {
        this.codewords.append(codewords)
    }

    /** Appends one codeword. */
    fun writeCodeword(codeword: Char) {
        codewords.append(codeword)
    }

    /** Number of codewords written so far. */
    val codewordCount: Int
        get() = codewords.length

    /** Asks the high-level encoder to switch to [encoding]. */
    fun signalEncoderChange(encoding: Int) {
        newEncoding = encoding
    }

    /** Clears a pending mode switch. */
    fun resetEncoderSignal() {
        newEncoding = -1
    }

    /** True while unencoded characters remain. */
    fun hasMoreCharacters(): Boolean = pos < totalMessageCharCount

    /** Characters to encode, excluding any skipped trailer. */
    private val totalMessageCharCount: Int
        get() = message.length - skipAtEnd

    /** Characters left to encode. */
    val remainingCharacters: Int
        get() = totalMessageCharCount - pos

    /** Grows [symbolInfo] to fit the codewords written so far. */
    fun updateSymbolInfo() {
        updateSymbolInfo(codewordCount)
    }

    /** Grows [symbolInfo] to fit [len] codewords. */
    fun updateSymbolInfo(len: Int) {
        val info = symbolInfo
        if (info == null || len > info.dataCapacity) {
            symbolInfo = SymbolInfo.lookup(len, shape)
        }
    }

    /** Forgets the current symbol so a smaller one can be chosen. */
    fun resetSymbolInfo() {
        symbolInfo = null
    }

    /** Helpers for message conversion. */
    private companion object {

        /** Converts [msg] to one char per ISO-8859-1 byte, rejecting characters outside that charset. */
        private fun toIsoBytes(msg: String): String {
            val msgBinary = Charsets.ISO_8859_1.encode(msg)
            val sb = StringBuilder(msgBinary.size)
            for (i in msgBinary.indices) {
                val ch = (msgBinary[i].toInt() and 0xff).toChar()
                require(ch != '?' || msg[i] == '?') { "Message contains characters outside ISO-8859-1 encoding." }
                sb.append(ch)
            }
            return sb.toString()
        }
    }
}
