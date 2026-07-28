package dev.crossui.compiler

import dev.crossui.ir.*
import dev.crossui.legalizer.ResolvedDocument
import dev.crossui.legalizer.compile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ExportTarget(
    val cliName: String,
    val fileName: String,
) {
    SwiftUi("swiftui", "CrossUiGenerated.swift"),
    JetpackCompose("compose", "CrossUiGenerated.kt"),
    WinUi3("winui3", "CrossUiGenerated.xaml");

    companion object {
        fun parse(value: String): ExportTarget =
            entries.firstOrNull { it.cliName == value.lowercase() }
                ?: error(
                    "Unsupported target '$value'. Use swiftui, compose, or winui3.",
                )
    }
}

data class GeneratedSource(
    val target: ExportTarget,
    val relativePath: String,
    val content: String,
    val mappings: List<SourceMapEntry> = emptyList(),
)

@Serializable
data class SourceMapEntry(
    val nodeKey: String,
    val generatedFile: String,
    val generatedLine: Int,
    val source: SourceLocation? = null,
)

@Serializable
data class SourceMapManifest(
    val version: Int = 1,
    val entries: List<SourceMapEntry>,
)

data class NativeViewKey(
    val target: ExportTarget,
    val name: String,
)

class NativeViewRegistry private constructor(
    private val renderers: Map<NativeViewKey, String>,
) {
    fun render(target: ExportTarget, name: String, payload: String): String? =
        renderers[NativeViewKey(target, name)]?.replace("{{payload}}", payload)

    class Builder {
        private val renderers = mutableMapOf<NativeViewKey, String>()

        fun register(target: ExportTarget, name: String, sourceTemplate: String) {
            require(name.isNotBlank()) { "Native view name cannot be blank." }
            renderers[NativeViewKey(target, name)] = sourceTemplate
        }

        fun build() = NativeViewRegistry(renderers.toMap())
    }

    companion object {
        val Empty = NativeViewRegistry(emptyMap())

        fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }
}

data class LocalizationResolverKey(
    val target: ExportTarget,
)

class LocalizationRegistry private constructor(
    val androidResourceClass: String,
    private val resolvers: Map<LocalizationResolverKey, String>,
) {
    fun render(target: ExportTarget, text: LocalizedText.Resource): String? =
        resolvers[LocalizationResolverKey(target)]
            ?.replace("{{key}}", text.key.sourceEscaped(target))
            ?.replace("{{fallback}}", text.fallback.sourceEscaped(target))
            ?.replace(
                "{{namespace}}",
                text.namespace.orEmpty().sourceEscaped(target),
            )

    class Builder {
        private var androidResourceClass = "R"
        private val resolvers = mutableMapOf<LocalizationResolverKey, String>()

        fun androidResources(resourceClass: String) {
            require(resourceClass.isNotBlank()) {
                "Android resource class cannot be blank."
            }
            androidResourceClass = resourceClass
        }

        fun register(target: ExportTarget, sourceTemplate: String) {
            require("{{key}}" in sourceTemplate) {
                "Localization resolver template must contain {{key}}."
            }
            resolvers[LocalizationResolverKey(target)] = sourceTemplate
        }

        fun build() = LocalizationRegistry(
            androidResourceClass = androidResourceClass,
            resolvers = resolvers.toMap(),
        )
    }

    companion object {
        val Native = LocalizationRegistry("R", emptyMap())

        fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }
}

interface PlatformLowering {
    val target: ExportTarget
    fun lower(document: UiDocument): ResolvedDocument
}

private class DefaultLowering(
    override val target: ExportTarget,
    private val profile: TargetProfile,
) : PlatformLowering {
    override fun lower(document: UiDocument): ResolvedDocument =
        compile(document, profile, UnsupportedExtensionPolicy.Strip)
}

interface CodeGenerator {
    val target: ExportTarget

    fun generate(
        document: ResolvedDocument,
        typeName: String,
        nativeViews: NativeViewRegistry,
        localization: LocalizationRegistry,
    ): List<GeneratedSource>
}

object CrossUiCompiler {
    fun generate(
        document: UiDocument,
        targets: Set<ExportTarget> = ExportTarget.entries.toSet(),
        typeName: String = "CrossUiGenerated",
        nativeViews: NativeViewRegistry = NativeViewRegistry.Empty,
        localization: LocalizationRegistry = LocalizationRegistry.Native,
    ): List<GeneratedSource> {
        document.validate()
        return targets.flatMap { target ->
            val lowering = DefaultLowering(target, target.profile())
            val resolved = lowering.lower(document)
            target.generator()
                .generate(resolved, typeName, nativeViews, localization)
                .map { it.withSourceMappings(document) }
        }
    }

    fun write(
        document: UiDocument,
        output: Path,
        targets: Set<ExportTarget> = ExportTarget.entries.toSet(),
        typeName: String = "CrossUiGenerated",
        nativeViews: NativeViewRegistry = NativeViewRegistry.Empty,
        localization: LocalizationRegistry = LocalizationRegistry.Native,
    ): List<Path> {
        val sources = generate(
            document,
            targets,
            typeName,
            nativeViews,
            localization,
        )
        val paths = sources.map { generated ->
            val path = output.resolve(generated.relativePath)
            Files.createDirectories(path.parent)
            Files.writeString(path, generated.content)
            path
        }
        val manifestPath = writeSourceMap(
            sources,
            output.resolve("crossui-map.json"),
        )
        return paths + manifestPath
    }

    fun writeSourceMap(sources: List<GeneratedSource>, path: Path): Path {
        val manifest = SourceMapManifest(
            entries = sources.flatMap(GeneratedSource::mappings),
        )
        Files.createDirectories(path.parent)
        Files.writeString(path, sourceMapJson.encodeToString(manifest))
        return path
    }
}

private val sourceMapJson = Json {
    prettyPrint = true
    explicitNulls = false
}

private fun GeneratedSource.withSourceMappings(
    document: UiDocument,
): GeneratedSource {
    val nodes = buildMap<String, Node> {
        document.root.walk { put(it.key.value, it) }
    }
    val entries = content.lineSequence().mapIndexedNotNull { index, line ->
        val key = markerFor(target)
            .find(line)
            ?.groupValues
            ?.get(1)
            ?: return@mapIndexedNotNull null
        SourceMapEntry(
            nodeKey = key,
            generatedFile = relativePath,
            generatedLine = index + 1,
            source = nodes[key]?.source,
        )
    }.toList()
    return copy(mappings = entries)
}

private fun markerFor(target: ExportTarget): Regex = when (target) {
    ExportTarget.WinUi3 -> Regex("""<!-- crossui-node:([^ ]+) -->""")
    else -> Regex("""// crossui-node:([^ ]+)""")
}

private fun ExportTarget.profile() = when (this) {
    ExportTarget.SwiftUi -> TargetProfile.iphone()
    ExportTarget.JetpackCompose -> TargetProfile.androidPhone()
    ExportTarget.WinUi3 -> TargetProfile.windowsDesktop()
}

private fun ExportTarget.generator(): CodeGenerator = when (this) {
    ExportTarget.SwiftUi -> SwiftUiGenerator
    ExportTarget.JetpackCompose -> ComposeGenerator
    ExportTarget.WinUi3 -> WinUiGenerator
}
