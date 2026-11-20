package com.mukeshsolanki.barcodes

import kotlin.test.Test
import kotlin.test.assertTrue

/** Every format encoded by us decodes back to the original text with our decoder and with zxing's reader. */
@OptIn(InternalBarcodesApi::class)
class RoundTripTest {

    /** Each format round-trips through both decoders; failures name the format only, never the payload. */
    @Test
    fun everyFormatDecodesWithOursAndZxing() {
        val failures = BarcodeFormat.entries.flatMap { format ->
            val content = roundTripSamples.getValue(format)
            val raster = encodeForRoundTrip(content, format).rasterizeForTest(scale = 4, quietZone = 10)
            val ours = BarcodeDecoder.decodeArgb(
                raster.pixels,
                raster.width,
                raster.height,
                setOf(format),
                ScanOptions(maxDimension = null, tryHarder = true),
            )
            listOfNotNull(
                "$format: ours returned null".takeIf { ours == null },
                "$format: ours decoded different text".takeIf { ours != null && ours.text != content },
                "$format: ours reported ${ours?.format}".takeIf { ours != null && ours.format != format },
                "$format: zxing could not read our output".takeIf { zxingDecodeText(raster, format) != content },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Wi-Fi pairing and device-id QR payloads round-trip through both decoders at EC L with a one-module quiet zone. */
    @Test
    fun pairingPayloadsRoundTrip() {
        val payloads = listOf(
            pairingPayload("HomeNet-5G", "S3cr3t-Pa55word!"),
            pairingPayload(null, null),
            pairingPayload("HomeNet", ""),
            SAMPLE_DEVICE_ID,
        )
        val options = QrOptions(errorCorrection = ErrorCorrection.L, quietZone = 1)
        val failures = payloads.flatMapIndexed { index, content ->
            val raster = BarcodeEncoder.encode(content, BarcodeFormat.QrCode, 0, 0, options)
                .rasterizeForTest(scale = 4, quietZone = 4)
            val ours = BarcodeDecoder.decodeArgb(
                raster.pixels,
                raster.width,
                raster.height,
                setOf(BarcodeFormat.QrCode),
                ScanOptions(),
            )
            listOfNotNull(
                "payload #$index: ours did not round-trip".takeIf { ours?.text != content },
                "payload #$index: zxing did not round-trip".takeIf {
                    zxingDecodeText(raster, BarcodeFormat.QrCode) != content
                },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
