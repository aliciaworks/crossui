// Handwritten compile-only types that mirror what Kotlin Swift Export generates
// for the KMP state/action boundary: States are Sendable value types and Actions
// are Sendable enums with associated values. In a real Apple host these types come
// from the Swift-exported framework (see swiftExport in the runtime and feature
// build scripts). This file lets the checked-in generated SwiftUI compile and be
// checked for strict concurrency without linking that framework on a non-macOS host.

struct ShowcaseState: Sendable {
    var activeRoute = "login"
    var email = ""
    var password = ""
    var search = ""
    var remember = false
    var volume = 0.5
    var volumeLabel = "Volume: 50%"
    var termsAccepted = false
    var language = "en-US"
    var darkMode = false
    var pickerStatus = "No file selected"
}

enum ShowcaseAction: Sendable {
    case navigate(route: String)
    case volumeChanged(value: Double)
    case languageChanged(value: String)
    case pickAttachment
    case pickPhotos
}

struct WinUiFixtureState: Sendable {
    var email = ""
    var status = ""
    var isSubmitting = false
    var canSubmit = true
    var darkMode = false
    var appointment: String?
}

enum WinUiFixtureAction: Sendable {
    case emailChanged(value: String)
    case darkModeChanged(value: Bool)
    case submit
}
