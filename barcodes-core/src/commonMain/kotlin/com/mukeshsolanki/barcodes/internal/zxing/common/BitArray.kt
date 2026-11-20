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

package com.mukeshsolanki.barcodes.internal.zxing.common

import kotlin.math.ceil
import kotlin.math.min

/**
 * A simple, fast array of bits, represented compactly by an array of ints internally.
 */
internal class BitArray internal constructor(
    /** Backing words; bit 0 is the least significant bit of word 0. */
    private var bits: IntArray,
    size: Int,
) {

    /** Number of bits in this array. */
    var size: Int = size
        private set

    /** Creates an empty, growable bit array. */
    constructor() : this(EMPTY_BITS, 0)

    /** Creates a bit array of [size] cleared bits. */
    constructor(size: Int) : this(makeArray(size), size)

    /** Number of bytes needed to hold [size] bits. */
    val sizeInBytes: Int
        get() = (size + 7) / 8

    /** Underlying array of ints; the first element holds the first 32 bits, least significant bit first. */
    val bitArray: IntArray
        get() = bits

    /** Grows the backing array so it can hold at least [newSize] bits. */
    private fun ensureCapacity(newSize: Int) {
        if (newSize > bits.size * 32) {
            val newBits = makeArray(ceil(newSize / LOAD_FACTOR).toInt())
            bits.copyInto(newBits, 0, 0, bits.size)
            this.bits = newBits
        }
    }

    /** Returns true iff bit [i] is set. */
    operator fun get(i: Int): Boolean = (bits[i / 32] and (1 shl (i and 0x1F))) != 0

    /** Sets bit [i]. */
    fun set(i: Int) {
        bits[i / 32] = bits[i / 32] or (1 shl (i and 0x1F))
    }

    /** Flips bit [i]. */
    fun flip(i: Int) {
        bits[i / 32] = bits[i / 32] xor (1 shl (i and 0x1F))
    }

    /** Index of the first set bit at or after [from], or [size] if none. */
    fun getNextSet(from: Int): Int {
        if (from >= size) {
            return size
        }
        var bitsOffset = from / 32
        var currentBits = bits[bitsOffset]
        currentBits = currentBits and -(1 shl (from and 0x1F))
        while (currentBits == 0) {
            if (++bitsOffset == bits.size) {
                return size
            }
            currentBits = bits[bitsOffset]
        }
        val result = (bitsOffset * 32) + currentBits.countTrailingZeroBits()
        return min(result, size)
    }

    /** Index of the first unset bit at or after [from], or [size] if none. */
    fun getNextUnset(from: Int): Int {
        if (from >= size) {
            return size
        }
        var bitsOffset = from / 32
        var currentBits = bits[bitsOffset].inv()
        currentBits = currentBits and -(1 shl (from and 0x1F))
        while (currentBits == 0) {
            if (++bitsOffset == bits.size) {
                return size
            }
            currentBits = bits[bitsOffset].inv()
        }
        val result = (bitsOffset * 32) + currentBits.countTrailingZeroBits()
        return min(result, size)
    }

    /** Sets the 32-bit block containing bit [i] to [newBits]; the least significant bit maps to bit [i]. */
    fun setBulk(i: Int, newBits: Int) {
        bits[i / 32] = newBits
    }

    /** Sets the bits from [start] inclusive to [end] exclusive. */
    fun setRange(start: Int, end: Int) {
        if (end < start || start < 0 || end > size) {
            throw IllegalArgumentException()
        }
        if (end == start) {
            return
        }
        val last = end - 1
        val firstInt = start / 32
        val lastInt = last / 32
        for (i in firstInt..lastInt) {
            val firstBit = if (i > firstInt) 0 else start and 0x1F
            val lastBit = if (i < lastInt) 31 else last and 0x1F
            val mask = (2 shl lastBit) - (1 shl firstBit)
            bits[i] = bits[i] or mask
        }
    }

    /** Clears all bits. */
    fun clear() {
        bits.fill(0)
    }

    /** True iff every bit from [start] inclusive to [end] exclusive equals [value]. */
    fun isRange(start: Int, end: Int, value: Boolean): Boolean {
        if (end < start || start < 0 || end > size) {
            throw IllegalArgumentException()
        }
        if (end == start) {
            return true
        }
        val last = end - 1
        val firstInt = start / 32
        val lastInt = last / 32
        for (i in firstInt..lastInt) {
            val firstBit = if (i > firstInt) 0 else start and 0x1F
            val lastBit = if (i < lastInt) 31 else last and 0x1F
            val mask = (2 shl lastBit) - (1 shl firstBit)
            if ((bits[i] and mask) != (if (value) mask else 0)) {
                return false
            }
        }
        return true
    }

    /** Appends one bit. */
    fun appendBit(bit: Boolean) {
        ensureCapacity(size + 1)
        if (bit) {
            bits[size / 32] = bits[size / 32] or (1 shl (size and 0x1F))
        }
        size++
    }

    /** Appends the [numBits] least significant bits of [value], most significant first. */
    fun appendBits(value: Int, numBits: Int) {
        if (numBits < 0 || numBits > 32) {
            throw IllegalArgumentException("Num bits must be between 0 and 32")
        }
        var nextSize = size
        ensureCapacity(nextSize + numBits)
        for (numBitsLeft in numBits - 1 downTo 0) {
            if ((value and (1 shl numBitsLeft)) != 0) {
                bits[nextSize / 32] = bits[nextSize / 32] or (1 shl (nextSize and 0x1F))
            }
            nextSize++
        }
        size = nextSize
    }

    /** Appends all bits of [other]. */
    fun appendBitArray(other: BitArray) {
        val otherSize = other.size
        ensureCapacity(size + otherSize)
        for (i in 0 until otherSize) {
            appendBit(other.get(i))
        }
    }

    /** XORs [other] into this array; sizes must match. */
    fun xor(other: BitArray) {
        if (size != other.size) {
            throw IllegalArgumentException("Sizes don't match")
        }
        for (i in bits.indices) {
            bits[i] = bits[i] xor other.bits[i]
        }
    }

    /** Writes [numBytes] bytes, most significant bit first, starting at [bitOffset] into [array] at [offset]. */
    fun toBytes(bitOffset: Int, array: ByteArray, offset: Int, numBytes: Int) {
        var bitIndex = bitOffset
        for (i in 0 until numBytes) {
            var theByte = 0
            for (j in 0 until 8) {
                if (get(bitIndex)) {
                    theByte = theByte or (1 shl (7 - j))
                }
                bitIndex++
            }
            array[offset + i] = theByte.toByte()
        }
    }

    /** Reverses all bits in the array. */
    fun reverse() {
        val newBits = IntArray(bits.size)
        val len = (size - 1) / 32
        val oldBitsLen = len + 1
        for (i in 0 until oldBitsLen) {
            newBits[len - i] = reverseInt(bits[i])
        }
        if (size != oldBitsLen * 32) {
            val leftOffset = oldBitsLen * 32 - size
            var currentInt = newBits[0] ushr leftOffset
            for (i in 1 until oldBitsLen) {
                val nextInt = newBits[i]
                currentInt = currentInt or (nextInt shl (32 - leftOffset))
                newBits[i - 1] = currentInt
                currentInt = nextInt ushr leftOffset
            }
            newBits[oldBitsLen - 1] = currentInt
        }
        bits = newBits
    }

    /** Equal when sizes and backing words match. */
    override fun equals(other: Any?): Boolean {
        if (other !is BitArray) {
            return false
        }
        return size == other.size && bits.contentEquals(other.bits)
    }

    /** Hash consistent with [equals]. */
    override fun hashCode(): Int = 31 * size + bits.contentHashCode()

    /** Renders bits as `X` / `.`, with a space before every group of 8. */
    override fun toString(): String {
        val result = StringBuilder(size + (size / 8) + 1)
        for (i in 0 until size) {
            if ((i and 0x07) == 0) {
                result.append(' ')
            }
            result.append(if (get(i)) 'X' else '.')
        }
        return result.toString()
    }

    /** Returns an independent copy of this array. */
    fun clone(): BitArray = BitArray(bits.copyOf(), size)

    /** Constants and helpers shared by all bit arrays. */
    companion object {
        /** Backing array of an empty bit array. */
        private val EMPTY_BITS = IntArray(0)

        /** Growth factor used by [ensureCapacity]. */
        private const val LOAD_FACTOR = 0.75f

        /** Allocates enough words for [size] bits. */
        private fun makeArray(size: Int): IntArray = IntArray((size + 31) / 32)

        /** Reverses the bit order of [value], like Java's `Integer.reverse`. */
        private fun reverseInt(value: Int): Int {
            var i = value
            i = ((i and 0x55555555) shl 1) or ((i ushr 1) and 0x55555555)
            i = ((i and 0x33333333) shl 2) or ((i ushr 2) and 0x33333333)
            i = ((i and 0x0F0F0F0F) shl 4) or ((i ushr 4) and 0x0F0F0F0F)
            i = ((i and 0x00FF00FF) shl 8) or ((i ushr 8) and 0x00FF00FF)
            return (i shl 16) or (i ushr 16)
        }
    }
}
