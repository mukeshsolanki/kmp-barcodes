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

/** Hints that may be passed to writers to specify their behaviour. */
internal enum class EncodeHintType {
    /** Error correction degree; type depends on the encoder (QR level, Aztec percent, PDF417 0..8, or String). */
    ERROR_CORRECTION,

    /** Character encoding to use where applicable (String). */
    CHARACTER_SET,

    /** Data Matrix shape (SymbolShapeHint). */
    DATA_MATRIX_SHAPE,

    /** Data Matrix compact mode (Boolean or "true"/"false"); not supported by this port. */
    DATA_MATRIX_COMPACT,

    /** Minimum Data Matrix size ([Dimension]); deprecated in zxing and not supported by this port. */
    MIN_SIZE,

    /** Maximum Data Matrix size ([Dimension]); deprecated in zxing and not supported by this port. */
    MAX_SIZE,

    /** Margin in pixels/modules, meaning varies by format (Int or String). */
    MARGIN,

    /** PDF417 compact mode (Boolean or "true"/"false"). */
    PDF417_COMPACT,

    /** PDF417 compaction mode (Compaction or its String name). */
    PDF417_COMPACTION,

    /** PDF417 min/max rows and columns (Dimensions). */
    PDF417_DIMENSIONS,

    /** PDF417 automatic ECI insertion (Boolean or String); not supported by this port. */
    PDF417_AUTO_ECI,

    /** Required number of Aztec layers; negative means compact, 0 means minimum (Int or String). */
    AZTEC_LAYERS,

    /** Exact QR version (Int or String); not supported by this port. */
    QR_VERSION,

    /** QR mask pattern 0..7 (Int or String). */
    QR_MASK_PATTERN,

    /** QR compact mode (Boolean or String); not supported by this port. */
    QR_COMPACT,

    /** Encode data to the GS1 standard (Boolean or String); not supported by this port's writers. */
    GS1_FORMAT,

    /** Forces a Code 128 code set: "A", "B" or "C" (String). */
    FORCE_CODE_SET,

    /** Forces Data Matrix C40 encoding (Boolean or String); not supported by this port. */
    FORCE_C40,

    /** Code 128 compact mode (Boolean or "true"/"false"). */
    CODE128_COMPACT,
}
