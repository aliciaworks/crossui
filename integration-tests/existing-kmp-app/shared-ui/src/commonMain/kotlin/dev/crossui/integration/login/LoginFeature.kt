package dev.crossui.integration.login

import dev.crossui.dsl.accessibility
import dev.crossui.dsl.bind
import dev.crossui.dsl.button
import dev.crossui.dsl.datePicker
import dev.crossui.dsl.emailInput
import dev.crossui.dsl.event
import dev.crossui.dsl.form
import dev.crossui.dsl.loading
import dev.crossui.dsl.picker
import dev.crossui.dsl.pickerOption
import dev.crossui.dsl.route
import dev.crossui.dsl.tabNavigation
import dev.crossui.dsl.text
import dev.crossui.dsl.typedDocument
import dev.crossui.dsl.enabledWhen
import dev.crossui.dsl.visibleWhen
import dev.crossui.ir.SemanticRole
import dev.crossui.ir.DatePickerMode
import dev.crossui.ir.AndroidTheme
import dev.crossui.ir.Theme
import dev.crossui.ir.UiDocument
import dev.crossui.ir.UiDocumentProvider
import dev.crossui.runtime.Async
import dev.crossui.runtime.AsyncEffectHandler
import dev.crossui.runtime.AsyncStore
import dev.crossui.runtime.Reducer
import dev.crossui.runtime.Reduction
import dev.crossui.runtime.UiActionMapper
import dev.crossui.runtime.UiError
import kotlinx.coroutines.CoroutineScope

data class LoginState(
    val activeRoute: String = "login",
    val email: String = "",
    val language: String = "en",
    val message: String? = null,
    val submission: Async<Unit> = Async.Idle,
    val appointment: String? = null,
) {
    val statusText: String get() = message.orEmpty()
    val isSubmitting: Boolean get() = submission is Async.Loading
    val canSubmit: Boolean get() = !isSubmitting
}

sealed interface LoginAction {
    data class Navigate(val route: String) : LoginAction
    data class EmailChanged(val value: String) : LoginAction
    data class LanguageChanged(val value: String) : LoginAction
    data class AppointmentChanged(val value: String?) : LoginAction
    data object Submit : LoginAction
    data object Succeeded : LoginAction
    data class Failed(val error: UiError) : LoginAction
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
        is LoginAction.Navigate -> Reduction(state.copy(activeRoute = action.route))
        is LoginAction.EmailChanged -> Reduction(
            state.copy(
                email = action.value,
                message = null,
                submission = Async.Idle,
            ),
        )
        is LoginAction.LanguageChanged -> Reduction(state.copy(language = action.value))
        is LoginAction.AppointmentChanged -> Reduction(
            state.copy(appointment = action.value),
        )
        LoginAction.Submit -> if ('@' in state.email) {
            Reduction(
                state.copy(submission = Async.Loading, message = null),
                listOf(LoginEffect.Authenticate(state.email)),
            )
        } else {
            Reduction(state.copy(message = "Enter a valid email address"))
        }
        LoginAction.Succeeded -> Reduction(
            state.copy(submission = Async.Success(Unit), message = "Signed in"),
        )
        is LoginAction.Failed -> Reduction(
            state.copy(
                submission = Async.Failure(action.error),
                message = action.error.message,
            ),
        )
    }
}

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
                LoginAction.Succeeded
            } catch (error: Throwable) {
                LoginAction.Failed(
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
        "navigate" -> LoginAction.Navigate(value.orEmpty())
        "email_changed" -> LoginAction.EmailChanged(value.orEmpty())
        "language_changed" -> LoginAction.LanguageChanged(value.orEmpty())
        "appointment_changed" -> LoginAction.AppointmentChanged(value)
        "submit" -> LoginAction.Submit
        else -> error("Unknown login action: $action")
    }
}

object LoginUiProvider : UiDocumentProvider {
    override fun document(): UiDocument = loginDocument(LoginState())
}

fun loginDocument(state: LoginState): UiDocument = typedDocument<LoginState, LoginAction>(
    tabNavigation(
        key = "app",
        active = bind(LoginState::activeRoute),
        onChange = "navigate",
        routes = listOf(
            route("login", "Sign in") {
                +form("login-form") {
                    +emailInput(
                        key = "email",
                        value = bind(LoginState::email),
                        placeholder = "Email address",
                        onChange = event("email_changed") {
                            LoginAction.EmailChanged(it)
                        },
                    ).accessibility("Email address", SemanticRole.TextField)
                    +text("message", bind(LoginState::statusText))
                    +datePicker(
                        key = "appointment",
                        value = bind(LoginState::appointment),
                        mode = DatePickerMode.DateTime,
                        onChange = "appointment_changed",
                    )
                    +loading("login-loading", "Signing in")
                        .visibleWhen(bind(LoginState::isSubmitting))
                    +button("submit", "Continue", event(LoginAction.Submit))
                        .enabledWhen(bind(LoginState::canSubmit))
                }
            },
            route("settings", "Settings") {
                +picker(
                    key = "language",
                    initial = "en",
                    selected = bind(LoginState::language),
                    options = listOf(
                        pickerOption("English", "en"),
                        pickerOption("日本語", "ja"),
                    ),
                    onChange = "language_changed",
                )
            },
        ),
    ),
    stateType = "dev.crossui.integration.login.LoginState",
    actionType = "dev.crossui.integration.login.LoginAction",
    theme = Theme(android = AndroidTheme(dynamicColor = true)),
)
