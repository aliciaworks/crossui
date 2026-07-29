# Adaptive large-screen layouts

CrossUI generates adaptive native layout code without identifying a device by
model name. Decisions use the current window width, so split screen, freeform
windows, folding, rotation, and Stage Manager remain responsive.

## Android

Tab navigation uses a Material 3 bottom `NavigationBar` below 600 dp and a
leading `NavigationRail` at 600 dp or wider. The selected route owns the
remaining space and scrolls independently. Large-screen route content is
centered and capped at 840 dp; ordinary form-like roots are capped at 720 dp.

The generated code uses `BoxWithConstraints`, so consumers do not need an
Activity-owned device check or a tablet resource qualifier. A semantic list
continues to use `LazyColumn` and is not nested inside another vertical scroll
container.

## iPadOS and macOS

Tab navigation uses SwiftUI's `.sidebarAdaptable` style. SwiftUI presents the
compact tab treatment when space is limited and the system sidebar treatment
when the window can support it. Routes without a native `Form` or `List` receive
a `ScrollView`; native scrolling containers remain responsible for their own
scroll behavior.

Non-navigation content is centered with a 720-point maximum width. This avoids
stretching forms and controls across a large iPad or Mac window while preserving
full-width backgrounds and navigation containers.

The existing `macShortcut` DSL modifier lowers to SwiftUI
`.keyboardShortcut`, including Command, Option, Control, and Shift modifiers.

## Semantic boundary

Window width changes presentation only. It does not change shared Kotlin state,
actions, route identity, or content. CrossUI does not infer a list-detail
relationship from arbitrary stacks. A future list-detail IR must describe the
selection and detail semantics explicitly before generators adopt
`ListDetailPaneScaffold` or a multi-column `NavigationSplitView`.
