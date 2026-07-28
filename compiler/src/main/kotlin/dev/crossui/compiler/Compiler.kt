package dev.crossui.compiler

import dev.crossui.ir.*
import dev.crossui.legalizer.ResolvedDocument
import dev.crossui.legalizer.compile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ExportTarget(
    val cliName: String,
    val fileName: String,
) {
    SwiftUi("swiftui", "CrossUiGenerated.swift"),
    JetpackCompose("compose", "CrossUiGenerated.kt"),
    WinUi3("winui3", "CrossUiGenerated.xaml");

    companion object {
        fun parse(value: String): ExportTarget =
            entries.firstOrNull { it.cliName == value.lowercase() }
                ?: error("Unsupported target '$value'. Use swiftui, compose, or winui3.")
    }
}

data class GeneratedSource(
    val target: ExportTarget,
    val relativePath: String,
    val content: String,
    val mappings: List<SourceMapEntry> = emptyList(),
)

@Serializable
data class SourceMapEntry(
    val nodeKey: String,
    val generatedFile: String,
    val generatedLine: Int,
    val source: SourceLocation? = null,
)

@Serializable
data class SourceMapManifest(
    val version: Int = 1,
    val entries: List<SourceMapEntry>,
)

data class NativeViewKey(
    val target: ExportTarget,
    val name: String,
)

class NativeViewRegistry private constructor(
    private val renderers: Map<NativeViewKey, String>,
) {
    fun render(target: ExportTarget, name: String, payload: String): String? =
        renderers[NativeViewKey(target, name)]?.replace("{{payload}}", payload)

    class Builder {
        private val renderers = mutableMapOf<NativeViewKey, String>()

        fun register(target: ExportTarget, name: String, sourceTemplate: String) {
            require(name.isNotBlank()) { "Native view name cannot be blank." }
            renderers[NativeViewKey(target, name)] = sourceTemplate
        }

        fun build() = NativeViewRegistry(renderers.toMap())
    }

    companion object {
        val Empty = NativeViewRegistry(emptyMap())

        fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }
}

interface PlatformLowering {
    val target: ExportTarget
    fun lower(document: UiDocument): ResolvedDocument
}

private class DefaultLowering(
    override val target: ExportTarget,
    private val profile: TargetProfile,
) : PlatformLowering {
    override fun lower(document: UiDocument): ResolvedDocument =
        compile(document, profile, UnsupportedExtensionPolicy.Strip)
}

interface CodeGenerator {
    val target: ExportTarget
    fun generate(
        document: ResolvedDocument,
        typeName: String,
        nativeViews: NativeViewRegistry,
    ): GeneratedSource
}

object CrossUiCompiler {
    fun generate(
        document: UiDocument,
        targets: Set<ExportTarget> = ExportTarget.entries.toSet(),
        typeName: String = "CrossUiGenerated",
        nativeViews: NativeViewRegistry = NativeViewRegistry.Empty,
    ): List<GeneratedSource> {
        document.validate()
        return targets.map { target ->
            val lowering = DefaultLowering(target, target.profile())
            val resolved = lowering.lower(document)
            target.generator()
                .generate(resolved, typeName, nativeViews)
                .withSourceMappings(document)
        }
    }

    fun write(
        document: UiDocument,
        output: Path,
        targets: Set<ExportTarget> = ExportTarget.entries.toSet(),
        typeName: String = "CrossUiGenerated",
        nativeViews: NativeViewRegistry = NativeViewRegistry.Empty,
    ): List<Path> {
        val sources = generate(document, targets, typeName, nativeViews)
        val paths = sources.map { generated ->
            val path = output.resolve(generated.relativePath)
            Files.createDirectories(path.parent)
            Files.writeString(path, generated.content)
            path
        }
        val manifestPath = writeSourceMap(sources, output.resolve("crossui-map.json"))
        return paths + manifestPath
    }

