# CrossUI

CrossUI is a Kotlin-first native UI compiler. A semantic Kotlin DSL is validated
and lowered on the JVM during the build, then emitted as SwiftUI, Jetpack Compose,
and WinUI 3 source. Applications do not ship an IR interpreter or render a JSON
tree at runtime.

## Architecture

```text
Kotlin UI DSL
    -> semantic KMP IR
    -> JVM legalizer and platform lowering
    -> build-time source generation
       -> SwiftUI
       -> Jetpack Compose
       -> WinUI 3
```

The modules intentionally have different responsibilities:

- `ui-ir`: versioned semantic IR, target profiles, typed platform extensions,
  validation, serialization, and keyed document diffs.
- `ui-dsl`: the portable Kotlin DSL and explicit platform escape hatches.
- `legalizer`: JVM validation and platform/HIG policy derivation.
- `compiler`: JVM CLI and native source backends.
- `gradle-plugin`: reusable generation, doctor, and stale-output tasks.
- `runtime`: small KMP state/event/binding/navigation/environment library.
- `examples/login-app`: shared state and business-logic example.
- `examples/showcase`: DSL input compiled into native host source.

The KMP libraries build for JVM and JavaScript plus the current host's native
target. Apple targets are enabled on macOS, Windows uses `mingwX64`, and Linux
uses `linuxX64`. Native UI itself is not shared: each backend emits the platform's
own controls and navigation conventions.

## Build and generate

The project requires JDK 21. It uses the Gradle wrapper:

```shell
./gradlew build
./gradlew generateNativeUi
./gradlew integrationTestExistingKmp
```

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat generateNativeUi
```

Generation writes:

- `hosts/ios/generated/CrossUiShowcase.swift`
- `hosts/android/generated/CrossUiShowcase.kt`
- `hosts/windows/generated/CrossUiShowcase.xaml`
- `hosts/windows/generated/CrossUiShowcase.xaml.cs`

The showcase module owns this task, so generation happens as part of its `build`.
Generated native source is checked in to make platform review and integration
straightforward.

Normal consumer output remains under `build/generated/crossui` and is ignored.
Only `hosts/*/generated` is checked in because those files are deterministic
review fixtures compiled by CI.

For an existing KMP repository, use the `dev.crossui` Gradle plugin with a
compiled `UiDocumentProvider`. It emits separate target directories and does
not require JSON in application code. See
[`docs/existing-kmp-integration.md`](docs/existing-kmp-integration.md).
Localization supports native project resources and compile-time custom
resolvers; see [`docs/localization.md`](docs/localization.md).
Date and time controls use one cross-platform wire format; see
[`docs/temporal-values.md`](docs/temporal-values.md).

File and media request semantics integrate with host-native async pickers; see
[`docs/content-pickers.md`](docs/content-pickers.md).

## Compiler CLI

The standalone JVM compiler accepts a serialized semantic IR document:

```shell
./gradlew :compiler:run --args="--input ui.json --output generated --targets swiftui,compose,winui3 --name SettingsView"
```

The normal Kotlin-first workflow calls `CrossUiCompiler.generate` from a build
source module, as `examples/showcase` does. This avoids unstable Kotlin compiler
plugin APIs while keeping generation deterministic and ahead of runtime.

## DSL example

```kotlin
val settings = document(
    route("settings", "Settings") {
        +title("heading", "Settings")
        +form("settings-form") {
            +emailInput("email", "", "you@example.com", "email_changed")
            +button("save", "Save", "save")
        }
    },
)
```

Platform-specific nodes remain explicit and fail generation until the relevant
native escape hatch is supplied. Typed hints such as `iosHaptic`,
`androidElevation`, and `macShortcut` are validated against target profiles.

## Agent and async support

- `bind(State::property)` records typed state bindings.
- `event(Action.Save)` and typed value factories reduce stringly-typed events.
- Generated files contain stable node markers and source-map manifests.
- `StateFlow`, `Async<T>`, structured effect handling, settings contracts, and
  lifecycle cancellation live in the KMP runtime.
- Generated Compose screens can consume `UiConnector<State, Action>` directly;
  state collection and typed action dispatch are wired without a runtime UI
  interpreter.
- Typed SwiftUI output includes an Observation-backed connected model; typed
  WinUI output includes compiled XAML plus an `INotifyPropertyChanged` adapter.
- Apple-owned preferences can explicitly generate `@AppStorage`; shared
  business settings retain the KMP `SettingsStore` as their source of truth.
- `crossuiDoctor`, `verifyCrossUi`, `explain`, and `diff` provide deterministic
  diagnostics for developers and coding agents.

`integration-tests/existing-kmp-app` is a standalone consumer build. It resolves
CrossUI from Maven Local, generates a stateful Compose login screen, compiles the
generated source, runs the async reducer test, and packages a debug Android APK.

## License

MIT or Apache-2.0.
