plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

kotlin {
    jvm()
    android {
        namespace = "dev.crossui.dsl"
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
