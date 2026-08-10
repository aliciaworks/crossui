# Motion

CrossUI treats motion as a **semantic property of a node**, not a
platform-specific animation call. When a node appears and disappears (typically
through `visibleWhen`), each generator lowers its `MotionPreset` to the native
motion idiom of the target platform, so generated UI carries the same motion
quality as the platform's reference apps (WinUI Gallery, Material 3 Expressive,
and Apple's SwiftUI samples).

## Presets

| Preset | SwiftUI | Compose (Material 3) | WinUI 3 |
| --- | --- | --- | --- |
| `Default` / `Fade` | `.transition(.opacity)` | `fadeIn()` / `fadeOut()` | `ContentThemeTransition` |
| `Scale` | `.transition(.scale)` | `scaleIn()` / `scaleOut()` | `PopupThemeTransition` |
| `SlideUp` | `.transition(.move(edge: .bottom))` | `slideInVertically()` / `slideOutVertically()` | `EntranceThemeTransition` |
| `Blend` | `.opacity.combined(with: .scale)` | `fadeIn() + scaleIn()` / `fadeOut() + scaleOut()` | `PopupThemeTransition` |

`Default` is used when no preset is declared, so **any `visibleWhen` node
animates with the platform-standard fade without authoring changes**.

## DSL

```kotlin
+loading("spinner", "Signing in")
    .visibleWhen(bind(State::isSubmitting))
    .appear(MotionPreset.Blend) // fade-through, Material 3 expressive style
```

## Generated output

- **SwiftUI** — the conditional is emitted with a `.transition(...)` on the
  content and a spring `.animation(_:value:)` keyed on the visibility binding:

  ```swift
  if state.isSubmitting {
      ProgressView("Signing in")
          .transition(.opacity.combined(with: .scale))
  }
  .animation(.spring(duration: 0.35, bounce: 0.25), value: state.isSubmitting)
  ```

- **Compose** — `AnimatedVisibility` with per-preset enter/exit:

  ```kotlin
  AnimatedVisibility(
      visible = state.isSubmitting,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
  ) {
      CircularProgressIndicator()
  }
  ```

- **WinUI 3** — a `Border` bound to `BooleanToVisibility(...)` carrying a theme
  `TransitionCollection`, the pattern WinUI Gallery uses for animated content:

  ```xml
  <Border Visibility="{x:Bind BooleanToVisibility(State.IsSubmitting), Mode=OneWay}">
      <Border.Transitions>
          <TransitionCollection>
              <PopupThemeTransition />
          </TransitionCollection>
      </Border.Transitions>
      <ProgressRing IsActive="True" />
  </Border>
  ```

## Scope and future work

- Route changes keep the native navigation transitions of each platform
  (`NavigationStack`, `NavigationSuiteScaffold`, `NavigationView`).
- **Reduced-motion preference** is a planned policy: generators will lower a
  global motion preference by emitting each platform's reduced-motion branch.
- **Value-change animation** (numbers, colors, sizes) is a planned follow-up
  using SwiftUI `.contentTransition`, Compose `animate*AsState`, and WinUI
  `VisualTransition`/`Storyboard`.
