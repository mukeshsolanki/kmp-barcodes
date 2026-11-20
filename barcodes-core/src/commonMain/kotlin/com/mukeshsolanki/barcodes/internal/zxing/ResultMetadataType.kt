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
 *
 * Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
 */
package com.mukeshsolanki.barcodes.internal.zxing

/** Metadata about a decoding result that a decoder wishes to communicate back to the caller. */
internal enum class ResultMetadataType {
    /** Unspecified, application-specific metadata. */
    OTHER,

    /** Approximate orientation in degrees clockwise from upright (Int in [0, 360)). */
    ORIENTATION,

    /** List of byte arrays holding the raw bytes of each byte segment, in order. */
    BYTE_SEGMENTS,

    /** Error correction level used, if applicable (typically a String). */
    ERROR_CORRECTION_LEVEL,

    /** Number of errors corrected (Int). */
    ERRORS_CORRECTED,

    /** Number of erasures corrected (Int). */
    ERASURES_CORRECTED,

    /** Issue number for some periodicals (Int). */
    ISSUE_NUMBER,

    /** Suggested retail price for some products (formatted String). */
    SUGGESTED_PRICE,

    /** Possible country of manufacture as an ISO country code String. */
    POSSIBLE_COUNTRY,

    /** UPC/EAN extension text. */
    UPC_EAN_EXTENSION,

    /** PDF417-specific metadata. */
    PDF417_EXTRA_METADATA,

    /** Structured append sequence number, if applicable. */
    STRUCTURED_APPEND_SEQUENCE,

    /** Structured append parity, if applicable. */
    STRUCTURED_APPEND_PARITY,

    /** Barcode symbology identifier. */
    SYMBOLOGY_IDENTIFIER,
}
