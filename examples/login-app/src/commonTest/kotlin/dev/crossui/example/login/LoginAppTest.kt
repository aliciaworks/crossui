package dev.crossui.example.login

import dev.crossui.ir.DiffOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginAppTest {
    @Test
    fun editingInputCreatesLeafPatch() {
        val app = createLoginApp()
        val update = app.dispatch(LoginAction.EmailChanged("ada@example.com"))
        assertEquals(1, update.patches.size)
        assertIs<DiffOp.Update>(update.patches.single())
        assertEquals("email", update.patches.single().key.value)
    }

    @Test
    fun successfulLoginNavigatesAndEmitsEffect() {
        val app = createLoginApp()
        app.dispatch(LoginAction.EmailChanged("ada@example.com"))
        val update = app.dispatch(LoginAction.Submit)
        assertTrue(update.document.toJson().contains("projects"))
        assertIs<LoginEffect.Notification>(update.effects.single())
    }
}
