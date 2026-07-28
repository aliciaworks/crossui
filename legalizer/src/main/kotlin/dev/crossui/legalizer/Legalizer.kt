package dev.crossui.legalizer

import dev.crossui.ir.*

data class ResolvedDocument(
    val document: UiDocument,
    val target: TargetProfile,
    val policies: List<DerivedPolicy>,
    val extensionReport: ExtensionReport,
    val violations: List<HigViolation>,
)

data class DerivedPolicy(
    val nodeKey: NodeKey,
    val confirmationRequired: Boolean = false,
    val emphasize: Boolean = false,
)

data class ExtensionReport(
    val mismatches: List<ExtensionMismatch> = emptyList(),
) {
    val isClean: Boolean get() = mismatches.isEmpty()
}

data class HigViolation(
    val ruleId: String,
    val nodeKey: NodeKey,
    val message: String,
    val severity: ViolationSeverity,
)

enum class ViolationSeverity { Warning, Error }

class LegalizerException(message: String) : IllegalArgumentException(message)

fun compile(
    document: UiDocument,
    target: TargetProfile,
    unsupportedExtensions: UnsupportedExtensionPolicy = UnsupportedExtensionPolicy.Reject,
    rules: RuleSet = RuleSet.default(),
): ResolvedDocument {
    document.validate()
    val mismatches = try {
        validateExtensions(document, target, unsupportedExtensions)
    } catch (error: IllegalStateException) {
        throw LegalizerException(error.message ?: "Invalid platform extension")
    }
    val violations = rules.evaluate(document, target)
    val errors = violations.filter { it.severity == ViolationSeverity.Error }
    if (errors.isNotEmpty()) {
        throw LegalizerException(
            errors.joinToString("; ") { "${it.ruleId}: ${it.message} (${it.nodeKey.value})" },
        )
    }
    return ResolvedDocument(
        document,
        target,
        resolveSemantics(document),
        ExtensionReport(mismatches),
        violations,
    )
}

private fun resolveSemantics(document: UiDocument): List<DerivedPolicy> = buildList {
    document.root.walk { node ->
        val traits = node.semantics.traits
        if (traits.irreversible ||
            traits.importance == Importance.Critical ||
            traits.importance == Importance.High
        ) {
            add(
                DerivedPolicy(
                    node.key,
                    confirmationRequired =
                        traits.irreversible && traits.importance == Importance.Critical,
                    emphasize = traits.importance != Importance.Normal,
                ),
            )
        }
    }
}

data class RuleSet(val rules: List<HigRule>) {
    fun evaluate(document: UiDocument, target: TargetProfile): List<HigViolation> = buildList {
        document.root.walk { node ->
            rules.filter { it.appliesTo(target, node) }.forEach { rule ->
                add(HigViolation(rule.id, node.key, rule.message, rule.severity))
            }
        }
    }

    companion object {
        fun default() = RuleSet(
            listOf(
                HigRule(
                    id = "watchos.horizontal-density",
                    platforms = setOf(PlatformIdentity.WatchOs),
                    nodePredicate = { node ->
                        val kind = node.kind
                        kind is NodeKind.Stack &&
                            kind.axis == Axis.Horizontal &&
                            node.children.size > 3
                    },
                    message = "watchOS rows should not expose more than three adjacent controls.",
                    severity = ViolationSeverity.Error,
                ),
                HigRule(
                    id = "touch.minimum-label",
                    platforms = setOf(
                        PlatformIdentity.Ios,
                        PlatformIdentity.IpadOs,
                        PlatformIdentity.Android,
                    ),
                    nodePredicate = { node ->
                        val kind = node.kind
                        kind is NodeKind.Button && kind.label.isBlank()
                    },
                    message = "Touch actions need a visible label.",
                    severity = ViolationSeverity.Error,
                ),
            ),
        )
    }
}

data class HigRule(
    val id: String,
    val platforms: Set<PlatformIdentity>,
    val nodePredicate: (Node) -> Boolean,
    val message: String,
    val severity: ViolationSeverity = ViolationSeverity.Warning,
) {
    fun appliesTo(target: TargetProfile, node: Node) =
        target.platform in platforms && nodePredicate(node)
}
