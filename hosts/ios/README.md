# Apple host

`generated/CrossUiShowcase.swift` is SwiftUI source emitted during the Gradle
build. Add generated Swift files directly to an Xcode target.

The generated view uses native SwiftUI controls and contains no C ABI, Rust
static library, JSON document provider, or runtime UI interpreter.

For a typed document, CrossUI also emits an Observation-backed model and a
connected view:

```swift
let model = SettingsScreenModel(
    initialState: feature.state,
    observe: { receive in feature.observeState(receive) },
    send: { action in feature.send(action) }
)

SettingsScreenConnected(
    model: model,
    actions: { action, value in
        SettingsActions.map(action: action, value: value)
    }
)
```

The host-provided `observe` closure returns a cancellation closure. Call
`model.cancel()` when the feature is permanently released. State updates are
marshalled onto `@MainActor`, `@Observable` drives SwiftUI invalidation, and
generated bindings send typed actions through the host mapper.

Apple-owned preferences can opt into readable generated `@AppStorage`:

```kotlin
val darkMode = appStorage("appearance.dark_mode", false)

typedDocument<SettingsState, SettingsAction>(
    root = settingsScreen,
    settings = listOf(
        setting(
            darkMode,
            SettingsState::darkMode,
            event("dark_mode_changed") {
                SettingsAction.DarkModeChanged(it.toBoolean())
            },
        ),
    ),
)
```

CrossUI synchronizes the stored value and typed KMP state in both directions.
Equality guards prevent the synchronization from redispatching its own update.
Use this only when the platform UI owns the preference. Settings owned by shared
business logic keep the default `SettingOwnership.SharedState` and use the KMP
`SettingsStore`.

For preferences owned by shared Kotlin business logic, instantiate the Apple
runtime adapter instead:

```kotlin
val settings: SettingsStore = UserDefaultsSettingsStore()
```

The adapter supports Boolean, String, Int, and Double preferences, preserves
the declared default when a key is absent, and returns one shared `StateFlow`
per key. It rejects `PlatformUi`, secure, and saved-state declarations so
ownership mistakes fail early.

Regenerate it from the repository root:

```shell
./gradlew generateNativeUi
```
