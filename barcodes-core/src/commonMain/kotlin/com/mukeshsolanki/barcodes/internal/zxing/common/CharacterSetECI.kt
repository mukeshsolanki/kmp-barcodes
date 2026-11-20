/*
 * Copyright 2008 ZXing authors
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

import com.mukeshsolanki.barcodes.internal.charset.Charset
import com.mukeshsolanki.barcodes.internal.charset.Charsets
import com.mukeshsolanki.barcodes.internal.zxing.FormatException

/**
 * Character Set ECIs per "Extended Channel Interpretations" 5.3.1.1 of ISO 18004.
 * Every ECI is registered; [charset] is null for encodings kmp-barcodes cannot transcode.
 */
internal enum class CharacterSetECI(
    /** ECI values mapping to this character set; the first is canonical. */
    private val eciValues: IntArray,
    /** Alternative encoding names that map to this character set. */
    private vararg val otherEncodingNames: String,
) {
    /** IBM code page 437. */
    Cp437(intArrayOf(0, 2)),

    /** ISO-8859-1. */
    ISO8859_1(intArrayOf(1, 3), "ISO-8859-1"),

    /** ISO-8859-2. */
    ISO8859_2(intArrayOf(4), "ISO-8859-2"),

    /** ISO-8859-3. */
    ISO8859_3(intArrayOf(5), "ISO-8859-3"),

    /** ISO-8859-4. */
    ISO8859_4(intArrayOf(6), "ISO-8859-4"),

    /** ISO-8859-5. */
    ISO8859_5(intArrayOf(7), "ISO-8859-5"),

    /** ISO-8859-6. */
    ISO8859_6(intArrayOf(8), "ISO-8859-6"),

    /** ISO-8859-7. */
    ISO8859_7(intArrayOf(9), "ISO-8859-7"),

    /** ISO-8859-8. */
    ISO8859_8(intArrayOf(10), "ISO-8859-8"),

    /** ISO-8859-9. */
    ISO8859_9(intArrayOf(11), "ISO-8859-9"),

    /** ISO-8859-10. */
    ISO8859_10(intArrayOf(12), "ISO-8859-10"),

    /** ISO-8859-11. */
    ISO8859_11(intArrayOf(13), "ISO-8859-11"),

    /** ISO-8859-13. */
    ISO8859_13(intArrayOf(15), "ISO-8859-13"),

    /** ISO-8859-14. */
    ISO8859_14(intArrayOf(16), "ISO-8859-14"),

    /** ISO-8859-15. */
    ISO8859_15(intArrayOf(17), "ISO-8859-15"),

    /** ISO-8859-16. */
    ISO8859_16(intArrayOf(18), "ISO-8859-16"),

    /** Shift_JIS. */
    SJIS(intArrayOf(20), "Shift_JIS"),

    /** windows-1250. */
    Cp1250(intArrayOf(21), "windows-1250"),

    /** windows-1251. */
    Cp1251(intArrayOf(22), "windows-1251"),

    /** windows-1252. */
    Cp1252(intArrayOf(23), "windows-1252"),

    /** windows-1256. */
    Cp1256(intArrayOf(24), "windows-1256"),

    /** UTF-16BE. */
    UnicodeBigUnmarked(intArrayOf(25), "UTF-16BE", "UnicodeBig"),

    /** UTF-8. */
    UTF8(intArrayOf(26), "UTF-8"),

    /** US-ASCII. */
    ASCII(intArrayOf(27, 170), "US-ASCII"),

    /** Big5. */
    Big5(intArrayOf(28)),

    /** GB18030. */
    GB18030(intArrayOf(29), "GB2312", "EUC_CN", "GBK"),

    /** EUC-KR. */
    EUC_KR(intArrayOf(30), "EUC-KR"),
    ;

    /** Canonical ECI value. */
    val value: Int
        get() = eciValues[0]

    /** Charset for this ECI, or null when kmp-barcodes has no transcoder for it. */
    val charset: Charset?
        get() = Charsets.forName(name)

    /** Lookups by ECI value, encoding name and charset. */
    companion object {
        /** ECI value to character set. */
        private val VALUE_TO_ECI: Map<Int, CharacterSetECI> =
            entries.flatMap { eci -> eci.eciValues.map { it to eci } }.toMap()

        /** Encoding name (enum name and aliases) to character set. */
        private val NAME_TO_ECI: Map<String, CharacterSetECI> =
            entries.flatMap { eci -> (listOf(eci.name) + eci.otherEncodingNames).map { it to eci } }.toMap()

        /** ECI for [charset], or null if it is legal but unsupported. */
        fun getCharacterSetECI(charset: Charset): CharacterSetECI? =
            NAME_TO_ECI[charset.name] ?: entries.firstOrNull { it.charset == charset }

        /** ECI for [value], or null if legal but unsupported; throws [FormatException] if invalid. */
        fun getCharacterSetECIByValue(value: Int): CharacterSetECI? {
            if (value < 0 || value >= 900) {
                throw FormatException.formatInstance
            }
            return VALUE_TO_ECI[value]
        }

        /** ECI for the encoding [name], or null if it is legal but unsupported. */
        fun getCharacterSetECIByName(name: String): CharacterSetECI? = NAME_TO_ECI[name]
    }
}
