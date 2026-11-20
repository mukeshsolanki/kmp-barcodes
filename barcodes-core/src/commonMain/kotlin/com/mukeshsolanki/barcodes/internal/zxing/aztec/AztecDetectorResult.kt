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

import com.mukeshsolanki.barcodes.internal.zxing.ResultPoint
import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix
import com.mukeshsolanki.barcodes.internal.zxing.common.DetectorResult

/**
 * Extends [DetectorResult] with Aztec-specific information such as the layer count and whether the
 * symbol is compact.
 */
internal class AztecDetectorResult(
    bits: BitMatrix,
    points: Array<ResultPoint>,
    /** Whether the symbol is compact. */
    val isCompact: Boolean,
    /** Number of data codewords. */
    val nbDatablocks: Int,
    /** Number of data layers. */
    val nbLayers: Int,
    /** Errors corrected while reading the mode message. */
    val errorsCorrected: Int = 0,
) : DetectorResult(bits, points)
