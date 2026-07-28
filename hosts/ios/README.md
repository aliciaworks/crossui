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

Regenerate it from the repository root:

```shell
./gradlew generateNativeUi
```
