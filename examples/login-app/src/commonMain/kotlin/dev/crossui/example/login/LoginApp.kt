package dev.crossui.example.login

import dev.crossui.dsl.*
import dev.crossui.ir.*
import dev.crossui.runtime.DocumentStore
import dev.crossui.runtime.Reducer
import dev.crossui.runtime.Reduction

data class LoginState(
    val email: String = "",
    val message: String? = null,
    val screen: Screen = Screen.Login,
)

sealed interface Screen {
    data object Login : Screen
    data object Projects : Screen
    data class Detail(val project: String) : Screen
}

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data object Submit : LoginAction
    data class ProjectSelected(val key: String) : LoginAction
    data object Back : LoginAction
}

sealed interface LoginEffect {
    data class Notification(val message: String) : LoginEffect
}

object LoginReducer : Reducer<LoginState, LoginAction, LoginEffect> {
    override fun reduce(
        state: LoginState,
        action: LoginAction,
    ): Reduction<LoginState, LoginEffect> = when (action) {
        is LoginAction.EmailChanged ->
            Reduction(state.copy(email = action.value, message = null))
        LoginAction.Submit -> if ('@' in state.email) {
            Reduction(
                state.copy(screen = Screen.Projects),
                listOf(LoginEffect.Notification("Signed in successfully")),
            )
        } else {
            Reduction(state.copy(message = "Enter a valid email address"))
        }
        is LoginAction.ProjectSelected ->
            Reduction(state.copy(screen = Screen.Detail(action.key)))
        LoginAction.Back -> Reduction(state.copy(screen = Screen.Projects))
    }
}

fun createLoginApp() = DocumentStore(
    LoginState(),
    LoginReducer,
    ::loginDocument,
)

object LoginUiProvider : UiDocumentProvider {
    override fun document(): UiDocument = loginDocument(LoginState())
}

fun loginDocument(state: LoginState): UiDocument {
    val active = when (state.screen) {
        Screen.Login -> "login"
        Screen.Projects -> "projects"
        is Screen.Detail -> "detail"
    }
    val detailTitle = (state.screen as? Screen.Detail)
        ?.project
        ?.replace("project-", "Project ")
        ?: "Project"

    return typedDocument<LoginState, LoginAction>(
        tabNavigation(
            "app-navigation",
            active,
            listOf(
                route("login", "Sign in") {
                    +vstack("content") {
                        +title("heading", "Welcome")
                        +form("login-form") {
                            +emailInput(
                                "email",
                                bind(LoginState::email),
                                "Email address",
                                event("email_changed") { LoginAction.EmailChanged(it) },
                            ).accessibility("Email address", SemanticRole.TextField)
                            state.message?.let { +text("message", it) }
                            +button(
                                "submit",
                                "Continue",
                                event(LoginAction.Submit),
                            )
                                .accessibility("Continue", SemanticRole.Button)
                        }
                    }
                },
                route("projects", "Projects") {
                    +title("projects-heading", "Choose a project")
                    +selectableList(
                        "projects-list",
                        event("project_selected") {
                            LoginAction.ProjectSelected(it)
                        },
                        listOf(
                            text("project-alpha", "Project Alpha"),
                            text("project-beta", "Project Beta"),
                            text("project-gamma", "Project Gamma"),
                        ),
                    )
                },
                route("detail", detailTitle) {
                    +title("detail-heading", detailTitle)
                    +text("detail-copy", "This screen is selected by shared Kotlin state.")
                    +button(
                        "back",
                        "Back to projects",
                        event(LoginAction.Back),
                    )
                },
            ),
        ),
        Theme(
            tokens = mapOf(
                "primary" to TokenValue.Color("#6750A4"),
                "spacing.md" to TokenValue.Number(16.0),
            ),
            android = AndroidTheme(
                material3Expressive = true,
                dynamicColor = true,
            ),
        ),
    )
}
