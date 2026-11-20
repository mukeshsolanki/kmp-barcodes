// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "KmpBarcodes",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "KmpBarcodes", targets: ["KmpBarcodes"]),
        .library(name: "KmpBarcodesScanner", targets: ["KmpBarcodesScanner"]),
    ],
    targets: [
        .binaryTarget(
            name: "KmpBarcodes",
            url: "https://github.com/mukeshsolanki/kmp-barcodes/releases/download/0.1.0/KmpBarcodes.xcframework.zip",
            checksum: "ce60a1b5531203d6c2445448a388c4b1adbac978fa2861b9445c84696232b9ad"
        ),
        .binaryTarget(
            name: "KmpBarcodesScanner",
            url: "https://github.com/mukeshsolanki/kmp-barcodes/releases/download/0.1.0/KmpBarcodesScanner.xcframework.zip",
            checksum: "e095f2674fc171dfa1772b1809bce246d3c88c1f7559491ad82b2b84f5ced7ed"
        ),
    ]
)
