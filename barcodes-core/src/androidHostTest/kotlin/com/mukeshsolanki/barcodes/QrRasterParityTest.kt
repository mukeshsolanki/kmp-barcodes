package com.mukeshsolanki.barcodes

import kotlin.test.Test

/** Pixel parity of scaled QR rasters (1200 px Wi-Fi pairing and 512 px device-id codes) against zxing. */
class QrRasterParityTest {

    /** Pairing and device-id payloads; messages refer to them by index only. */
    private val payloads = listOf(
        pairingPayload("HomeNet-5G", "S3cr3t-Pa55word!"),
        pairingPayload(null, null),
        pairingPayload("HomeNet", ""),
        pairingPayload("घर का वाईफाई", "S3cr3t-Pa55word!"),
        SAMPLE_DEVICE_ID,
    )

    /** Pairing options: EC L with a one-module quiet zone. */
    private val pairingOptions = QrOptions(errorCorrection = ErrorCorrection.L, quietZone = 1)

    /** At 1200x1200 and 512x512 the size and every pixel equal zxing's, remainder centred in the margin. */
    @Test
    fun scaledRasterMatchesZxingPixelForPixel() {
        val report = ParityReport()
        listOf(1200, 512).forEach { size ->
            payloads.forEachIndexed { index, content ->
                report.check(
                    "payload #$index at ${size}x$size",
                    { BarcodeEncoder.encode(content, BarcodeFormat.QrCode, size, size, pairingOptions) },
                    { zxingEncode(content, BarcodeFormat.QrCode, size, size, qrHints(pairingOptions)) },
                )
            }
        }
        report.assertAllPassed()
    }

    /** Non-square targets centre the same way zxing does. */
    @Test
    fun nonSquareRasterMatchesZxing() {
        val report = ParityReport()
        listOf(1200 to 900, 300 to 512).forEach { (width, height) ->
            payloads.forEachIndexed { index, content ->
                report.check(
                    "payload #$index at ${width}x$height",
                    { BarcodeEncoder.encode(content, BarcodeFormat.QrCode, width, height, pairingOptions) },
                    { zxingEncode(content, BarcodeFormat.QrCode, width, height, qrHints(pairingOptions)) },
                )
            }
        }
        report.assertAllPassed()
    }
}
