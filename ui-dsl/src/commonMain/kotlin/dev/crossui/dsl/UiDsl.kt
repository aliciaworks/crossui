@file:Suppress("FunctionName")

package dev.crossui.dsl

import dev.crossui.ir.*
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KProperty1

@DslMarker
annotation class CrossUiDsl

@CrossUiDsl
class ChildrenBuilder {
    private val nodes = mutableListOf<Node>()

    operator fun Node.unaryPlus() {
        nodes += this
    }

    fun add(node: Node) {
        nodes += node
    }

    internal fun build(): List<Node> = nodes.toList()
}

fun ui(block: ChildrenBuilder.() -> Unit): List<Node> =
    ChildrenBuilder().apply(block).build()

fun document(
    root: Node,
    theme: Theme = Theme(),
    settings: List<SettingDeclaration> = emptyList(),
): UiDocument =
    UiDocument(root = root, theme = theme, settings = settings)
        .also(UiDocument::validate)

inline fun <reified State : Any, reified Action : Any> typedDocument(
    root: Node,
    theme: Theme = Theme(),
    stateType: String = requireNotNull(State::class.simpleName),
    actionType: String = requireNotNull(Action::class.simpleName),
    settings: List<SettingDeclaration> = emptyList(),
): UiDocument = UiDocument(
    root = root,
    theme = theme,
    stateType = stateType,
    actionType = actionType,
    settings = settings,
).also(UiDocument::validate)

data class StateBinding<T>(val reference: BindingRef)

fun localized(
    key: String,
    fallback: String,
    namespace: String? = null,
): LocalizedText = LocalizedText.Resource(key, fallback, namespace)

fun literal(value: String): LocalizedText = LocalizedText.Literal(value)

inline fun <reified State : Any, reified Value> bind(
    property: KProperty1<State, Value>,
): StateBinding<Value> = StateBinding(
    BindingRef(
        path = property.name,
        valueType = Value::class.simpleName,
    ),
)

fun <Value> appStorage(
    name: String,
    default: Value,
): SettingKey<Value> = SettingKey(
    name = name,
    default = default,
    storage = SettingStorage.Preferences,
    ownership = SettingOwnership.PlatformUi,
)

inline fun <State : Any, reified Value> setting(
    key: SettingKey<Value>,
    state: KProperty1<State, Value>,
    onChange: String,
): SettingDeclaration {
    val valueType = when (Value::class.simpleName) {
        "Boolean" -> SettingValueType.Boolean
        "String" -> SettingValueType.String
        "Int" -> SettingValueType.Int
        "Double" -> SettingValueType.Double
        else -> error(
            "Unsupported setting type ${Value::class.simpleName}; " +
                "supported types are Boolean, String, Int, and Double.",
        )
    }
    return SettingDeclaration(
        key = key.name,
        statePath = state.name,
        valueType = valueType,
        defaultValue = key.default.toString(),
        storage = key.storage,
        ownership = key.ownership,
        onChange = onChange,
    )
}

fun <Action : Any> event(action: Action): String =
    requireNotNull(action::class.simpleName) {
        "Actions must have a stable class name."
    }.toSnakeCase()

fun <Action : Any> event(
    name: String,
    factory: (String) -> Action,
): String {
    @Suppress("UNUSED_VARIABLE")
    val typeCheck = factory
    require(name.isNotBlank()) { "Event name cannot be blank." }
    return name
}

private fun String.toSnakeCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

fun text(key: String, value: String) =
    Node(NodeKey(key), NodeKind.Text(value))

fun text(
    key: String,
    value: LocalizedText,
    style: TextStyle = TextStyle.Body,
) = Node(
    NodeKey(key),
    NodeKind.Text(value.fallback, style),
    localizedText = mapOf(LocalizedField.Value to value),
)

fun text(key: String, value: StateBinding<String>) =
    text(key, "").withBinding("value", value)

fun text(key: String, value: String, style: TextStyle) =
    Node(NodeKey(key), NodeKind.Text(value, style))

fun display(key: String, value: String) = text(key, value, TextStyle.Display)
fun display(key: String, value: LocalizedText) = text(key, value, TextStyle.Display)
fun headline(key: String, value: String) = text(key, value, TextStyle.Headline)
fun headline(key: String, value: LocalizedText) = text(key, value, TextStyle.Headline)

fun title(key: String, value: String) =
    text(key, value, TextStyle.Title)
        .accessibility(value, SemanticRole.Header)

fun title(key: String, value: LocalizedText) =
    text(key, value, TextStyle.Title)
        .accessibility(value.fallback, SemanticRole.Header)

