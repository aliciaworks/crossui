# CrossUI agent guide

## Editable sources

- Edit semantic models in `ui-ir/src/commonMain`.
- Edit authoring APIs in `ui-dsl/src/commonMain`.
- Edit shared state and async behavior in `runtime/src/commonMain`.
- Edit lowering and source generation in `legalizer` and `compiler`.
- Edit UI definitions in Kotlin provider modules such as `examples/login-app`.

Do not edit files below `hosts/*/generated` directly. Regenerate them with:

```text
./gradlew generateNativeUi
```

## Required verification

Run these commands after compiler, IR, DSL, or runtime changes:

```text
./gradlew build --no-configuration-cache
./gradlew generateNativeUi
./gradlew :gradle-plugin:test
dotnet build hosts/windows/CrossUi.Windows.csproj
```

Generated output must be deterministic. `git diff --check` must pass, and no
Rust source, Cargo manifest, FFI bridge, or runtime UI JSON renderer may be
introduced.

## Architecture constraints

- CrossUI DSL and IR describe semantics, not a shared rendered widget tree.
- Kotlin/JVM performs compilation and native source generation.
- The KMP runtime owns state, `StateFlow`, settings contracts, navigation,
  structured async effects, and lifecycle cancellation.
- Android output is Jetpack Compose.
- Apple output is SwiftUI.
- Windows output is WinUI 3 XAML.
- Platform views require an explicit compile-time `NativeViewRegistry` entry.
- Actions and bindings should use typed DSL helpers.
- Generated files carry `crossui-node` markers and `crossui-map.json`.

Keep source-code comments in English.
