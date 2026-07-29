package dev.crossui.gradle

import dev.crossui.compiler.LocalizationSources
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

enum class LocalizationMode {
    Generated,
    External,
    Disabled,
}

abstract class CrossUiLocalizationExtension @Inject constructor(
    objects: ObjectFactory,
) {
    val mode: Property<LocalizationMode> =
        objects.property(LocalizationMode::class.java)
    val sourceLocale: Property<String> = objects.property(String::class.java)
    val outputDirectory: DirectoryProperty = objects.directoryProperty()
}

internal fun CrossUiLocalizationExtension.configure(
    action: Action<in CrossUiLocalizationExtension>,
) {
    action.execute(this)
}

@DisableCachingByDefault(
    because = "Merges source keys into catalogs that can contain TMS translations.",
)
abstract class CrossUiGenerateLocalizationTask : CrossUiDocumentTask() {
    @get:Input
    abstract val mode: Property<LocalizationMode>

    @get:Input
    abstract val sourceLocale: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateLocalizationSources() {
        when (mode.get()) {
            LocalizationMode.Generated -> {
                val report = LocalizationSources.generate(
                    document = loadDocument(),
                    output = outputDirectory.get().asFile.toPath(),
                    sourceLocale = sourceLocale.get(),
                )
                logger.lifecycle(
                    "Generated {} CrossUI localization keys into {} native resource files.",
                    report.entries.size,
                    report.files.size,
                )
            }
            LocalizationMode.External -> {
                val report = LocalizationSources.verify(
                    document = loadDocument(),
                    output = null,
                    sourceLocale = sourceLocale.get(),
                    requirePlatformFiles = false,
                )
                logger.lifecycle(
                    "External localization validated: {} CrossUI keys.",
                    report.entries.size,
                )
            }
            LocalizationMode.Disabled ->
                logger.lifecycle("CrossUI localization generation is disabled.")
        }
    }
}

@DisableCachingByDefault(
    because = "Validates merge-managed resources that can change outside Gradle.",
)
abstract class CrossUiVerifyLocalizationTask : CrossUiDocumentTask() {
    @get:Input
    abstract val mode: Property<LocalizationMode>

    @get:Input
    abstract val sourceLocale: Property<String>

    @get:Internal
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun verifyLocalization() {
        when (mode.get()) {
            LocalizationMode.Generated -> LocalizationSources.verify(
                document = loadDocument(),
                output = outputDirectory.get().asFile.toPath(),
                sourceLocale = sourceLocale.get(),
            )
            LocalizationMode.External -> LocalizationSources.verify(
                document = loadDocument(),
                output = null,
                sourceLocale = sourceLocale.get(),
                requirePlatformFiles = false,
            )
            LocalizationMode.Disabled -> Unit
        }
        logger.lifecycle("CrossUI localization verification passed.")
    }
}
