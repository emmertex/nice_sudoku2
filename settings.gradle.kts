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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "nice-sudoku2"

include(":shared")
include(":android")
include(":web")
include(":backend")

// iOS project will be handled separately via Xcode

