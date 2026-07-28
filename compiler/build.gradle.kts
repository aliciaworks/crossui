plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ui-ir"))
    implementation(project(":legalizer"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(project(":ui-dsl"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.crossui.compiler.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("compiler") {
            from(components["java"])
        }
    }
}
