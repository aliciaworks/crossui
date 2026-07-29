package dev.crossui.compiler

import dev.crossui.ir.LocalizedField
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKind

internal fun composeToggle(
    node: Node,
    kind: NodeKind.Toggle,
    indentation: String,
    localization: LocalizationRegistry,
): String {
    val switch = "Switch(checked = ${node.composeValue("checked", kind.checked.toString())}, " +
        "onCheckedChange = { dispatch(\"${kind.onChange.kotlin()}\", it.toString()) }, " +
        "enabled = ${node.composeEnabled()})"
    val label = kind.label ?: return "$indentation$switch"
    return "$indentation${"Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {"}\n" +
        "$indentation    Text(" +
        node.composeText(LocalizedField.Label, label, localization) +
        ", modifier = Modifier.weight(1f))\n" +
        "$indentation    $switch\n" +
        "$indentation}"
}

internal fun composePicker(
    node: Node,
    kind: NodeKind.Picker,
    indentation: String,
    localization: LocalizationRegistry,
): String {
    val suffix = node.key.value.identifier()
    val expanded = "crossUi${suffix}Expanded"
    val selectedValue = node.composeValue("selected", "\"${kind.selected.kotlin()}\"")
    val selectedLabel = kind.options.joinToString(
        separator = "\n",
        prefix = "when ($selectedValue) {\n",
        postfix = "\n${indentation}    else -> $selectedValue\n$indentation}",
    ) {
        "$indentation    \"${it.value.kotlin()}\" -> ${it.composeText(localization)}"
    }
    val items = kind.options.joinToString("\n") {
        "$indentation        DropdownMenuItem(" +
            "text = { Text(${it.composeText(localization)}) }, " +
            "onClick = { $expanded = false; dispatch(\"${kind.onChange.kotlin()}\", " +
            "\"${it.value.kotlin()}\") })"
    }
    return """
        |${indentation}var $expanded by remember { mutableStateOf(false) }
        |${indentation}ExposedDropdownMenuBox(
        |${indentation}    expanded = $expanded,
        |${indentation}    onExpandedChange = { $expanded = it },
        |${indentation}) {
        |${indentation}    OutlinedTextField(
        |${indentation}        value = $selectedLabel,
        |${indentation}        onValueChange = {},
        |${indentation}        modifier = Modifier.menuAnchor(
        |${indentation}            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
        |${indentation}        ).fillMaxWidth(),
        |${indentation}        readOnly = true,
        |${indentation}        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon($expanded) },
        |${indentation}    )
        |${indentation}    ExposedDropdownMenu(
        |${indentation}        expanded = $expanded,
        |${indentation}        onDismissRequest = { $expanded = false },
        |${indentation}    ) {
        |$items
        |${indentation}    }
        |${indentation}}
    """.trimMargin()
}
