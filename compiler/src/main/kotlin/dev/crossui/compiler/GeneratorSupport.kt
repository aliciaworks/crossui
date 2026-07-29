package dev.crossui.compiler

import dev.crossui.ir.*

internal fun spacing(token: String?): Int = when (token) {
    "spacing.sm" -> 8
    "spacing.lg" -> 24
    else -> 16
}

internal fun ExportTarget.outputFileName(typeName: String): String = when (this) {
    ExportTarget.SwiftUi -> "$typeName.swift"
    ExportTarget.JetpackCompose -> "$typeName.kt"
    ExportTarget.WinUi3 -> "$typeName.xaml"
}

internal fun String.swift() =
    replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.kotlin() =
    replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.xml() =
    replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

internal fun String.csharp(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.sourceEscaped(target: ExportTarget): String = when (target) {
    ExportTarget.SwiftUi -> swift()
    ExportTarget.JetpackCompose -> kotlin()
    ExportTarget.WinUi3 -> csharp()
}

internal fun String.identifier() =
    split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        .ifEmpty { "Value" }

private fun String.androidResourceName(): String =
    lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifEmpty { "crossui_text" }
        .let { if (it.first().isDigit()) "crossui_$it" else it }

internal fun Node.swiftText(
    field: LocalizedField,
    fallback: String,
    localization: LocalizationRegistry,
): String = when (val text = localizedText[field]) {
    null -> "\"${fallback.swift()}\""
    is LocalizedText.Literal -> "\"${text.value.swift()}\""
    is LocalizedText.Resource -> localization.render(ExportTarget.SwiftUi, text)
        ?: buildString {
            append("String(localized: \"${text.key.swift()}\", ")
            append("defaultValue: \"${text.fallback.swift()}\"")
            text.namespace?.let { append(", table: \"${it.swift()}\"") }
            append(")")
        }
}

internal fun PickerOption.swiftText(
    localization: LocalizationRegistry,
): String = when (val text = localizedLabel) {
    null -> "\"${label.swift()}\""
    is LocalizedText.Literal -> "\"${text.value.swift()}\""
    is LocalizedText.Resource -> localization.render(ExportTarget.SwiftUi, text)
        ?: buildString {
            append("String(localized: \"${text.key.swift()}\", ")
            append("defaultValue: \"${text.fallback.swift()}\"")
            text.namespace?.let { append(", table: \"${it.swift()}\"") }
            append(")")
        }
}

internal fun Node.composeText(
    field: LocalizedField,
    fallback: String,
    localization: LocalizationRegistry,
): String = localizedText[field].composeText(fallback, localization)

internal fun PickerOption.composeText(
    localization: LocalizationRegistry,
): String = localizedLabel.composeText(label, localization)

private fun LocalizedText?.composeText(
    fallback: String,
    localization: LocalizationRegistry,
): String = when (this) {
    null -> "\"${fallback.kotlin()}\""
    is LocalizedText.Literal -> "\"${value.kotlin()}\""
    is LocalizedText.Resource -> localization.render(
        ExportTarget.JetpackCompose,
        this,
    ) ?: run {
        val prefix = namespace?.let { "${it}_" }.orEmpty()
        val resource = (prefix + key).androidResourceName()
        "stringResource(${localization.androidResourceClass}.string.$resource)"
    }
}

internal fun Node.xamlText(
    field: LocalizedField,
    fallback: String,
): String = when (val text = localizedText[field]) {
    is LocalizedText.Resource ->
        "{x:Bind ${localizedPropertyName(field)}, Mode=OneTime}"
    is LocalizedText.Literal -> text.value.xml()
    null -> fallback.xml()
}

internal fun PickerOption.xamlText(node: Node): String {
    val text = localizedLabel
    return when (text) {
        is LocalizedText.Resource ->
            "{x:Bind ${node.localizedOptionPropertyName(this)}, Mode=OneTime}"
        is LocalizedText.Literal -> text.value.xml()
        null -> label.xml()
    }
}

private fun Node.localizedPropertyName(field: LocalizedField): String =
    "Localized${key.value.identifier()}${field.name}"

private fun Node.localizedOptionPropertyName(option: PickerOption): String =
    "Localized${key.value.identifier()}Option${option.value.identifier()}"

internal data class WinLocalizedProperty(
    val propertyName: String,
    val text: LocalizedText.Resource,
)

internal fun Node.winLocalizedProperties(): List<WinLocalizedProperty> =
    buildList {
        walk { node ->
            node.localizedText.forEach { (field, text) ->
                if (text is LocalizedText.Resource) {
                    add(WinLocalizedProperty(node.localizedPropertyName(field), text))
                }
            }
            (node.kind as? NodeKind.Picker)?.options?.forEach { option ->
                val text = option.localizedLabel
                if (text is LocalizedText.Resource) {
                    add(
                        WinLocalizedProperty(
                            node.localizedOptionPropertyName(option),
                            text,
                        ),
                    )
                }
            }
        }
    }

internal fun SettingDeclaration.swiftStorageName(): String =
    "stored${statePath.identifier()}"

internal fun SettingDeclaration.swiftType(): String = when (valueType) {
    SettingValueType.Boolean -> "Bool"
    SettingValueType.String -> "String"
    SettingValueType.Int -> "Int"
    SettingValueType.Double -> "Double"
}

internal fun SettingDeclaration.swiftDefault(): String = when (valueType) {
    SettingValueType.Boolean -> defaultValue.lowercase()
    SettingValueType.String -> "\"${defaultValue.swift()}\""
    SettingValueType.Int,
    SettingValueType.Double,
    -> defaultValue
}

internal fun SettingDeclaration.swiftSerialized(expression: String): String =
    if (valueType == SettingValueType.String) expression else "String($expression)"

internal fun Node.swiftValue(field: String, fallback: String): String =
    bindings[field]?.let { "state.${it.path}" } ?: fallback

internal fun Node.swiftBinding(
    field: String,
    fallback: String,
    action: String,
): String = bindings[field]?.let {
    "Binding(get: { state.${it.path} }, set: { dispatch(\"${action.swift()}\", String(describing: \$0)) })"
} ?: ".constant($fallback)"

internal fun Node.composeValue(field: String, fallback: String): String =
    bindings[field]?.let { "state.${it.path}" } ?: fallback

internal fun Node.composeEnabled(): String =
    bindings["enabled"]?.let { "state.${it.path}" } ?: semantics.enabled.toString()

internal fun Node.anyNode(predicate: (Node) -> Boolean): Boolean =
    predicate(this) || children.any { it.anyNode(predicate) }

internal fun Node.xamlValue(
    field: String,
    fallback: String,
    mode: String = "TwoWay",
): String = bindings[field]?.let {
    "{x:Bind State.${it.path.replaceFirstChar(Char::uppercaseChar)}, Mode=$mode}"
} ?: fallback

internal data class WinBindingProperty(
    val propertyName: String,
    val csharpType: String,
    val defaultValue: String,
    val action: String?,
    val temporalMode: DatePickerMode? = null,
)

internal fun Node.bindingProperties(): List<WinBindingProperty> {
    val properties = linkedMapOf<String, WinBindingProperty>()
    walk { node ->
        node.bindings.forEach { (field, binding) ->
            val propertyName = binding.path.identifier()
            val temporalMode = (node.kind as? NodeKind.DatePicker)
                ?.takeIf { field == "value" }
                ?.mode
            val type = when {
                temporalMode == DatePickerMode.Time -> "TimeSpan?"
                temporalMode != null -> "DateTimeOffset?"
                binding.valueType == "Boolean" -> "bool"
                binding.valueType in setOf("Double", "Float") -> "double"
                binding.valueType in setOf("Int", "Long") -> "long"
                else -> "string"
            }
            val candidate = WinBindingProperty(
                propertyName = propertyName,
                csharpType = type,
                defaultValue = node.csharpDefault(field, type),
                action = node.bindingAction(field),
                temporalMode = temporalMode,
            )
            properties[propertyName] = properties[propertyName]
                ?.let { existing ->
                    existing.copy(action = existing.action ?: candidate.action)
                }
                ?: candidate
        }
    }
    return properties.values.toList()
}

private fun Node.bindingAction(field: String): String? = when (val value = kind) {
    is NodeKind.Input -> value.onChange.takeIf { field == "value" }
    is NodeKind.Toggle -> value.onChange.takeIf { field == "checked" }
    is NodeKind.Slider -> value.onChange.takeIf { field == "value" }
    is NodeKind.Picker -> value.onChange.takeIf { field == "selected" }
    is NodeKind.DatePicker -> value.onChange.takeIf { field == "value" }
    is NodeKind.Checkbox -> value.onChange.takeIf { field == "checked" }
    else -> null
}

private fun Node.csharpDefault(field: String, type: String): String = when (type) {
    "DateTimeOffset?", "TimeSpan?" -> "null"
    "bool" -> when (val value = kind) {
        is NodeKind.Toggle -> value.checked.toString()
        is NodeKind.Checkbox -> value.checked.toString()
        else -> if (field == "enabled") semantics.enabled.toString() else "false"
    }
    "double" -> when (val value = kind) {
        is NodeKind.Slider -> "${value.value}d"
        else -> "0d"
    }
    "long" -> "0L"
    else -> {
        val value = when (val kind = kind) {
            is NodeKind.Text -> kind.text
            is NodeKind.Input -> kind.value
            is NodeKind.Picker -> kind.selected
            is NodeKind.DatePicker -> kind.value.orEmpty()
            else -> ""
        }
        "\"${value.csharp()}\""
    }
}

internal fun missingNativeView(name: String, target: ExportTarget) =
    IllegalArgumentException(
        "Native view '$name' has no ${target.cliName} implementation. " +
            "Register it in NativeViewRegistry before generation.",
    )

internal fun InputType.composeKeyboard() = when (this) {
    InputType.Email -> "Email"
    InputType.Number -> "Number"
    InputType.Phone -> "Phone"
    InputType.Url -> "Uri"
    InputType.Password -> "Password"
    InputType.Text -> "Text"
}
