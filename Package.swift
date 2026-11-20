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
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
        .binaryTarget(
            name: "KmpBarcodesScanner",
            url: "https://github.com/mukeshsolanki/kmp-barcodes/releases/download/0.1.0/KmpBarcodesScanner.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
