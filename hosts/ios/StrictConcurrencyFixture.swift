// Handwritten compile-only types that mirror the KMP Swift export boundary.

struct ShowcaseState: Sendable {
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
    case fixture
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
    case fixture
}
