// Reference consumer: how a native SwiftUI host uses the Swift-exported CrossUI
// runtime and feature modules (see `swiftExport` in the runtime and feature
// build scripts). Add the generated CrossUI SwiftUI files from the CrossUI
// Gradle plugin alongside this target and wire them through `LoginFeatureHost`.
//
// The names here deliberately avoid the CrossUI-generated `LoginScreen`,
// `LoginScreenModel`, and `LoginScreenConnected` types so both can coexist.
//
// Swift Export turns the KMP boundary into real Swift types:
//   - data class LoginState        -> Sendable struct
//   - sealed interface LoginAction -> enum with associated values
//   - StateFlow<LoginState>        -> typed async sequence
//   - @MainActor / Sendable        -> preserved (no ObjC bridging shims)
// The store is created in Kotlin (Kotlin owns the CoroutineScope and reducer)
// and handed to Swift, so the host never touches ObjC interop.

import SwiftUI
import Observation
import LoginApp
import CrossUiRuntime

/// Owns the shared feature state and forwards typed actions to KMP.
@MainActor
@Observable
final class LoginFeatureHost {
    private(set) var state: LoginState
    private let store: AsyncStore<LoginState, LoginAction, LoginEffect>
    private var observation: Task<Void, Never>?

    init(store: AsyncStore<LoginState, LoginAction, LoginEffect>) {
        self.store = store
        self.state = store.state
        // StateFlow is exported as a typed async sequence; @MainActor flows
        // straight onto the model without a hand-written observer bridge.
        observation = Task { [weak self] in
            for await next in store.states.values {
                self?.state = next
            }
        }
    }

    func send(_ action: LoginAction) {
        // Dispatch a pattern-matchable Swift enum instead of an event string.
        store.send(action)
    }

    func stop() {
        observation?.cancel()
        store.close()
    }
}

struct LoginFeatureView: View {
    @Bindable var host: LoginFeatureHost

    var body: some View {
        Form {
            TextField("Email", text: Binding(
                get: { host.state.email },
                set: { host.send(.emailChanged(value: $0)) }
            ))
            .textContentType(.emailAddress)
            .autocapitalization(.none)

            if host.state.isSubmitting {
                ProgressView("Signing in")
            } else if let message = host.state.message {
                Text(message).foregroundStyle(.red)
            }

            Button("Continue") { host.send(.submit) }
                .disabled(!host.state.canSubmit)
        }
    }
}

// The store itself is created in Kotlin — `createLoginConnector(scope,
// service)` in `examples/login-app/.../LoginApp.kt` — then handed to Swift as
// an `AsyncStore<LoginState, LoginAction, LoginEffect>`. Kotlin owns the
// CoroutineScope and reducer; Swift only observes state and sends typed
// actions. Adjust the exported symbol name to what Swift Export emits.
//
//   let store = createLoginConnector(scope: ..., service: ...)
//   LoginFeatureView(host: LoginFeatureHost(store: store))
