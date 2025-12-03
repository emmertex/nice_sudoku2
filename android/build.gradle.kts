plugins {
    kotlin("android")
    kotlin("plugin.compose")
    id("com.android.application")
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.nicesudoku.android"

    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/main/res")

    defaultConfig {
        applicationId = "com.nicesudoku.android"
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-Xjvm-default=all"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Compose dependencies
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")


    // Other dependencies
    implementation("androidx.appcompat:appcompat:1.7.0")
}

