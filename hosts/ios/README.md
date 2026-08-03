# Apple host

`generated/CrossUiShowcase.swift` is SwiftUI source emitted during the Gradle
build. Add generated Swift files directly to an Xcode target.

The generated view uses native SwiftUI controls and contains no C ABI, Rust
static library, JSON document provider, or runtime UI interpreter.

## Swift Export boundary

The typed state/action seam is supplied by Kotlin **Swift Export** (see
`swiftExport` in `runtime/build.gradle.kts` and the feature build script). On a
macOS host, the runtime is exported as the `CrossUiRuntime` Swift module and the
feature surface (for example `LoginApp`) as its own module, producing real Swift
types instead of the legacy ObjC interop headers:

| Kotlin | Swift Export output |
| --- | --- |
| `data class State` | `Sendable` struct with value semantics |
| `sealed interface Action` | enum with associated values |
| `StateFlow<State>` | typed Swift sequence (coroutines exported) |
| `@MainActor` observation | preserved actor isolation |

`hosts/ios/StrictConcurrencyFixture.swift` is a compile-only mirror of that
exported boundary so the checked-in generated Swift can be compiled and checked
for strict concurrency on hosts that cannot link the framework.

## Other Apple platforms

The runtime and the example feature export for **every Apple platform with a
Kotlin/Native toolchain target** — iOS, macOS, watchOS, and tvOS (arm64 +
simulator) — so one KMP module feeds SwiftUI on all of them. (visionOS can be
added once the Kotlin Gradle plugin exposes its target accessor.) Generated Swift
guards the iOS/macOS-only
modifiers (`.sidebarAdaptable`, `.keyboardShortcut`, `.submitLabel`,
`.formStyle(.grouped)`) with `#if os(iOS) || os(macOS)` so common layouts
compile on watchOS, tvOS, and visionOS as well. Controls that a given platform
does not provide (for example `SecureField` and `Slider` on tvOS, or
`DatePicker` on watchOS) still need platform-specific authoring.

Generated `@Observable` code requires iOS 17+, macOS 14+, tvOS 17+,
watchOS 10+, or visionOS 1+. Build and verify the exported Swift frameworks on
a macOS host; each Swift Export task emits a framework and an SPM package per
Apple target.

See `consumer/` for a reference SwiftPM host that consumes the Swift-exported
modules directly: typed `StateFlow` observation, pattern-matchable `Action`
enums, and no hand-written bridge closures.

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
