pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-barcodes-root"

include(":kmp-barcodes-core", ":kmp-barcodes", ":kmp-barcodes-scanner")
project(":kmp-barcodes-core").projectDir = file("barcodes-core")
project(":kmp-barcodes").projectDir = file("barcodes")
project(":kmp-barcodes-scanner").projectDir = file("barcodes-scanner")
