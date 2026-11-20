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
 * Immutable polynomial whose coefficients are elements of a [GenericGF], highest power first.
 */
internal class GenericGFPoly(
    /** Field the coefficients belong to. */
    private val field: GenericGF,
    coefficients: IntArray,
) {

    /** Coefficients from highest to lowest power, with leading zeros stripped. */
    val coefficients: IntArray = stripLeadingZeros(coefficients)

    /** Degree of this polynomial. */
    val degree: Int
        get() = coefficients.size - 1

    /** True iff this is the zero polynomial. */
    val isZero: Boolean
        get() = coefficients[0] == 0

    /** Coefficient of the x^[degree] term. */
    fun getCoefficient(degree: Int): Int = coefficients[coefficients.size - 1 - degree]

    /** Evaluates this polynomial at [a]. */
    fun evaluateAt(a: Int): Int {
        if (a == 0) {
            return getCoefficient(0)
        }
        if (a == 1) {
            var result = 0
            for (coefficient in coefficients) {
                result = GenericGF.addOrSubtract(result, coefficient)
            }
            return result
        }
        var result = coefficients[0]
        val size = coefficients.size
        for (i in 1 until size) {
            result = GenericGF.addOrSubtract(field.multiply(a, result), coefficients[i])
        }
        return result
    }

    /** Sum (equivalently difference) of this and [other]. */
    fun addOrSubtract(other: GenericGFPoly): GenericGFPoly {
        requireSameField(other)
        if (isZero) {
            return other
        }
        if (other.isZero) {
            return this
        }
        var smallerCoefficients = this.coefficients
        var largerCoefficients = other.coefficients
        if (smallerCoefficients.size > largerCoefficients.size) {
            val temp = smallerCoefficients
            smallerCoefficients = largerCoefficients
            largerCoefficients = temp
        }
        val sumDiff = IntArray(largerCoefficients.size)
        val lengthDiff = largerCoefficients.size - smallerCoefficients.size
        largerCoefficients.copyInto(sumDiff, 0, 0, lengthDiff)
        for (i in lengthDiff until largerCoefficients.size) {
            sumDiff[i] = GenericGF.addOrSubtract(smallerCoefficients[i - lengthDiff], largerCoefficients[i])
        }
        return GenericGFPoly(field, sumDiff)
    }

    /** Product of this and [other]. */
    fun multiply(other: GenericGFPoly): GenericGFPoly {
        requireSameField(other)
        if (isZero || other.isZero) {
            return field.zero
        }
        val aCoefficients = this.coefficients
        val aLength = aCoefficients.size
        val bCoefficients = other.coefficients
        val bLength = bCoefficients.size
        val product = IntArray(aLength + bLength - 1)
        for (i in 0 until aLength) {
            val aCoeff = aCoefficients[i]
            for (j in 0 until bLength) {
                product[i + j] = GenericGF.addOrSubtract(product[i + j], field.multiply(aCoeff, bCoefficients[j]))
            }
        }
        return GenericGFPoly(field, product)
    }

    /** Product of this and the field element [scalar]. */
    fun multiply(scalar: Int): GenericGFPoly {
        if (scalar == 0) {
            return field.zero
        }
        if (scalar == 1) {
            return this
        }
        val size = coefficients.size
        val product = IntArray(size)
        for (i in 0 until size) {
            product[i] = field.multiply(coefficients[i], scalar)
        }
        return GenericGFPoly(field, product)
    }

    /** Product of this and coefficient * x^degree. */
    fun multiplyByMonomial(degree: Int, coefficient: Int): GenericGFPoly {
        if (degree < 0) {
            throw IllegalArgumentException()
        }
        if (coefficient == 0) {
            return field.zero
        }
        val size = coefficients.size
        val product = IntArray(size + degree)
        for (i in 0 until size) {
            product[i] = field.multiply(coefficients[i], coefficient)
        }
        return GenericGFPoly(field, product)
    }

    /** Quotient and remainder of dividing this by [other]. */
    fun divide(other: GenericGFPoly): Array<GenericGFPoly> {
        requireSameField(other)
        if (other.isZero) {
            throw IllegalArgumentException("Divide by 0")
        }
        var quotient = field.zero
        var remainder = this
        val denominatorLeadingTerm = other.getCoefficient(other.degree)
        val inverseDenominatorLeadingTerm = field.inverse(denominatorLeadingTerm)
        while (remainder.degree >= other.degree && !remainder.isZero) {
            val degreeDifference = remainder.degree - other.degree
            val scale = field.multiply(remainder.getCoefficient(remainder.degree), inverseDenominatorLeadingTerm)
            val term = other.multiplyByMonomial(degreeDifference, scale)
            val iterationQuotient = field.buildMonomial(degreeDifference, scale)
            quotient = quotient.addOrSubtract(iterationQuotient)
            remainder = remainder.addOrSubtract(term)
        }
        return arrayOf(quotient, remainder)
    }

    /** Throws when [other] belongs to a different field. */
    private fun requireSameField(other: GenericGFPoly) {
        if (field != other.field) {
            throw IllegalArgumentException("GenericGFPolys do not have same GenericGF field")
        }
    }

    /** Renders the polynomial in terms of powers of the generator `a`. */
    override fun toString(): String {
        if (isZero) {
            return "0"
        }
        val result = StringBuilder(8 * degree)
        for (power in degree downTo 0) {
            var coefficient = getCoefficient(power)
            if (coefficient != 0) {
                if (coefficient < 0) {
                    if (power == degree) {
                        result.append("-")
                    } else {
                        result.append(" - ")
                    }
                    coefficient = -coefficient
                } else {
                    if (result.isNotEmpty()) {
                        result.append(" + ")
                    }
                }
                if (power == 0 || coefficient != 1) {
                    val alphaPower = field.log(coefficient)
                    if (alphaPower == 0) {
                        result.append('1')
                    } else if (alphaPower == 1) {
                        result.append('a')
                    } else {
                        result.append("a^")
                        result.append(alphaPower)
                    }
                }
                if (power != 0) {
                    if (power == 1) {
                        result.append('x')
                    } else {
                        result.append("x^")
                        result.append(power)
                    }
                }
            }
        }
        return result.toString()
    }

    /** Coefficient normalisation. */
    private companion object {
        /** Strips leading zero coefficients, keeping `[0]` for the zero polynomial; throws if empty. */
        fun stripLeadingZeros(coefficients: IntArray): IntArray {
            if (coefficients.isEmpty()) {
                throw IllegalArgumentException()
            }
            val coefficientsLength = coefficients.size
            if (coefficientsLength <= 1 || coefficients[0] != 0) {
                return coefficients
            }
            var firstNonZero = 1
            while (firstNonZero < coefficientsLength && coefficients[firstNonZero] == 0) {
                firstNonZero++
            }
            if (firstNonZero == coefficientsLength) {
                return intArrayOf(0)
            }
            return coefficients.copyOfRange(firstNonZero, coefficientsLength)
        }
    }
}
