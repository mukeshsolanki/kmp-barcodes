/*
 * Copyright 2013 ZXing authors
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
package com.mukeshsolanki.barcodes.internal.zxing.pdf417.decoder

/**
 * The codewords detected in one barcode column, indexed by image row.
 */
internal open class DetectionResultColumn(boundingBox: BoundingBox) {

    /** The region this column was detected in. */
    val boundingBox: BoundingBox = BoundingBox(boundingBox)

    /** Codeword per image row within the bounding box, or null. */
    val codewords: Array<Codeword?> = arrayOfNulls(boundingBox.maxY - boundingBox.minY + 1)

    /** Returns the codeword at [imageRow], or the nearest one within [MAX_NEARBY_DISTANCE] rows. */
    fun getCodewordNearby(imageRow: Int): Codeword? {
        val codeword = getCodeword(imageRow)
        if (codeword != null) {
            return codeword
        }
        for (i in 1 until MAX_NEARBY_DISTANCE) {
            var nearImageRow = imageRowToCodewordIndex(imageRow) - i
            if (nearImageRow >= 0) {
                val above = codewords[nearImageRow]
                if (above != null) {
                    return above
                }
            }
            nearImageRow = imageRowToCodewordIndex(imageRow) + i
            if (nearImageRow < codewords.size) {
                val below = codewords[nearImageRow]
                if (below != null) {
                    return below
                }
            }
        }
        return null
    }

    /** Maps an image row to an index into [codewords]. */
    fun imageRowToCodewordIndex(imageRow: Int): Int = imageRow - boundingBox.minY

    /** Stores [codeword] at [imageRow]. */
    fun setCodeword(imageRow: Int, codeword: Codeword?) {
        codewords[imageRowToCodewordIndex(imageRow)] = codeword
    }

    /** Returns the codeword at [imageRow], or null. */
    fun getCodeword(imageRow: Int): Codeword? = codewords[imageRowToCodewordIndex(imageRow)]

    /** Renders one line per image row as `row: rowNumber|value`. */
    override fun toString(): String {
        val sb = StringBuilder()
        var row = 0
        for (codeword in codewords) {
            if (codeword == null) {
                sb.append(pad(row++, 3)).append(":    |   \n")
                continue
            }
            sb.append(pad(row++, 3)).append(": ").append(pad(codeword.rowNumber, 3)).append('|')
                .append(pad(codeword.value, 3)).append('\n')
        }
        return sb.toString()
    }

    /** Holds the nearby search distance and the formatting helper. */
    internal companion object {
        /** Maximum row distance searched by [getCodewordNearby]. */
        private const val MAX_NEARBY_DISTANCE = 5

        /** Right-aligns [value] in [width] characters, like `%{width}d`. */
        fun pad(value: Int, width: Int): String = value.toString().padStart(width)
    }
}
