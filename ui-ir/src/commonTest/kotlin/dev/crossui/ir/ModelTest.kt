package dev.crossui.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class ModelTest {
    @Test
    fun duplicateKeysAreRejected() {
        val document = UiDocument(
            root = Node(
                NodeKey("root"),
                NodeKind.Stack(Axis.Vertical),
                children = listOf(
                    Node(NodeKey("same"), NodeKind.Text("a")),
                    Node(NodeKey("same"), NodeKind.Text("b")),
                ),
            ),
        )
        assertFails { document.validate() }
    }

    @Test
    fun childChangesProduceLeafUpdates() {
        val before = UiDocument(
            root = Node(
                NodeKey("root"),
                NodeKind.Stack(Axis.Vertical),
                children = listOf(Node(NodeKey("value"), NodeKind.Text("a"))),
            ),
        )
        val after = before.copy(
            root = before.root.copy(
                children = listOf(Node(NodeKey("value"), NodeKind.Text("b"))),
            ),
        )
        val operation = diff(before, after).single()
        assertIs<DiffOp.Update>(operation)
        assertEquals(NodeKey("value"), operation.key)
    }

    @Test
    fun jsonRoundTripPreservesDocument() {
        val document = UiDocument(
            root = Node(NodeKey("root"), NodeKind.Text("hello")),
            stateType = "SettingsState",
            actionType = "SettingsAction",
            settings = listOf(
                SettingDeclaration(
                    key = "appearance.dark_mode",
                    statePath = "darkMode",
                    valueType = SettingValueType.Boolean,
                    defaultValue = "false",
                    ownership = SettingOwnership.PlatformUi,
                    onChange = "dark_mode_changed",
                ),
            ),
        )
        assertEquals(document, UiDocument.fromJson(document.toJson()))
    }

    @Test
    fun duplicateSettingKeysAreRejected() {
        val setting = SettingDeclaration(
            key = "appearance.dark_mode",
            statePath = "darkMode",
            valueType = SettingValueType.Boolean,
            defaultValue = "false",
            onChange = "dark_mode_changed",
        )
        val document = UiDocument(
            root = Node(NodeKey("root"), NodeKind.Text("hello")),
            settings = listOf(setting, setting),
        )

        assertFails { document.validate() }
    }

    @Test
    fun platformOwnedSettingRequiresTypedPreferencesDocument() {
        val document = UiDocument(
            root = Node(NodeKey("root"), NodeKind.Text("hello")),
            settings = listOf(
                SettingDeclaration(
                    key = "secret",
                    statePath = "secret",
                    valueType = SettingValueType.String,
                    defaultValue = "",
                    storage = SettingStorage.Secure,
                    ownership = SettingOwnership.PlatformUi,
                    onChange = "secret_changed",
                ),
            ),
        )

        assertFails { document.validate() }
    }

    @Test
    fun jsonRoundTripPreservesLocalizedResources() {
        val document = UiDocument(
            root = Node(
                key = NodeKey("welcome"),
                kind = NodeKind.Text("Welcome"),
                localizedText = mapOf(
                    LocalizedField.Value to LocalizedText.Resource(
                        key = "home.welcome",
                        fallback = "Welcome",
                        namespace = "Home",
                    ),
                ),
            ),
        )

        assertEquals(document, UiDocument.fromJson(document.toJson()))
    }

    @Test
    fun invalidLocalizedFieldIsRejected() {
        val document = UiDocument(
            root = Node(
                key = NodeKey("divider"),
                kind = NodeKind.Divider,
                localizedText = mapOf(
                    LocalizedField.Label to LocalizedText.Resource(
                        key = "invalid",
                        fallback = "Invalid",
                    ),
                ),
            ),
        )

        assertFails { document.validate() }
    }

    @Test
    fun contentPickerRoundTripsAndRejectsInvalidMediaRequests() {
        val picker = NodeKind.ContentPicker(
            label = "Choose photos",
            request = ContentPickerRequest.Media(setOf(MediaKind.Image), 3),
            onRequest = "pick_photos",
        )
        val document = UiDocument(root = Node(NodeKey("photos"), picker))

        assertEquals(document, UiDocument.fromJson(document.toJson()))
        assertFails {
            UiDocument(
                root = Node(
                    NodeKey("broken"),
                    picker.copy(request = ContentPickerRequest.Media(emptySet(), 0)),
                ),
            ).validate()
        }
    }
}
