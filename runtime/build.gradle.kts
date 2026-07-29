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
