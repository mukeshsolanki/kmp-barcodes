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

/** Thrown when a barcode was detected and decoded but its checksum failed. */
internal class ChecksumException private constructor() : ReaderException() {
    /** Holder of the shared instance. */
    companion object {
        /** The shared, stateless instance (zxing `getChecksumInstance()`). */
        val checksumInstance: ChecksumException = ChecksumException()

        /** Returns the shared instance; [cause] is dropped, as zxing does outside its test harness. */
        @Suppress("UNUSED_PARAMETER")
        fun getChecksumInstance(cause: Throwable?): ChecksumException = checksumInstance
    }
}
