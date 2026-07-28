package dev.crossui.compiler

import dev.crossui.dsl.button
import dev.crossui.dsl.document
import dev.crossui.dsl.route
import dev.crossui.dsl.title
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompilerTest {
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
                kind = dev.crossui.ir.NodeKind.PlatformView(
                    dev.crossui.ir.Platform.Ios,
                    "map",
                    kotlinx.serialization.json.JsonNull,
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            CrossUiCompiler.generate(source, setOf(ExportTarget.SwiftUi))
        }
    }
}
