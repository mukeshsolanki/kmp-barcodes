/*
 * Copyright 2009 ZXing authors
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

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Reader
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder.PDF417ScanningDecoder
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.detector.Detector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detects and decodes a PDF417 code in an image. zxing's multiple-barcode entry point is not ported.
 */
internal class PDF417Reader : Reader {

    /** Locates and decodes a PDF417 code; throws NotFoundException, FormatException or ChecksumException. */
    override fun decode(image: BinaryBitmap): Result = decode(image, null)

    /** Locates and decodes a PDF417 code using [hints]. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        val result = decode(image, hints, false)
        if (result.isEmpty()) {
            throw NotFoundException.notFoundInstance
        }
        return result[0]
    }

    /** Nothing needs to be reset. */
    override fun reset() {
    }

    /** Holds the stateless decode helpers. */
    private companion object {
        /** Detects then decodes every symbol found (at most one unless [multiple]). */
        fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?, multiple: Boolean): List<Result> {
            val results = ArrayList<Result>()
            val detectorResult = Detector.detect(image, hints, multiple)
            for (points in detectorResult.points) {
                val decoderResult = PDF417ScanningDecoder.decode(
                    detectorResult.bits,
                    points[4],
                    points[5],
                    points[6],
                    points[7],
                    getMinCodewordWidth(points),
                    getMaxCodewordWidth(points),
                )
                val result = Result(decoderResult.text, decoderResult.rawBytes, points, BarcodeFormat.PDF_417)
                result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, decoderResult.ecLevel)
                result.putMetadata(ResultMetadataType.ERRORS_CORRECTED, decoderResult.errorsCorrected)
                result.putMetadata(ResultMetadataType.ERASURES_CORRECTED, decoderResult.erasures)
                val pdf417ResultMetadata = decoderResult.other as PDF417ResultMetadata?
                if (pdf417ResultMetadata != null) {
                    result.putMetadata(ResultMetadataType.PDF417_EXTRA_METADATA, pdf417ResultMetadata)
                }
                result.putMetadata(ResultMetadataType.ORIENTATION, detectorResult.rotation)
                result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]L" + decoderResult.symbologyModifier)
                results.add(result)
            }
            return results
        }

        /** Horizontal distance between two points, or 0 when either is missing. */
        fun getMaxWidth(p1: ResultPoint?, p2: ResultPoint?): Int {
            if (p1 == null || p2 == null) {
                return 0
            }
            return abs(p1.x - p2.x).toInt()
        }

        /** Horizontal distance between two points, or Int.MAX_VALUE when either is missing. */
        fun getMinWidth(p1: ResultPoint?, p2: ResultPoint?): Int {
            if (p1 == null || p2 == null) {
                return Int.MAX_VALUE
            }
            return abs(p1.x - p2.x).toInt()
        }

        /** Largest codeword width estimate from the start/stop pattern corners. */
        fun getMaxCodewordWidth(p: Array<ResultPoint?>): Int = max(
            max(
                getMaxWidth(p[0], p[4]),
                getMaxWidth(p[6], p[2]) * PDF417Common.MODULES_IN_CODEWORD / PDF417Common.MODULES_IN_STOP_PATTERN,
            ),
            max(
                getMaxWidth(p[1], p[5]),
                getMaxWidth(p[7], p[3]) * PDF417Common.MODULES_IN_CODEWORD / PDF417Common.MODULES_IN_STOP_PATTERN,
            ),
        )

        /** Smallest codeword width estimate from the start/stop pattern corners. */
        fun getMinCodewordWidth(p: Array<ResultPoint?>): Int = min(
            min(
                getMinWidth(p[0], p[4]),
                getMinWidth(p[6], p[2]) * PDF417Common.MODULES_IN_CODEWORD / PDF417Common.MODULES_IN_STOP_PATTERN,
            ),
            min(
                getMinWidth(p[1], p[5]),
                getMinWidth(p[7], p[3]) * PDF417Common.MODULES_IN_CODEWORD / PDF417Common.MODULES_IN_STOP_PATTERN,
            ),
        )
    }
}
