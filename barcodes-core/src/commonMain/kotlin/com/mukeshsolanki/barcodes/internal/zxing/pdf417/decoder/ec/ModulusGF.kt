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

import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Common

/**
 * A field based on powers of a generator integer, modulo some modulus.
 */
internal class ModulusGF private constructor(
    /** The field modulus. */
    private val modulus: Int,
    generator: Int,
) {

    /** Powers of the generator, indexed by exponent. */
    private val expTable: IntArray = IntArray(modulus)

    /** Discrete logarithms, indexed by field element. */
    private val logTable: IntArray = IntArray(modulus)

    /** The constant polynomial 0. */
    val zero: ModulusPoly

    /** The constant polynomial 1. */
    val one: ModulusPoly

    init {
        var x = 1
        for (i in 0 until modulus) {
            expTable[i] = x
            x = (x * generator) % modulus
        }
        for (i in 0 until modulus - 1) {
            logTable[expTable[i]] = i
        }
        zero = ModulusPoly(this, intArrayOf(0))
        one = ModulusPoly(this, intArrayOf(1))
    }

    /** Builds the monomial `coefficient * x^degree`. */
    fun buildMonomial(degree: Int, coefficient: Int): ModulusPoly {
        if (degree < 0) {
            throw IllegalArgumentException()
        }
        if (coefficient == 0) {
            return zero
        }
        val coefficients = IntArray(degree + 1)
        coefficients[0] = coefficient
        return ModulusPoly(this, coefficients)
    }

    /** Adds two field elements. */
    fun add(a: Int, b: Int): Int = (a + b) % modulus

    /** Subtracts two field elements. */
    fun subtract(a: Int, b: Int): Int = (modulus + a - b) % modulus

    /** Returns the generator raised to [a]. */
    fun exp(a: Int): Int = expTable[a]

    /** Returns the discrete logarithm of [a]; [a] must be non-zero. */
    fun log(a: Int): Int {
        if (a == 0) {
            throw IllegalArgumentException()
        }
        return logTable[a]
    }

    /** Returns the multiplicative inverse of [a]; [a] must be non-zero. */
    fun inverse(a: Int): Int {
        if (a == 0) {
            throw ArithmeticException()
        }
        return expTable[modulus - logTable[a] - 1]
    }

    /** Multiplies two field elements. */
    fun multiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) {
            return 0
        }
        return expTable[(logTable[a] + logTable[b]) % (modulus - 1)]
    }

    /** The number of elements in the field. */
    val size: Int get() = modulus

    /** Holds the shared PDF417 field. */
    companion object {
        /** The field GF(929) with generator 3 used by PDF417. */
        val PDF417_GF: ModulusGF = ModulusGF(PDF417Common.NUMBER_OF_CODEWORDS, 3)
    }
}
