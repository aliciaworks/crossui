package dev.crossui.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

const val IR_VERSION: UInt = 6u

fun interface UiDocumentProvider {
    fun document(): UiDocument
}

val CrossUiJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
    ignoreUnknownKeys = false
}

@Serializable
data class UiDocument(
    val version: UInt = IR_VERSION,
    val root: Node,
    val theme: Theme = Theme(),
    val stateType: String? = null,
    val actionType: String? = null,
    val settings: List<SettingDeclaration> = emptyList(),
) {
    @kotlinx.serialization.Transient
    private var index: Map<NodeKey, Node> = buildIndex(root)

    fun validate() {
        require(version == IR_VERSION) { "Unsupported IR version $version" }
        val keys = mutableSetOf<NodeKey>()
        root.walk {
            require(it.key.value.isNotBlank()) { "Node key cannot be empty" }
            require(keys.add(it.key)) { "Duplicate node key: ${it.key.value}" }
            val supportedFields = it.kind.supportedLocalizedFields()
            it.localizedText.forEach { (field, text) ->
                require(field in supportedFields) {
                    "${it.kind::class.simpleName} does not support localized field $field"
                }
                text.validate("${it.key.value}.$field")
            }
            (it.kind as? NodeKind.Picker)?.options?.forEach { option ->
                option.localizedLabel?.validate("${it.key.value}.option.${option.value}")
            }
            (it.kind as? NodeKind.ContentPicker)?.let { picker ->
                require(picker.onRequest.isNotBlank()) {
                    "Content picker request action cannot be empty: ${it.key.value}"
                }
                picker.request.validate(it.key.value)
            }
            if (it.kind is NodeKind.DatePicker) {
                require("value" in it.bindings) {
                    "DatePicker requires a 'value' binding: ${it.key.value}"
                }
            }
            if (stateType != null) {
                it.kind.requiredBinding()?.let { field ->
                    require(field in it.bindings) {
                        "${it.kind::class.simpleName} requires a '$field' binding " +
                            "in typed document: ${it.key.value}"
                    }
                }
            }
        }
        val settingKeys = mutableSetOf<String>()
        settings.forEach { setting ->
            require(setting.key.isNotBlank()) { "Setting key cannot be empty" }
            require(setting.statePath.isNotBlank()) {
                "Setting state path cannot be empty: ${setting.key}"
            }
            require(setting.onChange.isNotBlank()) {
                "Setting action cannot be empty: ${setting.key}"
            }
            require(settingKeys.add(setting.key)) {
                "Duplicate setting key: ${setting.key}"
            }
            when (setting.valueType) {
                SettingValueType.Boolean -> require(
                    setting.defaultValue == "true" || setting.defaultValue == "false",
                ) {
                    "Invalid Boolean default for setting ${setting.key}"
                }
                SettingValueType.String -> Unit
                SettingValueType.Int -> require(setting.defaultValue.toIntOrNull() != null) {
                    "Invalid Int default for setting ${setting.key}"
                }
                SettingValueType.Double -> require(
                    setting.defaultValue.toDoubleOrNull()?.isFinite() == true,
                ) {
                    "Invalid Double default for setting ${setting.key}"
                }
            }
            if (setting.ownership == SettingOwnership.PlatformUi) {
                require(setting.storage == SettingStorage.Preferences) {
                    "PlatformUi settings must use Preferences storage: ${setting.key}"
                }
                require(stateType != null && actionType != null) {
                    "PlatformUi settings require a typed document: ${setting.key}"
                }
            }
        }
    }

    fun findNode(key: NodeKey): Node? {
        if (index.isEmpty()) index = buildIndex(root)
        return index[key]
    }

    fun toJson(): String = CrossUiJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(value: String): UiDocument =
            CrossUiJson.decodeFromString(serializer(), value).also { it.validate() }
    }
}

@Serializable
enum class SettingStorage {
    Preferences,
    SavedState,
    Secure,
}

@Serializable
enum class SettingOwnership {
    SharedState,
    PlatformUi,
    External,
}

@Serializable
enum class SettingValueType {
    Boolean,
    String,
    Int,
    Double,
}

data class SettingKey<Value>(
    val name: String,
    val default: Value,
    val storage: SettingStorage = SettingStorage.Preferences,
    val ownership: SettingOwnership = SettingOwnership.SharedState,
)

@Serializable
data class SettingDeclaration(
    val key: String,
    val statePath: String,
    val valueType: SettingValueType,
    val defaultValue: String,
    val storage: SettingStorage = SettingStorage.Preferences,
    val ownership: SettingOwnership = SettingOwnership.SharedState,
    val onChange: String,
)

