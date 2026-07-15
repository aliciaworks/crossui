# CrossUI

CrossUI is a Rust-first, native UI bridge. Application code produces a versioned,
serializable UI document. Small SwiftUI, Jetpack Compose, and eventually WinUI 3
hosts render that document with platform-native controls.

See [the architecture guide](docs/architecture.md) for runtime boundaries and
host responsibilities.

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
`CrossUiNativeProvider()`. Set `CROSSUI_IOS_TARGET=aarch64-apple-ios-sim` when
building for an Apple Silicon simulator.

## Boundary

The IR deliberately describes intent rather than pixels. A `Button` has an action,
variant, and accessibility semantics; each host chooses the native control and
platform treatment. Features that cannot be represented portably use an explicit
capability or platform view extension.

`crossui_core::ComponentCatalog` declares the portable component contract.
See [the component catalog](docs/component-catalog.md) for the host support
matrix.
Hosts render unsupported `platform_view` nodes as visible diagnostics instead of
silently substituting unrelated native controls. The current WinUI and SwiftUI
hosts natively map text, button, input (including secure input), stack, list,
form, loading, navigation, and route, and resolve `spacing.sm`, `spacing.md`,
and `spacing.lg` to platform-native layout values. The semantic `primary`
color is rendered as a WinUI brush and SwiftUI button tint; a theme change
invalidates the IR root so hosts refresh the native treatment.

On Android, `theme.android.dynamic_color` selects the Android system dynamic
scheme where available, and `theme.android.material3_expressive` selects
`MaterialExpressiveTheme`; when it is false the same host uses standard
Material 3. These flags are Android-only and do not force an iOS or Windows
visual style.

All native hosts currently accept IR version `1` only and reject unknown
versions before rendering. A version bump is therefore an explicit host
compatibility change rather than a best-effort decode.

The DSL provides `input`, `input_with_placeholder`, and `secure_input`; all
three preserve their input semantics through the JSON boundary. Native hosts use
their platform's secure text field for `secure_input`, rather than merely hiding
the value with a visual style.

`ButtonVariant::Primary`, `Secondary`, and `Destructive` remain semantic. The
Android host selects Material 3 filled, outlined, or error-colored buttons;
SwiftUI uses tint or its destructive role; WinUI uses its native button with the
configured primary or destructive brush. Use `button`, `secondary_button`, or
`destructive_button` from the DSL rather than hand-writing the IR variant.

## Experimental source export

The runtime remains the supported delivery path. For migration or prototyping,
`crossui-export` can generate a one-way native source starting point from an IR
document. Run the showcase with `cargo run -p showcase -- swiftui`, `compose`,
or `winui3`. Generated code deliberately leaves actions as target-project
stubs and rejects `PlatformView`; it is not a synchronised round trip.

Use `crossui_dsl::platform_view` only for an intentional platform extension,
such as a map or a camera preview. It carries a target `Platform`, a host-owned
name, and JSON payload. `CapabilitySet` is constructed by the native host, so
an application must check an advertised capability before requesting a native
operation; unavailable capabilities are explicit errors, never silent fallbacks.

For operations such as camera, storage, or maps, host integrations can route a
Rust `NativeRequest` through `NativeModuleRegistry`. A host registers only
modules for its own platform. The registry rejects platform mismatches and
checks the request's advertised capability before calling the adapter.
