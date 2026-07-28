plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
        }
        hostOs.startsWith("Windows") -> mingwX64()
        else -> linuxX64()
    }

    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
