plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":ui-ir"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
