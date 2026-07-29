package dev.crossui.compiler

import dev.crossui.ir.Alignment
import dev.crossui.ir.Axis
import dev.crossui.ir.ButtonVariant
import dev.crossui.ir.InputType
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKind

internal val platformKeyboards = setOf(
    InputType.Email,
    InputType.Number,
    InputType.Phone,
    InputType.Url,
)

internal fun swiftAdaptiveContent(typeName: String): String = """
    |
    |
    |@ViewBuilder
    |private func ${typeName}AdaptiveContent<Content: View>(
    |    @ViewBuilder content: () -> Content
    |) -> some View {
    |    content()
    |        .frame(maxWidth: 720, alignment: .topLeading)
    |        .frame(
    |            maxWidth: .infinity,
    |            maxHeight: .infinity,
    |            alignment: .top
    |        )
    |        .padding(.horizontal, 20)
    |        .padding(.vertical, 16)
    |}
    |""".trimMargin()

internal fun swiftRouteLayout(
    node: Node,
    kind: NodeKind.Route,
    indentation: String,
    children: String,
    title: String,
): String {
    val hasNativeScroller = node.children.size == 1 &&
        node.children.single().kind.let {
            it is NodeKind.Form || it is NodeKind.ListNode
        }
    val content = if (hasNativeScroller) {
        children.removePrefix("        ")
    } else {
        """
        |${indentation}ScrollView {
        |${indentation}    VStack(alignment: .leading, spacing: 16) {
        |$children
        |${indentation}    }
        |${indentation}    .frame(maxWidth: 840, alignment: .leading)
        |${indentation}    .frame(maxWidth: .infinity, alignment: .top)
        |${indentation}    .padding(.horizontal, 20)
        |${indentation}    .padding(.vertical, 16)
        |${indentation}}
        |""".trimMargin().trimEnd()
    }
    return content + ".navigationTitle($title)" +
        if (kind.respectSafeArea) "" else ".ignoresSafeArea()"
}

internal fun NodeKind.Stack.swiftStackAlignment(): String = when (axis) {
    Axis.Vertical -> when (alignment) {
        Alignment.Start, Alignment.Stretch -> "alignment: .leading, "
        Alignment.Center -> "alignment: .center, "
        Alignment.End -> "alignment: .trailing, "
    }
    Axis.Horizontal -> when (alignment) {
        Alignment.Start -> "alignment: .top, "
        Alignment.Center, Alignment.Stretch -> "alignment: .center, "
        Alignment.End -> "alignment: .bottom, "
    }
}

internal fun NodeKind.Stack.swiftStretch(): String =
    if (axis == Axis.Vertical && alignment == Alignment.Stretch) {
        ".frame(maxWidth: .infinity, alignment: .leading)"
    } else {
        ""
    }

internal fun ButtonVariant.swiftButtonStyle(): String = when (this) {
    ButtonVariant.Primary -> ".buttonStyle(.borderedProminent)"
    ButtonVariant.Secondary, ButtonVariant.Destructive -> ".buttonStyle(.bordered)"
}

internal fun swiftKeyboardSupport(): String = """
    |
    |
    |private enum CrossUiGeneratedKeyboard {
    |    case email
    |    case number
    |    case phone
    |    case url
    |}
    |
    |private extension View {
    |    @ViewBuilder
    |    func crossUiKeyboard(_ keyboard: CrossUiGeneratedKeyboard) -> some View {
    |#if os(iOS)
    |        switch keyboard {
    |        case .email:
    |            keyboardType(.emailAddress)
    |        case .number:
    |            keyboardType(.numberPad)
    |        case .phone:
    |            keyboardType(.phonePad)
    |        case .url:
    |            keyboardType(.URL)
    |        }
    |#else
    |        self
    |#endif
    |    }
    |}
    |""".trimMargin()
