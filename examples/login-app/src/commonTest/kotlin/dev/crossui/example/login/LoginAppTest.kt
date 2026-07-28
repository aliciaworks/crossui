package dev.crossui.example.login

import dev.crossui.ir.DiffOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginAppTest {
    @Test
    fun editingBoundInputUpdatesSharedStateWithoutRebuildingUi() {
        val app = createLoginApp()
        val update = app.dispatch(LoginAction.EmailChanged("ada@example.com"))
        assertTrue(update.patches.isEmpty())
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
