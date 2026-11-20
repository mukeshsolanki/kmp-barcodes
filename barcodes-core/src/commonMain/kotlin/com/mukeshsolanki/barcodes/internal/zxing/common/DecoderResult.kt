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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */

package com.mukeshsolanki.barcodes.internal.zxing.common

/**
 * Result of decoding a matrix of bits: raw bytes, their text interpretation and metadata.
 */
internal class DecoderResult(
    /** Raw bytes of the result, or null if not applicable. */
    val rawBytes: ByteArray?,
    /** Text representation of the result. */
    val text: String,
    /** Byte segments of the result, or null if not applicable. */
    val byteSegments: List<ByteArray>?,
    /** Name of the error correction level used, or null if not applicable. */
    val ecLevel: String?,
    /** Structured append sequence number, or -1. */
    val structuredAppendSequenceNumber: Int,
    /** Structured append parity, or -1. */
    val structuredAppendParity: Int,
    /** Symbology identifier modifier. */
    val symbologyModifier: Int,
) {

    /** Creates a result without structured append or symbology modifier. */
    constructor(rawBytes: ByteArray?, text: String, byteSegments: List<ByteArray>?, ecLevel: String?) :
        this(rawBytes, text, byteSegments, ecLevel, -1, -1, 0)

    /** Creates a result with a [symbologyModifier] and no structured append. */
    constructor(
        rawBytes: ByteArray?,
        text: String,
        byteSegments: List<ByteArray>?,
        ecLevel: String?,
        symbologyModifier: Int,
    ) : this(rawBytes, text, byteSegments, ecLevel, -1, -1, symbologyModifier)

    /** Creates a result with structured append info and no symbology modifier. */
    constructor(
        rawBytes: ByteArray?,
        text: String,
        byteSegments: List<ByteArray>?,
        ecLevel: String?,
        saSequence: Int,
        saParity: Int,
    ) : this(rawBytes, text, byteSegments, ecLevel, saSequence, saParity, 0)

    /** How many bits of [rawBytes] are valid; typically 8 times its length. */
    var numBits: Int = if (rawBytes == null) 0 else 8 * rawBytes.size

    /** Number of errors corrected, or null if not applicable. */
    var errorsCorrected: Int? = null

    /** Number of erasures corrected, or null if not applicable. */
    var erasures: Int? = null

    /** Arbitrary additional metadata. */
    var other: Any? = null

    /** True when the result carries structured append information. */
    fun hasStructuredAppend(): Boolean = structuredAppendParity >= 0 && structuredAppendSequenceNumber >= 0
}
