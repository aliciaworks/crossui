plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.0" apply false
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

tasks.register("publishCrossUiToMavenLocal") {
    group = "publishing"
    description = "Publishes the CrossUI DSL, runtime, compiler, and Gradle plugin locally."
    dependsOn(
        ":ui-ir:publishToMavenLocal",
        ":ui-dsl:publishToMavenLocal",
        ":runtime:publishToMavenLocal",
        ":legalizer:publishToMavenLocal",
        ":compiler:publishToMavenLocal",
        ":gradle-plugin:publishToMavenLocal",
    )
}
