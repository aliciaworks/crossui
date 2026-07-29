package dev.crossui.compiler

import dev.crossui.ir.LocalizedText
import dev.crossui.ir.NodeKind
import dev.crossui.ir.UiDocument
import dev.crossui.ir.walk
import java.nio.file.Files
import java.nio.file.Path
import java.util.IllformedLocaleException
import java.util.Locale
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LocalizationSourceEntry(
    val key: String,
    val fallback: String,
    val table: String = "Localizable",
)

data class LocalizationSourceReport(
    val entries: List<LocalizationSourceEntry>,
    val files: List<Path>,
)

object LocalizationSources {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun extract(document: UiDocument): List<LocalizationSourceEntry> {
        val entries = linkedMapOf<Pair<String, String>, LocalizationSourceEntry>()
        document.root.walk { node ->
            node.localizedText.values.forEach { text ->
                if (text is LocalizedText.Resource) {
                    entries.merge(text.toSourceEntry())
                }
            }
            (node.kind as? NodeKind.Picker)?.options?.forEach { option ->
                val text = option.localizedLabel
                if (text is LocalizedText.Resource) {
                    entries.merge(text.toSourceEntry())
                }
            }
        }
        return entries.values
            .sortedWith(compareBy({ it.table }, { it.key }))
            .also(::verifyPlatformIdentities)
    }

    fun generate(
        document: UiDocument,
        output: Path,
        sourceLocale: String,
    ): LocalizationSourceReport {
        validateLocale(sourceLocale)
        val entries = extract(document)
        val files = buildList {
            entries.groupBy(LocalizationSourceEntry::table).forEach { (table, tableEntries) ->
                add(mergeApple(output, sourceLocale, table, tableEntries))
            }
            add(mergeAndroid(output, entries))
            add(mergeWindows(output, sourceLocale, entries))
        }
        verify(document, output, sourceLocale)
        return LocalizationSourceReport(entries, files)
    }

    fun verify(
        document: UiDocument,
        output: Path?,
        sourceLocale: String,
        requirePlatformFiles: Boolean = true,
    ): LocalizationSourceReport {
        validateLocale(sourceLocale)
        val entries = extract(document)
        if (!requirePlatformFiles) {
            return LocalizationSourceReport(entries, emptyList())
        }
        requireNotNull(output) { "Localization output directory is required." }
        val files = mutableListOf<Path>()
        val missing = mutableListOf<String>()
        entries.groupBy(LocalizationSourceEntry::table).forEach { (table, tableEntries) ->
            val file = appleFile(output, table)
            files.add(file)
            val strings = readAppleStrings(file)
            strings.values.forEach { value ->
                value["localizations"]?.jsonObject?.keys?.forEach(::validateLocale)
            }
            tableEntries.forEach {
                val source = strings[it.key]
                    ?.get("localizations")
                    ?.jsonObject
                    ?.get(sourceLocale)
                if (source == null) missing += "apple:${it.table}:${it.key}"
            }
        }
        output.resolve("windows/Strings").toFile()
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .forEach { validateLocale(it.name) }
        val androidFile = androidFile(output)
        files.add(androidFile)
        val android = readAndroid(androidFile)
        entries.forEach {
            if (it.androidName() !in android) missing += "android:${it.androidName()}"
        }
        val windowsFile = windowsFile(output, sourceLocale)
        files.add(windowsFile)
        val windows = readResw(windowsFile)
        entries.forEach {
            if (it.key !in windows) missing += "windows:${it.key}"
        }
        check(missing.isEmpty()) {
            "Missing localization keys: ${missing.sorted().joinToString()}"
        }
        return LocalizationSourceReport(entries, files)
    }

    private fun MutableMap<Pair<String, String>, LocalizationSourceEntry>.merge(
        entry: LocalizationSourceEntry,
    ) {
        val identity = entry.table to entry.key
        val previous = putIfAbsent(identity, entry)
        check(previous == null || previous.fallback == entry.fallback) {
            "Localization fallback conflict for ${entry.table}:${entry.key}: " +
                "'${previous?.fallback}' versus '${entry.fallback}'."
        }
    }

    private fun LocalizedText.Resource.toSourceEntry() = LocalizationSourceEntry(
        key = key,
        fallback = fallback,
        table = namespace?.ifBlank { null } ?: "Localizable",
    )

    private fun verifyPlatformIdentities(entries: List<LocalizationSourceEntry>) {
        checkUniquePlatformIdentity(
            entries,
            "Android",
            { it.androidName() },
            { it.table to it.key },
        )
        checkUniquePlatformIdentity(
            entries,
            "Windows",
            { it.key },
            { it.table to it.key },
        )
        checkUniquePlatformIdentity(
            entries,
            "Apple table",
            { fileName(it.table) },
            { it.table },
        )
    }

    private fun <T> checkUniquePlatformIdentity(
        entries: List<LocalizationSourceEntry>,
        platform: String,
        identity: (LocalizationSourceEntry) -> String,
        sourceIdentity: (LocalizationSourceEntry) -> T,
    ) {
        entries.groupBy(identity).forEach { (resolved, matches) ->
            val sources = matches.map(sourceIdentity).distinct()
            check(sources.size == 1) {
                "$platform localization key collision '$resolved': " +
                    sources.joinToString()
            }
        }
    }

