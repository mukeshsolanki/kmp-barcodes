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
 * Arithmetic over a Galois field GF(size) defined by a primitive polynomial; size is a power of two.
 */
internal class GenericGF(
    /** Irreducible polynomial; bit i is the coefficient of x^i. */
    private val primitive: Int,
    /** Number of field elements. */
    val size: Int,
    /** Generator base b in g(x) = (x+a^b)(x+a^(b+1))...; 0 for QR, 1 otherwise. */
    val generatorBase: Int,
) {

    /** Powers of the generator: expTable[i] = 2^i. */
    private val expTable = IntArray(size)

    /** Discrete logarithms: logTable[expTable[i]] = i. */
    private val logTable = IntArray(size)

    init {
        var x = 1
        for (i in 0 until size) {
            expTable[i] = x
            x *= 2
            if (x >= size) {
                x = x xor primitive
                x = x and (size - 1)
            }
        }
        for (i in 0 until size - 1) {
            logTable[expTable[i]] = i
        }
    }

    /** The zero polynomial. */
    internal val zero: GenericGFPoly = GenericGFPoly(this, intArrayOf(0))

    /** The constant polynomial 1. */
    internal val one: GenericGFPoly = GenericGFPoly(this, intArrayOf(1))

    /** Monomial coefficient * x^degree. */
    internal fun buildMonomial(degree: Int, coefficient: Int): GenericGFPoly {
        if (degree < 0) {
            throw IllegalArgumentException()
        }
        if (coefficient == 0) {
            return zero
        }
        val coefficients = IntArray(degree + 1)
        coefficients[0] = coefficient
        return GenericGFPoly(this, coefficients)
    }

    /** 2 to the power of [a] in this field. */
    internal fun exp(a: Int): Int = expTable[a]

    /** Base-2 logarithm of [a] in this field; [a] must be non-zero. */
    internal fun log(a: Int): Int {
        if (a == 0) {
            throw IllegalArgumentException()
        }
        return logTable[a]
    }

    /** Multiplicative inverse of [a]; [a] must be non-zero. */
    internal fun inverse(a: Int): Int {
        if (a == 0) {
            throw ArithmeticException()
        }
        return expTable[size - logTable[a] - 1]
    }

    /** Product of [a] and [b] in this field. */
    internal fun multiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) {
            return 0
        }
        return expTable[(logTable[a] + logTable[b]) % (size - 1)]
    }

    /** Renders as `GF(0x<primitive>,<size>)`. */
    override fun toString(): String = "GF(0x" + primitive.toString(16) + ',' + size + ')'

    /** Standard fields used by the barcode formats. */
    companion object {
        /** Aztec 12-bit data field: x^12 + x^6 + x^5 + x^3 + 1. */
        val AZTEC_DATA_12: GenericGF = GenericGF(0b1000001101001, 4096, 1)

        /** Aztec 10-bit data field: x^10 + x^3 + 1. */
        val AZTEC_DATA_10: GenericGF = GenericGF(0b10000001001, 1024, 1)

        /** Aztec 6-bit data field: x^6 + x + 1. */
        val AZTEC_DATA_6: GenericGF = GenericGF(0b1000011, 64, 1)

        /** Aztec mode message field: x^4 + x + 1. */
        val AZTEC_PARAM: GenericGF = GenericGF(0b10011, 16, 1)

        /** QR Code field: x^8 + x^4 + x^3 + x^2 + 1. */
        val QR_CODE_FIELD_256: GenericGF = GenericGF(0b100011101, 256, 0)

        /** Data Matrix field: x^8 + x^5 + x^3 + x^2 + 1. */
        val DATA_MATRIX_FIELD_256: GenericGF = GenericGF(0b100101101, 256, 1)

        /** Aztec 8-bit data field, same as Data Matrix. */
        val AZTEC_DATA_8: GenericGF = DATA_MATRIX_FIELD_256

        /** MaxiCode field, same as Aztec 6-bit. */
        val MAXICODE_FIELD_64: GenericGF = AZTEC_DATA_6

        /** Sum or difference of [a] and [b]; they are the same in GF(2^n). */
        fun addOrSubtract(a: Int, b: Int): Int = a xor b
    }
}
