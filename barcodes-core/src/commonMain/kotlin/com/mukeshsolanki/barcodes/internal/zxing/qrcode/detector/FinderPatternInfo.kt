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

package com.mukeshsolanki.barcodes.internal.zxing.qrcode.detector

/**
 * The three located finder patterns, ordered bottom-left, top-left, top-right.
 */
internal class FinderPatternInfo(patternCenters: Array<FinderPattern>) {

    /** The bottom-left finder pattern. */
    val bottomLeft: FinderPattern = patternCenters[0]

    /** The top-left finder pattern. */
    val topLeft: FinderPattern = patternCenters[1]

    /** The top-right finder pattern. */
    val topRight: FinderPattern = patternCenters[2]
}