fun caption(key: String, value: String) = text(key, value, TextStyle.Caption)
fun caption(key: String, value: LocalizedText) = text(key, value, TextStyle.Caption)
fun footnote(key: String, value: String) = text(key, value, TextStyle.Footnote)
fun footnote(key: String, value: LocalizedText) = text(key, value, TextStyle.Footnote)

fun button(
    key: String,
    label: String,
    action: String,
    variant: ButtonVariant = ButtonVariant.Primary,
) = Node(NodeKey(key), NodeKind.Button(label, action, variant))

fun button(
    key: String,
    label: LocalizedText,
    action: String,
    variant: ButtonVariant = ButtonVariant.Primary,
) = Node(
    NodeKey(key),
    NodeKind.Button(label.fallback, action, variant),
    localizedText = mapOf(LocalizedField.Label to label),
)

fun secondaryButton(key: String, label: String, action: String) =
    button(key, label, action, ButtonVariant.Secondary)

fun destructiveButton(key: String, label: String, action: String) =
    button(key, label, action, ButtonVariant.Destructive)

fun input(
    key: String,
    value: String,
    onChange: String,
    placeholder: String? = null,
    secure: Boolean = false,
    inputType: InputType = InputType.Text,
    returnKey: ReturnKey? = null,
) = Node(
    NodeKey(key),
    NodeKind.Input(value, placeholder, onChange, secure, inputType, returnKey),
)

fun input(
    key: String,
    value: StateBinding<String>,
    onChange: String,
    placeholder: String? = null,
    secure: Boolean = false,
    inputType: InputType = InputType.Text,
    returnKey: ReturnKey? = null,
) = input(
    key = key,
    value = "",
    onChange = onChange,
    placeholder = placeholder,
    secure = secure,
    inputType = inputType,
    returnKey = returnKey,
).withBinding("value", value)

fun secureInput(key: String, value: String, placeholder: String?, onChange: String) =
    input(key, value, onChange, placeholder, secure = true, inputType = InputType.Password)

fun secureInput(
    key: String,
    value: StateBinding<String>,
    placeholder: String?,
    onChange: String,
) = input(
    key,
    value,
    onChange,
    placeholder,
    secure = true,
    inputType = InputType.Password,
)

fun emailInput(key: String, value: String, placeholder: String?, onChange: String) =
    input(key, value, onChange, placeholder, inputType = InputType.Email)

fun emailInput(
    key: String,
    value: StateBinding<String>,
    placeholder: String?,
    onChange: String,
) = input(key, value, onChange, placeholder, inputType = InputType.Email)

fun numberInput(key: String, value: String, placeholder: String?, onChange: String) =
    input(key, value, onChange, placeholder, inputType = InputType.Number)

fun searchInput(key: String, value: String, placeholder: String?, onChange: String) =
    input(key, value, onChange, placeholder, returnKey = ReturnKey.Search)

fun Node.inputType(value: InputType): Node = copy(
    kind = (kind as NodeKind.Input).copy(inputType = value),
)

fun Node.returnKey(value: ReturnKey): Node = copy(
    kind = (kind as NodeKind.Input).copy(returnKey = value),
)

fun vstack(
    key: String,
    children: List<Node>,
    spacing: String? = null,
    alignment: Alignment = Alignment.Start,
) = Node(
    NodeKey(key),
    NodeKind.Stack(Axis.Vertical, spacing, alignment),
    children = children,
)

fun vstack(
    key: String,
    spacing: String? = null,
    alignment: Alignment = Alignment.Start,
    block: ChildrenBuilder.() -> Unit,
) = vstack(key, ui(block), spacing, alignment)

fun hstack(
    key: String,
    children: List<Node>,
    spacing: String? = null,
    alignment: Alignment = Alignment.Center,
) = Node(
    NodeKey(key),
    NodeKind.Stack(Axis.Horizontal, spacing, alignment),
    children = children,
)

fun hstack(
    key: String,
    spacing: String? = null,
    alignment: Alignment = Alignment.Center,
    block: ChildrenBuilder.() -> Unit,
) = hstack(key, ui(block), spacing, alignment)

fun list(key: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.ListNode(), children = children)

fun selectableList(key: String, onSelect: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.ListNode(onSelect), children = children)

fun form(key: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Form, children = children)

fun form(key: String, block: ChildrenBuilder.() -> Unit) = form(key, ui(block))

fun loading(key: String, label: String? = null) =
    Node(NodeKey(key), NodeKind.Loading(label))

fun loading(key: String, label: LocalizedText) =
    Node(
        NodeKey(key),
        NodeKind.Loading(label.fallback),
        localizedText = mapOf(LocalizedField.Label to label),
    )

