# CrossUI

CrossUI is a Rust-first, native UI bridge. Application code produces a versioned,
serializable UI document. Small SwiftUI, Jetpack Compose, and eventually WinUI 3
hosts render that document with platform-native controls.

This repository currently implements the Rust MVP contract:

- `crossui-ir`: the stable, JSON-serializable UI boundary and keyed diff.
- `crossui-dsl`: typed Rust constructors for the cross-platform component set.
- `crossui-core`: unidirectional state, effects, capabilities, and the JSON bridge.
- `crossui-testing`: assertions for UI documents and diffs.
- `hosts`: reference SwiftUI and Compose renderers for the wire contract.

Run `cargo test --workspace` or `cargo run -p showcase`.

## Android bridge

`crossui-android` is an arm64 JNI bridge for the sample Compose host located at
the sibling repository `../testandroidapp`. The host calls Rust for the initial
document and for every UI event; Rust returns the next JSON document.

The JNI runtime only depends on `crossui_core::Application`. The sample
application is `examples/login-app`; replace that crate's `create_app` factory
with an application-specific crate to change the UI and business state without
changing the Android runtime or Compose renderer.

Build and copy the native library before building the Android app:

```powershell
.\scripts\build-android.ps1
```

The script requires the Android NDK installed at the standard Android Studio
location and the Rust `aarch64-linux-android` target. Then build the Android
app from `../testandroidapp` with `./gradlew.bat :app:assembleDebug`.

## Windows and iOS hosts

`hosts/windows` is a WinUI 3 renderer that loads `crossui-ffi.dll` through the
C ABI and renders the same document/event contract as Android. Build the Rust
library with `./scripts/build-windows.ps1`, then run `dotnet run` from
`hosts/windows`.

`hosts/ios` contains the SwiftUI renderer and `CrossUiNativeProvider`, which
binds the same C ABI. On macOS, run `./scripts/build-ios.sh`, add the resulting
`libcrossui_ffi.a` to an Xcode target, and initialize `CrossUiHost` with
`CrossUiNativeProvider()`.

## Boundary

The IR deliberately describes intent rather than pixels. A `Button` has an action,
variant, and accessibility semantics; each host chooses the native control and
platform treatment. Features that cannot be represented portably use an explicit
capability or platform view extension.
