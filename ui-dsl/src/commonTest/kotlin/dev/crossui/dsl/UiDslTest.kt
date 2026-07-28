package dev.crossui.dsl

import dev.crossui.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiDslTest {
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
}
