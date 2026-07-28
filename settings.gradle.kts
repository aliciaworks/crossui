pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
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
    ":examples:login-app",
    ":examples:showcase",
)
