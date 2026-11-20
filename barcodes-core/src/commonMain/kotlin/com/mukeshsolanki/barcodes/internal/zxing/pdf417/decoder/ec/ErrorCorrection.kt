/*
 * Copyright 2012 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder.ec

import com.mukeshsolanki.barcodes.internal.zxing.ChecksumException

/**
 * PDF417 Reed-Solomon style error correction over GF(929).
 */
internal class ErrorCorrection {

    /** The PDF417 field. */
    private val field: ModulusGF = ModulusGF.PDF417_GF

    /**
     * Corrects [received] in place and returns the number of errors corrected.
     * Throws [ChecksumException] when the errors cannot be corrected.
     */
    fun decode(received: IntArray, numECCodewords: Int, erasures: IntArray?): Int {
        val poly = ModulusPoly(field, received)
        val s = IntArray(numECCodewords)
        var error = false
        for (i in numECCodewords downTo 1) {
            val eval = poly.evaluateAt(field.exp(i))
            s[numECCodewords - i] = eval
            if (eval != 0) {
                error = true
            }
        }
        if (!error) {
            return 0
        }
        val syndrome = ModulusPoly(field, s)
        val sigmaOmega = runEuclideanAlgorithm(field.buildMonomial(numECCodewords, 1), syndrome, numECCodewords)
        val sigma = sigmaOmega[0]
        val omega = sigmaOmega[1]
        val errorLocations = findErrorLocations(sigma)
        val errorMagnitudes = findErrorMagnitudes(omega, sigma, errorLocations)
        for (i in errorLocations.indices) {
            val position = received.size - 1 - field.log(errorLocations[i])
            if (position < 0) {
                throw ChecksumException.checksumInstance
            }
            received[position] = field.subtract(received[position], errorMagnitudes[i])
        }
        return errorLocations.size
    }

    /** Runs the extended Euclidean algorithm and returns `[sigma, omega]`. */
    private fun runEuclideanAlgorithm(first: ModulusPoly, second: ModulusPoly, rDegree: Int): Array<ModulusPoly> {
        var a = first
        var b = second
        if (a.degree < b.degree) {
            val temp = a
            a = b
            b = temp
        }
        var rLast = a
        var r = b
        var tLast = field.zero
        var t = field.one
        while (r.degree >= rDegree / 2) {
            val rLastLast = rLast
            val tLastLast = tLast
            rLast = r
            tLast = t
            if (rLast.isZero) {
                throw ChecksumException.checksumInstance
            }
            r = rLastLast
            var q = field.zero
            val denominatorLeadingTerm = rLast.getCoefficient(rLast.degree)
            val dltInverse = field.inverse(denominatorLeadingTerm)
            while (r.degree >= rLast.degree && !r.isZero) {
                val degreeDiff = r.degree - rLast.degree
                val scale = field.multiply(r.getCoefficient(r.degree), dltInverse)
                q = q.add(field.buildMonomial(degreeDiff, scale))
                r = r.subtract(rLast.multiplyByMonomial(degreeDiff, scale))
            }
            t = q.multiply(tLast).subtract(tLastLast).negative()
        }
        val sigmaTildeAtZero = t.getCoefficient(0)
        if (sigmaTildeAtZero == 0) {
            throw ChecksumException.checksumInstance
        }
        val inverse = field.inverse(sigmaTildeAtZero)
        val sigma = t.multiply(inverse)
        val omega = r.multiply(inverse)
        return arrayOf(sigma, omega)
    }

    /** Finds the error locations with a Chien search. */
    private fun findErrorLocations(errorLocator: ModulusPoly): IntArray {
        val numErrors = errorLocator.degree
        val result = IntArray(numErrors)
        var e = 0
        var i = 1
        while (i < field.size && e < numErrors) {
            if (errorLocator.evaluateAt(i) == 0) {
                result[e] = field.inverse(i)
                e++
            }
            i++
        }
        if (e != numErrors) {
            throw ChecksumException.checksumInstance
        }
        return result
    }

    /** Computes the error magnitudes with Forney's formula. */
    private fun findErrorMagnitudes(
        errorEvaluator: ModulusPoly,
        errorLocator: ModulusPoly,
        errorLocations: IntArray,
    ): IntArray {
        val errorLocatorDegree = errorLocator.degree
        if (errorLocatorDegree < 1) {
            return IntArray(0)
        }
        val formalDerivativeCoefficients = IntArray(errorLocatorDegree)
        for (i in 1..errorLocatorDegree) {
            formalDerivativeCoefficients[errorLocatorDegree - i] = field.multiply(i, errorLocator.getCoefficient(i))
        }
        val formalDerivative = ModulusPoly(field, formalDerivativeCoefficients)
        val s = errorLocations.size
        val result = IntArray(s)
        for (i in 0 until s) {
            val xiInverse = field.inverse(errorLocations[i])
            val numerator = field.subtract(0, errorEvaluator.evaluateAt(xiInverse))
            val denominator = field.inverse(formalDerivative.evaluateAt(xiInverse))
            result[i] = field.multiply(numerator, denominator)
        }
        return result
    }
}
