/*
 * Copyright (C) 2010 ZXing authors
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

package com.mukeshsolanki.barcodes.internal.zxing.oned

/**
 * Records EAN prefix to GS1 Member Organization, where the member organization
 * correlates strongly with a country. This is an imperfect means of identifying
 * a country of origin by EAN-13 barcode value.
 */
internal class EANManufacturerOrgSupport {

    /** Returns the country identifier for the 3 digit GS1 prefix of [productCode], or null when unknown. */
    fun lookupCountryIdentifier(productCode: String): String? {
        val prefix = productCode.substring(0, 3).toInt()
        for ((range, id) in RANGES) {
            val start = range[0]
            if (prefix < start) {
                return null
            }
            val end = if (range.size == 1) start else range[1]
            if (prefix <= end) {
                return id
            }
        }
        return null
    }

    /** Prefix table shared by all instances. */
    private companion object {

        /** Sorted prefix ranges (single value or inclusive start/end) paired with their country identifiers. */
        val RANGES: List<Pair<IntArray, String>> = listOf(
            intArrayOf(0, 19) to "US/CA",
            intArrayOf(30, 39) to "US",
            intArrayOf(60, 139) to "US/CA",
            intArrayOf(300, 379) to "FR",
            intArrayOf(380) to "BG",
            intArrayOf(383) to "SI",
            intArrayOf(385) to "HR",
            intArrayOf(387) to "BA",
            intArrayOf(400, 440) to "DE",
            intArrayOf(450, 459) to "JP",
            intArrayOf(460, 469) to "RU",
            intArrayOf(471) to "TW",
            intArrayOf(474) to "EE",
            intArrayOf(475) to "LV",
            intArrayOf(476) to "AZ",
            intArrayOf(477) to "LT",
            intArrayOf(478) to "UZ",
            intArrayOf(479) to "LK",
            intArrayOf(480) to "PH",
            intArrayOf(481) to "BY",
            intArrayOf(482) to "UA",
            intArrayOf(484) to "MD",
            intArrayOf(485) to "AM",
            intArrayOf(486) to "GE",
            intArrayOf(487) to "KZ",
            intArrayOf(489) to "HK",
            intArrayOf(490, 499) to "JP",
            intArrayOf(500, 509) to "GB",
            intArrayOf(520) to "GR",
            intArrayOf(528) to "LB",
            intArrayOf(529) to "CY",
            intArrayOf(531) to "MK",
            intArrayOf(535) to "MT",
            intArrayOf(539) to "IE",
            intArrayOf(540, 549) to "BE/LU",
            intArrayOf(560) to "PT",
            intArrayOf(569) to "IS",
            intArrayOf(570, 579) to "DK",
            intArrayOf(590) to "PL",
            intArrayOf(594) to "RO",
            intArrayOf(599) to "HU",
            intArrayOf(600, 601) to "ZA",
            intArrayOf(603) to "GH",
            intArrayOf(608) to "BH",
            intArrayOf(609) to "MU",
            intArrayOf(611) to "MA",
            intArrayOf(613) to "DZ",
            intArrayOf(616) to "KE",
            intArrayOf(618) to "CI",
            intArrayOf(619) to "TN",
            intArrayOf(621) to "SY",
            intArrayOf(622) to "EG",
            intArrayOf(624) to "LY",
            intArrayOf(625) to "JO",
            intArrayOf(626) to "IR",
            intArrayOf(627) to "KW",
            intArrayOf(628) to "SA",
            intArrayOf(629) to "AE",
            intArrayOf(640, 649) to "FI",
            intArrayOf(690, 695) to "CN",
            intArrayOf(700, 709) to "NO",
            intArrayOf(729) to "IL",
            intArrayOf(730, 739) to "SE",
            intArrayOf(740) to "GT",
            intArrayOf(741) to "SV",
            intArrayOf(742) to "HN",
            intArrayOf(743) to "NI",
            intArrayOf(744) to "CR",
            intArrayOf(745) to "PA",
            intArrayOf(746) to "DO",
            intArrayOf(750) to "MX",
            intArrayOf(754, 755) to "CA",
            intArrayOf(759) to "VE",
            intArrayOf(760, 769) to "CH",
            intArrayOf(770) to "CO",
            intArrayOf(773) to "UY",
            intArrayOf(775) to "PE",
            intArrayOf(777) to "BO",
            intArrayOf(779) to "AR",
            intArrayOf(780) to "CL",
            intArrayOf(784) to "PY",
            intArrayOf(785) to "PE",
            intArrayOf(786) to "EC",
            intArrayOf(789, 790) to "BR",
            intArrayOf(800, 839) to "IT",
            intArrayOf(840, 849) to "ES",
            intArrayOf(850) to "CU",
            intArrayOf(858) to "SK",
            intArrayOf(859) to "CZ",
            intArrayOf(860) to "YU",
            intArrayOf(865) to "MN",
            intArrayOf(867) to "KP",
            intArrayOf(868, 869) to "TR",
            intArrayOf(870, 879) to "NL",
            intArrayOf(880) to "KR",
            intArrayOf(885) to "TH",
            intArrayOf(888) to "SG",
            intArrayOf(890) to "IN",
            intArrayOf(893) to "VN",
            intArrayOf(896) to "PK",
            intArrayOf(899) to "ID",
            intArrayOf(900, 919) to "AT",
            intArrayOf(930, 939) to "AU",
            intArrayOf(940, 949) to "AZ",
            intArrayOf(955) to "MY",
            intArrayOf(958) to "MO",
        )
    }
}
