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

import com.mukeshsolanki.barcodes.internal.zxing.pdf417.PDF417Common

/**
 * Counts how often each candidate value was read for one barcode cell.
 */
internal class BarcodeValue {

    /** Occurrence count per value, in insertion order. */
    private val values: MutableMap<Int, Int> = LinkedHashMap()

    /** Adds an occurrence of [value]. */
    fun setValue(value: Int) {
        values[value] = (values[value] ?: 0) + 1
    }

    /**
     * The values with the highest occurrence (empty if none was set), in java.util.HashMap iteration
     * order so ties resolve exactly as in zxing.
     */
    val value: IntArray
        get() {
            var maxConfidence = -1
            val result = ArrayList<Int>()
            for (entry in hashMapOrder()) {
                if (entry.value > maxConfidence) {
                    maxConfidence = entry.value
                    result.clear()
                    result.add(entry.key)
                } else if (entry.value == maxConfidence) {
                    result.add(entry.key)
                }
            }
            return PDF417Common.toIntArray(result)
        }

    /** Returns the occurrence count of [value], or null if it was never set. */
    fun getConfidence(value: Int): Int? = values[value]

    /**
     * Orders entries as java.util.HashMap<Integer, Integer> iterates them: by bucket (key masked by the
     * table capacity), then by insertion. Keys here are small non-negative ints, so the hash is the key.
     */
    private fun hashMapOrder(): List<Map.Entry<Int, Int>> {
        var capacity = 16
        while (values.size > capacity * 3 / 4) {
            capacity *= 2
        }
        val mask = capacity - 1
        return values.entries.sortedBy { it.key and mask }
    }
}
