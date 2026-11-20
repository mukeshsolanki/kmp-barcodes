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

package com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon

/**
 * Reed-Solomon encoder over a [GenericGF].
 */
internal class ReedSolomonEncoder(
    /** Field to encode over. */
    private val field: GenericGF,
) {

    /** Generator polynomials by degree, built lazily. */
    private val cachedGenerators: MutableList<GenericGFPoly> = mutableListOf(GenericGFPoly(field, intArrayOf(1)))

    /** Generator polynomial of the given [degree], extending the cache as needed. */
    private fun buildGenerator(degree: Int): GenericGFPoly {
        if (degree >= cachedGenerators.size) {
            var lastGenerator = cachedGenerators[cachedGenerators.size - 1]
            for (d in cachedGenerators.size..degree) {
                val nextGenerator = lastGenerator.multiply(
                    GenericGFPoly(field, intArrayOf(1, field.exp(d - 1 + field.generatorBase))),
                )
                cachedGenerators.add(nextGenerator)
                lastGenerator = nextGenerator
            }
        }
        return cachedGenerators[degree]
    }

    /** Writes [ecBytes] error-correction codewords into the tail of [toEncode], after the data codewords. */
    fun encode(toEncode: IntArray, ecBytes: Int) {
        if (ecBytes == 0) {
            throw IllegalArgumentException("No error correction bytes")
        }
        val dataBytes = toEncode.size - ecBytes
        if (dataBytes <= 0) {
            throw IllegalArgumentException("No data bytes provided")
        }
        val generator = buildGenerator(ecBytes)
        val infoCoefficients = IntArray(dataBytes)
        toEncode.copyInto(infoCoefficients, 0, 0, dataBytes)
        var info = GenericGFPoly(field, infoCoefficients)
        info = info.multiplyByMonomial(ecBytes, 1)
        val remainder = info.divide(generator)[1]
        val coefficients = remainder.coefficients
        val numZeroCoefficients = ecBytes - coefficients.size
        for (i in 0 until numZeroCoefficients) {
            toEncode[dataBytes + i] = 0
        }
        coefficients.copyInto(toEncode, dataBytes + numZeroCoefficients, 0, coefficients.size)
    }
}
