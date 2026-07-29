package dev.crossui.compiler

import dev.crossui.ir.InputType

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
    |}
    |""".trimMargin()

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
