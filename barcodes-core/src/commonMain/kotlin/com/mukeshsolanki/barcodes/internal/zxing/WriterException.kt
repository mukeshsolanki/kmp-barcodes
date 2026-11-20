/*
 * Copyright 2008 ZXing authors
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

/** Covers the exceptions that may occur when encoding a barcode with a [Writer]. */
internal class WriterException : Exception {
    /** Creates an exception without a message. */
    constructor() : super()

    /** Creates an exception with [message]. */
    constructor(message: String?) : super(message)

    /** Creates an exception wrapping [cause]. */
    constructor(cause: Throwable?) : super(cause)
}
