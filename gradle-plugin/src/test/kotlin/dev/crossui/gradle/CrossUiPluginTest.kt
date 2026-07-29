package dev.crossui.gradle

import dev.crossui.ir.Node
import dev.crossui.ir.NodeKey
import dev.crossui.ir.NodeKind
import dev.crossui.ir.LocalizedField
import dev.crossui.ir.LocalizedText
import dev.crossui.ir.UiDocument
import dev.crossui.ir.UiDocumentProvider
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class CrossUiPluginTest {
    object TestProvider : UiDocumentProvider {
        override fun document() = UiDocument(
            root = Node(NodeKey("provider-root"), NodeKind.Text("From provider")),
        )
    }

    @Test
    fun generatesAndVerifiesTargetSpecificDirectories() {
        val directory = createTempDirectory("crossui-plugin-test")
        val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
        project.pluginManager.apply(CrossUiPlugin::class.java)

        val input = directory.resolve("ui.json")
        Files.writeString(
            input,
            UiDocument(
                root = Node(NodeKey("root"), NodeKind.Text("Hello")),
            ).toJson(),
        )

        val extension = project.extensions.getByType(CrossUiExtension::class.java)
        extension.input.set(input.toFile())
        extension.outputDirectory.set(directory.resolve("generated").toFile())
        extension.targets.set(listOf("swiftui", "compose", "winui3"))
        extension.typeName.set("HelloView")

        val generate = project.tasks.getByName("generateCrossUi") as CrossUiGenerateTask
        generate.generate()
        val doctor = project.tasks.getByName("crossuiDoctor") as CrossUiDoctorTask
        doctor.diagnose()
        val verify = project.tasks.getByName("verifyCrossUi") as CrossUiVerifyTask
        verify.verify()

        assertTrue(Files.exists(directory.resolve("generated/swiftui/HelloView.swift")))
        assertTrue(Files.exists(directory.resolve("generated/compose/HelloView.kt")))
        assertTrue(Files.exists(directory.resolve("generated/winui3/HelloView.xaml")))
        assertTrue(Files.exists(directory.resolve("generated/compose/crossui-map.json")))
    }

    @Test
    fun loadsCompiledKotlinProviderWithoutJsonInput() {
        val directory = createTempDirectory("crossui-provider-test")
        val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
        project.pluginManager.apply(CrossUiPlugin::class.java)

        val extension = project.extensions.getByType(CrossUiExtension::class.java)
        extension.providerClass.set(TestProvider::class.java.name)
        extension.providerClasspath.from(
            TestProvider::class.java.protectionDomain.codeSource.location,
        )
        extension.outputDirectory.set(directory.resolve("generated").toFile())
        extension.targets.set(listOf("compose"))
        extension.typeName.set("ProviderView")

        val generate = project.tasks.getByName("generateCrossUi") as CrossUiGenerateTask
        generate.generate()

        val output = directory.resolve("generated/compose/ProviderView.kt")
        assertTrue(Files.readString(output).contains("From provider"))
    }

    @Test
    fun registersComposeOutputWithExistingAndroidSourceSet() {
        val directory = createTempDirectory("crossui-kmp-wiring-test")
        val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val androidMain = kotlin.sourceSets.create("androidMain")

        project.pluginManager.apply(CrossUiPlugin::class.java)

        assertTrue(
            androidMain.kotlin.srcDirs.any {
                it.toPath().endsWith("build/generated/crossui/compose")
            },
        )
    }

    @Test
    fun generatesAndVerifiesNativeLocalizationSources() {
        val directory = createTempDirectory("crossui-plugin-localization")
        val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
        project.pluginManager.apply(CrossUiPlugin::class.java)
        val input = directory.resolve("ui.json")
        Files.writeString(input, localizedDocument().toJson())

        val extension = project.extensions.getByType(CrossUiExtension::class.java)
        extension.input.set(input.toFile())
        extension.localization.mode.set(LocalizationMode.Generated)
        extension.localization.sourceLocale.set("en-US")
        extension.localization.outputDirectory.set(
            directory.resolve("localization").toFile(),
        )

        val verify = project.tasks.getByName(
            "verifyLocalization",
        ) as CrossUiVerifyLocalizationTask
        assertFailsWith<IllegalStateException> {
            verify.verifyLocalization()
        }
        val generate = project.tasks.getByName(
            "generateLocalizationSources",
        ) as CrossUiGenerateLocalizationTask
        generate.generateLocalizationSources()
        verify.verifyLocalization()

        assertTrue(
            Files.exists(
                directory.resolve("localization/apple/Localizable.xcstrings"),
            ),
        )
        assertTrue(
            Files.exists(
                directory.resolve(
                    "localization/android/values/crossui_strings.xml",
                ),
            ),
        )
        assertTrue(
            Files.exists(
                directory.resolve(
                    "localization/windows/Strings/en-US/Resources.resw",
                ),
            ),
        )
    }

    @Test
    fun externalLocalizationOnlyValidatesKeys() {
        val directory = createTempDirectory("crossui-plugin-external-localization")
        val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
        project.pluginManager.apply(CrossUiPlugin::class.java)
        val input = directory.resolve("ui.json")
        Files.writeString(input, localizedDocument().toJson())
        val output = directory.resolve("localization")

        val extension = project.extensions.getByType(CrossUiExtension::class.java)
        extension.input.set(input.toFile())
        extension.localization.mode.set(LocalizationMode.External)
        extension.localization.outputDirectory.set(output.toFile())

        val generate = project.tasks.getByName(
            "generateLocalizationSources",
        ) as CrossUiGenerateLocalizationTask
        generate.generateLocalizationSources()
        val verify = project.tasks.getByName(
            "verifyLocalization",
        ) as CrossUiVerifyLocalizationTask
        verify.verifyLocalization()

        assertFalse(Files.exists(output))
    }

    private fun localizedDocument() = UiDocument(
        root = Node(
            key = NodeKey("welcome"),
            kind = NodeKind.Text("Welcome"),
            localizedText = mapOf(
                LocalizedField.Value to LocalizedText.Resource(
                    key = "home.welcome",
                    fallback = "Welcome",
                ),
            ),
        ),
    )
}
