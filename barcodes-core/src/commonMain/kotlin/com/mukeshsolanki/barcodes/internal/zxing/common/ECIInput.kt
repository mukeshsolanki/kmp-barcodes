/*
 * Copyright 2021 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.common

/**
 * Navigates a sequence of ECIs and bytes.
 */
internal interface ECIInput {

    /** Number of bytes or ECIs in the sequence. */
    fun length(): Int

    /** Byte value at [index] as a char (or FNC1); throws if it is an ECI. */
    fun charAt(index: Int): Char

    /** Subsequence from [start] inclusive to [end] exclusive; throws if it contains an ECI. */
    fun subSequence(start: Int, end: Int): CharSequence

    /** True if the value at [index] is an ECI. */
    fun isECI(index: Int): Boolean

    /** ECI value at [index]; throws if the value is not an ECI. */
    fun getECIValue(index: Int): Int

    /** True if [n] non-ECI characters are available starting at [index]. */
    fun haveNCharacters(index: Int, n: Int): Boolean
}