fun toggle(key: String, label: String?, checked: Boolean, onChange: String) =
    Node(NodeKey(key), NodeKind.Toggle(label, checked, onChange))

fun toggle(
    key: String,
    label: String?,
    checked: StateBinding<Boolean>,
    onChange: String,
) = toggle(key, label, false, onChange).withBinding("checked", checked)

fun toggle(
    key: String,
    label: LocalizedText,
    checked: Boolean,
    onChange: String,
) = Node(
    NodeKey(key),
    NodeKind.Toggle(label.fallback, checked, onChange),
    localizedText = mapOf(LocalizedField.Label to label),
)

fun toggle(
    key: String,
    label: LocalizedText,
    checked: StateBinding<Boolean>,
    onChange: String,
) = toggle(key, label, false, onChange).withBinding("checked", checked)

fun image(key: String, src: String, alt: String? = null) =
    Node(NodeKey(key), NodeKind.Image(src, alt))

fun dialog(
    key: String,
    title: String,
    confirmLabel: String? = null,
    confirmAction: String? = null,
    cancelLabel: String? = null,
    cancelAction: String? = null,
) = Node(
    NodeKey(key),
    NodeKind.Dialog(
        title,
        confirmLabel,
        confirmAction,
        cancelLabel,
        cancelAction,
    ),
)

fun slider(
    key: String,
    value: Double,
    min: Double,
    max: Double,
    step: Double? = null,
    onChange: String,
) = Node(NodeKey(key), NodeKind.Slider(value, min, max, step, onChange))

fun slider(
    key: String,
    value: StateBinding<Double>,
    min: Double,
    max: Double,
    step: Double? = null,
    onChange: String,
) = slider(key, 0.0, min, max, step, onChange).withBinding("value", value)

fun picker(key: String, selected: String, options: List<PickerOption>, onChange: String) =
    Node(NodeKey(key), NodeKind.Picker(selected, options, onChange))

fun picker(
    key: String,
    selected: StateBinding<String>,
    options: List<PickerOption>,
    onChange: String,
) = picker(key, "", options, onChange).withBinding("selected", selected)

fun pickerOption(label: String, value: String) = PickerOption(label, value)
fun pickerOption(label: LocalizedText, value: String) =
    PickerOption(label.fallback, value, localizedLabel = label)

fun datePicker(
    key: String,
    value: String?,
    mode: DatePickerMode = DatePickerMode.DateTime,
    onChange: String,
) = Node(NodeKey(key), NodeKind.DatePicker(value, mode, onChange))

fun datePicker(
    key: String,
    value: StateBinding<String?>,
    mode: DatePickerMode = DatePickerMode.DateTime,
    onChange: String,
) = datePicker(key, null, mode, onChange).withBinding("value", value)

fun platformView(key: String, platform: Platform, name: String, payload: JsonElement) =
    Node(NodeKey(key), NodeKind.PlatformView(platform, name, payload))

fun checkbox(key: String, label: String?, checked: Boolean, onChange: String) =
    Node(NodeKey(key), NodeKind.Checkbox(label, checked, onChange))

fun checkbox(
    key: String,
    label: String?,
    checked: StateBinding<Boolean>,
    onChange: String,
) = checkbox(key, label, false, onChange).withBinding("checked", checked)

fun checkbox(
    key: String,
    label: LocalizedText,
    checked: Boolean,
    onChange: String,
) = Node(
    NodeKey(key),
    NodeKind.Checkbox(label.fallback, checked, onChange),
    localizedText = mapOf(LocalizedField.Label to label),
)

fun checkbox(
    key: String,
    label: LocalizedText,
    checked: StateBinding<Boolean>,
    onChange: String,
) = checkbox(key, label, false, onChange).withBinding("checked", checked)

fun divider(key: String) = Node(NodeKey(key), NodeKind.Divider)
fun card(key: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Card, children = children)

fun chip(
    key: String,
    label: String,
    variant: ChipVariant = ChipVariant.Input,
    onDismiss: String? = null,
) = Node(NodeKey(key), NodeKind.Chip(label, variant, onDismiss))

fun chip(
    key: String,
    label: LocalizedText,
    variant: ChipVariant = ChipVariant.Input,
    onDismiss: String? = null,
) = Node(
    NodeKey(key),
    NodeKind.Chip(label.fallback, variant, onDismiss),
    localizedText = mapOf(LocalizedField.Label to label),
)

fun inputChip(key: String, label: String) = chip(key, label)
fun filterChip(key: String, label: String, onDismiss: String? = null) =
    chip(key, label, ChipVariant.Filter, onDismiss)
