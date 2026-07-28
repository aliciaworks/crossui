package dev.crossui.example.login

import dev.crossui.runtime.Async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LoginAppTest {
    @Test
    fun editingBoundInputUpdatesSharedStateWithoutRebuildingUi() {
        val app = createLoginApp()
        val update = app.dispatch(LoginAction.EmailChanged("ada@example.com"))
        assertTrue(update.patches.isEmpty())
    }

    @Test
    fun validSubmissionStartsAnAsyncEffect() {
        val app = createLoginApp()
        app.dispatch(LoginAction.EmailChanged("ada@example.com"))
        val update = app.dispatch(LoginAction.Submit)
        assertIs<Async.Loading>(app.state.submission)
        assertIs<LoginEffect.Authenticate>(update.effects.single())
    }

    @Test
    fun connectorCompletesAsyncLoginAndNavigates() = runTest {
        val connector = createLoginConnector(this) { email ->
            assertEquals("ada@example.com", email)
        }

        connector.send(LoginActions.map("email_changed", "ada@example.com"))
        connector.send(LoginActions.map("submit", null))
        advanceUntilIdle()

        assertIs<Async.Success<Unit>>(connector.state.submission)
        assertIs<Screen.Projects>(connector.state.screen)
        connector.close()
    }
}