private fun buildIndex(root: Node): Map<NodeKey, Node> = buildMap {
    root.walk { put(it.key, it) }
}

fun Node.walk(visitor: (Node) -> Unit) {
    visitor(this)
    children.forEach { it.walk(visitor) }
}

@Serializable
data class NodeKey(val value: String)

@Serializable
data class Node(
    val key: NodeKey,
    val kind: NodeKind,
    val semantics: Semantics = Semantics(),
    val children: List<Node> = emptyList(),
    val extensions: List<PlatformExtension> = emptyList(),
    val bindings: Map<String, BindingRef> = emptyMap(),
    val localizedText: Map<LocalizedField, LocalizedText> = emptyMap(),
    val source: SourceLocation? = null,
    val transition: MotionPreset = MotionPreset.Default,
) {
    fun withChildren(vararg nodes: Node) = copy(children = nodes.toList())
    fun withChildren(nodes: List<Node>) = copy(children = nodes)
}

@Serializable
data class BindingRef(
    val path: String,
    val valueType: String? = null,
)

@Serializable
enum class LocalizedField {
    Value,
    Label,
    Placeholder,
    Title,
    Alt,
    ConfirmLabel,
    CancelLabel,
}

@Serializable
sealed interface LocalizedText {
    val fallback: String

    @Serializable
    @SerialName("literal")
    data class Literal(
        val value: String,
    ) : LocalizedText {
        override val fallback: String get() = value
    }

    @Serializable
    @SerialName("resource")
    data class Resource(
        val key: String,
        override val fallback: String,
        val namespace: String? = null,
    ) : LocalizedText
}

@Serializable
data class SourceLocation(
    val file: String,
    val line: Int? = null,
    val column: Int? = null,
)

@Serializable
sealed interface NodeKind {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val style: TextStyle = TextStyle.Body) : NodeKind

    @Serializable
    @SerialName("button")
    data class Button(
        val label: String,
        val action: String,
        val variant: ButtonVariant = ButtonVariant.Primary,
    ) : NodeKind

    @Serializable
    @SerialName("input")
    data class Input(
        val value: String,
        val placeholder: String? = null,
        val onChange: String,
        val secure: Boolean = false,
        val inputType: InputType = InputType.Text,
        val returnKey: ReturnKey? = null,
    ) : NodeKind

    @Serializable
    @SerialName("stack")
    data class Stack(
        val axis: Axis,
        val spacing: String? = null,
        val alignment: Alignment = Alignment.Center,
    ) : NodeKind

    @Serializable
    @SerialName("list")
    data class ListNode(val onSelect: String? = null) : NodeKind

    @Serializable
    @SerialName("form")
    data object Form : NodeKind

    @Serializable
    @SerialName("loading")
    data class Loading(val label: String? = null) : NodeKind

    @Serializable
    @SerialName("navigation")
    data class Navigation(
        val active: String,
        val mode: NavigationMode = NavigationMode.Tab,
        val onChange: String = "navigate",
    ) : NodeKind

    @Serializable
    @SerialName("route")
    data class Route(
        val title: String,
        val respectSafeArea: Boolean = true,
    ) : NodeKind

    @Serializable
    @SerialName("platform_view")
    data class PlatformView(
        val platform: Platform,
        val name: String,
        val payload: JsonElement,
    ) : NodeKind

    @Serializable
    @SerialName("toggle")
    data class Toggle(
        val label: String? = null,
        val checked: Boolean,
        val onChange: String,
    ) : NodeKind

    @Serializable
    @SerialName("image")
    data class Image(val src: String, val alt: String? = null) : NodeKind

    @Serializable
    @SerialName("dialog")
    data class Dialog(
        val title: String,
        val confirmLabel: String? = null,
        val confirmAction: String? = null,
        val cancelLabel: String? = null,
        val cancelAction: String? = null,
    ) : NodeKind

    @Serializable
    @SerialName("slider")
    data class Slider(
        val value: Double,
        val min: Double,
        val max: Double,
        val step: Double? = null,
        val onChange: String,
    ) : NodeKind

    @Serializable
    @SerialName("picker")
    data class Picker(
        val selected: String,
        val options: List<PickerOption>,
        val onChange: String,
    ) : NodeKind

    @Serializable
    @SerialName("date_picker")
    data class DatePicker(
        val value: String? = null,
        val mode: DatePickerMode = DatePickerMode.DateTime,
        val onChange: String,
    ) : NodeKind

    @Serializable
    @SerialName("checkbox")
    data class Checkbox(
        val label: String? = null,
        val checked: Boolean,
        val onChange: String,
    ) : NodeKind

    @Serializable
    @SerialName("divider")
    data object Divider : NodeKind

    @Serializable
    @SerialName("card")
    data object Card : NodeKind

    @Serializable
    @SerialName("chip")
    data class Chip(
        val label: String,
        val variant: ChipVariant = ChipVariant.Input,
        val onDismiss: String? = null,
    ) : NodeKind

    @Serializable
    @SerialName("content_picker")
    data class ContentPicker(
        val label: String,
        val request: ContentPickerRequest,
        val onRequest: String,
        val variant: ButtonVariant = ButtonVariant.Primary,
    ) : NodeKind
}