    fun writeSourceMap(sources: List<GeneratedSource>, path: Path): Path {
        val manifest = SourceMapManifest(
            entries = sources.flatMap(GeneratedSource::mappings),
        )
        Files.createDirectories(path.parent)
        Files.writeString(path, sourceMapJson.encodeToString(manifest))
        return path
    }
}

private val sourceMapJson = Json {
    prettyPrint = true
    explicitNulls = false
}

private fun GeneratedSource.withSourceMappings(document: UiDocument): GeneratedSource {
    val nodes = buildMap<String, Node> {
        document.root.walk { put(it.key.value, it) }
    }
    val entries = content.lineSequence().mapIndexedNotNull { index, line ->
        val key = markerFor(target).find(line)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
        SourceMapEntry(
            nodeKey = key,
            generatedFile = relativePath,
            generatedLine = index + 1,
            source = nodes[key]?.source,
        )
    }.toList()
    return copy(mappings = entries)
}

private fun markerFor(target: ExportTarget): Regex = when (target) {
    ExportTarget.WinUi3 -> Regex("""<!-- crossui-node:([^ ]+) -->""")
    else -> Regex("""// crossui-node:([^ ]+)""")
}

private fun ExportTarget.profile() = when (this) {
    ExportTarget.SwiftUi -> TargetProfile.iphone()
    ExportTarget.JetpackCompose -> TargetProfile.androidPhone()
    ExportTarget.WinUi3 -> TargetProfile.windowsDesktop()
}

private fun ExportTarget.generator(): CodeGenerator = when (this) {
    ExportTarget.SwiftUi -> SwiftUiGenerator
    ExportTarget.JetpackCompose -> ComposeGenerator
    ExportTarget.WinUi3 -> WinUiGenerator
}

private object SwiftUiGenerator : CodeGenerator {
    override val target = ExportTarget.SwiftUi

    override fun generate(
        document: ResolvedDocument,
        typeName: String,
        nativeViews: NativeViewRegistry,
    ): GeneratedSource {
        val body = swiftNode(document.document.root, 2, nativeViews)
        val state = document.document.stateType?.let {
            "    @Bindable var state: ${it.substringAfterLast('.')}\n"
        }.orEmpty()
        return GeneratedSource(
            target,
            target.outputFileName(typeName),
            """
            |// Generated by CrossUI. Do not edit.
            |import SwiftUI
            |
            |struct $typeName: View {
            |$state
            |    let dispatch: (_ action: String, _ value: String?) -> Void
            |
            |    var body: some View {
            |$body
            |    }
            |}
            |""".trimMargin(),
        )
    }

