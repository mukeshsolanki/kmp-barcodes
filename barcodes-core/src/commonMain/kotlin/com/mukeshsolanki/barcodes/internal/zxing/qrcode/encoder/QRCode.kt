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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.encoder

import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Mode
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.decoder.Version

/**
 * An encoded QR Code: mode, EC level, version, mask and module matrix.
 */
internal class QRCode {
    /** The data mode used. */
    var mode: Mode? = null

    /** The error correction level. */
    var ecLevel: ErrorCorrectionLevel? = null

    /** The symbol version. */
    var version: Version? = null

    /** The mask pattern 0..7, or -1 when unset. */
    var maskPattern: Int = -1

    /** The final module matrix without quiet zone. */
    var matrix: ByteMatrix? = null

    /** Debug rendering of all fields. */
    override fun toString(): String {
        val result = StringBuilder(200)
        result.append("<<\n")
        result.append(" mode: ")
        result.append(mode)
        result.append("\n ecLevel: ")
        result.append(ecLevel)
        result.append("\n version: ")
        result.append(version)
        result.append("\n maskPattern: ")
        result.append(maskPattern)
        val matrix = matrix
        if (matrix == null) {
            result.append("\n matrix: null\n")
        } else {
            result.append("\n matrix:\n")
            result.append(matrix)
        }
        result.append(">>\n")
        return result.toString()
    }

    /** Mask pattern constants. */
    companion object {
        /** Number of QR mask patterns. */
        const val NUM_MASK_PATTERNS = 8

        /** True when [maskPattern] is in 0 until [NUM_MASK_PATTERNS]. */
        fun isValidMaskPattern(maskPattern: Int): Boolean = maskPattern >= 0 && maskPattern < NUM_MASK_PATTERNS
    }
}
