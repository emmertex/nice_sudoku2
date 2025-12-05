plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("Generate_puzzlesKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Coroutines for parallel processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.named<JavaExec>("run") {
    // Arguments can be passed via -P properties (handles spaces in paths)
    // Usage: ./gradlew :scripts:run -PapiUrl="http://localhost:8181"
    // Or: ./gradlew :scripts:run -PapiUrl="http://localhost:8181" -PinputFile="/path/to/file.txt" -Pparallelism=8
    val apiUrl = project.findProperty("apiUrl") as String? ?: "http://localhost:8181"
    val inputFile = project.findProperty("inputFile") as String?
    val parallelism = project.findProperty("parallelism") as String? ?: "8"
    
    args = if (inputFile != null) {
        listOf(apiUrl, inputFile, parallelism)
    } else {
        listOf(apiUrl, "", parallelism)
    }
}

