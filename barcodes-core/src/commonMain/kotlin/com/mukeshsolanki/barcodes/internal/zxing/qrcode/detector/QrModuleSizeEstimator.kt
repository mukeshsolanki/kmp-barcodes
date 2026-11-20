package com.mukeshsolanki.barcodes.internal.zxing.qrcode.detector

import com.mukeshsolanki.barcodes.internal.zxing.DecodeHintType
import com.mukeshsolanki.barcodes.internal.zxing.ReaderException
import com.mukeshsolanki.barcodes.internal.zxing.ResultPointCallback
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Estimates the QR module size in pixels from the finder patterns alone, for camera auto-zoom.
 */
internal object QrModuleSizeEstimator {

    /** Returns the detector's module-size estimate when three finder patterns are found, else null; never throws a ReaderException. */
    fun estimate(image: BitMatrix, hints: Map<DecodeHintType, Any?>?): Float? = try {
        val callback = hints?.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK) as? ResultPointCallback
        val info = FinderPatternFinder(image, callback).find(hints)
        Detector(image).calculateModuleSize(info.topLeft, info.topRight, info.bottomLeft)
    } catch (e: ReaderException) {
        null
    }
}
