// Reference Jetpack Compose host. Production code should decode the same versioned JSON contract.
package dev.crossui.host

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column

data class CrossUiNode(val key: String, val type: String, val text: String? = null, val label: String? = null, val action: String? = null, val children: List<CrossUiNode> = emptyList())

@Composable
fun CrossUiRenderer(node: CrossUiNode, dispatch: (String) -> Unit) {
    when (node.type) {
        "text" -> Text(node.text.orEmpty())
        "button" -> Button(onClick = { dispatch(node.action.orEmpty()) }) { Text(node.label.orEmpty()) }
        "stack", "route" -> Column { node.children.forEach { CrossUiRenderer(it, dispatch) } }
    }
}
