plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.kotlin.multiplatform.library")
    id("dev.crossui")
}

kotlin {
    jvm()

    android {
        namespace = "dev.crossui.integration.shared"
        compileSdk = 37
        minSdk = 31
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api("dev.crossui:ui-ir:0.1.0")
            api("dev.crossui:ui-dsl:0.1.0")
            api("dev.crossui:runtime:0.1.0")
            implementation("androidx.compose.runtime:runtime:1.11.4")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            implementation("androidx.compose.material3:material3:1.4.0")
        }
    }
}

crossui {
    providerClass.set("dev.crossui.integration.login.LoginUiProvider")
    typeName.set("LoginScreen")
    targets.set(listOf("compose"))
    localization {
        mode.set(dev.crossui.gradle.LocalizationMode.Generated)
        outputDirectory.set(
            layout.buildDirectory.dir("generated/crossui-localization"),
        )
    }
}
