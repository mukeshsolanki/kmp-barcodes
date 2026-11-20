/*
 * Copyright 2013 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.aztec.encoder

import com.mukeshsolanki.barcodes.internal.zxing.common.BitMatrix

/**
 * Aztec 2D code representation.
 */
internal class AztecCode {

    /** `true` if compact instead of full mode. */
    var isCompact: Boolean = false

    /** Size in pixels (width and height). */
    var size: Int = 0

    /** Number of layers. */
    var layers: Int = 0

    /** Number of data codewords. */
    var codeWords: Int = 0

    /** The symbol image. */
    var matrix: BitMatrix? = null
}
