plugins {
    //trick: for the same plugin versions in all submodules
    kotlin("multiplatform").version("2.1.0") apply false
    kotlin("jvm").version("2.1.0") apply false
    kotlin("plugin.compose").version("2.1.0") apply false
    kotlin("plugin.serialization").version("2.1.0") apply false
}
