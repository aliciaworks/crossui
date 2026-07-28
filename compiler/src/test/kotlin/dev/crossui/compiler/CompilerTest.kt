package dev.crossui.compiler

import dev.crossui.dsl.*
import dev.crossui.ir.NodeKind
import dev.crossui.ir.Platform
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompilerTest {
    private data class TestState(val email: String = "")
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
        assertTrue(sources.single { it.target == ExportTarget.WinUi3 }.content.contains("<UserControl"))
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
        assertTrue(generated.content.contains("// crossui-node:email"))
        val mapping = generated.mappings.single { it.nodeKey == "email" }
        assertTrue(mapping.generatedLine > 0)
        assertTrue(mapping.source?.file == "ui/login.kt")
        assertTrue(generated.relativePath == "LoginView.kt")
    }
}
