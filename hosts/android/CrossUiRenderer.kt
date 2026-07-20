// Reference Jetpack Compose host. Production code should decode the same
// versioned JSON contract (IR v2).
// HIG targets: Material Design 3 (Android system dynamic colour where available).
package dev.crossui.host

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.serialization.*
import kotlinx.serialization.json.*

// ---- JSON model -------------------------------------------------------------

@Serializable
data class CrossUiDocument(
    val version: Int = 2,
    val root: CrossUiNode,
    val theme: CrossUiTheme = CrossUiTheme()
)

@Serializable
data class CrossUiTheme(
    val color_scheme: String? = null,
    val tokens: Map<String, CrossUiToken> = emptyMap()
) {
    fun primaryColor(): String? = (tokens["primary"] as? CrossUiToken.Color)?.value
}

@Serializable
sealed class CrossUiToken {
    @Serializable @SerialName("color") data class Color(val value: String) : CrossUiToken()
    @Serializable @SerialName("number") data class Number(val value: Double) : CrossUiToken()
}

@Serializable
data class CrossUiNode(
    val key: String, val type: String,
    val text: String? = null, val style: String? = null,
    val title: String? = null, val label: String? = null,
    val value: String? = null, val action: String? = null,
    val variant: String? = null,
    @SerialName("on_change") val onChange: String? = null,
    @SerialName("on_select") val onSelect: String? = null,
    val active: String? = null, val axis: String? = null,
    val alignment: String? = null, val spacing: String? = null,
    val platform: String? = null, val name: String? = null,
    val payload: JsonElement? = null, val placeholder: String? = null,
    val secure: Boolean = false,
    @SerialName("input_type") val inputType: String? = null,
    @SerialName("return_key") val returnKey: String? = null,
    val src: String? = null, val alt: String? = null,
    val checked: Boolean = false,
    @SerialName("confirm_label") val confirmLabel: String? = null,
    @SerialName("confirm_action") val confirmAction: String? = null,
    @SerialName("cancel_label") val cancelLabel: String? = null,
    @SerialName("cancel_action") val cancelAction: String? = null,
    val mode: String? = null,
    @SerialName("respect_safe_area") val respectSafeArea: Boolean = true,
    val min: Double? = null, val max: Double? = null,
    val step: Double? = null,
    val options: List<PickerOption>? = null,
    val selected: String? = null,
    @SerialName("date_mode") val dateMode: String? = null,
    val semantics: CrossUiSemantics = CrossUiSemantics(),
    val children: List<CrossUiNode> = emptyList()
) {
    val enabled: Boolean get() = semantics.enabled
}

@Serializable
data class PickerOption(val label: String, val value: String)

@Serializable
data class CrossUiSemantics(
    val label: String? = null, val hint: String? = null,
    val role: String? = null, val enabled: Boolean = true,
)

// ---- Renderer ---------------------------------------------------------------

val LocalCrossUiTheme = staticCompositionLocalOf<CrossUiTheme> { CrossUiTheme() }

