plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.6.11")
                implementation("org.jetbrains.compose.foundation:foundation:1.6.11")
                implementation("org.jetbrains.compose.material3:material3:1.6.11")
                implementation("org.jetbrains.compose.ui:ui:1.6.11")
                implementation("org.jetbrains.compose.components:components-resources:1.6.11")

                // Kotlinx
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                // Include the Java JAR - only available on JVM
                implementation(files("libs/StormDoku.jar"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.compose.html:html-core:1.6.11")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0")
            }
        }
    }
}
