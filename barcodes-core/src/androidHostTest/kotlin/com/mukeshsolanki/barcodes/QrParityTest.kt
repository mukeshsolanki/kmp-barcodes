package com.mukeshsolanki.barcodes

import kotlin.test.Test

/** Builds a Wi-Fi pairing payload; ssid and password are omitted together when [ssid] is null. */
internal fun pairingPayload(ssid: String?, password: String?): String {
    val tail = "9f8e7d6c5b4a39281706f5e4d3c2b1a0\nap-south-1\nprod"
    return if (ssid == null) tail else "$ssid\n${password.orEmpty()}\n$tail"
}

/** A 27-character, space-free sample device id. */
internal const val SAMPLE_DEVICE_ID: String = "AB12CD34EF56GH78IJ90KL12MN3"

/** Module-matrix parity of [BarcodeEncoder] against zxing's MultiFormatWriter for QR codes. */
class QrParityTest {

    /** One QR corpus entry. */
    private class QrCase(
        /** Non-secret name used in failure messages. */
        val tag: String,
        /** Payload to encode; never printed. */
        val content: String,
        /** Charset options worth testing for this payload. */
        val charsets: List<BarcodeCharset?>,
    )

    /** Charsets that apply to any content. */
    private val anyCharset = listOf(null, BarcodeCharset.Utf8, BarcodeCharset.Iso8859_1)

    /** Charsets that also include Shift_JIS, for ASCII or Japanese content. */
    private val withShiftJis = anyCharset + BarcodeCharset.ShiftJis

    /** Only the default charset, for near-capacity strings where an ECI header would overflow. */
    private val defaultOnly = listOf<BarcodeCharset?>(null)

    /** Payload corpus covering pairing payloads, device ids, Unicode, empty values, every QR mode and capacity limits. */
    private val corpus = listOf(
        QrCase("pairing-wifi", pairingPayload("HomeNet-5G", "S3cr3t-Pa55word!"), withShiftJis),
        QrCase("pairing-no-wifi", pairingPayload(null, null), withShiftJis),
        QrCase("device-id", SAMPLE_DEVICE_ID, withShiftJis),
        QrCase("pairing-hindi-ssid", pairingPayload("घर का वाईफाई", "S3cr3t-Pa55word!"), anyCharset),
        QrCase("pairing-emoji-ssid", pairingPayload("Home📶Wifi🏠", "S3cr3t-Pa55word!"), anyCharset),
        QrCase("pairing-japanese-ssid", pairingPayload("自宅のネットワーク", "S3cr3t-Pa55word!"), withShiftJis),
        QrCase("pairing-latin1-ssid", pairingPayload("Café Wi-Fi ñ", "S3cr3t-Pa55word!"), anyCharset),
        QrCase("pairing-empty-password", pairingPayload("HomeNet", ""), withShiftJis),
        QrCase("numeric", "01234567890123456789012345", withShiftJis),
        QrCase("alphanumeric", "HELLO WORLD \$%*+-./:0123456789", withShiftJis),
        QrCase("kanji", "日本語漢字", listOf(null, BarcodeCharset.Utf8, BarcodeCharset.ShiftJis)),
        QrCase("byte-max-v40-L", "a".repeat(2953), defaultOnly),
        QrCase("byte-max-v40-H", "a".repeat(1273), defaultOnly),
        QrCase("numeric-max-v40-L", "1".repeat(7089), defaultOnly),
        QrCase("byte-over-capacity", "a".repeat(2954), defaultOnly),
    )

    /** Every corpus entry x EC level x charset x quiet zone x mask matches zxing at minimum size (0x0). */
    @Test
    fun matrixMatchesZxingAcrossCorpus() {
        val report = ParityReport()
        corpus.forEachIndexed { index, case ->
            case.charsets.forEach { charset ->
                ErrorCorrection.entries.forEach { ec ->
                    listOf(1, 4).forEach { margin ->
                        listOf(null, 3).forEach { mask ->
                            val options = QrOptions(ec, charset, mask, margin)
                            report.check(
                                "#$index ${case.tag} ec=$ec charset=$charset margin=$margin mask=$mask",
                                { BarcodeEncoder.encode(case.content, BarcodeFormat.QrCode, 0, 0, options) },
                                { zxingEncode(case.content, BarcodeFormat.QrCode, 0, 0, qrHints(options)) },
                            )
                        }
                    }
                }
            }
        }
        report.assertAllPassed()
    }

    /** A Unicode SSID with no charset must follow zxing's ISO-8859-1 default ('?' substitution), not switch to UTF-8. */
    @Test
    fun unicodeWithoutCharsetKeepsZxingDefault() {
        val report = ParityReport()
        listOf("घर का वाईफाई", "Home📶Wifi🏠", "自宅のネットワーク").forEachIndexed { index, ssid ->
            val content = pairingPayload(ssid, "S3cr3t-Pa55word!")
            report.check(
                "unicode ssid #$index",
                { BarcodeEncoder.encode(content, BarcodeFormat.QrCode) },
                { zxingEncode(content, BarcodeFormat.QrCode, 0, 0, emptyMap()) },
            )
        }
        report.assertAllPassed()
    }
}
