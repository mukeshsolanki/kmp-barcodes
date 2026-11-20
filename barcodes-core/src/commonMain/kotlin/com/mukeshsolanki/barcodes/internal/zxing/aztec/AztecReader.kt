/*
 * Copyright 2010 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.aztec

import com.mukeshsolanki.barcodes.internal.zxing.BarcodeFormat
import com.mukeshsolanki.barcodes.internal.zxing.BinaryBitmap
import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.FormatException
import com.mukeshsolanki.barcodes.internal.zxing.NotFoundException
import com.mukeshsolanki.barcodes.internal.zxing.Reader
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.Result
import com.mukeshsolanki.barcodes.internal.zxing.ResultMetadataType
import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.aztec.decoder.Decoder
import com.mukeshsolanki.barcodes.internal.zxing.aztec.detector.Detector
import com.mukeshsolanki.barcodes.internal.zxing.common.DecoderResult

/**
 * Detects and decodes Aztec codes in an image.
 */
internal class AztecReader : Reader {

    /** One successful detect-and-decode pass. */
    private class Attempt(
        /** Corner points of the symbol. */
        val points: Array<ResultPoint>,
        /** The decoded payload. */
        val decoderResult: DecoderResult,
        /** Errors corrected while reading the mode message. */
        val errorsCorrected: Int,
    )

    /** Locates and decodes an Aztec code in [image]. */
    override fun decode(image: BinaryBitmap): Result = decode(image, null)

    /** Locates and decodes an Aztec code, trying the mirror image when the normal one fails. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        val detector = Detector(image.blackMatrix)
        var firstFailure: ReaderException? = null
        val first = try {
            detectAndDecode(detector, false)
        } catch (e: NotFoundException) {
            firstFailure = e
            null
        } catch (e: FormatException) {
            firstFailure = e
            null
        }
        val attempt = first ?: try {
            detectAndDecode(detector, true)
        } catch (e: NotFoundException) {
            throw firstFailure ?: e
        } catch (e: FormatException) {
            throw firstFailure ?: e
        }

        val rpcb = hints?.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK) as ResultPointCallback?
        if (rpcb != null) {
            for (point in attempt.points) {
                rpcb.foundPossibleResultPoint(point)
            }
        }

        val decoderResult = attempt.decoderResult
        val result = Result(
            decoderResult.text,
            decoderResult.rawBytes,
            decoderResult.numBits,
            attempt.points,
            BarcodeFormat.AZTEC,
        )
        val byteSegments = decoderResult.byteSegments
        if (byteSegments != null) {
            result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments)
        }
        val ecLevel = decoderResult.ecLevel
        if (ecLevel != null) {
            result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel)
        }
        val errorsCorrected = attempt.errorsCorrected + (decoderResult.errorsCorrected ?: 0)
        result.putMetadata(ResultMetadataType.ERRORS_CORRECTED, errorsCorrected)
        result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]z" + decoderResult.symbologyModifier)
        return result
    }

    /** Runs one detection (optionally mirrored) and decodes it. */
    private fun detectAndDecode(detector: Detector, isMirror: Boolean): Attempt {
        val detectorResult = detector.detect(isMirror)
        val decoderResult = Decoder().decode(detectorResult)
        return Attempt(detectorResult.points, decoderResult, detectorResult.errorsCorrected)
    }

    /** Nothing to reset. */
    override fun reset(): Unit = Unit
}
