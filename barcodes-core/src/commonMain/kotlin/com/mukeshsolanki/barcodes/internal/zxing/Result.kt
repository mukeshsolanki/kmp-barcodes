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
package com.mukeshsolanki.barcodes.internal.zxing

/** Encapsulates the result of decoding a barcode within an image. */
internal class Result(
    /** Raw text encoded by the barcode. */
    val text: String,
    /** Raw bytes encoded by the barcode, if applicable. */
    val rawBytes: ByteArray?,
    /** How many bits of [rawBytes] are valid; typically 8 times its length. */
    val numBits: Int,
    resultPoints: Array<out ResultPoint?>?,
    /** The format of the decoded barcode (zxing `getBarcodeFormat()`). */
    val barcodeFormat: BarcodeFormat,
    /** Decode time; always 0 in this port since nothing reads it. */
    val timestamp: Long = 0L,
) {
    /** Creates a result with [numBits] derived from [rawBytes] and the given [timestamp]. */
    constructor(
        text: String,
        rawBytes: ByteArray?,
        resultPoints: Array<out ResultPoint?>?,
        format: BarcodeFormat,
        timestamp: Long,
    ) : this(text, rawBytes, if (rawBytes == null) 0 else 8 * rawBytes.size, resultPoints, format, timestamp)

    /** Creates a result with [numBits] derived from [rawBytes] and a zero timestamp. */
    constructor(
        text: String,
        rawBytes: ByteArray?,
        resultPoints: Array<out ResultPoint?>?,
        format: BarcodeFormat,
    ) : this(text, rawBytes, resultPoints, format, 0L)

    /** Points related to the barcode in the image (finder patterns or corners); entries may be null. */
    @Suppress("UNCHECKED_CAST")
    var resultPoints: Array<ResultPoint?>? = resultPoints as Array<ResultPoint?>?
        private set

    /** Optional metadata about the decode, or null when none was recorded. */
    var resultMetadata: MutableMap<ResultMetadataType, Any?>? = null
        private set

    /** Records one metadata [value] under [type]. */
    fun putMetadata(type: ResultMetadataType, value: Any?) {
        val metadata = resultMetadata ?: mutableMapOf<ResultMetadataType, Any?>().also { resultMetadata = it }
        metadata[type] = value
    }

    /** Merges all entries of [metadata], if any, into this result's metadata. */
    fun putAllMetadata(metadata: Map<ResultMetadataType, Any?>?) {
        if (metadata == null) return
        val current = resultMetadata
        if (current == null) {
            resultMetadata = metadata.toMutableMap()
        } else {
            current.putAll(metadata)
        }
    }

    /** Appends [newPoints] to the existing result points. */
    fun addResultPoints(newPoints: Array<out ResultPoint?>?) {
        val oldPoints = resultPoints
        if (oldPoints == null) {
            @Suppress("UNCHECKED_CAST")
            resultPoints = newPoints as Array<ResultPoint?>?
        } else if (newPoints != null && newPoints.isNotEmpty()) {
            val allPoints = arrayOfNulls<ResultPoint>(oldPoints.size + newPoints.size)
            oldPoints.copyInto(allPoints, 0)
            newPoints.copyInto(allPoints, oldPoints.size)
            resultPoints = allPoints
        }
    }

    /** Returns the decoded text. */
    override fun toString(): String = text
}