    private fun mergeApple(
        output: Path,
        sourceLocale: String,
        table: String,
        entries: List<LocalizationSourceEntry>,
    ): Path {
        val file = appleFile(output, table)
        val existingRoot = readJsonObject(file)
        val existingStrings = existingRoot["strings"]?.jsonObject.orEmpty()
        val mergedStrings = linkedMapOf<String, JsonObject>()
        existingStrings.forEach { (key, value) -> mergedStrings[key] = value.jsonObject }
        entries.forEach { entry ->
            val existing = mergedStrings[entry.key].orEmpty()
            val localizations = existing["localizations"]?.jsonObject.orEmpty().toMutableMap()
            localizations[sourceLocale] = buildJsonObject {
                put("stringUnit", buildJsonObject {
                    put("state", JsonPrimitive("translated"))
                    put("value", JsonPrimitive(entry.fallback))
                })
            }
            mergedStrings[entry.key] = buildJsonObject {
                existing.forEach { (key, value) ->
                    if (key != "localizations") put(key, value)
                }
                put("extractionState", existing["extractionState"] ?: JsonPrimitive("manual"))
                put("localizations", JsonObject(localizations.toSortedMap()))
            }
        }
        val root = buildJsonObject {
            put("sourceLanguage", JsonPrimitive(sourceLocale))
            put("strings", JsonObject(mergedStrings.toSortedMap()))
            put("version", existingRoot["version"] ?: JsonPrimitive("1.0"))
        }
        write(file, json.encodeToString(JsonObject.serializer(), root) + "\n")
        return file
    }

    private fun mergeAndroid(
        output: Path,
        entries: List<LocalizationSourceEntry>,
    ): Path {
        val file = androidFile(output)
        val existing = readAndroid(file).toMutableMap()
        entries.forEach { existing[it.androidName()] = androidEncode(it.fallback) }
        val content = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<resources>")
            existing.toSortedMap().forEach { (key, value) ->
                appendLine("""    <string name="${xml(key)}">${xml(value)}</string>""")
            }
            appendLine("</resources>")
        }
        write(file, content)
        return file
    }

    private fun mergeWindows(
        output: Path,
        sourceLocale: String,
        entries: List<LocalizationSourceEntry>,
    ): Path {
        val file = windowsFile(output, sourceLocale)
        val existing = readResw(file).toMutableMap()
        entries.forEach { entry -> existing[entry.key] = entry.fallback }
        val content = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<root>")
            existing.toSortedMap().forEach { (key, value) ->
                appendLine("""  <data name="${xml(key)}" xml:space="preserve">""")
                appendLine("    <value>${xml(value)}</value>")
                appendLine("  </data>")
            }
            appendLine("</root>")
        }
        write(file, content)
        return file
    }

    private fun readAppleStrings(path: Path): Map<String, JsonObject> =
        readJsonObject(path)["strings"]?.jsonObject
            ?.mapValues { it.value.jsonObject }
            .orEmpty()

    private fun readJsonObject(path: Path): JsonObject {
        if (!Files.exists(path)) return JsonObject(emptyMap())
        return json.parseToJsonElement(Files.readString(path)).jsonObject
    }

    private fun readAndroid(path: Path): Map<String, String> =
        readXml(path, "string", "name")

    private fun readResw(path: Path): Map<String, String> =
        readXml(path, "data", "name", "value")

    private fun readXml(
        path: Path,
        entryElement: String,
        keyAttribute: String,
        valueElement: String? = null,
    ): Map<String, String> {
        if (!Files.exists(path)) return emptyMap()
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        val values = linkedMapOf<String, String>()
        Files.newInputStream(path).use { input ->
            val reader = factory.createXMLStreamReader(input)
            var key: String? = null
            var collecting = false
            val text = StringBuilder()
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        if (reader.localName == entryElement) {
                            key = reader.getAttributeValue(null, keyAttribute)
                            if (valueElement == null) {
                                collecting = true
                                text.clear()
                            }
                        } else if (key != null && reader.localName == valueElement) {
                            collecting = true
                            text.clear()
                        }
                    }
                    XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    -> if (collecting) text.append(reader.text)
                    XMLStreamConstants.END_ELEMENT -> {
                        val endsValue = valueElement == null &&
                            reader.localName == entryElement ||
                            valueElement != null && reader.localName == valueElement
                        if (collecting && endsValue) {
                            val resolvedKey = requireNotNull(key) {
                                "Missing $keyAttribute in $path."
                            }
                            check(values.putIfAbsent(resolvedKey, text.toString()) == null) {
                                "Duplicate localization key '$resolvedKey' in $path."
                            }
                            collecting = false
                            if (valueElement == null) key = null
                        }
                        if (reader.localName == entryElement) key = null
                    }
                }
            }
            reader.close()
        }
        return values
    }

    private fun validateLocale(value: String) {
        try {
            val locale = Locale.Builder().setLanguageTag(value).build()
            require(locale.language.isNotBlank() && locale.toLanguageTag() != "und") {
                "Invalid BCP-47 source locale '$value'."
            }
        } catch (_: IllformedLocaleException) {
            error("Invalid BCP-47 source locale '$value'.")
        }
    }

    private fun LocalizationSourceEntry.androidName(): String =
        ((if (table == "Localizable") "" else "${table}_") + key)
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifEmpty { "crossui_text" }
            .let { if (it.first().isDigit()) "crossui_$it" else it }

    private fun appleFile(output: Path, table: String): Path =
        output.resolve("apple").resolve("${fileName(table)}.xcstrings")

    private fun androidFile(output: Path): Path =
        output.resolve("android/values/crossui_strings.xml")

    private fun windowsFile(output: Path, locale: String): Path =
        output.resolve("windows/Strings").resolve(locale).resolve("Resources.resw")

    private fun fileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_.-]+"), "_").ifEmpty { "CrossUI" }

    private fun androidEncode(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
