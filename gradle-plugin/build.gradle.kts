plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":ui-ir"))
    compileOnly(gradleApi())
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    testImplementation(gradleTestKit())
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("crossui") {
            id = "dev.crossui"
            implementationClass = "dev.crossui.gradle.CrossUiPlugin"
            displayName = "CrossUI source generation"
            description = "Generates native SwiftUI, Compose, and WinUI source from CrossUI IR."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
