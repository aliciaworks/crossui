pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "crossui"

include(
    ":ui-ir",
    ":ui-dsl",
    ":runtime",
    ":legalizer",
    ":compiler",
    ":gradle-plugin",
    ":examples:login-app",
    ":examples:showcase",
)
