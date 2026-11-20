# kmp-barcodes

Barcode generation and scanning for Android, iOS, Kotlin Multiplatform and Compose Multiplatform. Encoding and decoding run on a pure Kotlin port of [ZXing](https://github.com/zxing/zxing) 3.5.3, so the output matches ZXing module for module, with no native libraries, Google Play Services or network access.

- **Formats:** QR Code, Data Matrix, Aztec, PDF417, UPC-A, UPC-E, EAN-8, EAN-13, Code 128, Code 93, Code 39, Codabar and ITF. All can be generated and read.
- **Generate:** a vector Compose `Painter` with QR shapes, brushes and a logo, or exact-pixel rasters and PNG, JPEG or WEBP bytes.
- **Scan:** a Compose camera scanner (CameraX on Android, AVFoundation on iOS) with torch, zoom and auto-zoom, or decode a still image.

## Install

Three libraries are available:

- `kmp-barcodes-core`: encode, decode still images and build payloads. No Compose.
- `kmp-barcodes`: Compose painters and image export. Includes `kmp-barcodes-core`.
- `kmp-barcodes-scanner`: the Compose camera scanner. Includes `kmp-barcodes-core`.

### Android, KMP and Compose Multiplatform

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://mukeshsolanki.github.io/kmp-barcodes")
    }
}

// build.gradle.kts
implementation("com.mukeshsolanki.barcodes:kmp-barcodes-core:0.1.0")
implementation("com.mukeshsolanki.barcodes:kmp-barcodes:0.1.0")         // generation, includes core
implementation("com.mukeshsolanki.barcodes:kmp-barcodes-scanner:0.1.0") // camera scanner, includes core
```

Requirements:

- **Android:** `minSdk` 24.
- **iOS:** iOS 15, `iosArm64` and `iosSimulatorArm64`.
- **Kotlin:** `kmp-barcodes-core` is built for Kotlin 2.0 or newer consumers on Android. `kmp-barcodes` and `kmp-barcodes-scanner` are built with Kotlin 2.4.21 and Compose Multiplatform 1.12.2. iOS consumers need Kotlin 2.4 or newer.

### Swift (Swift Package Manager)

Add `https://github.com/mukeshsolanki/kmp-barcodes` as a package, then choose **one** product:

- `KmpBarcodes`: generation, plus everything in core.
- `KmpBarcodesScanner`: the camera scanner, plus everything in core.

Each XCFramework embeds its own copy of core. Don't link both, because each would have its own incompatible `BarcodeFormat` and `Barcode` types. iOS simulators on Intel Macs aren't supported.

## Usage

### Encode

```kotlin
import com.mukeshsolanki.barcodes.BarcodeEncoder
import com.mukeshsolanki.barcodes.BarcodeFormat
import com.mukeshsolanki.barcodes.ErrorCorrection
import com.mukeshsolanki.barcodes.QrOptions

val matrix = BarcodeEncoder.encode(
    content = "https://example.com",
    format = BarcodeFormat.QrCode,
    options = QrOptions(errorCorrection = ErrorCorrection.M),
)
val dark = matrix[0, 0]                         // matrix.width x matrix.height cells
```

Width and height `0` give the minimum size. Larger sizes are scaled and centred exactly as ZXing's `MultiFormatWriter` does. Each format has its own options class: `QrOptions`, `DataMatrixOptions`, `AztecOptions`, `Pdf417Options`, `Code128Options` and `LinearOptions`.

### Compose painter

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import com.mukeshsolanki.barcodes.BallShape
import com.mukeshsolanki.barcodes.BarcodeBrush
import com.mukeshsolanki.barcodes.FrameShape
import com.mukeshsolanki.barcodes.PixelShape
import com.mukeshsolanki.barcodes.QrStyle
import com.mukeshsolanki.barcodes.rememberBarcodePainter

