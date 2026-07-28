package dev.crossui.dsl

import dev.crossui.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiDslTest {
    private data class SettingsState(
        val email: String = "",
        val enabled: Boolean = false,
    )

    private sealed interface SettingsAction {
        data class EmailChanged(val value: String) : SettingsAction
        data object Save : SettingsAction
    }

    @Test
    fun kotlinDslCreatesSemanticTree() {
        val screen = document(
            route("settings", "Settings") {
                +title("heading", "Settings")
                +form("settings-form") {
                    +emailInput("email", "", "you@example.com", "email_changed")
                        .accessibility("Email", SemanticRole.TextField)
                    +button("save", "Save", "save")
                }
            },
        )
        screen.validate()
        assertEquals("settings", screen.root.key.value)
        assertIs<NodeKind.Form>(screen.findNode(NodeKey("settings-form"))?.kind)
    }

    @Test
    fun extensionsRemainTyped() {
        val action = destructiveButton("delete", "Delete", "delete")
            .irreversible()
            .critical()
            .iosHaptic(HapticType.Error)
        assertEquals(1, action.extensions.size)
        assertEquals(Importance.Critical, action.semantics.traits.importance)
    }

    @Test
    fun typedBindingsAndEventsAreRecordedInIr() {
        val document = typedDocument<SettingsState, SettingsAction>(
            form("settings") {
                +emailInput(
                    "email",
                    bind(SettingsState::email),
                    "Email",
                    event("email_changed") { SettingsAction.EmailChanged(it) },
                )
                +button("save", "Save", event(SettingsAction.Save))
            },
        )

        assertEquals("SettingsState", document.stateType?.substringAfterLast('.'))
        assertEquals("SettingsAction", document.actionType?.substringAfterLast('.'))
        assertEquals("email", document.root.children.first().bindings["value"]?.path)
        assertEquals("save", (document.root.children.last().kind as NodeKind.Button).action)
    }
}
