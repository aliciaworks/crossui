# Adaptive large-screen layouts

CrossUI generates adaptive native layout code without identifying a device by
model name. Decisions use the current window width, so split screen, freeform
windows, folding, rotation, and Stage Manager remain responsive.

## Android

Tab navigation uses Material 3 `NavigationSuiteScaffold`, the same adaptive
navigation foundation used by Google's Now in Android reference app. The
platform selects the appropriate navigation bar or rail from current window
adaptive information instead of a handwritten device or width branch.

The selected route owns the remaining space and scrolls independently. Route
content observes `WindowInsets.safeDrawing`, is centered, and is capped at
840 dp; ordinary form-like roots are capped at 720 dp. Generated text maps
semantic styles to `MaterialTheme.typography`, inputs use the available content
width, and pickers lower to `ExposedDropdownMenuBox`.

The Gradle plugin adds
`androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0` to the
Android source set when it integrates generated Compose source. A semantic list
continues to use `LazyColumn` and is not nested inside another vertical scroll
container.

## iPadOS and macOS

Tab navigation uses SwiftUI's `.sidebarAdaptable` style. SwiftUI presents the
compact tab treatment when space is limited and the system sidebar treatment
when the window can support it. Routes without a native `Form` or `List` receive
a `ScrollView`, leading-aligned content, 20-point margins, and an 840-point
maximum width. Native scrolling containers remain responsible for their own
scroll behavior, and semantic forms use the native grouped form style.

Non-navigation content is centered with a 720-point maximum width. This avoids
stretching forms and controls across a large iPad or Mac window while preserving
full-width backgrounds and navigation containers.

The existing `macShortcut` DSL modifier lowers to SwiftUI
`.keyboardShortcut`, including Command, Option, Control, and Shift modifiers.

## Windows

Tab navigation lowers to WinUI 3 `NavigationView` with `PaneDisplayMode="Auto"`
and real `NavigationViewItem` entries. This preserves the control's compact and
minimal modes as a window narrows. Each route is generated once and selected
through native XAML visibility, while the selection action is still dispatched
to shared Kotlin state.

Content is centered at a 1064-pixel maximum width. A native
`VisualStateManager` switches desktop margins to compact 16-pixel margins below
the 640-pixel Windows breakpoint. Non-navigation roots use the same pattern with
an 840-pixel content maximum.

## State-owned selection

Use the binding overload when navigation belongs to shared KMP state:

```kotlin
tabNavigation(
    key = "app",
    active = bind(AppState::activeRoute),
    onChange = "navigate",
    routes = routes,
)
```

SwiftUI emits a selection `Binding`, Compose reads the current route from state,
and WinUI synchronizes `NavigationView` selection with its generated observable
state. The string-only overload remains available for platform-owned or static
navigation.

## Semantic boundary

Window width changes presentation only. It does not change shared Kotlin state,
actions, route identity, or content. CrossUI does not infer a list-detail
relationship from arbitrary stacks. A future list-detail IR must describe the
selection and detail semantics explicitly before generators adopt
`ListDetailPaneScaffold` or a multi-column `NavigationSplitView`.

The navigation and sizing choices are informed by Apple's
[Food Truck](https://github.com/apple/sample-food-truck), Microsoft's
[WinUI Gallery](https://github.com/microsoft/WinUI-Gallery), and Google's
[Now in Android](https://github.com/android/nowinandroid). CrossUI adopts their
native structural patterns, not their app-specific branding or business
hierarchies.
