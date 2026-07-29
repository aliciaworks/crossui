package dev.crossui.gradle

import dev.crossui.compiler.CrossUiCompiler
import dev.crossui.compiler.ExportTarget
import dev.crossui.compiler.LocalizationRegistry
import dev.crossui.ir.UiDocument
import dev.crossui.ir.UiDocumentProvider
import dev.crossui.ir.walk
import java.net.URLClassLoader
import java.nio.file.Files
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

abstract class CrossUiExtension @Inject constructor(objects: ObjectFactory) {
    val input: RegularFileProperty = objects.fileProperty()
    val providerClass: Property<String> = objects.property(String::class.java)
    val providerClasspath: ConfigurableFileCollection = objects.fileCollection()
    val outputDirectory: DirectoryProperty = objects.directoryProperty()
    val targets: ListProperty<String> = objects.listProperty(String::class.java)
    val typeName: Property<String> = objects.property(String::class.java)
    val androidResourceClass: Property<String> = objects.property(String::class.java)
    val localizationResolvers: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java)
}

@DisableCachingByDefault(because = "Base task has no outputs of its own.")
abstract class CrossUiDocumentTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val providerClass: Property<String>

    @get:Classpath
    abstract val providerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val androidResourceClass: Property<String>

    @get:Input
    abstract val localizationResolvers: MapProperty<String, String>

    protected fun loadDocument(): UiDocument {
        if (providerClass.isPresent) {
            val loader = URLClassLoader(
                providerClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
                UiDocumentProvider::class.java.classLoader,
            )
            loader.use {
                val type = it.loadClass(providerClass.get())
                val instance = type.fields
                    .firstOrNull { field -> field.name == "INSTANCE" }
                    ?.get(null)
                    ?: type.getDeclaredConstructor().newInstance()
                check(instance is UiDocumentProvider) {
                    "${providerClass.get()} must implement UiDocumentProvider."
                }
                return instance.document()
            }
        }

        check(input.isPresent) {
            "Configure crossui.input or crossui.providerClass."
        }
        return UiDocument.fromJson(input.get().asFile.readText())
    }

    protected fun localization(): LocalizationRegistry = LocalizationRegistry.build {
        androidResources(androidResourceClass.get())
        localizationResolvers.get().forEach { (target, template) ->
            register(ExportTarget.parse(target), template)
        }
    }
}

@CacheableTask
abstract class CrossUiGenerateTask : CrossUiDocumentTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val targets: ListProperty<String>

    @get:Input
    abstract val typeName: Property<String>

    @TaskAction
    fun generate() {
        val document = loadDocument()
        targets.get().map(ExportTarget::parse).forEach { target ->
            CrossUiCompiler.write(
                document = document,
                output = outputDirectory.dir(target.cliName).get().asFile.toPath(),
                targets = setOf(target),
                typeName = typeName.get(),
                localization = localization(),
            )
        }
    }
}

@DisableCachingByDefault(because = "Diagnostic task only writes lifecycle output.")
abstract class CrossUiDoctorTask : CrossUiDocumentTask() {
    @TaskAction
    fun diagnose() {
        val document = loadDocument()
        document.validate()
        val nodes = mutableListOf<dev.crossui.ir.Node>()
        document.root.walk { nodes += it }
        val bindings = nodes.sumOf { it.bindings.size }
        check(bindings == 0 || document.stateType != null) {
            "CrossUI document contains bindings but has no stateType."
        }
        logger.lifecycle(
            "CrossUI doctor passed: {} nodes, {} bindings, state={}",
            nodes.size,
            bindings,
            document.stateType ?: "none",
        )
    }
}

@DisableCachingByDefault(because = "Verification task only checks existing output.")
abstract class CrossUiVerifyTask : CrossUiDocumentTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val targets: ListProperty<String>

    @get:Input
    abstract val typeName: Property<String>

    @TaskAction
    fun verify() {
        val document = loadDocument()
        val generated = CrossUiCompiler.generate(
            document = document,
            targets = targets.get().map(ExportTarget::parse).toSet(),
            typeName = typeName.get(),
            localization = localization(),
        )
        val stale = generated.filter {
            val path = outputDirectory
                .dir(it.target.cliName)
                .get()
                .file(it.relativePath)
                .asFile
                .toPath()
            !Files.exists(path) || Files.readString(path) != it.content
        }
        check(stale.isEmpty()) {
            "Stale CrossUI sources: ${stale.joinToString { it.relativePath }}"
        }
    }
}

class CrossUiPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val extension = extensions.create("crossui", CrossUiExtension::class.java)
        extension.outputDirectory.convention(layout.buildDirectory.dir("generated/crossui"))
        extension.targets.convention(ExportTarget.entries.map(ExportTarget::cliName))
        extension.typeName.convention("CrossUiGenerated")
        extension.androidResourceClass.convention("R")
        extension.localizationResolvers.convention(emptyMap())

        val generate = tasks.register(
            "generateCrossUi",
            CrossUiGenerateTask::class.java,
        ) { task ->
            task.group = "crossui"
            task.description = "Generates native CrossUI source at build time."
            task.configureDocument(extension)
            task.outputDirectory.set(extension.outputDirectory)
            task.targets.set(extension.targets)
            task.typeName.set(extension.typeName)
        }
        val doctor = tasks.register(
            "crossuiDoctor",
            CrossUiDoctorTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description = "Validates CrossUI IR and integration settings."
            task.configureDocument(extension)
        }
        val verify = tasks.register(
            "verifyCrossUi",
            CrossUiVerifyTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description = "Fails when checked-in CrossUI source is stale."
            task.configureDocument(extension)
            task.outputDirectory.set(extension.outputDirectory)
            task.targets.set(extension.targets)
            task.typeName.set(extension.typeName)
        }
        verify.configure { it.dependsOn(generate) }
        tasks.matching { it.name == "assemble" }.configureEach { task ->
            task.dependsOn(generate)
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configurations.matching {
                it.name == "androidMainImplementation"
            }.configureEach { configuration ->
                dependencies.add(
                    configuration.name,
                    "androidx.lifecycle:lifecycle-runtime-compose:2.11.0",
                )
                dependencies.add(
                    configuration.name,
                    "androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0",
                )
            }
            extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
                kotlin.sourceSets.matching { it.name == "androidMain" }.configureEach {
                    it.kotlin.srcDir(extension.outputDirectory.dir("compose"))
                }
            }
            configurations.matching { it.name == "jvmRuntimeClasspath" }.configureEach { configuration ->
                extension.providerClasspath.from(configuration)
            }
            val providerJars = tasks.matching { it.name == "jvmJar" }
            extension.providerClasspath.from(providerJars)
            listOf(generate, doctor, verify).forEach { providerTask ->
                providerTask.configure { it.dependsOn(providerJars) }
            }
            tasks.matching { it.name == "compileAndroidMain" }.configureEach {
                it.dependsOn(generate)
            }
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            configurations.matching { it.name == "runtimeClasspath" }.configureEach { configuration ->
                extension.providerClasspath.from(configuration)
            }
            val providerJars = tasks.matching { it.name == "jar" }
            extension.providerClasspath.from(providerJars)
            listOf(generate, doctor, verify).forEach { providerTask ->
                providerTask.configure { it.dependsOn(providerJars) }
            }
        }
    }

    private fun CrossUiDocumentTask.configureDocument(extension: CrossUiExtension) {
        input.set(extension.input)
        providerClass.set(extension.providerClass)
        providerClasspath.from(extension.providerClasspath)
        androidResourceClass.set(extension.androidResourceClass)
        localizationResolvers.set(extension.localizationResolvers)
    }
}
