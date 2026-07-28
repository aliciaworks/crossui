package dev.crossui.showcase

import dev.crossui.compiler.CrossUiCompiler
import dev.crossui.compiler.ExportTarget
import dev.crossui.dsl.*
import dev.crossui.ir.*
import java.nio.file.Files
import java.nio.file.Path

private data class WinUiFixtureState(
    val email: String = "",
    val status: String = "",
    val isSubmitting: Boolean = false,
    val canSubmit: Boolean = true,
)

private sealed interface WinUiFixtureAction {
    data class EmailChanged(val value: String) : WinUiFixtureAction
    data object Submit : WinUiFixtureAction
}

fun showcaseDocument(): UiDocument = document(
    tabNavigation(
        "app",
        "login",
        listOf(
            route("login", "Sign in") {
                +vstack("login-content") {
                    +display("heading", "Welcome")
                    +image("logo", "https://example.com/logo.png", "App logo")
                    +emailInput("email", "", "you@example.com", "email_changed")
                        .accessibility("Email", SemanticRole.TextField)
                    +secureInput("password", "", "Password", "password_changed")
                        .returnKey(ReturnKey.Go)
                        .accessibility("Password", SemanticRole.TextField)
                    +searchInput("search", "", "Search…", "search_changed")
                    +toggle("remember", "Remember me", false, "remember_changed")
                    +caption("volume-label", "Volume: 50%")
                    +slider("volume", 0.5, 0.0, 1.0, 0.05, "volume_changed")
                        .accessibility("Volume", SemanticRole.Slider)
                    +checkbox("terms", "I agree to the Terms", false, "terms_changed")
                    +hstack("chips") {
                        +inputChip("ios", "iOS")
                        +inputChip("android", "Android")
                        +filterChip("active", "Active", "remove_active")
                    }
                    +divider("separator")
                    +picker(
                        "language",
                        "en",
                        listOf(
                            pickerOption("English", "en"),
                            pickerOption("中文", "zh"),
                            pickerOption("日本語", "ja"),
                        ),
                        "language_changed",
                    )
                    +hstack("actions") {
                        +button("submit", "Continue", "submit")
                            .accessibility("Continue", SemanticRole.Button)
                            .macShortcut("↩", listOf(KeyModifier.Command))
                        +destructiveButton("delete", "Delete", "show_delete")
                            .accessibility("Delete account", SemanticRole.Button)
                            .irreversible()
                            .critical()
                            .iosHaptic(HapticType.Error)
                            .iosPresentation(PresentationStyle.Sheet)
                            .androidElevation(8f)
                    }
                    +card("summary", listOf(text("summary-copy", "Generated native controls")))
                    +footnote("legal", "By continuing you agree to our Terms")
                }
            },
            route("settings", "Settings") {
                +title("settings-title", "App Preferences")
                +toggle("dark-mode", "Dark Mode", false, "dark_mode_changed")
            },
        ),
    ),
    Theme(
        tokens = mapOf(
            "primary" to TokenValue.Color("#6750A4"),
            "error" to TokenValue.Color("#B3261E"),
            "spacing.md" to TokenValue.Number(16.0),
        ),
        android = AndroidTheme(
            material3Expressive = true,
            dynamicColor = true,
        ),
    ),
)

private fun winUiFixtureDocument(): UiDocument =
    typedDocument<WinUiFixtureState, WinUiFixtureAction>(
        route("fixture", "Typed binding fixture") {
            +vstack("fixture-content") {
                +emailInput(
                    "fixture-email",
                    bind(WinUiFixtureState::email),
                    "Email address",
                    event("email_changed") { WinUiFixtureAction.EmailChanged(it) },
                )
                +text("fixture-status", bind(WinUiFixtureState::status))
                +loading("fixture-loading", "Signing in")
                    .visibleWhen(bind(WinUiFixtureState::isSubmitting))
                +button(
                    "fixture-submit",
                    "Continue",
                    event(WinUiFixtureAction.Submit),
                ).enabledWhen(bind(WinUiFixtureState::canSubmit))
            }
        },
        stateType = "dev.crossui.showcase.WinUiFixtureState",
        actionType = "dev.crossui.showcase.WinUiFixtureAction",
    )

fun main(args: Array<String>) {
    val document = showcaseDocument()
    if (args.firstOrNull() == "--generate") {
        val hosts = Path.of(args.getOrElse(1) { error("Missing hosts directory") })
        val sources = CrossUiCompiler.generate(document, typeName = "CrossUiShowcase") +
            CrossUiCompiler.generate(
                winUiFixtureDocument(),
                targets = setOf(ExportTarget.WinUi3),
                typeName = "CrossUiTypedFixture",
            )
        val mappedSources = sources.map { source ->
            val directory = when (source.target) {
                ExportTarget.SwiftUi -> "ios/generated"
                ExportTarget.JetpackCompose -> "android/generated"
                ExportTarget.WinUi3 -> "windows/generated"
            }
            val relative = "$directory/${source.relativePath}"
            val mapped = source.copy(
                relativePath = relative,
                mappings = source.mappings.map { it.copy(generatedFile = relative) },
            )
            hosts.resolve(relative).also {
                Files.createDirectories(it.parent)
                Files.writeString(it, mapped.content)
            }
            mapped
        }
        val sourceMap = CrossUiCompiler.writeSourceMap(
            mappedSources,
            hosts.resolve("crossui-map.json"),
        )
        mappedSources.forEach { println(hosts.resolve(it.relativePath)) }
        println(sourceMap)
        return
    }

    val target = args.firstOrNull()?.let(ExportTarget::parse)
    if (target == null) {
        println(document.toJson())
    } else {
        CrossUiCompiler.generate(document, setOf(target))
            .forEach { println(it.content) }
    }
}
