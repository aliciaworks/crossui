package dev.crossui.compiler

import dev.crossui.ir.Alignment
import dev.crossui.ir.Axis
import dev.crossui.ir.LocalizedField
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKind
import dev.crossui.ir.TextStyle

internal fun composeNavigation(
    node: Node,
    kind: NodeKind.Navigation,
    depth: Int,
    indentation: String,
    localization: LocalizationRegistry,
    render: (Node, Int) -> String,
): String {
    val selected = node.children.firstOrNull { it.key.value == kind.active }
        ?: node.children.firstOrNull()
    if (kind.mode != dev.crossui.ir.NavigationMode.Tab) {
        return selected?.let { render(it, depth) } ?: "${indentation}Spacer(Modifier)"
    }
    val selectionName = "crossUiSelected${node.key.value.identifier()}"
    val selection = node.bindings["active"]?.let {
        "${indentation}val $selectionName = state.${it.path}"
    } ?: run {
        "${indentation}var $selectionName by rememberSaveable { " +
            "mutableStateOf(\"${kind.active.kotlin()}\") }"
    }
    val items = node.children.joinToString("\n") { route ->
        val title = (route.kind as? NodeKind.Route)?.title ?: route.key.value
        val label = route.composeText(LocalizedField.Title, title, localization)
        val selectLocally = if ("active" in node.bindings) {
            ""
        } else {
            "$selectionName = \"${route.key.value.kotlin()}\"; "
        }
        "${indentation}        item(" +
            "selected = $selectionName == \"${route.key.value.kotlin()}\", " +
            "onClick = { $selectLocally" +
            "dispatch(\"${kind.onChange.kotlin()}\", \"${route.key.value.kotlin()}\") }, " +
            "icon = { Text($label.take(1)) }, label = { Text($label) })"
    }
    val routeContents = node.children.joinToString("\n") { route ->
        val contentName = "crossUi${node.key.value.identifier()}${route.key.value.identifier()}"
        val scroll = if (route.anyNode { it.kind is NodeKind.ListNode }) {
            ""
        } else {
            ".verticalScroll(rememberScrollState())"
        }
        """
        |${indentation}val $contentName: @Composable () -> Unit = {
        |${indentation}    Box(
        |${indentation}        Modifier.fillMaxSize()$scroll,
        |${indentation}        contentAlignment = Alignment.TopCenter,
        |${indentation}    ) {
        |${indentation}        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
        |${render(route, depth + 3)}
        |${indentation}        }
        |${indentation}    }
        |${indentation}}
        """.trimMargin()
    }
    val branches = node.children.joinToString("\n") { route ->
        val contentName = "crossUi${node.key.value.identifier()}${route.key.value.identifier()}"
        "${indentation}            \"${route.key.value.kotlin()}\" -> $contentName()"
    }
    val fallbackName = selected?.let {
        "crossUi${node.key.value.identifier()}${it.key.value.identifier()}"
    }
    val fallback = "${indentation}            else -> ${fallbackName ?: "Unit"}" +
        if (fallbackName == null) "" else "()"
    return """
        |$selection
        |$routeContents
        |${indentation}NavigationSuiteScaffold(
        |${indentation}    modifier = Modifier.fillMaxSize(),
        |${indentation}    navigationSuiteItems = {
        |$items
        |${indentation}    },
        |${indentation}) {
        |${indentation}    Box(
        |${indentation}        Modifier.fillMaxSize()
        |${indentation}            .windowInsetsPadding(WindowInsets.safeDrawing)
        |${indentation}            .padding(horizontal = 16.dp, vertical = 16.dp),
        |${indentation}        contentAlignment = Alignment.TopCenter,
        |${indentation}    ) {
        |${indentation}        when ($selectionName) {
        |$branches
        |$fallback
        |${indentation}        }
        |${indentation}    }
        |${indentation}}
    """.trimMargin()
}

internal fun NodeKind.Stack.composeAlignment(): String = when (axis) {
    Axis.Vertical -> when (alignment) {
        Alignment.Start, Alignment.Stretch -> ", horizontalAlignment = Alignment.Start"
        Alignment.Center -> ", horizontalAlignment = Alignment.CenterHorizontally"
        Alignment.End -> ", horizontalAlignment = Alignment.End"
    }
    Axis.Horizontal -> when (alignment) {
        Alignment.Start -> ", verticalAlignment = Alignment.Top"
        Alignment.Center, Alignment.Stretch -> ", verticalAlignment = Alignment.CenterVertically"
        Alignment.End -> ", verticalAlignment = Alignment.Bottom"
    }
}

internal fun TextStyle.composeTypography(): String = when (this) {
    TextStyle.Display -> "displaySmall"
    TextStyle.Headline -> "headlineMedium"
    TextStyle.Title -> "titleLarge"
    TextStyle.Body -> "bodyLarge"
    TextStyle.Caption -> "labelMedium"
    TextStyle.Footnote -> "bodySmall"
}
