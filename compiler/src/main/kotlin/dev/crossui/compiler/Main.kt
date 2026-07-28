package dev.crossui.compiler

import dev.crossui.ir.UiDocument
import java.nio.file.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val options = args.toList()
    val input = options.valueAfter("--input")
        ?: error("Missing --input <document.json>")
    val output = options.valueAfter("--output")
        ?: error("Missing --output <directory>")
    val targets = options.valueAfter("--targets")
        ?.split(',')
        ?.map(ExportTarget::parse)
        ?.toSet()
        ?: ExportTarget.entries.toSet()
    val typeName = options.valueAfter("--name") ?: "CrossUiGenerated"

    val document = UiDocument.fromJson(Path.of(input).readText())
    CrossUiCompiler.write(document, Path.of(output), targets, typeName)
        .forEach { println(it.toAbsolutePath()) }
}

private fun List<String>.valueAfter(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0) getOrNull(index + 1) else null
}
