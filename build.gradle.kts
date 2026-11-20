import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    group = "com.mukeshsolanki.barcodes"
    version = providers.gradleProperty("libVersion").getOrElse("0.1.0")

    if (path == ":kmp-barcodes-core") {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configure<KotlinMultiplatformExtension> {
                coreLibrariesVersion = "2.0.21"
                compilerOptions {
                    languageVersion.set(KotlinVersion.KOTLIN_2_0)
                    apiVersion.set(KotlinVersion.KOTLIN_2_0)
                }
            }
        }
    }

    pluginManager.withPlugin("maven-publish") {
        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("Barcode generation and scanning for Kotlin Multiplatform and Compose Multiplatform on Android and iOS.")
                    url.set("https://github.com/mukeshsolanki/kmp-barcodes")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                }
            }
            providers.gradleProperty("releaseRepoDir").orNull?.let { dir ->
                repositories.maven {
                    name = "Release"
                    url = uri(dir)
                }
            }
        }
    }

    pluginManager.withPlugin("dev.detekt") {
        configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom("src/commonMain/kotlin", "src/commonTest/kotlin")
        }
        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            exclude("**/generated/**", "**/internal/zxing/**")
        }
    }
}
