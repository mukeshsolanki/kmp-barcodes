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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.detector

import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Output of the PDF417 [Detector]: the (possibly rotated) bit matrix and the corner points per symbol.
 */
internal class PDF417DetectorResult(
    /** The bit matrix the points refer to. */
    val bits: BitMatrix,
    /** Eight vertices per detected symbol; entries may be null. */
    val points: List<Array<ResultPoint?>>,
    /** Rotation in degrees applied to the original matrix. */
    val rotation: Int = 0,
)
