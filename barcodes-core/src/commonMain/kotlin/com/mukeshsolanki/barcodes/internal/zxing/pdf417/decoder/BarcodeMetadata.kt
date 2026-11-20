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
 */
// Ported to Kotlin from zxing 3.5.3 for kmp-barcodes.
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

/**
 * Symbol-level metadata read from the row indicator columns.
 */
internal class BarcodeMetadata(
    /** Number of data columns. */
    val columnCount: Int,
    /** Row count encoded in the upper part of the indicator. */
    val rowCountUpperPart: Int,
    /** Row count encoded in the lower part of the indicator. */
    val rowCountLowerPart: Int,
    /** Error correction level (0-8). */
    val errorCorrectionLevel: Int,
) {
    /** Total number of rows. */
    val rowCount: Int = rowCountUpperPart + rowCountLowerPart
}
