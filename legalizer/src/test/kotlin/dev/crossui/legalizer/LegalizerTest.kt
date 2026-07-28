package dev.crossui.legalizer

import dev.crossui.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegalizerTest {
    @Test
    fun criticalIrreversibleActionsRequireConfirmation() {
        val action = Node(
            NodeKey("delete"),
            NodeKind.Button("Delete", "delete", ButtonVariant.Destructive),
            semantics = Semantics(
                traits = SemanticTraits(
                    irreversible = true,
                    importance = Importance.Critical,
                ),
            ),
        )
        val result = compile(UiDocument(root = action), TargetProfile.iphone())
        assertTrue(result.policies.single().confirmationRequired)
    }

    @Test
    fun wrongPlatformExtensionIsRejected() {
        val node = Node(
            NodeKey("button"),
            NodeKind.Button("Save", "save"),
            extensions = listOf(
                PlatformExtension.Windows(WindowsExtension.CornerRadius(4f)),
            ),
        )
        assertFailsWith<LegalizerException> {
            compile(UiDocument(root = node), TargetProfile.iphone())
        }
    }

    @Test
    fun profileFactoriesCarryCapabilities() {
        assertEquals(PlatformIdentity.Android, TargetProfile.androidPhone().platform)
        assertTrue(TargetProfile.windowsDesktop().capabilities.input.pointer)
    }
}
