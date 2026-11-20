/*
 * Copyright 2006 Jeremias Maerki
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

package com.mukeshsolanki.barcodes.internal.zxing.datamatrix.encoder

/** The 144x144 symbol, whose last two interleaved blocks hold one data codeword fewer. */
internal class DataMatrixSymbolInfo144 : SymbolInfo(false, 1558, 620, 22, 22, 36, -1, 62) {

    /** The 144x144 symbol always uses 10 interleaved blocks. */
    override val interleavedBlockCount: Int
        get() = 10

    /** Blocks 1 to 8 carry 156 data codewords, the rest 155. */
    override fun getDataLengthForInterleavedBlock(index: Int): Int = if (index <= 8) 156 else 155
}