@Serializable
data class PickerOption(
    val label: String,
    val value: String,
    val localizedLabel: LocalizedText? = null,
)
@Serializable enum class TextStyle { Display, Headline, Title, Body, Caption, Footnote }
@Serializable enum class InputType { Text, Email, Number, Phone, Url, Password }
@Serializable enum class ReturnKey { Done, Go, Search, Send, Next }
@Serializable enum class ButtonVariant { Primary, Secondary, Destructive }
@Serializable enum class ChipVariant { Input, Filter, Suggestion }
@Serializable enum class NavigationMode { Tab, Stack }
@Serializable enum class DatePickerMode { Date, Time, DateTime }
@Serializable enum class Axis { Horizontal, Vertical }
@Serializable enum class Alignment { Start, Center, End, Stretch }
@Serializable enum class Platform { Ios, Android, Windows }

/**
 * Semantic appearance motion for a node that appears and disappears (typically
 * through a `visibleWhen` binding). Generators lower each preset to the native
 * motion idiom of the target platform; [Default] is the platform-standard fade.
 */
@Serializable
enum class MotionPreset {
    Default,
    Fade,
    Scale,
    SlideUp,
    Blend,
}

@Serializable
data class Semantics(
    val label: String? = null,
    val hint: String? = null,
    val role: SemanticRole? = null,
    val enabled: Boolean = true,
    val traits: SemanticTraits = SemanticTraits(),
)

@Serializable
data class SemanticTraits(
    val irreversible: Boolean = false,
    val frequency: ActionFrequency = ActionFrequency.Frequent,
    val importance: Importance = Importance.Normal,
)

@Serializable enum class ActionFrequency { Rare, Occasional, Frequent }
@Serializable enum class Importance { Normal, High, Critical }
@Serializable enum class SemanticRole {
    Button, Header, TextField, List, Form, Image, Slider, Picker, Checkbox, Link,
}

@Serializable
data class Theme(
    val colorScheme: ColorScheme = ColorScheme.System,
    val tokens: Map<String, TokenValue> = emptyMap(),
    val android: AndroidTheme = AndroidTheme(),
)

@Serializable enum class ColorScheme { System, Light, Dark }

@Serializable
sealed interface TokenValue {
    @Serializable @SerialName("color") data class Color(val value: String) : TokenValue
    @Serializable @SerialName("number") data class Number(val value: Double) : TokenValue
    @Serializable @SerialName("text") data class Text(val value: String) : TokenValue
}

@Serializable
data class AndroidTheme(
    val material3Expressive: Boolean = false,
    val dynamicColor: Boolean = false,
)

@Serializable
sealed interface DiffOp {
    val key: NodeKey
    @Serializable @SerialName("insert") data class Insert(override val key: NodeKey) : DiffOp
    @Serializable @SerialName("remove") data class Remove(override val key: NodeKey) : DiffOp
    @Serializable @SerialName("update") data class Update(override val key: NodeKey) : DiffOp
}

fun diff(previous: UiDocument, next: UiDocument): List<DiffOp> {
    val before = buildIndex(previous.root)
    val after = buildIndex(next.root)
    val operations = buildList {
        before.forEach { (key, old) ->
            val current = after[key]
            when {
                current == null -> add(DiffOp.Remove(key))
                old.kind != current.kind || old.semantics != current.semantics ->
                    add(DiffOp.Update(key))
            }
        }
        after.keys.filterNot(before::containsKey).forEach { add(DiffOp.Insert(it)) }
        if (previous.theme != next.theme &&
            none { it is DiffOp.Update && it.key == next.root.key }
        ) add(DiffOp.Update(next.root.key))
    }
    return operations
}
