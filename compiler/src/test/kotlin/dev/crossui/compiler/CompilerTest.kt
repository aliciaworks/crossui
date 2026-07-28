package dev.crossui.compiler

import dev.crossui.dsl.*
import dev.crossui.ir.NodeKind
import dev.crossui.ir.Platform
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompilerTest {
    private data class TestState(
        val email: String = "",
        val status: String = "",
        val loading: Boolean = false,
        val canSubmit: Boolean = true,
    )
    private sealed interface TestAction {
        data class EmailChanged(val value: String) : TestAction
    }

    private val document = document(
        route("home", "Home", listOf(title("title", "Hello"), button("go", "Go", "go"))),
    )

    @Test
    fun emitsNativeSourceForAllTargets() {
        val sources = CrossUiCompiler.generate(document, typeName = "HomeView")
        assertTrue(sources.single { it.target == ExportTarget.SwiftUi }.content.contains("struct HomeView"))
        assertTrue(sources.single { it.target == ExportTarget.JetpackCompose }.content.contains("@Composable"))
        assertTrue(
            sources.single { it.relativePath == "HomeView.xaml" }
                .content
                .contains("<UserControl"),
        )
        assertTrue(sources.any { it.relativePath == "HomeView.xaml.cs" })
    }

    @Test
    fun platformViewsRequireExplicitEscapeHatches() {
        val source = document.copy(
            root = document.root.copy(
                kind = NodeKind.PlatformView(
                    Platform.Ios,
                    "map",
                    JsonNull,
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            CrossUiCompiler.generate(source, setOf(ExportTarget.SwiftUi))
        }
    }

    @Test
    fun nativeViewRegistryResolvesEscapeHatchesAtCompileTime() {
        val source = document.copy(
            root = document.root.copy(
                kind = NodeKind.PlatformView(Platform.Ios, "map", JsonNull),
            ),
        )
        val registry = NativeViewRegistry.build {
            register(ExportTarget.SwiftUi, "map", "ExistingMapView()")
        }
        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.SwiftUi),
            nativeViews = registry,
        ).single()
        assertTrue(generated.content.contains("ExistingMapView()"))
    }

    @Test
    fun typedBindingsAreLoweredAndMappedBackToDslNodes() {
        val source = typedDocument<TestState, TestAction>(
            emailInput(
                "email",
                bind(TestState::email),
                "Email",
                event("email_changed") { TestAction.EmailChanged(it) },
            ).fromSource("ui/login.kt", 12),
        )
        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.JetpackCompose),
            typeName = "LoginView",
        ).single()

        assertTrue(generated.content.contains("state: TestState"))
        assertTrue(generated.content.contains("value = state.email"))
        assertTrue(generated.content.contains("connector: UiConnector<TestState, TestAction>"))
        assertTrue(generated.content.contains("connector.send(actions.map(action, value))"))
        assertTrue(generated.content.contains("// crossui-node:email"))
        val mapping = generated.mappings.single { it.nodeKey == "email" }
        assertTrue(mapping.generatedLine > 0)
        assertTrue(mapping.source?.file == "ui/login.kt")
        assertTrue(generated.relativePath == "LoginView.kt")
    }

    @Test
    fun composeLowersDynamicTextVisibilityAndEnabledBindings() {
        val source = typedDocument<TestState, TestAction>(
            vstack("content") {
                +text("status", bind(TestState::status))
                +loading("loading").visibleWhen(bind(TestState::loading))
                +button("submit", "Submit", "submit")
                    .enabledWhen(bind(TestState::canSubmit))
            },
        )

        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.JetpackCompose),
            typeName = "LoginView",
        ).single().content

        assertTrue(generated.contains("Text(state.status)"))
        assertTrue(generated.contains("if (state.loading)"))
        assertTrue(generated.contains("enabled = state.canSubmit"))
    }

    @Test
    fun swiftUiEmitsObservationConnectorForTypedStateAndActions() {
        val source = typedDocument<TestState, TestAction>(
            text("status", bind(TestState::status)),
        )

        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.SwiftUi),
            typeName = "LoginView",
        ).single().content

        assertTrue(generated.contains("@Observable"))
        assertTrue(generated.contains("final class LoginViewModel"))
        assertTrue(generated.contains("struct LoginViewConnected"))
        assertTrue(generated.contains("model.dispatch(actions(action, value))"))
    }

    @Test
    fun winUiEmitsObservableStateAndEventCodeBehind() {
        val source = typedDocument<TestState, TestAction>(
            vstack("content") {
                +emailInput(
                    "email",
                    bind(TestState::email),
                    "Email",
                    "email_changed",
                )
                +text("status", bind(TestState::status))
                +loading("loading").visibleWhen(bind(TestState::loading))
                +button("submit", "Submit", "submit")
                    .enabledWhen(bind(TestState::canSubmit))
            },
        )

        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.WinUi3),
            typeName = "LoginView",
        )
        val xaml = generated.single { it.relativePath == "LoginView.xaml" }.content
        val codeBehind = generated.single { it.relativePath == "LoginView.xaml.cs" }.content

        assertTrue(xaml.contains("BooleanToVisibility(State.Loading)"))
        assertTrue(xaml.contains("Click=\"OnAction\""))
        assertTrue(codeBehind.contains("INotifyPropertyChanged"))
        assertTrue(codeBehind.contains("public void ApplyEmail(string value)"))
        assertTrue(codeBehind.contains("dispatch(\"email_changed\", value)"))
    }
}
