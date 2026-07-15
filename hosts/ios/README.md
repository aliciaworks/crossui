# SwiftUI host

`CrossUiRenderer.swift` renders the versioned CrossUI document using SwiftUI
controls. `CrossUiNativeProvider.swift` invokes the Rust C ABI and has no
application-specific business logic.

The renderer maps text, button, secure and plain input, stacks, lists, forms,
loading, navigation, and routes. Accessibility labels and hints are forwarded to
SwiftUI. Unsupported or platform-specific nodes are shown explicitly instead of
being silently replaced.

## Build the Rust library

On macOS, build a device library:

```bash
./scripts/build-ios.sh
```

To build for an Apple Silicon simulator:

```bash
CROSSUI_IOS_TARGET=aarch64-apple-ios-sim ./scripts/build-ios.sh
```

The output is `target/<target>/release/libcrossui_ffi.a`. Add the static library
and these two Swift source files to an iOS Xcode target, then create the root
view with `CrossUiHost(provider: CrossUiNativeProvider())`.

To render a target-only `PlatformView`, inject a closure when constructing the
host. The closure receives the name, platform, and typed JSON payload through
`CrossUiNode`; return `AnyView` for known iOS views and `nil` to keep the
explicit unsupported diagnostic.

```swift
CrossUiHost(provider: CrossUiNativeProvider()) { node in
    guard node.type == "platform_view", node.name == "map" else { return nil }
    return AnyView(MyMapView(payload: node.payload))
}
```