val painter = rememberBarcodePainter(
    content = "https://example.com",
    style = QrStyle(
        darkPixel = PixelShape.Circle,
        ball = BallShape.RoundCorners(0.3f),
        frame = FrameShape.RoundCorners(0.3f),
        darkBrush = BarcodeBrush.Solid(Color(0xFF123239)),
    ),
)
Image(painter, contentDescription = "QR code")
```

Other formats use `SimpleStyle(dark, background)`, for example `rememberBarcodePainter("4006381333931", BarcodeFormat.Ean13, style = SimpleStyle())`. Outside composition, use `BarcodePainter(...)`.

### Export images

```kotlin
import com.mukeshsolanki.barcodes.ImageFormat
import com.mukeshsolanki.barcodes.toByteArray
import com.mukeshsolanki.barcodes.toImageBitmap

val png = painter.toByteArray(1024, 1024, ImageFormat.Png)

// Exact pixels, identical to ZXing's BitMatrix and MatrixToImageWriter output:
val exact = BarcodeEncoder.encode("https://example.com", BarcodeFormat.QrCode, 512, 512, QrOptions(quietZone = 1))
val bitmap = exact.toImageBitmap()              // one matrix cell = one pixel
```

On Android, `BarcodeMatrix.toBitmap()` returns an `android.graphics.Bitmap`.

### Payloads

```kotlin
import com.mukeshsolanki.barcodes.Payloads
import com.mukeshsolanki.barcodes.WifiSecurity

val wifi = Payloads.wifi(ssid = "Home", password = "secret", security = WifiSecurity.Wpa)
// WIFI:T:WPA;S:Home;P:secret;;
```

`Payloads` also builds `text`, `url`, `email`, `sms`, `phone`, `geo`, `vCard`, `meCard` and `calendarEvent` strings, with the escaping each format needs.

### Scan an image

```kotlin
import com.mukeshsolanki.barcodes.scanImage

val barcode = scanImage(bytes)                  // PNG or JPEG bytes; null when nothing reads
barcode?.text
barcode?.format
```

`scanImage` is a suspend function that never throws. It downscales the image so its longest side is at most 500 px (see `ScanOptions`) and tries harder. On Android, `scanImage(context, uri)` reads a content URI. `kmp-barcodes` adds `scanImage(imageBitmap)`.

### Camera scanner

```kotlin
import com.mukeshsolanki.barcodes.BarcodeFormat
import com.mukeshsolanki.barcodes.BarcodeScanner
import com.mukeshsolanki.barcodes.rememberScannerState

val state = rememberScannerState()
BarcodeScanner(
    state = state,
    formats = setOf(BarcodeFormat.QrCode),
    onResult = { barcode -> println(barcode.text) },
    onError = { error -> /* ScannerError.NoCamera or ScannerError.BindFailed */ },
)

state.torchEnabled = true
state.zoom = 2f                                 // clamped to 1f..state.maxZoom
state.paused = true
```

`onResult` fires once per new value. Holding the same code in view delivers it once. It's delivered again after about 1 second with no code in view, or after `paused` goes from true to false. Auto-zoom steps in when a QR code is too small to decode, and resets to 1x on resume. Configure it with `rememberScannerState(autoZoom = AutoZoom(...))`.

## Permissions

The scanner needs camera access, but **the library never requests permission**. Request it in your app before showing `BarcodeScanner`.

Android `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
```

iOS `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Scan barcodes with the camera.</string>
```

`kmp-barcodes-core` and `kmp-barcodes` need no permissions.

## Privacy

The library makes no network calls and doesn't log anything. Error messages never include barcode contents.

## License

[Apache-2.0](LICENSE). This library contains code ported from [ZXing](https://github.com/zxing/zxing) 3.5.3, Copyright ZXing authors, licensed under Apache-2.0. See [NOTICE](NOTICE) and [licenses/](licenses) for attribution. Keep the `NOTICE` file with anything you redistribute. It's also attached to each GitHub Release in `licenses.zip`.
