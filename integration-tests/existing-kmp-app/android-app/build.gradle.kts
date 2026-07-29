plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

android {
    namespace = "dev.crossui.integration.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.crossui.integration.android"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared-ui"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
}
