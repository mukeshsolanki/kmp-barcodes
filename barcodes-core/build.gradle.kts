import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.detekt)
    `maven-publish`
}

kotlin {
    explicitApi()

    android {
        namespace = "com.mukeshsolanki.barcodes.core"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    val xcFramework = XCFramework("KmpBarcodesCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBarcodesCore"
            isStatic = true
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.zxing.core)
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
