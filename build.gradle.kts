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

tasks.register("verifySourceFileSize") {
    group = "verification"
    description = "Fails when a handwritten source file exceeds 500 lines."
    val sourceFiles = fileTree(rootDir) {
        include("**/*.kt", "**/*.kts", "**/*.cs", "**/*.swift", "**/*.xaml")
        exclude(
            "**/build/**",
            "**/.gradle/**",
            "**/bin/**",
            "**/obj/**",
            "hosts/*/generated/**",
        )
    }
    inputs.files(sourceFiles)
    doLast {
        val oversized = sourceFiles.files.mapNotNull { file ->
            val lines = file.readLines().size
            if (lines > 500) "${file.relativeTo(rootDir)}: $lines lines" else null
        }
        check(oversized.isEmpty()) {
            "Handwritten source files must not exceed 500 lines:\n" +
                oversized.joinToString("\n")
        }
    }
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

val existingKmpFixtureCommand = listOf(
    if (System.getProperty("os.name").startsWith("Windows")) {
        rootProject.file("gradlew.bat").absolutePath
    } else {
        rootProject.file("gradlew").absolutePath
    },
    "-p",
    rootProject.file("integration-tests/existing-kmp-app").absolutePath,
    ":shared-ui:testAndroidHostTest",
    ":shared-ui:crossuiDoctor",
    ":shared-ui:verifyCrossUi",
    ":android-app:assembleDebug",
)

val integrationTestExistingKmpWarmup = tasks.register<Exec>(
    "integrationTestExistingKmpWarmup",
) {
    dependsOn("publishCrossUiToMavenLocal")
    commandLine(existingKmpFixtureCommand)
}

tasks.register<Exec>("integrationTestExistingKmp") {
    group = "verification"
    description = "Builds an independent KMP Android consumer and reuses its configuration cache."
    dependsOn(integrationTestExistingKmpWarmup)
    commandLine(existingKmpFixtureCommand)
}
