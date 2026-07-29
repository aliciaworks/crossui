package dev.crossui.compiler

import dev.crossui.dsl.*
import dev.crossui.ir.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedUiQualityTest {
    private data class NavigationState(val activeRoute: String = "home")
    private sealed interface NavigationAction {
        data class Navigate(val route: String) : NavigationAction
    }

    @Test
    fun navigationUsesOfficialAdaptivePlatformStructures() {
        val source = document(
            tabNavigation(
                "main",
                "home",
                listOf(
                    route("home", "Home") { +text("welcome", "Welcome") },
                    route("settings", "Settings") { +text("prefs", "Preferences") },
                ),
            ),
        )

        val generated = CrossUiCompiler.generate(source, typeName = "AdaptiveView")
        val compose = generated.single {
            it.target == ExportTarget.JetpackCompose
        }.content
        val swift = generated.single {
            it.target == ExportTarget.SwiftUi
        }.content
        val xaml = generated.single {
            it.relativePath == "AdaptiveView.xaml"
        }.content
        val csharp = generated.single {
            it.relativePath == "AdaptiveView.xaml.cs"
        }.content

        assertTrue(compose.contains("NavigationSuiteScaffold("))
        assertTrue(compose.contains(".windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertTrue(compose.contains("Modifier.widthIn(max = 840.dp)"))
        assertFalse(compose.contains("BoxWithConstraints"))
        assertTrue(swift.contains(".tabViewStyle(.sidebarAdaptable)"))
        assertTrue(swift.contains(".frame(maxWidth: 840, alignment: .leading)"))
        assertTrue(xaml.contains("PaneDisplayMode=\"Auto\""))
        assertTrue(xaml.contains("<NavigationView.MenuItems>"))
        assertTrue(xaml.contains("Tag=\"settings\""))
        assertTrue(xaml.contains("AdaptiveTrigger MinWindowWidth=\"640\""))
        assertTrue(xaml.contains("MaxWidth=\"1064\""))
        assertTrue(csharp.contains("OnCrossUiNavigationMainSelectionChanged"))
        assertTrue(csharp.contains("ApplyCrossUiNavigationMainSelection"))
        assertTrue(csharp.contains("Dispatch(\"navigate\", route)"))
    }

    @Test
    fun commonControlsReceiveNativeReadableSizingAndTypography() {
        val source = document(
            vstack("content") {
                +display("heading", "Heading")
                +input("name", "", "name_changed", "Name")
                +picker(
                    "language",
                    "en",
                    listOf(
                        pickerOption("English", "en"),
                        pickerOption("日本語", "ja"),
                    ),
                    "language_changed",
                )
                +toggle("updates", "Updates", false, "updates_changed")
            },
        )

        val generated = CrossUiCompiler.generate(source, typeName = "ControlsView")
        val compose = generated.single {
            it.target == ExportTarget.JetpackCompose
        }.content
        val swift = generated.single {
            it.target == ExportTarget.SwiftUi
        }.content
        val xaml = generated.single {
            it.relativePath == "ControlsView.xaml"
        }.content

        assertTrue(compose.contains("MaterialTheme.typography.displaySmall"))
        assertTrue(compose.contains("ExposedDropdownMenuBox("))
        assertTrue(compose.contains("Modifier.fillMaxWidth()"))
        assertTrue(swift.contains("VStack(alignment: .leading"))
        assertTrue(swift.contains(".padding(.vertical, 16)"))
        assertTrue(xaml.contains("DisplayTextBlockStyle"))
        assertTrue(xaml.contains("TextWrapping=\"Wrap\""))
        assertTrue(xaml.contains("HorizontalContentAlignment=\"Stretch\""))
    }

    @Test
    fun navigationSelectionCanBeOwnedByTypedKmpState() {
        val source = typedDocument<NavigationState, NavigationAction>(
            tabNavigation(
                key = "main",
                active = bind(NavigationState::activeRoute),
                onChange = "navigate",
                routes = listOf(
                    route("home", "Home") { +text("welcome", "Welcome") },
                    route("settings", "Settings") { +text("prefs", "Preferences") },
                ),
            ),
        )

        val generated = CrossUiCompiler.generate(source, typeName = "StateNavView")
        val compose = generated.single {
            it.target == ExportTarget.JetpackCompose
        }.content
        val swift = generated.single {
            it.target == ExportTarget.SwiftUi
        }.content
        val csharp = generated.single {
            it.relativePath == "StateNavView.xaml.cs"
        }.content

        assertTrue(compose.contains("val crossUiSelectedMain = state.activeRoute"))
        assertTrue(compose.contains("when (crossUiSelectedMain)"))
        assertTrue(swift.contains("TabView(selection: Binding(get: { state.activeRoute }"))
        assertTrue(swift.contains(".tag(\"settings\")"))
        assertTrue(csharp.contains("State.ActiveRoute = route"))
        assertTrue(csharp.contains("State.PropertyChanged += OnNavigationStateChanged"))
    }
}
