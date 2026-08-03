# Reference Apple consumer

A minimal SwiftPM package showing how a native SwiftUI host consumes the
**Swift-exported** CrossUI boundary. This is where the Swift Export payoff
actually lands:

- `LoginState` (Kotlin `data class`) arrives as a `Sendable` Swift struct.
- `LoginAction` (Kotlin sealed interface) arrives as a Swift enum you can
  `switch` over — no string events, no ObjC casts.
- `StateFlow<LoginState>` arrives as a typed async sequence, observed with
  `for await` on the main actor.
- `@MainActor` / `Sendable` are preserved, so there are no hand-written
  `observe`/`send`/`cancel` bridge closures.

## Layout

- `Package.swift` — depends on the KGP-generated Swift Export SPM package.
- `Sources/CrossUiConsumer/LoginScreen.swift` — the consumer model + view
  (`LoginFeatureHost` / `LoginFeatureView`).
- The CrossUI Gradle plugin also emits SwiftUI source for a screen; add those
  generated files to the same target and wire them through `LoginFeatureHost`.
  The reference names avoid the generated `LoginScreen` / `LoginScreenModel` /
  `LoginScreenConnected` types so both can coexist.

## Build on macOS

Kotlin Swift Export only runs on macOS, so this package is built there:

```shell
# 1. Export the shared frameworks (KGP emits one SPM package per Apple target).
./gradlew :examples:login-app:embedSwiftExport

# 2. Point Package.swift at the generated package (adjust target/config):
#    examples/login-app/build/SPMPackage/iosSimulatorArm64/Debug

# 3. Build this consumer (or open it in Xcode).
cd hosts/ios/consumer
swift build
```

If the exported product names differ from `<Module>Library`, update the
`dependencies` in `Package.swift` to match the KGP-generated manifest
(`SPMPackage/<target>/<config>/Package.swift`).

## Creating the store

`AsyncStore<LoginState, LoginAction, LoginEffect>` is created in Kotlin via
`createLoginConnector(scope, service)` in `examples/login-app` — Kotlin owns the
`CoroutineScope`, reducer, and effect handler. Swift holds the store, collects
`states`, and calls `send(_ action: LoginAction)`.
