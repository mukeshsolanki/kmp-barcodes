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

import com.mukeshsolanki.barcodes.internal.zxing.aztec.AztecReader
import com.mukeshsolanki.barcodes.internal.zxing.datamatrix.DataMatrixReader
import com.mukeshsolanki.barcodes.internal.zxing.oned.MultiFormatOneDReader
import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Reader
import com.mukeshsolanki.barcodes.internal.zxing.qrcode.QRCodeReader

/** Main decoding entry point: tries every supported reader, or the subset named by `POSSIBLE_FORMATS`. MaxiCode is not ported. */
internal class MultiFormatReader : Reader {

    /** Hints used by the configured readers. */
    private var hints: Map<DecodeHintType, Any?>? = null

    /** Readers configured by [setHints], or null before the first call. */
    private var readers: Array<Reader>? = null

    /** Decodes [image] with no hints, resetting any previous state. */
    override fun decode(image: BinaryBitmap): Result {
        setHints(null)
        return decodeInternal(image)
    }

    /** Decodes [image] with [hints], replacing any previous state. */
    override fun decode(image: BinaryBitmap, hints: Map<DecodeHintType, Any?>?): Result {
        setHints(hints)
        return decodeInternal(image)
    }

    /** Decodes [image] reusing the readers from the last [setHints] call; much faster for continuous scanning. */
    fun decodeWithState(image: BinaryBitmap): Result {
        if (readers == null) {
            setHints(null)
        }
        return decodeInternal(image)
    }

    /** Configures the reader list from [hints] for subsequent decodes. */
    fun setHints(hints: Map<DecodeHintType, Any?>?) {
        this.hints = hints
        val tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER)
        val formats = hints?.get(DecodeHintType.POSSIBLE_FORMATS) as Collection<*>?
        val readers = ArrayList<Reader>()
        if (formats != null) {
            addReadersFor(formats, tryHarder, hints, readers)
        }
        if (readers.isEmpty()) {
            addAllReaders(tryHarder, hints, readers)
        }
        this.readers = readers.toTypedArray()
    }

    /** Resets every configured reader. */
    override fun reset() {
        readers?.forEach { it.reset() }
    }

    /** Adds the readers for [formats], with 1D first unless [tryHarder] puts it last. */
    private fun addReadersFor(
        formats: Collection<*>,
        tryHarder: Boolean,
        hints: Map<DecodeHintType, Any?>?,
        readers: MutableList<Reader>,
    ) {
        val addOneDReader = ONE_D_FORMATS.any { it in formats }
        if (addOneDReader && !tryHarder) {
            readers.add(MultiFormatOneDReader(hints))
        }
        if (BarcodeFormat.QR_CODE in formats) {
            readers.add(QRCodeReader())
        }
        if (BarcodeFormat.DATA_MATRIX in formats) {
            readers.add(DataMatrixReader())
        }
        if (BarcodeFormat.AZTEC in formats) {
            readers.add(AztecReader())
        }
        if (BarcodeFormat.PDF_417 in formats) {
            readers.add(PDF417Reader())
        }
        if (addOneDReader && tryHarder) {
            readers.add(MultiFormatOneDReader(hints))
        }
    }

    /** Adds every reader, with 1D first unless [tryHarder] puts it last. */
    private fun addAllReaders(tryHarder: Boolean, hints: Map<DecodeHintType, Any?>?, readers: MutableList<Reader>) {
        if (!tryHarder) {
            readers.add(MultiFormatOneDReader(hints))
        }
        readers.add(QRCodeReader())
        readers.add(DataMatrixReader())
        readers.add(AztecReader())
        readers.add(PDF417Reader())
        if (tryHarder) {
            readers.add(MultiFormatOneDReader(hints))
        }
    }

    /** Tries each reader in order, then again on the inverted image when `ALSO_INVERTED` is set. */
    private fun decodeInternal(image: BinaryBitmap): Result {
        val readers = this.readers ?: throw NotFoundException.notFoundInstance
        firstResult(readers, image)?.let { return it }
        if (hints?.containsKey(DecodeHintType.ALSO_INVERTED) == true) {
            image.blackMatrix.flip()
            firstResult(readers, image)?.let { return it }
        }
        throw NotFoundException.notFoundInstance
    }

    /** Returns the first successful decode of [image] by [readers], or null when all fail. */
    private fun firstResult(readers: Array<Reader>, image: BinaryBitmap): Result? {
        for (reader in readers) {
            try {
                return reader.decode(image, hints)
            } catch (e: ReaderException) {
                continue
            }
        }
        return null
    }

    /** Shared constants. */
    private companion object {
        /** Formats handled by [MultiFormatOneDReader]. */
        val ONE_D_FORMATS: List<BarcodeFormat> = listOf(
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.CODABAR,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.ITF,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
        )
    }
}
