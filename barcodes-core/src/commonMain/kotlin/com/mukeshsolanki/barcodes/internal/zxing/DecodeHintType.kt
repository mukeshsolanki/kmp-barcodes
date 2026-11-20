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

/** Hints a caller may pass to a barcode reader to help it decode more quickly or accurately. */
internal enum class DecodeHintType {
    /** Unspecified, application-specific hint. */
    OTHER,

    /** Image is a pure monochrome image of a barcode; the value does not matter. */
    PURE_BARCODE,

    /** Image is known to be one of a few formats; maps to a collection of [BarcodeFormat]. */
    POSSIBLE_FORMATS,

    /** Spend more time to find a barcode, optimising for accuracy over speed; the value does not matter. */
    TRY_HARDER,

    /** Character encoding to use when decoding, where applicable (String). */
    CHARACTER_SET,

    /** Allowed lengths of encoded data; maps to an [IntArray]. */
    ALLOWED_LENGTHS,

    /** Assume Code 39 codes employ a check digit; the value does not matter. */
    ASSUME_CODE_39_CHECK_DIGIT,

    /** Assume the barcode is GS1 (affects FNC1 handling for Code 128); the value does not matter. */
    ASSUME_GS1,

    /** Return Codabar start/end characters instead of stripping them; the value does not matter. */
    RETURN_CODABAR_START_END,

    /** Callback notified when a possible [ResultPoint] is found; maps to a [ResultPointCallback]. */
    NEED_RESULT_POINT_CALLBACK,

    /** Allowed EAN/UPC extension lengths; maps to an [IntArray] such as [2], [5] or [2, 5]. */
    ALLOWED_EAN_EXTENSIONS,

    /** Also try decoding the inverted image; the value does not matter. */
    ALSO_INVERTED,
}
