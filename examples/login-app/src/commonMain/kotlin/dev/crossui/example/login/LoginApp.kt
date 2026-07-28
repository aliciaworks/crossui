package dev.crossui.example.login

import dev.crossui.dsl.*
import dev.crossui.ir.*
import dev.crossui.runtime.*
import kotlinx.coroutines.CoroutineScope

data class LoginState(
    val email: String = "",
    val message: String? = null,
    val submission: Async<Unit> = Async.Idle,
    val screen: Screen = Screen.Login,
) {
    val statusText: String get() = message.orEmpty()
    val isSubmitting: Boolean get() = submission is Async.Loading
    val canSubmit: Boolean get() = !isSubmitting
}

sealed interface Screen {
    data object Login : Screen
    data object Projects : Screen
    data class Detail(val project: String) : Screen
}

sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data object Submit : LoginAction
    data object LoginSucceeded : LoginAction
    data class LoginFailed(val error: UiError) : LoginAction
    data class ProjectSelected(val key: String) : LoginAction
    data class Navigate(val route: String) : LoginAction
    data object Back : LoginAction
}

sealed interface LoginEffect {
    data class Authenticate(val email: String) : LoginEffect
}

fun interface LoginService {
    suspend fun authenticate(email: String)
}

object LoginReducer : Reducer<LoginState, LoginAction, LoginEffect> {
    override fun reduce(
        state: LoginState,
        action: LoginAction,
    ): Reduction<LoginState, LoginEffect> = when (action) {
        is LoginAction.EmailChanged ->
            Reduction(
                state.copy(
                    email = action.value,
                    message = null,
                    submission = Async.Idle,
                ),
            )
        LoginAction.Submit -> if ('@' in state.email) {
            Reduction(
                state.copy(submission = Async.Loading, message = null),
                listOf(LoginEffect.Authenticate(state.email)),
            )
        } else {
            Reduction(state.copy(message = "Enter a valid email address"))
        }
        LoginAction.LoginSucceeded -> Reduction(
            state.copy(
                screen = Screen.Projects,
                submission = Async.Success(Unit),
                message = null,
            ),
        )
        is LoginAction.LoginFailed -> Reduction(
            state.copy(
                submission = Async.Failure(action.error),
                message = action.error.message,
            ),
        )
        is LoginAction.ProjectSelected ->
            Reduction(state.copy(screen = Screen.Detail(action.key)))
        is LoginAction.Navigate -> Reduction(
            state.copy(
                screen = when (action.route) {
                    "login" -> Screen.Login
                    "projects" -> Screen.Projects
                    else -> state.screen
                },
            ),
        )
        LoginAction.Back -> Reduction(state.copy(screen = Screen.Projects))
    }
}

fun createLoginApp() = DocumentStore(
    LoginState(),
    LoginReducer,
    ::loginDocument,
)

fun createLoginConnector(
    scope: CoroutineScope,
    service: LoginService,
) = AsyncStore(
    initialState = LoginState(),
    reducer = LoginReducer,
    scope = scope,
    handler = AsyncEffectHandler { effect ->
        when (effect) {
            is LoginEffect.Authenticate -> try {
                service.authenticate(effect.email)
                LoginAction.LoginSucceeded
            } catch (error: Throwable) {
                LoginAction.LoginFailed(
                    UiError(
                        code = "login_failed",
                        message = error.message ?: "Sign in failed",
                        retryable = true,
                    ),
                )
            }
        }
    },
)

object LoginActions : UiActionMapper<LoginAction> {
    override fun map(action: String, value: String?): LoginAction = when (action) {
        "email_changed" -> LoginAction.EmailChanged(value.orEmpty())
        "submit" -> LoginAction.Submit
        "project_selected" -> LoginAction.ProjectSelected(requireNotNull(value))
        "navigate" -> LoginAction.Navigate(requireNotNull(value))
        "back" -> LoginAction.Back
        else -> error("Unknown login action: $action")
    }
}

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
                            +text("message", bind(LoginState::statusText))
                            +loading("login-loading", "Signing in")
                                .visibleWhen(bind(LoginState::isSubmitting))
                            +button(
                                "submit",
                                "Continue",
                                event(LoginAction.Submit),
                            )
                                .accessibility("Continue", SemanticRole.Button)
                                .enabledWhen(bind(LoginState::canSubmit))
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
        stateType = "dev.crossui.example.login.LoginState",
        actionType = "dev.crossui.example.login.LoginAction",
    )
}
