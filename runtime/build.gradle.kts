@file:OptIn(org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class)

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

kotlin {
    jvm()
    android {
        namespace = "dev.crossui.runtime"
        compileSdk = 37
        minSdk = 31
        withHostTest {}
    }
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
            // Export the shared runtime to Swift for every Apple platform so
            // generated SwiftUI consumes typed value types instead of the legacy
            // ObjC interop bridge. Coroutines are exported too so StateFlow
            // surfaces as a real Swift type in host code.
            swiftExport {
                moduleName.set("CrossUiRuntime")
                flattenPackage.set("dev.crossui.runtime")
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
            api(project(":ui-ir"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            api("androidx.activity:activity-ktx:1.13.0")
        }
    }
}
