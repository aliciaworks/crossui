package dev.crossui.dsl

import dev.crossui.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiDslTest {
    private data class SettingsState(
        val email: String = "",
        val enabled: Boolean = false,
        val darkMode: Boolean = false,
        val volume: Double = 0.5,
        val language: String = "en",
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

    @Test
    fun appStorageSettingRecordsExplicitPlatformOwnership() {
        val darkMode = appStorage("appearance.dark_mode", false)
        val declaration = setting(
            darkMode,
            SettingsState::darkMode,
            "dark_mode_changed",
        )
        val document = typedDocument<SettingsState, SettingsAction>(
            text("title", "Settings"),
            settings = listOf(declaration),
        )

        assertEquals(SettingOwnership.PlatformUi, declaration.ownership)
        assertEquals(SettingStorage.Preferences, declaration.storage)
        assertEquals(SettingValueType.Boolean, declaration.valueType)
        assertEquals("darkMode", declaration.statePath)
        assertEquals(listOf(declaration), document.settings)
    }

    @Test
    fun localizedTextKeepsFallbackAndResourceIdentity() {
        val resource = localized(
            key = "settings.save",
            fallback = "Save",
            namespace = "Settings",
        )
        val document = document(
            button("save", resource, "save"),
        )

        assertEquals("Save", (document.root.kind as NodeKind.Button).label)
        assertEquals(resource, document.root.localizedText[LocalizedField.Label])
    }

    @Test
    fun ordinaryStringsRemainLiteralDslInput() {
        val document = document(text("message", "Hello"))

        assertEquals("Hello", (document.root.kind as NodeKind.Text).text)
        assertEquals(emptyMap(), document.root.localizedText)
    }

    @Test
    fun contentPickerDslKeepsRequestSemanticsOutOfPlatformCode() {
        val node = filePicker(
            key = "attachment",
            label = "Attach PDF",
            onRequest = "pick_attachment",
            mimeTypes = listOf("application/pdf"),
        )

        val kind = assertIs<NodeKind.ContentPicker>(node.kind)
        assertEquals("pick_attachment", kind.onRequest)
        assertEquals(
            ContentPickerRequest.Files(listOf("application/pdf")),
            kind.request,
        )
    }

    @Test
    fun boundSelectionsCanKeepNativeHostDefaults() {
        val slider = slider(
            "volume",
            0.5,
            bind(SettingsState::volume),
            0.0,
            1.0,
            onChange = "volume_changed",
        )
        val picker = picker(
            "language",
            "en",
            bind(SettingsState::language),
            listOf(pickerOption("English", "en")),
            "language_changed",
        )

        assertEquals(0.5, (slider.kind as NodeKind.Slider).value)
        assertEquals("en", (picker.kind as NodeKind.Picker).selected)
        assertEquals("volume", slider.bindings.getValue("value").path)
        assertEquals("language", picker.bindings.getValue("selected").path)
    }
}
