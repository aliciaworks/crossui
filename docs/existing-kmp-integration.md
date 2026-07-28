# Existing KMP project integration

CrossUI is designed for incremental adoption. Existing applications can keep
their current Compose, SwiftUI, UIKit, Android View, or WinUI screens and add one
generated screen at a time.

## 1. Publish a local development build

From the CrossUI repository:

```shell
./gradlew publishCrossUiToMavenLocal
```

In the consuming repository, allow local plugin and dependency resolution:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
```

## 2. Add CrossUI to a KMP definitions module

The definitions module needs a JVM target because source generation runs on the
development machine or CI:

```kotlin
plugins {
    kotlin("multiplatform")
    // Declare the AGP version in the root project or version catalog.
    id("com.android.kotlin.multiplatform.library")
    id("dev.crossui") version "0.1.0"
}

kotlin {
    jvm()
    android {
        namespace = "com.example.ui.definitions"
        compileSdk = 35
        minSdk = 24
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("dev.crossui:ui-ir:0.1.0")
            implementation("dev.crossui:ui-dsl:0.1.0")
            implementation("dev.crossui:runtime:0.1.0")
        }
    }
}

crossui {
    providerClass.set("com.example.settings.SettingsUiProvider")
    typeName.set("SettingsScreen")
    targets.set(listOf("swiftui", "compose", "winui3"))
}
```

The plugin automatically adds the module's JVM artifact and runtime
dependencies to the provider classpath. It also registers the generated Compose
directory with `androidMain` and runs generation before Android compilation. A
dedicated `ui-definitions` module is recommended so generation never creates a
compile cycle with an application source set.

## 3. Provide a typed Kotlin definition

```kotlin
data class SettingsState(
    val email: String = "",
    val darkMode: Boolean = false,
)

sealed interface SettingsAction {
    data class EmailChanged(val value: String) : SettingsAction
    data object Save : SettingsAction
}

object SettingsUiProvider : UiDocumentProvider {
    override fun document() = typedDocument<SettingsState, SettingsAction>(
        route("settings", "Settings") {
            +emailInput(
                key = "email",
                value = bind(SettingsState::email),
                placeholder = "Email",
                onChange = event("email_changed") {
                    SettingsAction.EmailChanged(it)
                },
            ).fromSource("ui/SettingsUi.kt", 18)

            +button(
                key = "save",
                label = "Save",
                action = event(SettingsAction.Save),
            )
        },
        stateType = "com.example.settings.SettingsState",
        actionType = "com.example.settings.SettingsAction",
    )
}
```

## 4. Generate and inspect

```shell
./gradlew generateCrossUi
./gradlew crossuiDoctor
./gradlew verifyCrossUi
```

Default outputs:

```text
build/generated/crossui/
├── swiftui/
├── compose/
└── winui3/
```

Each target directory includes `crossui-map.json`. Generated files contain
`crossui-node:<key>` markers so platform compiler errors can be traced to the
semantic node and optional DSL source location.

## Incremental adoption modes

1. Share only KMP state and async effects while retaining all existing UI.
2. Generate one reusable component inside an existing native screen.
3. Generate one complete screen while retaining native navigation.
4. Generate a feature flow.

CrossUI does not require an all-at-once migration.

## Existing native components

Register an explicit native view implementation during generation:

```kotlin
val nativeViews = NativeViewRegistry.build {
    register(
        ExportTarget.JetpackCompose,
        "payment-sheet",
        "ExistingPaymentSheet(payload = {{payload}})",
    )
}
```

Generation fails if a `PlatformView` has no implementation for the selected
target. Unsupported components never become runtime placeholders.
