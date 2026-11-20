import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    `maven-publish`
}

kotlin {
    explicitApi()

    android {
        namespace = "com.mukeshsolanki.barcodes"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    val xcFramework = XCFramework("KmpBarcodes")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBarcodes"
            isStatic = true
            export(dependencies.project(":kmp-barcodes-core"))
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-barcodes-core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
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
