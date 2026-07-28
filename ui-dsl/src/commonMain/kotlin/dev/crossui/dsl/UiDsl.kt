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

fun document(root: Node, theme: Theme = Theme()): UiDocument =
    UiDocument(root = root, theme = theme).also(UiDocument::validate)

inline fun <reified State : Any, reified Action : Any> typedDocument(
    root: Node,
    theme: Theme = Theme(),
    stateType: String = requireNotNull(State::class.simpleName),
    actionType: String = requireNotNull(Action::class.simpleName),
): UiDocument = UiDocument(
    root = root,
    theme = theme,
    stateType = stateType,
    actionType = actionType,
).also(UiDocument::validate)

data class StateBinding<T>(val reference: BindingRef)

inline fun <reified State : Any, reified Value> bind(
    property: KProperty1<State, Value>,
): StateBinding<Value> = StateBinding(
    BindingRef(
        path = property.name,
        valueType = Value::class.simpleName,
    ),
)

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

fun text(key: String, value: StateBinding<String>) =
    text(key, "").withBinding("value", value)

fun text(key: String, value: String, style: TextStyle) =
    Node(NodeKey(key), NodeKind.Text(value, style))

fun display(key: String, value: String) = text(key, value, TextStyle.Display)
fun headline(key: String, value: String) = text(key, value, TextStyle.Headline)

fun title(key: String, value: String) =
    text(key, value, TextStyle.Title)
        .accessibility(value, SemanticRole.Header)

fun caption(key: String, value: String) = text(key, value, TextStyle.Caption)
fun footnote(key: String, value: String) = text(key, value, TextStyle.Footnote)

fun button(
    key: String,
    label: String,
    action: String,
    variant: ButtonVariant = ButtonVariant.Primary,
) = Node(NodeKey(key), NodeKind.Button(label, action, variant))

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
    alignment: Alignment = Alignment.Center,
) = Node(
    NodeKey(key),
    NodeKind.Stack(Axis.Vertical, spacing, alignment),
    children = children,
)

fun vstack(
    key: String,
    spacing: String? = null,
    alignment: Alignment = Alignment.Center,
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

fun tabNavigation(key: String, active: String, routes: List<Node>) =
    Node(NodeKey(key), NodeKind.Navigation(active, NavigationMode.Tab), children = routes)

fun stackNavigation(key: String, active: String, routes: List<Node>) =
    Node(NodeKey(key), NodeKind.Navigation(active, NavigationMode.Stack), children = routes)

fun navigation(key: String, active: String, routes: List<Node>) =
    tabNavigation(key, active, routes)

fun route(key: String, title: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Route(title), children = children)

fun route(key: String, title: String, block: ChildrenBuilder.() -> Unit) =
    route(key, title, ui(block))

fun fullscreenRoute(key: String, title: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Route(title, respectSafeArea = false), children = children)

fun toggle(key: String, label: String?, checked: Boolean, onChange: String) =
    Node(NodeKey(key), NodeKind.Toggle(label, checked, onChange))

fun toggle(
    key: String,
    label: String?,
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

fun divider(key: String) = Node(NodeKey(key), NodeKind.Divider)
fun card(key: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Card, children = children)

fun chip(
    key: String,
    label: String,
    variant: ChipVariant = ChipVariant.Input,
    onDismiss: String? = null,
) = Node(NodeKey(key), NodeKind.Chip(label, variant, onDismiss))

fun inputChip(key: String, label: String) = chip(key, label)
fun filterChip(key: String, label: String, onDismiss: String? = null) =
    chip(key, label, ChipVariant.Filter, onDismiss)

fun Node.accessibility(
    label: String,
    role: SemanticRole,
    hint: String? = semantics.hint,
) = copy(semantics = semantics.copy(label = label, role = role, hint = hint))

fun Node.fromSource(file: String, line: Int? = null, column: Int? = null) =
    copy(source = SourceLocation(file, line, column))

fun Node.visibleWhen(binding: StateBinding<Boolean>) =
    withBinding("visible", binding)

fun Node.enabledWhen(binding: StateBinding<Boolean>) =
    withBinding("enabled", binding)

private fun <T> Node.withBinding(field: String, binding: StateBinding<T>) =
    copy(bindings = bindings + (field to binding.reference))

fun Node.disabled() = copy(semantics = semantics.copy(enabled = false))

fun Node.irreversible() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(irreversible = true),
    ),
)

fun Node.frequent() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(frequency = ActionFrequency.Frequent),
    ),
)

fun Node.critical() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(importance = Importance.Critical),
    ),
)

private fun Node.extension(extension: PlatformExtension) =
    copy(extensions = extensions + extension)

fun Node.iosHaptic(type: HapticType) =
    extension(PlatformExtension.Ios(IosExtension.Haptic(type)))

fun Node.iosPresentation(style: PresentationStyle) =
    extension(PlatformExtension.Ios(IosExtension.Presentation(style)))

fun Node.iosSwipeAction(action: String) =
    extension(PlatformExtension.Ios(IosExtension.SwipeAction(action)))

fun Node.ipadosMulticolumn(columns: UInt) =
    extension(PlatformExtension.IpadOs(IpadOsExtension.MultiColumn(columns)))

fun Node.ipadosSidebar() =
    extension(PlatformExtension.IpadOs(IpadOsExtension.Sidebar))

fun Node.watchCrown(sensitivity: CrownSensitivity) =
    extension(PlatformExtension.WatchOs(WatchOsExtension.DigitalCrown(sensitivity)))

fun Node.watchGlance(priority: GlancePriority) =
    extension(PlatformExtension.WatchOs(WatchOsExtension.Glance(priority)))

fun Node.macShortcut(key: String, modifiers: List<KeyModifier>) =
    extension(PlatformExtension.MacOs(MacOsExtension.KeyboardShortcut(key, modifiers)))

fun Node.macToolbar(itemId: String) =
    extension(PlatformExtension.MacOs(MacOsExtension.ToolbarItem(itemId)))

fun Node.androidElevation(dp: Float) =
    extension(PlatformExtension.Android(AndroidExtension.Elevation(dp)))

fun Node.windowsCorner(radius: Float) =
    extension(PlatformExtension.Windows(WindowsExtension.CornerRadius(radius)))
