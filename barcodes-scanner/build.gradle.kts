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
        namespace = "com.mukeshsolanki.barcodes.scanner"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    val xcFramework = XCFramework("KmpBarcodesScanner")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KmpBarcodesScanner"
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
            implementation(libs.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
