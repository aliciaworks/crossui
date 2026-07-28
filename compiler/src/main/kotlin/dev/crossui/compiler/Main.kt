package dev.crossui.compiler

import dev.crossui.ir.NodeKey
import dev.crossui.ir.NodeKind
import dev.crossui.ir.UiDocument
import dev.crossui.ir.diff
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val arguments = args.toList()
    val command = arguments.firstOrNull()?.takeUnless { it.startsWith("--") } ?: "generate"
    val options = if (command == "generate" && arguments.firstOrNull()?.startsWith("--") == true) {
        arguments
    } else {
        arguments.drop(1)
    }

    when (command) {
        "generate" -> generate(options)
        "doctor" -> doctor(options)
        "explain" -> explain(options)
        "diff" -> diffDocuments(options)
        "verify" -> verify(options)
        else -> error(
            "Unknown command '$command'. Use generate, doctor, explain, diff, or verify.",
        )
    }
}

private fun generate(options: List<String>) {
    val document = options.readDocument()
    val output = Path.of(options.required("--output"))
    val targets = options.targets()
    val typeName = options.valueAfter("--name") ?: "CrossUiGenerated"
    val registry = options.nativeViews()

    CrossUiCompiler.write(
        document,
        output,
        targets,
        typeName,
        registry,
        options.localization(),
    )
        .forEach { println(it.toAbsolutePath()) }
}

private fun doctor(options: List<String>) {
    val document = options.readDocument()
    val targets = options.targets()
    val registry = options.nativeViews()
    val issues = mutableListOf<String>()

    if (document.root.containsBindings() && document.stateType == null) {
        issues += "Document contains bindings but has no stateType. Use typedDocument<State, Action>."
    }

    val generated = runCatching {
        CrossUiCompiler.generate(
            document = document,
            targets = targets,
            typeName = options.valueAfter("--name") ?: "CrossUiGenerated",
            nativeViews = registry,
            localization = options.localization(),
        )
    }.onFailure {
        issues += requireNotNull(it.message)
    }.getOrNull()

    println("CrossUI doctor")
    println("IR version: ${document.version}")
    println("Nodes: ${document.root.nodeCount()}")
    println("Bindings: ${document.root.bindingCount()}")
    println("Targets: ${targets.joinToString { it.cliName }}")
    println("Generated sources: ${generated?.size ?: 0}")
    println("Issues: ${issues.size}")
    issues.forEach { println("- $it") }

    check(issues.isEmpty()) {
        "CrossUI doctor found ${issues.size} issue(s)."
    }
}

private fun explain(options: List<String>) {
    val document = options.readDocument()
    val key = NodeKey(options.required("--node"))
    val target = ExportTarget.parse(options.valueAfter("--target") ?: "compose")
    val node = document.findNode(key) ?: error("Node '${key.value}' was not found.")
    val sources = CrossUiCompiler.generate(
        document = document,
        targets = setOf(target),
        typeName = options.valueAfter("--name") ?: "CrossUiGenerated",
        nativeViews = options.nativeViews(),
        localization = options.localization(),
    )
    val source = sources.firstOrNull { generated ->
        generated.mappings.any { it.nodeKey == key.value }
    } ?: sources.first()
    val mapping = source.mappings.firstOrNull { it.nodeKey == key.value }

    println("CrossUI node ${key.value}")
    println("Kind: ${node.kind::class.simpleName}")
    println("Target: ${target.cliName}")
    println("Bindings: ${node.bindings.ifEmpty { emptyMap() }}")
    println("Enabled: ${node.semantics.enabled}")
    println("Source: ${node.source ?: "not recorded"}")
    println(
        "Generated: ${mapping?.let { "${it.generatedFile}:${it.generatedLine}" } ?: "not emitted"}",
    )
}

private fun diffDocuments(options: List<String>) {
    val before = UiDocument.fromJson(Path.of(options.required("--before")).readText())
    val after = UiDocument.fromJson(Path.of(options.required("--after")).readText())
    val operations = diff(before, after)

    println("CrossUI semantic diff")
    println("Operations: ${operations.size}")
    operations.forEach { operation ->
        println("- ${operation::class.simpleName?.lowercase()}: ${operation.key.value}")
    }
}

private fun verify(options: List<String>) {
    val document = options.readDocument()
    val output = Path.of(options.required("--output"))
    val sources = CrossUiCompiler.generate(
        document = document,
        targets = options.targets(),
        typeName = options.valueAfter("--name") ?: "CrossUiGenerated",
        nativeViews = options.nativeViews(),
        localization = options.localization(),
    )
    val stale = sources.filter { generated ->
        val path = output.resolve(generated.relativePath)
        !path.exists() || path.readText() != generated.content
    }
    if (stale.isEmpty()) {
        println("CrossUI generated sources are current.")
    } else {
        stale.forEach { println("Stale: ${output.resolve(it.relativePath)}") }
        error("${stale.size} generated source file(s) are stale.")
    }
}

private fun List<String>.readDocument(): UiDocument =
    UiDocument.fromJson(Path.of(required("--input")).readText())

private fun List<String>.targets(): Set<ExportTarget> =
    valueAfter("--targets")
        ?.split(',')
        ?.filter(String::isNotBlank)
        ?.map(ExportTarget::parse)
        ?.toSet()
        ?: ExportTarget.entries.toSet()

private fun List<String>.nativeViews(): NativeViewRegistry {
    val registrations = valuesAfter("--native-view")
    if (registrations.isEmpty()) return NativeViewRegistry.Empty
    return NativeViewRegistry.build {
        registrations.forEach { registration ->
            val (identity, templateValue) = registration.split('=', limit = 2)
                .takeIf { it.size == 2 }
                ?: error("Native view must use target:name=template syntax.")
            val (target, name) = identity.split(':', limit = 2)
                .takeIf { it.size == 2 }
                ?: error("Native view must use target:name=template syntax.")
            val template = if (templateValue.startsWith("@")) {
                Files.readString(Path.of(templateValue.drop(1)))
            } else {
                templateValue
            }
            register(ExportTarget.parse(target), name, template)
        }
    }
}

private fun List<String>.localization(): LocalizationRegistry =
    LocalizationRegistry.build {
        androidResources(valueAfter("--android-resource-class") ?: "R")
        valuesAfter("--localization").forEach { registration ->
            val (target, templateValue) = registration.split('=', limit = 2)
                .takeIf { it.size == 2 }
                ?: error("Localization resolver must use target=template syntax.")
            val template = if (templateValue.startsWith("@")) {
                Files.readString(Path.of(templateValue.drop(1)))
            } else {
                templateValue
            }
            register(ExportTarget.parse(target), template)
        }
    }

private fun List<String>.required(flag: String): String =
    valueAfter(flag) ?: error("Missing $flag <value>")

private fun List<String>.valueAfter(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0) getOrNull(index + 1) else null
}

private fun List<String>.valuesAfter(flag: String): List<String> =
    mapIndexedNotNull { index, value ->
        if (value == flag) getOrNull(index + 1) else null
    }

private fun dev.crossui.ir.Node.containsBindings(): Boolean =
    bindings.isNotEmpty() || children.any { it.containsBindings() }

private fun dev.crossui.ir.Node.nodeCount(): Int =
    1 + children.sumOf { it.nodeCount() }

private fun dev.crossui.ir.Node.bindingCount(): Int =
    bindings.size + children.sumOf { it.bindingCount() }
