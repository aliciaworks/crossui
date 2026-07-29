package dev.crossui.compiler

import dev.crossui.dsl.document
import dev.crossui.dsl.localized
import dev.crossui.dsl.text
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalizationSourcesTest {
    private val document = document(
        text("welcome", localized("home.welcome", "Welcome")),
    )

    @Test
    fun generatesNativeSourceCatalogs() {
        val output = createTempDirectory("crossui-localization")

        val report = LocalizationSources.generate(document, output, "en-US")

        assertTrue(report.entries.single().key == "home.welcome")
        assertTrue(
            Files.readString(output.resolve("apple/Localizable.xcstrings"))
                .contains("\"home.welcome\""),
        )
        assertTrue(
            Files.readString(output.resolve("android/values/crossui_strings.xml"))
                .contains("""name="home_welcome">Welcome</string>"""),
        )
        assertTrue(
            Files.readString(
                output.resolve("windows/Strings/en-US/Resources.resw"),
            ).contains("""name="home.welcome""""),
        )
    }

    @Test
    fun incrementalGenerationPreservesTargetTranslations() {
        val output = createTempDirectory("crossui-localization-merge")
        LocalizationSources.generate(document, output, "en-US")
        val apple = output.resolve("apple/Localizable.xcstrings")
        Files.writeString(
            apple,
            Files.readString(apple).replace(
                """"en-US": {""",
                """
                |"zh-CN": {
                |          "stringUnit": {
                |            "state": "translated",
                |            "value": "欢迎"
                |          }
                |        },
                |        "en-US": {
                """.trimMargin(),
            ),
        )
        val androidTranslation =
            output.resolve("android/values-zh-rCN/crossui_strings.xml")
        Files.createDirectories(androidTranslation.parent)
        Files.writeString(androidTranslation, "<resources>translated</resources>")
        val windowsTranslation =
            output.resolve("windows/Strings/zh-CN/Resources.resw")
        Files.createDirectories(windowsTranslation.parent)
        Files.writeString(windowsTranslation, "<root>translated</root>")

        val updatedDocument = document(
            text("welcome", localized("home.welcome", "Welcome back")),
        )
        LocalizationSources.generate(updatedDocument, output, "en-US")

        assertTrue(Files.readString(apple).contains("\"value\": \"欢迎\""))
        assertTrue(Files.readString(apple).contains("\"value\": \"Welcome back\""))
        assertTrue(
            Files.readString(output.resolve("android/values/crossui_strings.xml"))
                .contains(">Welcome back</string>"),
        )
        assertTrue(
            Files.readString(output.resolve("windows/Strings/en-US/Resources.resw"))
                .contains("<value>Welcome back</value>"),
        )
        assertTrue(
            Files.readString(androidTranslation) ==
                "<resources>translated</resources>",
        )
        assertTrue(
            Files.readString(windowsTranslation) == "<root>translated</root>",
        )
    }

    @Test
    fun rejectsFallbackConflictsAndInvalidLocales() {
        val conflict = document(
            dev.crossui.dsl.vstack(
                "content",
                listOf(
                    text("first", localized("shared.key", "First")),
                    text("second", localized("shared.key", "Second")),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            LocalizationSources.extract(conflict)
        }
        assertFailsWith<IllegalStateException> {
            LocalizationSources.generate(
                document,
                createTempDirectory("crossui-invalid-locale"),
                "not_a_locale",
            )
        }
    }

    @Test
    fun rejectsNativeResourceNameCollisions() {
        val collision = document(
            dev.crossui.dsl.vstack(
                "content",
                listOf(
                    text("first", localized("shared.key", "First")),
                    text("second", localized("shared-key", "Second")),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            LocalizationSources.extract(collision)
        }
    }

    @Test
    fun verifiesMissingAndDuplicateNativeKeys() {
        val output = createTempDirectory("crossui-localization-verify")
        LocalizationSources.generate(document, output, "en-US")
        Files.writeString(
            output.resolve("android/values/crossui_strings.xml"),
            """
            |<resources>
            |  <string name="home_welcome">Welcome</string>
            |  <string name="home_welcome">Again</string>
            |</resources>
            |""".trimMargin(),
        )

        assertFailsWith<IllegalStateException> {
            LocalizationSources.verify(document, output, "en-US")
        }
    }

    @Test
    fun rejectsInvalidTargetLocaleDirectory() {
        val output = createTempDirectory("crossui-localization-target-locale")
        LocalizationSources.generate(document, output, "en-US")
        Files.createDirectories(output.resolve("windows/Strings/not_a_locale"))

        assertFailsWith<IllegalStateException> {
            LocalizationSources.verify(document, output, "en-US")
        }
    }

    @Test
    fun externalVerificationDoesNotRequireGeneratedFiles() {
        val report = LocalizationSources.verify(
            document = document,
            output = null,
            sourceLocale = "en-US",
            requirePlatformFiles = false,
        )

        assertTrue(report.entries.single().fallback == "Welcome")
    }
}
