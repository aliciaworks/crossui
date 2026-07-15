# CrossUI architecture

CrossUI is a Rust-first declarative UI runtime. It is not a compiler for
arbitrary Rust code. Applications construct a constrained typed DSL, which is
lowered to a versioned JSON UI document and rendered by small native hosts.

```text
Rust DSL -> UiDocument -> JSON/FFI -> SwiftUI | Compose | WinUI 3
     ^                                      |
     +---- Action <- RuntimeEvent <- native controls
```

## Stable boundary

`crossui-ir` owns `UiDocument`, the component nodes, semantics, theme tokens,
and keyed `DiffOp`s. Hosts support version `1` only and reject unknown versions.
Node keys are globally unique and identify controls across updates.

`crossui-core` owns the unidirectional store. A host sends a `RuntimeEvent`; a
reducer creates the next document, keyed patch, and serializable effects. The
host may apply a leaf-only patch in place, or rebuild for structural changes.

## Host responsibilities

Hosts do not contain application reducers or business state. They map IR nodes
to native controls, map native events to actions, execute host-owned effects,
and preserve platform conventions for typography, accessibility, navigation,
and system appearance.

Theme tokens are semantic. For example, `primary` becomes a native WinUI brush
or SwiftUI tint, while the Android-only flags select dynamic color and Material
3 Expressive behavior.

## Native-only features

`PlatformView` and `NativeModuleRegistry` are explicit escape hatches. A
platform view names its target platform and payload. A native module declares
capabilities and is rejected on platform or capability mismatch. Generic hosts
show a diagnostic until a target application registers a renderer.

## Experimental source export

`crossui-export` produces one-way SwiftUI, Jetpack Compose, or WinUI source
starting points. Generated actions are stubs and platform views are rejected;
the runtime remains the supported production delivery model.
