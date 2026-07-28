package dev.crossui.integration.login

import dev.crossui.runtime.Async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LoginFeatureTest {
    @Test
    fun generatedUiConnectorCompletesLoginEffect() = runTest {
        val connector = createLoginConnector(this) { email ->
            assertEquals("agent@example.com", email)
        }

        connector.send(LoginActions.map("email_changed", "agent@example.com"))
        connector.send(LoginActions.map("submit", null))
        advanceUntilIdle()

        assertIs<Async.Success<Unit>>(connector.state.submission)
        assertEquals("Signed in", connector.state.message)
        connector.close()
    }
}
