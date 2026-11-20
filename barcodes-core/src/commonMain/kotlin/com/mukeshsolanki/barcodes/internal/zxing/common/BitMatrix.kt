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

/**
 * A 2D matrix of bits; x is the column and y the row, origin top-left.
 * Each row starts on a new int and bits within an int are least significant first.
 */
internal class BitMatrix private constructor(
    width: Int,
    height: Int,
    rowSize: Int,
    /** Row-major backing words. */
    private var bits: IntArray,
) {

    /** Width of the matrix in bits. */
    var width: Int = width
        private set

    /** Height of the matrix in bits. */
    var height: Int = height
        private set

    /** Number of ints per row. */
    var rowSize: Int = rowSize
        private set

    /** Creates an empty square matrix of [dimension] x [dimension]. */
    constructor(dimension: Int) : this(dimension, dimension)

    /** Creates an empty [width] x [height] matrix. */
    constructor(width: Int, height: Int) : this(
        width,
        height,
        checkedRowSize(width, height),
        IntArray(checkedRowSize(width, height) * height),
    )

    /** Returns true when the bit at ([x], [y]) is set (black). */
    operator fun get(x: Int, y: Int): Boolean {
        val offset = y * rowSize + (x / 32)
        return ((bits[offset] ushr (x and 0x1f)) and 1) != 0
    }

    /** Sets the bit at ([x], [y]). */
    fun set(x: Int, y: Int) {
        val offset = y * rowSize + (x / 32)
        bits[offset] = bits[offset] or (1 shl (x and 0x1f))
    }

    /** Clears the bit at ([x], [y]). */
    fun unset(x: Int, y: Int) {
        val offset = y * rowSize + (x / 32)
        bits[offset] = bits[offset] and (1 shl (x and 0x1f)).inv()
    }

    /** Flips the bit at ([x], [y]). */
    fun flip(x: Int, y: Int) {
        val offset = y * rowSize + (x / 32)
        bits[offset] = bits[offset] xor (1 shl (x and 0x1f))
    }

    /** Flips every bit in the matrix. */
    fun flip() {
        for (i in bits.indices) {
            bits[i] = bits[i].inv()
        }
    }

    /** Flips each bit whose corresponding [mask] bit is set. */
    fun xor(mask: BitMatrix) {
        if (width != mask.width || height != mask.height || rowSize != mask.rowSize) {
            throw IllegalArgumentException("input matrix dimensions do not match")
        }
        val rowArray = BitArray(width)
        for (y in 0 until height) {
            val offset = y * rowSize
            val row = mask.getRow(y, rowArray).bitArray
            for (x in 0 until rowSize) {
                bits[offset + x] = bits[offset + x] xor row[x]
            }
        }
    }

    /** Clears all bits. */
    fun clear() {
        bits.fill(0)
    }

    /** Sets every bit in the [width] x [height] region starting at ([left], [top]). */
    fun setRegion(left: Int, top: Int, width: Int, height: Int) {
        if (top < 0 || left < 0) {
            throw IllegalArgumentException("Left and top must be nonnegative")
        }
        if (height < 1 || width < 1) {
            throw IllegalArgumentException("Height and width must be at least 1")
        }
        val right = left + width
        val bottom = top + height
        if (bottom > this.height || right > this.width) {
            throw IllegalArgumentException("The region must fit inside the matrix")
        }
        for (y in top until bottom) {
            val offset = y * rowSize
            for (x in left until right) {
                bits[offset + (x / 32)] = bits[offset + (x / 32)] or (1 shl (x and 0x1f))
            }
        }
    }

    /** Copies row [y] into [row] (allocated when null or too small) and returns the row to use. */
    fun getRow(y: Int, row: BitArray?): BitArray {
        val result = if (row == null || row.size < width) BitArray(width) else row.also { it.clear() }
        val offset = y * rowSize
        for (x in 0 until rowSize) {
            result.setBulk(x * 32, bits[offset + x])
        }
        return result
    }

    /** Replaces row [y] with the contents of [row]. */
    fun setRow(y: Int, row: BitArray) {
        row.bitArray.copyInto(bits, y * rowSize, 0, rowSize)
    }

    /** Rotates the matrix counter-clockwise by [degrees] (0, 90, 180 or 270). */
    fun rotate(degrees: Int) {
        when (degrees % 360) {
            0 -> return
            90 -> {
                rotate90()
                return
            }
            180 -> {
                rotate180()
                return
            }
            270 -> {
                rotate90()
                rotate180()
                return
            }
        }
        throw IllegalArgumentException("degrees must be a multiple of 0, 90, 180, or 270")
    }

    /** Rotates the matrix 180 degrees. */
    fun rotate180() {
        var topRow = BitArray(width)
        var bottomRow = BitArray(width)
        val maxHeight = (height + 1) / 2
        for (i in 0 until maxHeight) {
            topRow = getRow(i, topRow)
            val bottomRowIndex = height - 1 - i
            bottomRow = getRow(bottomRowIndex, bottomRow)
            topRow.reverse()
            bottomRow.reverse()
            setRow(i, bottomRow)
            setRow(bottomRowIndex, topRow)
        }
    }

    /** Rotates the matrix 90 degrees counter-clockwise. */
    fun rotate90() {
        val newWidth = height
        val newHeight = width
        val newRowSize = (newWidth + 31) / 32
        val newBits = IntArray(newRowSize * newHeight)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * rowSize + (x / 32)
                if (((bits[offset] ushr (x and 0x1f)) and 1) != 0) {
                    val newOffset = (newHeight - 1 - x) * newRowSize + (y / 32)
                    newBits[newOffset] = newBits[newOffset] or (1 shl (y and 0x1f))
                }
            }
        }
        width = newWidth
        height = newHeight
        rowSize = newRowSize
        bits = newBits
    }

    /** `left, top, width, height` of the rectangle enclosing all set bits, or null if all white. */
    val enclosingRectangle: IntArray?
        get() {
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            for (y in 0 until height) {
                for (x32 in 0 until rowSize) {
                    val theBits = bits[y * rowSize + x32]
                    if (theBits != 0) {
                        if (y < top) {
                            top = y
                        }
                        if (y > bottom) {
                            bottom = y
                        }
                        if (x32 * 32 < left) {
                            var bit = 0
                            while ((theBits shl (31 - bit)) == 0) {
                                bit++
                            }
                            if ((x32 * 32 + bit) < left) {
                                left = x32 * 32 + bit
                            }
                        }
                        if (x32 * 32 + 31 > right) {
                            var bit = 31
                            while ((theBits ushr bit) == 0) {
                                bit--
                            }
                            if ((x32 * 32 + bit) > right) {
                                right = x32 * 32 + bit
                            }
                        }
                    }
                }
            }
            if (right < left || bottom < top) {
                return null
            }
            return intArrayOf(left, top, right - left + 1, bottom - top + 1)
        }

    /** `x, y` of the top-left-most set bit, or null if all white. */
    val topLeftOnBit: IntArray?
        get() {
            var bitsOffset = 0
            while (bitsOffset < bits.size && bits[bitsOffset] == 0) {
                bitsOffset++
            }
            if (bitsOffset == bits.size) {
                return null
            }
            val y = bitsOffset / rowSize
            var x = (bitsOffset % rowSize) * 32
            val theBits = bits[bitsOffset]
            var bit = 0
            while ((theBits shl (31 - bit)) == 0) {
                bit++
            }
            x += bit
            return intArrayOf(x, y)
        }

    /** `x, y` of the bottom-right-most set bit, or null if all white. */
    val bottomRightOnBit: IntArray?
        get() {
            var bitsOffset = bits.size - 1
            while (bitsOffset >= 0 && bits[bitsOffset] == 0) {
                bitsOffset--
            }
            if (bitsOffset < 0) {
                return null
            }
            val y = bitsOffset / rowSize
            var x = (bitsOffset % rowSize) * 32
            val theBits = bits[bitsOffset]
            var bit = 31
            while ((theBits ushr bit) == 0) {
                bit--
            }
            x += bit
            return intArrayOf(x, y)
        }

    /** Equal when dimensions and bits match. */
    override fun equals(other: Any?): Boolean {
        if (other !is BitMatrix) {
            return false
        }
        return width == other.width && height == other.height && rowSize == other.rowSize &&
            bits.contentEquals(other.bits)
    }

    /** Hash consistent with [equals], computed as zxing does. */
    override fun hashCode(): Int {
        var hash = width
        hash = 31 * hash + width
        hash = 31 * hash + height
        hash = 31 * hash + rowSize
        hash = 31 * hash + bits.contentHashCode()
        return hash
    }

    /** Renders the matrix with `X ` for set and two spaces for unset bits. */
    override fun toString(): String = toString("X ", "  ")

    /** Renders the matrix using [setString] / [unsetString] and `\n` after each row. */
    fun toString(setString: String, unsetString: String): String = buildToString(setString, unsetString, "\n")

    /** Builds the textual rendering used by [toString]. */
    private fun buildToString(setString: String, unsetString: String, lineSeparator: String): String {
        val result = StringBuilder(height * (width + 1))
        for (y in 0 until height) {
            for (x in 0 until width) {
                result.append(if (get(x, y)) setString else unsetString)
            }
            result.append(lineSeparator)
        }
        return result.toString()
    }

    /** Returns an independent copy of this matrix. */
    fun clone(): BitMatrix = BitMatrix(width, height, rowSize, bits.copyOf())

    /** Factories for building matrices from other representations. */
    companion object {
        /** Validates dimensions and returns the ints-per-row for [width]. */
        private fun checkedRowSize(width: Int, height: Int): Int {
            if (width < 1 || height < 1) {
                throw IllegalArgumentException("Both dimensions must be greater than 0")
            }
            return (width + 31) / 32
        }

        /** Builds a matrix from row-major booleans, true meaning set. */
        fun parse(image: Array<BooleanArray>): BitMatrix {
            val height = image.size
            val width = image[0].size
            val bits = BitMatrix(width, height)
            for (i in 0 until height) {
                val imageI = image[i]
                for (j in 0 until width) {
                    if (imageI[j]) {
                        bits.set(j, i)
                    }
                }
            }
            return bits
        }

        /** Parses a text rendering where [setString] marks set bits and [unsetString] unset ones. */
        fun parse(stringRepresentation: String, setString: String, unsetString: String): BitMatrix {
            val bits = BooleanArray(stringRepresentation.length)
            var bitsPos = 0
            var rowStartPos = 0
            var rowLength = -1
            var nRows = 0
            var pos = 0
            while (pos < stringRepresentation.length) {
                if (stringRepresentation[pos] == '\n' || stringRepresentation[pos] == '\r') {
                    if (bitsPos > rowStartPos) {
                        if (rowLength == -1) {
                            rowLength = bitsPos - rowStartPos
                        } else if (bitsPos - rowStartPos != rowLength) {
                            throw IllegalArgumentException("row lengths do not match")
                        }
                        rowStartPos = bitsPos
                        nRows++
                    }
                    pos++
                } else if (stringRepresentation.startsWith(setString, pos)) {
                    pos += setString.length
                    bits[bitsPos] = true
                    bitsPos++
                } else if (stringRepresentation.startsWith(unsetString, pos)) {
                    pos += unsetString.length
                    bits[bitsPos] = false
                    bitsPos++
                } else {
                    throw IllegalArgumentException(
                        "illegal character encountered: " + stringRepresentation.substring(pos),
                    )
                }
            }
            if (bitsPos > rowStartPos) {
                if (rowLength == -1) {
                    rowLength = bitsPos - rowStartPos
                } else if (bitsPos - rowStartPos != rowLength) {
                    throw IllegalArgumentException("row lengths do not match")
                }
                nRows++
            }
            val matrix = BitMatrix(rowLength, nRows)
            for (i in 0 until bitsPos) {
                if (bits[i]) {
                    matrix.set(i % rowLength, i / rowLength)
                }
            }
            return matrix
        }
    }
}
