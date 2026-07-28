plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

android {
    namespace = "dev.crossui.integration.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.crossui.integration.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared-ui"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.4.0")
}
