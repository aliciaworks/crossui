plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
}

allprojects {
    group = "dev.crossui"
    version = "0.1.0"
}

tasks.register("generateNativeUi") {
    group = "crossui"
    description = "Generates SwiftUI, Jetpack Compose, and WinUI source at build time."
    dependsOn(":examples:showcase:generateNativeUi")
}
