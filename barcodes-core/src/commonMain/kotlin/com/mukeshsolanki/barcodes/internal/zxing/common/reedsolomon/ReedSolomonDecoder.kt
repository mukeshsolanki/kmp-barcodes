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

package com.mukeshsolanki.barcodes.internal.zxing.common.reedsolomon

/**
 * Reed-Solomon decoder over a [GenericGF], using the Euclidean algorithm, Chien search and Forney's formula.
 */
internal class ReedSolomonDecoder(
    /** Field to decode over. */
    private val field: GenericGF,
) {

    /** Corrects [received] (data + [twoS] EC codewords) in place; throws [ReedSolomonException] on failure. */
    fun decode(received: IntArray, twoS: Int) {
        decodeWithECCount(received, twoS)
    }

    /** Corrects [received] in place and returns the number of errors corrected. */
    fun decodeWithECCount(received: IntArray, twoS: Int): Int {
        val poly = GenericGFPoly(field, received)
        val syndromeCoefficients = IntArray(twoS)
        var noError = true
        for (i in 0 until twoS) {
            val eval = poly.evaluateAt(field.exp(i + field.generatorBase))
            syndromeCoefficients[syndromeCoefficients.size - 1 - i] = eval
            if (eval != 0) {
                noError = false
            }
        }
        if (noError) {
            return 0
        }
        val syndrome = GenericGFPoly(field, syndromeCoefficients)
        val sigmaOmega = runEuclideanAlgorithm(field.buildMonomial(twoS, 1), syndrome, twoS)
        val sigma = sigmaOmega[0]
        val omega = sigmaOmega[1]
        val errorLocations = findErrorLocations(sigma)
        val errorMagnitudes = findErrorMagnitudes(omega, errorLocations)
        for (i in errorLocations.indices) {
            val position = received.size - 1 - field.log(errorLocations[i])
            if (position < 0) {
                throw ReedSolomonException("Bad error location")
            }
            received[position] = GenericGF.addOrSubtract(received[position], errorMagnitudes[i])
        }
        return errorLocations.size
    }

    /** Runs the extended Euclidean algorithm and returns the error locator and evaluator polynomials. */
    private fun runEuclideanAlgorithm(first: GenericGFPoly, second: GenericGFPoly, R: Int): Array<GenericGFPoly> {
        val a = if (first.degree < second.degree) second else first
        val b = if (first.degree < second.degree) first else second

        var rLast = a
        var r = b
        var tLast = field.zero
        var t = field.one

        while (2 * r.degree >= R) {
            val rLastLast = rLast
            val tLastLast = tLast
            rLast = r
            tLast = t

            if (rLast.isZero) {
                throw ReedSolomonException("r_{i-1} was zero")
            }
            r = rLastLast
            var q = field.zero
            val denominatorLeadingTerm = rLast.getCoefficient(rLast.degree)
            val dltInverse = field.inverse(denominatorLeadingTerm)
            while (r.degree >= rLast.degree && !r.isZero) {
                val degreeDiff = r.degree - rLast.degree
                val scale = field.multiply(r.getCoefficient(r.degree), dltInverse)
                q = q.addOrSubtract(field.buildMonomial(degreeDiff, scale))
                r = r.addOrSubtract(rLast.multiplyByMonomial(degreeDiff, scale))
            }

            t = q.multiply(tLast).addOrSubtract(tLastLast)

            if (r.degree >= rLast.degree) {
                throw IllegalStateException("Division algorithm failed to reduce polynomial")
            }
        }

        val sigmaTildeAtZero = t.getCoefficient(0)
        if (sigmaTildeAtZero == 0) {
            throw ReedSolomonException("sigmaTilde(0) was zero")
        }

        val inverse = field.inverse(sigmaTildeAtZero)
        val sigma = t.multiply(inverse)
        val omega = r.multiply(inverse)
        return arrayOf(sigma, omega)
    }

    /** Finds the error locations as roots of [errorLocator] by Chien search. */
    private fun findErrorLocations(errorLocator: GenericGFPoly): IntArray {
        val numErrors = errorLocator.degree
        if (numErrors == 1) {
            return intArrayOf(errorLocator.getCoefficient(1))
        }
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
            throw ReedSolomonException("Error locator degree does not match number of roots")
        }
        return result
    }

    /** Computes the error magnitudes at [errorLocations] with Forney's formula. */
    private fun findErrorMagnitudes(errorEvaluator: GenericGFPoly, errorLocations: IntArray): IntArray {
        val s = errorLocations.size
        val result = IntArray(s)
        for (i in 0 until s) {
            val xiInverse = field.inverse(errorLocations[i])
            var denominator = 1
            for (j in 0 until s) {
                if (i != j) {
                    val term = field.multiply(errorLocations[j], xiInverse)
                    val termPlus1 = if ((term and 0x1) == 0) term or 1 else term and 1.inv()
                    denominator = field.multiply(denominator, termPlus1)
                }
            }
            result[i] = field.multiply(errorEvaluator.evaluateAt(xiInverse), field.inverse(denominator))
            if (field.generatorBase != 0) {
                result[i] = field.multiply(result[i], xiInverse)
            }
        }
        return result
    }
}
