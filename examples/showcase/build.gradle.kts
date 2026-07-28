plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ui-ir"))
    implementation(project(":ui-dsl"))
    implementation(project(":compiler"))
}

application {
    mainClass.set("dev.crossui.showcase.ShowcaseKt")
}

val generatedRoot = rootProject.layout.projectDirectory.dir("hosts")

val generateNativeUi = tasks.register<JavaExec>("generateNativeUi") {
    group = "crossui"
    description = "Compiles the showcase DSL to checked-in native platform source."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("--generate", generatedRoot.asFile.absolutePath)
    inputs.files(sourceSets.main.get().allSource)
    outputs.files(
        generatedRoot.file("ios/generated/CrossUiShowcase.swift"),
        generatedRoot.file("android/generated/CrossUiShowcase.kt"),
        generatedRoot.file("windows/generated/CrossUiShowcase.xaml"),
        generatedRoot.file("windows/generated/CrossUiShowcase.xaml.cs"),
        generatedRoot.file("windows/generated/CrossUiTypedFixture.xaml"),
        generatedRoot.file("windows/generated/CrossUiTypedFixture.xaml.cs"),
        generatedRoot.file("crossui-map.json"),
    )
}

tasks.named("build") {
    dependsOn(generateNativeUi)
}
