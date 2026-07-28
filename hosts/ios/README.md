# Apple host

`generated/CrossUiShowcase.swift` is SwiftUI source emitted during the Gradle
build. Add it directly to an Xcode target and initialize `CrossUiShowcase` with
an event dispatcher.

The generated view uses native SwiftUI controls and contains no C ABI, Rust
static library, JSON document provider, or runtime UI interpreter.

Regenerate it from the repository root:

```shell
./gradlew generateNativeUi
```
