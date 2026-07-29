package dev.crossui.compiler

import dev.crossui.dsl.*
import dev.crossui.ir.DatePickerMode
import dev.crossui.ir.AndroidTheme
import dev.crossui.ir.KeyModifier
import dev.crossui.ir.Theme
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
        val language: String = "en",
    )
    private sealed interface TestAction {
        data class EmailChanged(val value: String) : TestAction
    }
    private data class TemporalState(val value: String? = null)

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
    fun composeEmitsOptInDynamicColorThemeAdapter() {
        val source = document.copy(
            theme = Theme(android = AndroidTheme(dynamicColor = true)),
        )
        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.JetpackCompose),
            typeName = "HomeView",
        ).single().content

        assertTrue(generated.contains("fun HomeViewTheme"))
        assertTrue(generated.contains("dynamicDarkColorScheme(context)"))
        assertTrue(generated.contains("dynamicLightColorScheme(context)"))
        assertTrue(generated.contains("isSystemInDarkTheme()"))
    }

    @Test
    fun composeMakesRootContainersVerticallyScrollable() {
        val generated = CrossUiCompiler.generate(
            document,
            setOf(ExportTarget.JetpackCompose),
            typeName = "HomeView",
        ).single().content

        assertTrue(
            generated.contains(
                "Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()" +
                    ".verticalScroll(rememberScrollState())",
            ),
        )
    }

    @Test
    fun largeScreenNavigationUsesNativeAdaptivePatterns() {
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
        val swift = generated.single { it.target == ExportTarget.SwiftUi }.content

        assertTrue(compose.contains("if (maxWidth >= 600.dp)"))
        assertTrue(compose.contains("NavigationRailItem"))
        assertTrue(compose.contains("NavigationBarItem"))
        assertTrue(swift.contains(".tabViewStyle(.sidebarAdaptable)"))
    }

    @Test
    fun swiftLowersMacKeyboardShortcuts() {
        val source = document(
            button("save", "Save", "save")
                .macShortcut("s", listOf(KeyModifier.Command, KeyModifier.Shift)),
        )
        val swift = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.SwiftUi),
        ).single().content

        assertTrue(
            swift.contains(
                ".keyboardShortcut(\"s\", modifiers: [.command, .shift])",
            ),
        )
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
        assertTrue(generated.content.contains("collectAsStateWithLifecycle()"))
        assertTrue(generated.content.contains("// crossui-node:email"))
        val mapping = generated.mappings.single { it.nodeKey == "email" }
        assertTrue(mapping.generatedLine > 0)
        assertTrue(mapping.source?.file == "ui/login.kt")
        assertTrue(generated.relativePath == "LoginView.kt")
    }

    @Test
    fun typedDocumentsRejectUnboundInteractiveControls() {
        assertFailsWith<IllegalArgumentException> {
            typedDocument<TestState, TestAction>(
                input("email", "", "email_changed"),
            )
        }
    }

    @Test
    fun temporalValuesUseOneCanonicalWireFormatOnEveryPlatform() {
        val source = typedDocument<TemporalState, TestAction>(
            datePicker(
                "appointment",
                bind(TemporalState::value),
                DatePickerMode.DateTime,
                "appointment_changed",
            ),
        )
        val generated = CrossUiCompiler.generate(source, typeName = "AppointmentView")
        val swift = generated.single { it.target == ExportTarget.SwiftUi }.content
        val compose = generated.single {
            it.target == ExportTarget.JetpackCompose
        }.content
        val xaml = generated.single {
            it.relativePath == "AppointmentView.xaml"
        }.content
        val csharp = generated.single {
            it.relativePath == "AppointmentView.xaml.cs"
        }.content

        assertTrue(swift.contains("CrossUiTemporalCodec.encode"))
        assertTrue(swift.contains("displayedComponents: [.date, .hourAndMinute]"))
        assertTrue(compose.contains("CrossUiTemporalCodec.dateTime"))
        assertTrue(compose.contains("yyyy-MM-dd'T'HH:mm:ssX"))
        assertTrue(xaml.contains("Date=\"{x:Bind State.ValueDate, Mode=TwoWay}\""))
        assertTrue(
            xaml.contains(
                "SelectedTime=\"{x:Bind State.ValueTime, Mode=TwoWay}\"",
            ),
        )
        assertTrue(csharp.contains("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    }

    @Test
    fun composeLowersDynamicTextVisibilityAndEnabledBindings() {
        val source = typedDocument<TestState, TestAction>(
            vstack("content") {
                +text("status", bind(TestState::status))
                +loading("loading").visibleWhen(bind(TestState::loading))
                +button("submit", "Submit", "submit")
                    .enabledWhen(bind(TestState::canSubmit))
                +picker(
                    "language",
                    bind(TestState::language),
                    listOf(pickerOption("English", "en")),
                    "language_changed",
                )
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
    fun swiftUiEmitsAppStorageForPlatformOwnedPreference() {
        val source = typedDocument<TestState, TestAction>(
            toggle(
                "dark-mode",
                "Dark Mode",
                bind(TestState::loading),
                "dark_mode_changed",
            ),
            settings = listOf(
                setting(
                    appStorage("appearance.dark_mode", false),
                    TestState::loading,
                    "dark_mode_changed",
                ),
            ),
        )

        val generated = CrossUiCompiler.generate(
            source,
            setOf(ExportTarget.SwiftUi),
            typeName = "SettingsView",
        ).single().content

        assertTrue(
            generated.contains(
                "@AppStorage(\"appearance.dark_mode\") private var storedLoading: Bool = false",
            ),
        )
        assertTrue(generated.contains(".onChange(of: storedLoading)"))
        assertTrue(generated.contains(".onChange(of: model.state.loading)"))
        assertTrue(
            generated.contains(
                "model.dispatch(actions(\"dark_mode_changed\", String(value)))",
            ),
        )
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
                +picker(
                    "language",
                    bind(TestState::language),
                    listOf(pickerOption("English", "en")),
                    "language_changed",
                )
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
        assertTrue(xaml.contains("SelectedValuePath=\"Tag\""))
        assertTrue(xaml.contains("Click=\"OnAction\""))
        assertTrue(codeBehind.contains("INotifyPropertyChanged"))
        assertTrue(codeBehind.contains("public void ApplyEmail(string value)"))
        assertTrue(codeBehind.contains("dispatch(\"email_changed\", value)"))
    }

    @Test
    fun lowersLocalizedResourcesToNativePlatformApis() {
        val source = document(
            text("welcome", localized("home.welcome", "Welcome")),
        )
        val localization = LocalizationRegistry.build {
            androidResources("com.example.app.R")
        }
        val generated = CrossUiCompiler.generate(
            source,
            typeName = "WelcomeView",
            localization = localization,
        )
        val swift = generated.single { it.target == ExportTarget.SwiftUi }.content
        val compose = generated
            .single { it.target == ExportTarget.JetpackCompose }
            .content
        val xaml = generated
            .single { it.relativePath == "WelcomeView.xaml" }
            .content
        val csharp = generated
            .single { it.relativePath == "WelcomeView.xaml.cs" }
            .content

        assertTrue(
            swift.contains(
                "String(localized: \"home.welcome\", defaultValue: \"Welcome\")",
            ),
        )
        assertTrue(
            compose.contains(
                "stringResource(com.example.app.R.string.home_welcome)",
            ),
        )
        assertTrue(xaml.contains("LocalizedWelcomeValue"))
        assertTrue(csharp.contains("ResourceLoader resourceLoader = new()"))
        assertTrue(csharp.contains("Localize(\"home.welcome\", \"Welcome\")"))
        assertTrue(csharp.contains("public void RefreshLocalization()"))
        assertTrue(csharp.contains("LocalizationError?.Invoke(exception)"))
    }

    @Test
    fun customLocalizationResolversAreEmbeddedAtCompileTime() {
        val source = document(
            button("save", localized("settings.save", "Save"), "save"),
        )
        val localization = LocalizationRegistry.build {
            register(
                ExportTarget.SwiftUi,
                "AppStrings.resolve(\"{{key}}\", fallback: \"{{fallback}}\")",
            )
            register(
                ExportTarget.JetpackCompose,
                "AppStrings.resolve(\"{{key}}\", \"{{fallback}}\")",
            )
            register(
                ExportTarget.WinUi3,
                "AppStrings.Resolve(\"{{key}}\", \"{{fallback}}\")",
            )
        }
        val generated = CrossUiCompiler.generate(
            source,
            typeName = "SettingsView",
            localization = localization,
        )

        assertTrue(
            generated.single { it.target == ExportTarget.SwiftUi }
                .content.contains("AppStrings.resolve(\"settings.save\""),
        )
        assertTrue(
            generated.single { it.target == ExportTarget.JetpackCompose }
                .content.contains("AppStrings.resolve(\"settings.save\""),
        )
        val csharp = generated
            .single { it.relativePath == "SettingsView.xaml.cs" }
            .content
        assertTrue(csharp.contains("AppStrings.Resolve(\"settings.save\""))
        assertTrue(!csharp.contains("ResourceLoader resourceLoader"))
    }

    @Test
    fun contentPickersLowerToTypedRequestActionsOnEveryPlatform() {
        val source = document(
            mediaPicker(
                key = "photos",
                label = localized("profile.photos", "Choose photos"),
                onRequest = "pick_photos",
                maxSelection = 3,
            ),
        )

        val generated = CrossUiCompiler.generate(source, typeName = "ProfileView")

        assertTrue(
            generated.single { it.target == ExportTarget.SwiftUi }
                .content.contains("dispatch(\"pick_photos\", nil)"),
        )
        assertTrue(
            generated.single { it.target == ExportTarget.JetpackCompose }
                .content.contains("dispatch(\"pick_photos\", null)"),
        )
        assertTrue(
            generated.single { it.relativePath == "ProfileView.xaml" }
                .content.contains("Tag=\"pick_photos\" Click=\"OnAction\""),
        )
    }
}
