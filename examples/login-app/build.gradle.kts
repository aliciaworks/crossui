@file:OptIn(org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class)

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> {
            iosArm64()
            iosSimulatorArm64()
            macosArm64()
            watchosArm64()
            watchosSimulatorArm64()
            tvosArm64()
            tvosSimulatorArm64()
            // The login feature is the canonical Apple boundary: export its typed
            // state/action surface plus the runtime for every Apple platform so
            // generated SwiftUI can consume Swift value types.
            swiftExport {
                moduleName.set("LoginApp")
                flattenPackage.set("dev.crossui.example.login")
                export(project(":runtime")) {
                    moduleName.set("CrossUiRuntime")
                }
                export("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0") {
                    moduleName.set("Coroutines")
                }
            }
        }
        hostOs.startsWith("Windows") -> mingwX64()
        else -> linuxX64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":ui-ir"))
            implementation(project(":ui-dsl"))
            implementation(project(":runtime"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
