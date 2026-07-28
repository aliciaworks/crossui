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
