// swift-tools-version: 5.9
// Reference Apple consumer for CrossUI + Kotlin Swift Export.
//
// On macOS, first export the shared frameworks from the KMP modules:
//
//   ./gradlew :examples:login-app:embedSwiftExport -P... (or the per-target
//   swiftExport tasks). KGP emits an SPM package per Apple target under
//   examples/login-app/build/SPMPackage/<target>/<configuration>.
//
// Then point the `LoginApp` dependency below at that directory and, if needed,
// adjust the product names (KGP names them `<Module>Library`).

import PackageDescription

let package = Package(
    name: "CrossUiConsumer",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
        .tvOS(.v17),
        .watchOS(.v10),
    ],
    products: [
        .library(name: "CrossUiConsumer", targets: ["CrossUiConsumer"]),
    ],
    dependencies: [
        // Swift-exported modules: LoginApp (feature) + CrossUiRuntime + Coroutines.
        // Adjust this path to the KGP-generated SPM package on your Mac.
        .package(path: "../../../examples/login-app/build/SPMPackage/iosSimulatorArm64/Debug"),
    ],
    targets: [
        .target(
            name: "CrossUiConsumer",
            dependencies: [
                .product(name: "LoginAppLibrary", package: "LoginApp"),
                .product(name: "CrossUiRuntimeLibrary", package: "LoginApp"),
            ],
            path: "Sources/CrossUiConsumer"
        ),
    ]
)
