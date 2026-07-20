# CrossUI architecture

CrossUI is a Rust-first declarative UI runtime. It is not a compiler for
arbitrary Rust code. Applications construct a constrained typed DSL, which is
lowered to a versioned JSON UI document and rendered by small native hosts.

```text
Authored Semantic IR (traits + extensions)
    │
    ├── TargetProfile (PlatformIdentity + Capabilities + InteractionProfile)
    ├── PlatformExtension validation (mismatch = Error by default)
    ├── Semantic resolution (traits → derived policies)
    └── HIG rule enforcement (YAML rule packs)
    ↓
Host Renderer (consumes extensions + resolved policies)
```

## Target profile

`TargetProfile` describes the lowering target through three orthogonal
dimensions defined in `crossui-ir/src/profile.rs`:

| Dimension | Description |
|---|---|
| `PlatformIdentity` | Which HIG applies: `Ios`, `IpadOs`, `WatchOs`, `MacOs`, `Android`, `Windows` |
| `Capabilities` | What the device can do: input (touch/pointer/keyboard/digital-crown), display class (wrist-compact/phone/tablet/desktop), windowing (single-scene/multi-window/menu-bar) |
| `InteractionProfile` | How users interact: `expected_duration` (brief/normal/extended), `glanceable` (watch-first vs engagement-first) |

Profiles carry an optional `os_version` for runtime availability checks.
Vendor is derived from identity: Apple (Ios/IpadOs/WatchOs/MacOs), Google
(Android), Microsoft (Windows).

## Platform extensions

Nodes carry zero or more `PlatformExtension` values serialized under the
`extensions` key:

```json
"extensions": [
  {"platform": "ios", "data": {"type": "presentation_style", "style": "sheet"}},
  {"platform": "ios", "data": {"type": "haptic_feedback", "feedback_type": "success"}},
  {"platform": "android", "data": {"type": "elevation", "dp": 4.0}},
  {"platform": "windows", "data": {"type": "corner_preference", "radius": 8.0}}
]
```

Extension variants per platform (`crossui-ir/src/extensions.rs`):

| Platform | Extension type | Description |
|---|---|---|
| **Ios** | `presentation_style` | Modal presentation: `sheet`, `full_screen_cover`, `popover` |
| | `haptic_feedback` | Haptic trigger: `light`, `medium`, `heavy`, `success`, `warning`, `error` |
| | `context_menu` | Context menu with action names |
| | `refreshable` | Pull-to-refresh action binding |
| | `swipe_action` | Swipe-to-delete action binding |
| **IpadOs** | `multicolumn_layout` | Multi-column layout with column count |
| | `stage_manager` | Stage Manager window preferences (width/height) |
| | `sidebar` | Sidebar visibility toggle |
| **WatchOs** | `crown_input` | Digital Crown sensitivity: `low`, `normal`, `high` |
| | `complication` | Complication family: `circular`, `rectangular`, `corner`, `extra_large`, `graphic` |
| | `glance_priority` | Smart Stack priority: `low`, `normal`, `high` |
| | `compact_navigation` | Navigation depth hint |
| **MacOs** | `keyboard_shortcut` | Key + modifiers (command, shift, option, control) |
| | `toolbar_item` | Toolbar item placement by ID |
| | `menu_command` | Menu-bar command registration by path |
| **Android** | `elevation` | Material 3 elevation override in dp |
| | `dynamic_color_hint` | Hue hint for dynamic color generation |
| **Windows** | `connected_animation` | Connected animation identifier |
| | `corner_preference` | Corner radius in device-independent pixels |

### Extension validation

By default, a `PlatformExtension` targeting a platform that doesn't match the
host's `TargetProfile.platform` is treated as an **error** (not silently
ignored). The `UnsupportedExtensionPolicy` enum allows callers to choose
`Error` (default), `Warning`, or `Strip` at lowering time.

## Stable boundary

`crossui-ir` owns `UiDocument`, the component nodes, semantics, theme tokens,
and keyed `DiffOp`s. Hosts support version `2` only and reject unknown
versions. Node keys are globally unique and identify controls across updates.

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

Hosts parse the `extensions` array from each node's JSON and apply
platform-specific rendering hooks. Each host ignores extensions targeting other
platforms and applies its own platform's extensions to the native control tree.

## Native-only features

`PlatformView` and `NativeModuleRegistry` are explicit escape hatches. A
platform view names its target platform and payload. A native module declares
capabilities and is rejected on platform or capability mismatch. Generic hosts
show a diagnostic until a target application registers a renderer.

## Experimental source export

`crossui-export` produces one-way SwiftUI, Jetpack Compose, or WinUI source
starting points. Generated actions are stubs and platform views are rejected;
the runtime remains the supported production delivery model.