@Composable
fun CrossUiRenderer(node: CrossUiNode, dispatch: (String) -> Unit, modifier: Modifier = Modifier) {
    when (node.type) {
        "navigation" -> {
            val activeRoute = node.children.firstOrNull { it.key == node.active }
            if (node.mode == "stack") {
                activeRoute?.let { CrossUiRenderer(it, dispatch, modifier) }
            } else {
                // Tab bar
                var selectedTab by remember { mutableStateOf(node.active ?: node.children.firstOrNull()?.key ?: "") }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            node.children.forEach { route ->
                                NavigationBarItem(
                                    selected = selectedTab == route.key,
                                    onClick = { selectedTab = route.key },
                                    icon = { Text(route.title?.take(1) ?: route.key.take(1)) },
                                    label = { Text(route.title ?: route.key) },
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        node.children.firstOrNull { it.key == selectedTab }?.let {
                            CrossUiRenderer(it, dispatch)
                        }
                    }
                }
            }
        }
        "route" -> {
            val safe = node.respectSafeArea
            Scaffold(
                topBar = { TopAppBar(title = { Text(node.title.orEmpty()) }) },
                modifier = if (!safe) Modifier.fillMaxSize().systemBarsPadding() else Modifier
            ) { innerPadding ->
                Column(Modifier.padding(innerPadding)) {
                    node.children.forEach { CrossUiRenderer(it, dispatch) }
                }
            }
        }
        "stack" -> {
            val dir = if (node.axis == "horizontal") Arrangement.Horizontal else Arrangement.Vertical
            if (dir == Arrangement.Horizontal) {
                Row(modifier, horizontalArrangement = arr(node.alignment, true), verticalAlignment = Alignment.CenterVertically) {
                    node.children.forEach { CrossUiRenderer(it, dispatch) }
                }
            } else {
                Column(modifier, verticalArrangement = arr(node.alignment, false), horizontalAlignment = colAlign(node.alignment)) {
                    node.children.forEach { CrossUiRenderer(it, dispatch) }
                }
            }
        }
        "form" -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            node.children.forEach { CrossUiRenderer(it, dispatch) }
        }
        "list" -> {
            val action = node.onSelect
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(node.children) { child ->
                    if (action != null) TextButton(onClick = { dispatch(ev(node.key, action, child.key)) }) { CrossUiRenderer(child, dispatch) }
                    else CrossUiRenderer(child, dispatch)
                }
            }
        }
        "loading" -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp))
            node.label?.let { Text(it, Modifier.padding(start = 12.dp)) }
        }
        "text" -> {
            val fs = when (node.style) { "display" -> 36.sp; "headline" -> 28.sp; "title" -> 24.sp; "caption" -> 12.sp; "footnote" -> 11.sp; else -> 16.sp }
            Text(node.text.orEmpty(), fontSize = fs, modifier = modifier.semantics {
                node.semantics.label?.let { contentDescription = it }
                if (node.semantics.role == "header") heading()
            })
        }
        "input" -> {
            val text = remember(node.key) { mutableStateOf(node.value.orEmpty()) }
            val kt = when (node.inputType) { "email" -> KeyboardType.Email; "number" -> KeyboardType.Number; "phone" -> KeyboardType.Phone; "url" -> KeyboardType.Uri; "password" -> KeyboardType.Password; else -> KeyboardType.Text }
            val ia = when (node.returnKey) { "go" -> ImeAction.Go; "search" -> ImeAction.Search; "send" -> ImeAction.Send; "next" -> ImeAction.Next; else -> ImeAction.Done }
            val vt: VisualTransformation = if (node.secure) PasswordVisualTransformation() else VisualTransformation.None
            OutlinedTextField(text.value, onValueChange = { text.value = it; dispatch(ev(node.key, node.onChange.orEmpty(), it)) },
                label = node.label?.let { { Text(it) } }, placeholder = node.placeholder?.let { { Text(it) } },
                singleLine = true, enabled = node.enabled, visualTransformation = vt,
                keyboardOptions = KeyboardOptions(keyboardType = kt, imeAction = ia),
                modifier = Modifier.fillMaxWidth().semantics { node.semantics.label?.let { contentDescription = it }; node.semantics.hint?.let { hint = it } })
        }
        "button" -> {
            val colors = when (node.variant) { "destructive" -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error); "secondary" -> ButtonDefaults.outlinedButtonColors(); else -> ButtonDefaults.buttonColors() }
            if (node.variant == "secondary") OutlinedButton(onClick = { dispatch(ev(node.key, node.action.orEmpty())) }, enabled = node.enabled, modifier = Modifier.fillMaxWidth()) { Text(node.label.orEmpty()) }
            else Button(onClick = { dispatch(ev(node.key, node.action.orEmpty())) }, enabled = node.enabled, colors = colors, modifier = Modifier.fillMaxWidth()) { Text(node.label.orEmpty()) }
        }
        "toggle" -> {
            val checkedState = remember(node.key) { mutableStateOf(node.checked) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checkedState.value, onCheckedChange = { checkedState.value = it; dispatch(ev(node.key, node.onChange.orEmpty(), if (it) "true" else "false")) }, enabled = node.enabled)
                node.label?.let { Text(it, Modifier.padding(start = 12.dp)) }
            }.semantics { node.semantics.label?.let { contentDescription = it } }
        }
        "image" -> node.src?.let { AsyncImage(model = it, contentDescription = node.alt ?: node.semantics.label ?: "", modifier = modifier) }
        "slider" -> {
            val sliderValue = remember(node.key) { mutableStateOf((node.value?.toFloatOrNull() ?: ((node.min ?: 0.0) + (node.max ?: 1.0)).toFloat() / 2f)) }
            Slider(value = sliderValue.value, onValueChange = { sliderValue.value = it; dispatch(ev(node.key, node.onChange.orEmpty(), it.toString())) },
                enabled = node.enabled, valueRange = (node.min ?: 0.0).toFloat()..(node.max ?: 1.0).toFloat(),
                modifier = Modifier.fillMaxWidth())
        }
        "picker" -> {
            var expanded by remember { mutableStateOf(false) }
            val sel = remember(node.key) { mutableStateOf(node.selected.orEmpty()) }
            Box(modifier) {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(node.options?.firstOrNull { it.value == sel.value }?.label ?: sel.value)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    node.options?.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt.label) }, onClick = {
                            sel.value = opt.value
                            expanded = false
                            dispatch(ev(node.key, node.onChange.orEmpty(), opt.value))
                        })
                    }
                }
            }
        }
        "date_picker" -> {
            val dateState = remember { mutableStateOf(node.value.orEmpty()) }
            // Stub: Material3 DatePickerDialog requires Compose 1.2+ BOM.
            Text("DatePicker (value=${dateState.value}, mode=${node.dateMode ?: "datetime"})")
        }
        "dialog" -> {
            var openDialog by remember { mutableStateOf(true) }
            if (openDialog) AlertDialog(
                onDismissRequest = { openDialog = false; node.cancelAction?.let { dispatch(ev(node.key, it)) } },
                title = { Text(node.title.orEmpty()) },
                text = { node.children.firstOrNull()?.let { if (it.type == "text") Text(it.text.orEmpty()) else CrossUiRenderer(it, dispatch) } },
                confirmButton = { node.confirmLabel?.let { lbl -> TextButton(onClick = { openDialog = false; node.confirmAction?.let { dispatch(ev(node.key, it)) } }) { Text(lbl) } } },
                dismissButton = { node.cancelLabel?.let { lbl -> TextButton(onClick = { openDialog = false; node.cancelAction?.let { dispatch(ev(node.key, it)) } }) { Text(lbl) } } },
            )
        }
        "checkbox" -> {
            val checkedState = remember(node.key) { mutableStateOf(node.checked) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checkedState.value, onCheckedChange = { checkedState.value = it; dispatch(ev(node.key, node.onChange.orEmpty(), if (it) "true" else "false")) }, enabled = node.enabled)
                node.label?.let { Text(it, Modifier.padding(start = 8.dp)) }
            }.semantics { node.semantics.label?.let { contentDescription = it } }
        }
        "divider" -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        "card" -> ElevatedCard(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(16.dp)) { node.children.forEach { CrossUiRenderer(it, dispatch) } }
        }
        "chip" -> {
            val hasDismiss = node.onDismiss != null
            if (hasDismiss) InputChip(
                selected = false, onClick = { },
                label = { Text(node.label.orEmpty()) },
                trailingIcon = { Icon(Icons.Default.Close, "Dismiss", Modifier.clickable { dispatch(ev(node.key, node.onDismiss!!)) }) },
            ) else SuggestionChip(onClick = { }, label = { Text(node.label.orEmpty()) })
        }
        "platform_view" -> {
            if (node.platform == "android" && node.name != null) Text("PlatformView: ${node.name}")
            else Text("Unsupported platform view: ${node.key}")
        }
        else -> Text("Unsupported CrossUI node: ${node.type}")
    }
}

// ---- Helpers ----------------------------------------------------------------

private fun arr(a: String?, h: Boolean): Arrangement.HorizontalOrVertical = when (a) {
    "start" -> if (h) Arrangement.Start else Arrangement.Top; "end" -> if (h) Arrangement.End else Arrangement.Bottom
    "stretch" -> Arrangement.Top; else -> Arrangement.Center
} as Arrangement.HorizontalOrVertical

private fun colAlign(a: String?): Alignment.Horizontal = when (a) {
    "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start
}

private fun ev(nodeKey: String, action: String, value: String? = null): String =
    buildString { append("{\"node_key\":\"$nodeKey\",\"action\":{\"type\":\"$action\""); if (value != null) append(",\"value\":\"$value\""); append("}}") }