    private fun swiftNode(
        node: Node,
        depth: Int,
        nativeViews: NativeViewRegistry,
    ): String {
        val i = "    ".repeat(depth)
        val marker = "$i// crossui-node:${node.key.value}\n"
        val child = { extra: Int ->
            node.children.joinToString("\n") {
                swiftNode(it, depth + extra, nativeViews)
            }
        }
        val generated = when (val kind = node.kind) {
            is NodeKind.Text -> {
                val style = when (kind.style) {
                    TextStyle.Display -> ".largeTitle"
                    TextStyle.Headline -> ".headline"
                    TextStyle.Title -> ".title"
                    TextStyle.Body -> ".body"
                    TextStyle.Caption -> ".caption"
                    TextStyle.Footnote -> ".footnote"
                }
                "$i${"Text(\"${kind.text.swift()}\")"}.font($style)"
            }
            is NodeKind.Button -> {
                val role = if (kind.variant == ButtonVariant.Destructive) ", role: .destructive" else ""
                "$i${"Button(\"${kind.label.swift()}\"$role) { dispatch(\"${kind.action.swift()}\", nil) }"}" +
                    enabled(node) + accessibility(node)
            }
            is NodeKind.Input -> {
                val prompt = (kind.placeholder ?: "").swift()
                val value = node.bindings["value"]?.let {
                    "Binding(get: { state.${it.path} }, set: { dispatch(\"${kind.onChange.swift()}\", \$0) })"
                } ?: ".constant(\"${kind.value.swift()}\")"
                val field = if (kind.secure || kind.inputType == InputType.Password) {
                    "SecureField(\"$prompt\", text: $value)"
                } else {
                    "TextField(\"$prompt\", text: $value)"
                }
                "$i$field${keyboard(kind.inputType)}${submit(kind.returnKey)}" +
                    enabled(node) + accessibility(node)
            }
            is NodeKind.Stack -> {
                val stack = if (kind.axis == Axis.Horizontal) "HStack" else "VStack"
                "$i$stack(spacing: ${spacing(kind.spacing)}) {\n${child(1)}\n$i}"
            }
            is NodeKind.ListNode ->
                "$i${"List {\n${child(1)}\n$i}"}.onTapGesture { dispatch(\"${kind.onSelect.orEmpty().swift()}\", nil) }"
            NodeKind.Form -> "$i${"Form {\n${child(1)}\n$i}"}"
            is NodeKind.Loading ->
                "$i${"ProgressView(\"${kind.label.orEmpty().swift()}\")"}"
            is NodeKind.Navigation -> {
                val selected = node.children.firstOrNull { it.key.value == kind.active }
                    ?: node.children.firstOrNull()
                if (kind.mode == NavigationMode.Tab) {
                    "$i${"TabView {\n"}" +
                        node.children.joinToString("\n") { route ->
                            val title = (route.kind as? NodeKind.Route)?.title ?: route.key.value
                            swiftNode(route, depth + 1, nativeViews) +
                                ".tabItem { Text(\"${title.swift()}\") }"
                        } + "\n$i}"
                } else {
                    "$i${"NavigationStack {\n${selected?.let { swiftNode(it, depth + 1, nativeViews) }.orEmpty()}\n$i}"}"
                }
            }
            is NodeKind.Route ->
                "$i${"VStack {\n${child(1)}\n$i}"}.navigationTitle(\"${kind.title.swift()}\")" +
                    if (kind.respectSafeArea) "" else ".ignoresSafeArea()"
            is NodeKind.PlatformView -> nativeViews.render(
                target,
                kind.name,
                kind.payload.toString(),
            )?.prependIndent(i) ?: throw missingNativeView(kind.name, target)
            is NodeKind.Toggle ->
                "$i${"Toggle(\"${kind.label.orEmpty().swift()}\", isOn: ${node.swiftBinding("checked", kind.checked.toString(), kind.onChange)})"}" +
                    enabled(node) + accessibility(node)
            is NodeKind.Image ->
                "$i${"AsyncImage(url: URL(string: \"${kind.src.swift()}\"))"}.accessibilityLabel(\"${kind.alt.orEmpty().swift()}\")"
            is NodeKind.Dialog -> {
                val body = child(1).ifBlank { "${"    ".repeat(depth + 1)}EmptyView()" }
                "$i${"VStack {\n$body\n$i}"}.confirmationDialog(\"${kind.title.swift()}\", isPresented: .constant(true)) {\n" +
                    listOfNotNull(
                        kind.confirmLabel?.let {
                            "${"    ".repeat(depth + 1)}Button(\"${it.swift()}\") { dispatch(\"${kind.confirmAction.orEmpty().swift()}\", nil) }"
                        },
                        kind.cancelLabel?.let {
                            "${"    ".repeat(depth + 1)}Button(\"${it.swift()}\", role: .cancel) { dispatch(\"${kind.cancelAction.orEmpty().swift()}\", nil) }"
                        },
                    ).joinToString("\n") + "\n$i}"
            }
            is NodeKind.Slider ->
                "$i${"Slider(value: ${node.swiftBinding("value", kind.value.toString(), kind.onChange)}, in: ${kind.min}...${kind.max}, step: ${kind.step ?: 1.0})"}" +
                    enabled(node) + accessibility(node)
            is NodeKind.Picker ->
                "$i${"Picker(\"\", selection: ${node.swiftBinding("selected", "\"${kind.selected.swift()}\"", kind.onChange)}) {\n"}" +
                    kind.options.joinToString("\n") {
                        "${"    ".repeat(depth + 1)}Text(\"${it.label.swift()}\").tag(\"${it.value.swift()}\")"
                    } + "\n$i}"
            is NodeKind.DatePicker ->
                "$i${"DatePicker(\"\", selection: .constant(Date()))"}" +
                    ".onChange(of: Date()) { _, value in dispatch(\"${kind.onChange.swift()}\", value.ISO8601Format()) }"
            is NodeKind.Checkbox ->
                "$i${"Button { dispatch(\"${kind.onChange.swift()}\", String(!${node.swiftValue("checked", kind.checked.toString())})) } label: { Label(\"${kind.label.orEmpty().swift()}\", systemImage: ${node.swiftValue("checked", kind.checked.toString())} ? \"checkmark.square.fill\" : \"square\") }"}" +
                    enabled(node) + accessibility(node)
            NodeKind.Divider -> "${i}Divider()"
            NodeKind.Card -> "$i${"GroupBox {\n${child(1)}\n$i}"}"
            is NodeKind.Chip ->
                "$i${"HStack { Text(\"${kind.label.swift()}\")"}" +
                    (kind.onDismiss?.let {
                        "; Button { dispatch(\"${it.swift()}\", nil) } label: { Image(systemName: \"xmark\") }"
                    } ?: "") + " }"
        }
        return marker + generated
    }

    private fun enabled(node: Node) = if (node.semantics.enabled) "" else ".disabled(true)"
    private fun accessibility(node: Node) =
        node.semantics.label?.let { ".accessibilityLabel(\"${it.swift()}\")" } ?: ""
    private fun keyboard(type: InputType) = when (type) {
        InputType.Email -> ".keyboardType(.emailAddress)"
        InputType.Number -> ".keyboardType(.numberPad)"
        InputType.Phone -> ".keyboardType(.phonePad)"
        InputType.Url -> ".keyboardType(.URL)"
        else -> ""
    }
    private fun submit(key: ReturnKey?) = key?.let {
        ".submitLabel(.${it.name.lowercase()})"
    } ?: ""
}

private object ComposeGenerator : CodeGenerator {
    override val target = ExportTarget.JetpackCompose

    override fun generate(
        document: ResolvedDocument,
        typeName: String,
        nativeViews: NativeViewRegistry,
    ): GeneratedSource {
        val body = composeNode(document.document.root, 1, nativeViews)
        val stateType = document.document.stateType
        val stateParameter = stateType?.let {
            "state: ${it.substringAfterLast('.')}, "
        }.orEmpty()
        val stateImport = stateType?.takeIf { '.' in it }?.let { "import $it\n" }.orEmpty()
        return GeneratedSource(
            target,
            target.outputFileName(typeName),
            """
            |// Generated by CrossUI. Do not edit.
            |package dev.crossui.generated
            |
            |import androidx.compose.foundation.layout.*
            |import androidx.compose.foundation.lazy.LazyColumn
            |import androidx.compose.foundation.lazy.items
            |import androidx.compose.foundation.text.KeyboardOptions
            |import androidx.compose.material3.*
            |import androidx.compose.runtime.*
            |import androidx.compose.ui.Modifier
            |import androidx.compose.ui.text.input.KeyboardType
            |import androidx.compose.ui.text.input.PasswordVisualTransformation
            |import androidx.compose.ui.unit.dp
            |import coil.compose.AsyncImage
            |$stateImport
            |
            |@OptIn(ExperimentalMaterial3Api::class)
            |@Composable
            |fun $typeName(${stateParameter}dispatch: (action: String, value: String?) -> Unit) {
            |$body
            |}
            |""".trimMargin(),
        )
    }

    private fun composeNode(
        node: Node,
        depth: Int,
        nativeViews: NativeViewRegistry,
    ): String {
        val i = "    ".repeat(depth)
        val marker = "$i// crossui-node:${node.key.value}\n"
        val child = { extra: Int ->
            node.children.joinToString("\n") {
                composeNode(it, depth + extra, nativeViews)
            }
        }
        val generated = when (val kind = node.kind) {
            is NodeKind.Text -> "$i${"Text(\"${kind.text.kotlin()}\")"}"
            is NodeKind.Button -> {
                val name = when (kind.variant) {
                    ButtonVariant.Primary -> "Button"
                    ButtonVariant.Secondary -> "OutlinedButton"
                    ButtonVariant.Destructive -> "Button"
                }
                "$i$name(onClick = { dispatch(\"${kind.action.kotlin()}\", null) }, enabled = ${node.semantics.enabled}) { Text(\"${kind.label.kotlin()}\") }"
            }
            is NodeKind.Input -> {
                val keyboard = "KeyboardOptions(keyboardType = KeyboardType.${kind.inputType.composeKeyboard()})"
                val value = node.composeValue("value", "\"${kind.value.kotlin()}\"")
                "$i${"OutlinedTextField(value = $value, onValueChange = { dispatch(\"${kind.onChange.kotlin()}\", it) }, label = { Text(\"${kind.placeholder.orEmpty().kotlin()}\") }, enabled = ${node.semantics.enabled}, keyboardOptions = $keyboard"}" +
                    if (kind.secure || kind.inputType == InputType.Password) {
                        ", visualTransformation = PasswordVisualTransformation())"
                    } else ")"
            }
            is NodeKind.Stack -> {
                val layout = if (kind.axis == Axis.Horizontal) "Row" else "Column"
                val arrangement = if (kind.axis == Axis.Horizontal) {
                    "horizontalArrangement"
                } else {
                    "verticalArrangement"
                }
                "$i$layout($arrangement = Arrangement.spacedBy(${spacing(kind.spacing)}.dp)) {\n${child(1)}\n$i}"
            }
            is NodeKind.ListNode ->
                "$i${"LazyColumn { item {\n${child(2)}\n${"    ".repeat(depth + 1)}}\n$i}"}"
            NodeKind.Form -> "$i${"Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {\n${child(1)}\n$i}"}"
            is NodeKind.Loading -> "$i${"CircularProgressIndicator()"}"
            is NodeKind.Navigation -> {
                val selected = node.children.firstOrNull { it.key.value == kind.active }
                    ?: node.children.firstOrNull()
                if (kind.mode == NavigationMode.Tab) {
                    "$i${"Column {\n${"    ".repeat(depth + 1)}NavigationBar {\n"}" +
                        node.children.joinToString("\n") { route ->
                            val title = (route.kind as? NodeKind.Route)?.title ?: route.key.value
                            "${"    ".repeat(depth + 2)}NavigationBarItem(selected = ${route.key.value == kind.active}, onClick = { dispatch(\"navigate\", \"${route.key.value.kotlin()}\") }, icon = {}, label = { Text(\"${title.kotlin()}\") })"
                        } + "\n${"    ".repeat(depth + 1)}}\n" +
                    selected?.let { composeNode(it, depth + 1, nativeViews) }.orEmpty() + "\n$i}"
                } else {
                    selected?.let { composeNode(it, depth, nativeViews) } ?: "${i}Spacer(Modifier)"
                }
            }
            is NodeKind.Route -> "$i${"Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {\n${child(1)}\n$i}"}"
            is NodeKind.PlatformView -> nativeViews.render(
                target,
                kind.name,
                kind.payload.toString(),
            )?.prependIndent(i) ?: throw missingNativeView(kind.name, target)
            is NodeKind.Toggle ->
                "$i${"Row { Switch(checked = ${node.composeValue("checked", kind.checked.toString())}, onCheckedChange = { dispatch(\"${kind.onChange.kotlin()}\", it.toString()) }, enabled = ${node.semantics.enabled})"}" +
                    (kind.label?.let { "; Text(\"${it.kotlin()}\")" } ?: "") + " }"
            is NodeKind.Image ->
                "$i${"AsyncImage(model = \"${kind.src.kotlin()}\", contentDescription = \"${kind.alt.orEmpty().kotlin()}\")"}"
            is NodeKind.Dialog ->
                "$i${"AlertDialog(onDismissRequest = { dispatch(\"${kind.cancelAction.orEmpty().kotlin()}\", null) }, title = { Text(\"${kind.title.kotlin()}\") }, confirmButton = { TextButton(onClick = { dispatch(\"${kind.confirmAction.orEmpty().kotlin()}\", null) }) { Text(\"${kind.confirmLabel.orEmpty().kotlin()}\") } }, dismissButton = { TextButton(onClick = { dispatch(\"${kind.cancelAction.orEmpty().kotlin()}\", null) }) { Text(\"${kind.cancelLabel.orEmpty().kotlin()}\") } })"}"
            is NodeKind.Slider ->
                "$i${"Slider(value = ${node.composeValue("value", "${kind.value}f")}, onValueChange = { dispatch(\"${kind.onChange.kotlin()}\", it.toString()) }, valueRange = ${kind.min}f..${kind.max}f, enabled = ${node.semantics.enabled})"}"
            is NodeKind.Picker ->
                "$i${"Column { Text(${node.composeValue("selected", "\"${kind.options.firstOrNull { it.value == kind.selected }?.label.orEmpty().kotlin()}\"")})"}" +
                    kind.options.joinToString("") {
                        "; TextButton(onClick = { dispatch(\"${kind.onChange.kotlin()}\", \"${it.value.kotlin()}\") }) { Text(\"${it.label.kotlin()}\") }"
                    } + " }"
            is NodeKind.DatePicker -> {
                val variable = "dateState" + node.key.value.identifier()
                "$i${"val $variable = rememberDatePickerState()"}\n" +
                    "$i${"DatePicker(state = $variable)"}\n" +
                    "$i${"LaunchedEffect($variable.selectedDateMillis) { $variable.selectedDateMillis?.let { dispatch(\"${kind.onChange.kotlin()}\", it.toString()) } }"}"
            }
            is NodeKind.Checkbox ->
                "$i${"Row { Checkbox(checked = ${node.composeValue("checked", kind.checked.toString())}, onCheckedChange = { dispatch(\"${kind.onChange.kotlin()}\", it.toString()) }, enabled = ${node.semantics.enabled})"}" +
                    (kind.label?.let { "; Text(\"${it.kotlin()}\")" } ?: "") + " }"
            NodeKind.Divider -> "${i}HorizontalDivider()"
            NodeKind.Card -> "$i${"Card { Column(Modifier.padding(16.dp)) {\n${child(2)}\n${"    ".repeat(depth + 1)}}\n$i}"}"
            is NodeKind.Chip -> {
                val onDismiss = kind.onDismiss
                if (onDismiss == null) {
                    "$i${"AssistChip(onClick = {}, label = { Text(\"${kind.label.kotlin()}\") })"}"
                } else {
                    "$i${"InputChip(selected = true, onClick = { dispatch(\"${onDismiss.kotlin()}\", null) }, label = { Text(\"${kind.label.kotlin()}\") })"}"
                }
            }
        }
        return marker + generated
    }
}

private object WinUiGenerator : CodeGenerator {
    override val target = ExportTarget.WinUi3

    override fun generate(
        document: ResolvedDocument,
        typeName: String,
        nativeViews: NativeViewRegistry,
    ): GeneratedSource {
        val body = xamlNode(document.document.root, 2, nativeViews)
        return GeneratedSource(
            target,
            target.outputFileName(typeName),
            """
            |<!-- Generated by CrossUI. Do not edit. -->
            |<UserControl
            |    x:Class="CrossUi.Generated.$typeName"
            |    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
            |    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
            |    <ScrollViewer>
            |$body
            |    </ScrollViewer>
            |</UserControl>
            |""".trimMargin(),
        )
    }

    private fun xamlNode(
        node: Node,
        depth: Int,
        nativeViews: NativeViewRegistry,
    ): String {
        val i = "    ".repeat(depth)
        val marker = "$i<!-- crossui-node:${node.key.value} -->\n"
        val child = { extra: Int ->
            node.children.joinToString("\n") {
                xamlNode(it, depth + extra, nativeViews)
            }
        }
        val enabled = if (node.semantics.enabled) "" else " IsEnabled=\"False\""
        val automation = node.semantics.label?.let {
            " AutomationProperties.Name=\"${it.xml()}\""
        } ?: ""
        val generated = when (val kind = node.kind) {
            is NodeKind.Text -> "$i<TextBlock Text=\"${kind.text.xml()}\"$automation />"
            is NodeKind.Button -> "$i<Button Content=\"${kind.label.xml()}\" Tag=\"${kind.action.xml()}\"$enabled$automation />"
            is NodeKind.Input -> {
                val control = if (kind.secure || kind.inputType == InputType.Password) "PasswordBox" else "TextBox"
                val value = if (control == "PasswordBox") "Password" else "Text"
                val content = node.xamlValue("value", kind.value.xml())
                "$i<$control $value=\"$content\" PlaceholderText=\"${kind.placeholder.orEmpty().xml()}\" Tag=\"${kind.onChange.xml()}\"$enabled$automation />"
            }
            is NodeKind.Stack -> {
                val orientation = if (kind.axis == Axis.Horizontal) "Horizontal" else "Vertical"
                "$i<StackPanel Orientation=\"$orientation\" Spacing=\"${spacing(kind.spacing)}\">\n${child(1)}\n$i</StackPanel>"
            }
            is NodeKind.ListNode -> "$i<StackPanel Tag=\"${kind.onSelect.orEmpty().xml()}\">\n${child(1)}\n$i</StackPanel>"
            NodeKind.Form -> "$i<StackPanel Spacing=\"16\">\n${child(1)}\n$i</StackPanel>"
            is NodeKind.Loading -> "$i<ProgressRing IsActive=\"True\" />"
            is NodeKind.Navigation -> {
                val selected = node.children.firstOrNull { it.key.value == kind.active }
                    ?: node.children.firstOrNull()
                "$i<NavigationView PaneDisplayMode=\"${if (kind.mode == NavigationMode.Tab) "Top" else "Left"}\">\n" +
                    "${"    ".repeat(depth + 1)}<NavigationView.Content>\n" +
                    selected?.let { xamlNode(it, depth + 2, nativeViews) }.orEmpty() +
                    "\n${"    ".repeat(depth + 1)}</NavigationView.Content>\n$i</NavigationView>"
            }
            is NodeKind.Route -> "$i<StackPanel Spacing=\"16\">\n${child(1)}\n$i</StackPanel>"
            is NodeKind.PlatformView -> nativeViews.render(
                target,
                kind.name,
                kind.payload.toString(),
            )?.prependIndent(i) ?: throw missingNativeView(kind.name, target)
            is NodeKind.Toggle ->
                "$i<ToggleSwitch Header=\"${kind.label.orEmpty().xml()}\" IsOn=\"${node.xamlValue("checked", kind.checked.toString())}\" Tag=\"${kind.onChange.xml()}\"$enabled$automation />"
            is NodeKind.Image -> "$i<Image Source=\"${kind.src.xml()}\" AutomationProperties.Name=\"${kind.alt.orEmpty().xml()}\" />"
            is NodeKind.Dialog ->
                "$i<!-- ContentDialog Title=\"${kind.title.xml()}\" PrimaryButtonText=\"${kind.confirmLabel.orEmpty().xml()}\" CloseButtonText=\"${kind.cancelLabel.orEmpty().xml()}\" -->"
            is NodeKind.Slider ->
                "$i<Slider Value=\"${node.xamlValue("value", kind.value.toString())}\" Minimum=\"${kind.min}\" Maximum=\"${kind.max}\" StepFrequency=\"${kind.step ?: 1.0}\" Tag=\"${kind.onChange.xml()}\"$enabled$automation />"
            is NodeKind.Picker ->
                "$i<ComboBox SelectedValue=\"${node.xamlValue("selected", kind.selected.xml())}\" Tag=\"${kind.onChange.xml()}\"$enabled>\n" +
                    kind.options.joinToString("\n") {
                        "${"    ".repeat(depth + 1)}<ComboBoxItem Content=\"${it.label.xml()}\" Tag=\"${it.value.xml()}\" />"
                    } + "\n$i</ComboBox>"
            is NodeKind.DatePicker -> when (kind.mode) {
                DatePickerMode.Time -> "$i<TimePicker Tag=\"${kind.onChange.xml()}\"$enabled />"
                else -> "$i<CalendarDatePicker Tag=\"${kind.onChange.xml()}\"$enabled />"
            }
            is NodeKind.Checkbox ->
                "$i<CheckBox Content=\"${kind.label.orEmpty().xml()}\" IsChecked=\"${node.xamlValue("checked", kind.checked.toString())}\" Tag=\"${kind.onChange.xml()}\"$enabled$automation />"
            NodeKind.Divider ->
                "$i<Border Height=\"1\" Background=\"{ThemeResource SystemControlForegroundBaseLowBrush}\" />"
            NodeKind.Card ->
                "$i<Border CornerRadius=\"8\" Padding=\"16\" Background=\"{ThemeResource CardBackgroundFillColorDefaultBrush}\">\n${child(1)}\n$i</Border>"
            is NodeKind.Chip ->
                "$i<Border CornerRadius=\"12\" Padding=\"8,4\" Background=\"{ThemeResource AccentFillColorDefaultBrush}\"><TextBlock Text=\"${kind.label.xml()}\" /></Border>"
        }
        return marker + generated
    }
}

private fun spacing(token: String?): Int = when (token) {
    "spacing.sm" -> 8
    "spacing.lg" -> 24
    else -> 16
}

private fun ExportTarget.outputFileName(typeName: String): String = when (this) {
    ExportTarget.SwiftUi -> "$typeName.swift"
    ExportTarget.JetpackCompose -> "$typeName.kt"
    ExportTarget.WinUi3 -> "$typeName.xaml"
}

private fun String.swift() = replace("\\", "\\\\").replace("\"", "\\\"")
private fun String.kotlin() = replace("\\", "\\\\").replace("\"", "\\\"")
private fun String.xml() = replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
private fun String.identifier() =
    split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        .ifEmpty { "Value" }

private fun Node.swiftValue(field: String, fallback: String): String =
    bindings[field]?.let { "state.${it.path}" } ?: fallback

private fun Node.swiftBinding(field: String, fallback: String, action: String): String =
    bindings[field]?.let {
        "Binding(get: { state.${it.path} }, set: { dispatch(\"${action.swift()}\", String(describing: \$0)) })"
    } ?: ".constant($fallback)"

private fun Node.composeValue(field: String, fallback: String): String =
    bindings[field]?.let { "state.${it.path}" } ?: fallback

private fun Node.xamlValue(field: String, fallback: String): String =
    bindings[field]?.let {
        "{x:Bind State.${it.path.replaceFirstChar(Char::uppercaseChar)}, Mode=TwoWay}"
    } ?: fallback

private fun missingNativeView(name: String, target: ExportTarget) =
    IllegalArgumentException(
        "Native view '$name' has no ${target.cliName} implementation. " +
            "Register it in NativeViewRegistry before generation.",
    )

private fun InputType.composeKeyboard() = when (this) {
    InputType.Email -> "Email"
    InputType.Number -> "Number"
    InputType.Phone -> "Phone"
    InputType.Url -> "Uri"
    InputType.Password -> "Password"
    InputType.Text -> "Text"
}
