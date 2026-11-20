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

/**
 * A polynomial over [ModulusGF], coefficients ordered from the highest degree term down.
 */
internal class ModulusPoly(
    /** The field the coefficients live in. */
    private val field: ModulusGF,
    coefficients: IntArray,
) {

    /** Coefficients with leading zeros stripped (except for the constant polynomial 0). */
    val coefficients: IntArray = normalize(coefficients)

    /** Degree of this polynomial. */
    val degree: Int get() = coefficients.size - 1

    /** True iff this polynomial is the monomial "0". */
    val isZero: Boolean get() = coefficients[0] == 0

    /** Returns the coefficient of the x^[degree] term. */
    fun getCoefficient(degree: Int): Int = coefficients[coefficients.size - 1 - degree]

    /** Evaluates this polynomial at [a]. */
    fun evaluateAt(a: Int): Int {
        if (a == 0) {
            return getCoefficient(0)
        }
        if (a == 1) {
            var sum = 0
            for (coefficient in coefficients) {
                sum = field.add(sum, coefficient)
            }
            return sum
        }
        var result = coefficients[0]
        val size = coefficients.size
        for (i in 1 until size) {
            result = field.add(field.multiply(a, result), coefficients[i])
        }
        return result
    }

    /** Returns `this + other`. */
    fun add(other: ModulusPoly): ModulusPoly {
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
            sumDiff[i] = field.add(smallerCoefficients[i - lengthDiff], largerCoefficients[i])
        }
        return ModulusPoly(field, sumDiff)
    }

    /** Returns `this - other`. */
    fun subtract(other: ModulusPoly): ModulusPoly {
        requireSameField(other)
        if (other.isZero) {
            return this
        }
        return add(other.negative())
    }

    /** Returns `this * other`. */
    fun multiply(other: ModulusPoly): ModulusPoly {
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
                product[i + j] = field.add(product[i + j], field.multiply(aCoeff, bCoefficients[j]))
            }
        }
        return ModulusPoly(field, product)
    }

    /** Returns `-this`. */
    fun negative(): ModulusPoly {
        val size = coefficients.size
        val negativeCoefficients = IntArray(size)
        for (i in 0 until size) {
            negativeCoefficients[i] = field.subtract(0, coefficients[i])
        }
        return ModulusPoly(field, negativeCoefficients)
    }

    /** Returns `this * scalar`. */
    fun multiply(scalar: Int): ModulusPoly {
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
        return ModulusPoly(field, product)
    }

    /** Returns `this * coefficient * x^degree`. */
    fun multiplyByMonomial(degree: Int, coefficient: Int): ModulusPoly {
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
        return ModulusPoly(field, product)
    }

    /** Throws when [other] belongs to a different field. */
    private fun requireSameField(other: ModulusPoly) {
        if (field != other.field) {
            throw IllegalArgumentException("ModulusPolys do not have same ModulusGF field")
        }
    }

    /** Renders the polynomial as a human-readable expression. */
    override fun toString(): String {
        val result = StringBuilder(8 * degree)
        for (d in degree downTo 0) {
            var coefficient = getCoefficient(d)
            if (coefficient != 0) {
                if (coefficient < 0) {
                    result.append(" - ")
                    coefficient = -coefficient
                } else if (result.isNotEmpty()) {
                    result.append(" + ")
                }
                if (d == 0 || coefficient != 1) {
                    result.append(coefficient)
                }
                appendVariable(result, d)
            }
        }
        return result.toString()
    }

    /** Holds the coefficient normalisation helpers. */
    private companion object {
        /** Strips leading zero coefficients, keeping `{0}` for the zero polynomial. */
        fun normalize(coefficients: IntArray): IntArray {
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

        /** Appends `x` or `x^degree` for a non-constant term. */
        fun appendVariable(result: StringBuilder, degree: Int) {
            if (degree == 0) {
                return
            }
            if (degree == 1) {
                result.append('x')
            } else {
                result.append("x^")
                result.append(degree)
            }
        }
    }
}
