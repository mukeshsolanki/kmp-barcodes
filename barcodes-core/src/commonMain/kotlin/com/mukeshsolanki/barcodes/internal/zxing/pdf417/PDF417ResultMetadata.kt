/*
 * Copyright 2013 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417

/**
 * Macro PDF417 metadata decoded from a symbol's control block.
 */
internal class PDF417ResultMetadata {

    /** Index of this segment within the whole file. */
    var segmentIndex: Int = 0

    /** File ID, the same for every related symbol. */
    var fileId: String? = null

    /** True if this is the last segment. */
    var isLastSegment: Boolean = false

    /** Count of segments, -1 if not set. */
    var segmentCount: Int = -1

    /** Sender, or null if not set. */
    var sender: String? = null

    /** Addressee, or null if not set. */
    var addressee: String? = null

    /** Name of the encoded file, or null if not set. */
    var fileName: String? = null

    /** Size in bytes of the encoded file, -1 if not set. */
    var fileSize: Long = -1

    /** Unix epoch timestamp in seconds, -1 if not set. */
    var timestamp: Long = -1

    /** CCITT-16 CRC checksum, -1 if not set. */
    var checksum: Int = -1

    /** Raw optional field codewords (legacy format). */
    var optionalData: IntArray? = null
}
