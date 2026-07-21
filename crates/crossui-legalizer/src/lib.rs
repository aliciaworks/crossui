//! Platform-aware lowering pipeline for CrossUI IR.
//!
//! # Pipeline
//!
//! ```text
//! Authored Semantic IR
//!     │
//!     ├── 1. Schema validation
//!     ├── 2. Extension validation (extensions must match target profile)
//!     ├── 3. Semantic resolution (traits → derived policies)
//!     ├── 4. Capability legality check
//!     └── 5. HIG rule enforcement
//!     ↓
//! Resolved Semantic IR
//! ```
//!
//! # Rule format
//!
//! Rules are YAML assets.  Example:
//!
//! ```yaml
//! id: watchos.data-table-density
//! applies_to:
//!   platform: watchos
//! when:
//!   node_kind: data_table
//!   columns:
//!     greater_than: 3
//! result:
//!   legality: illegal
//! rewrites:
//!   - summary_and_drilldown
//!   - top_metrics_only
//! ```

use crossui_ir::{
    DocumentError, UiDocument,
    extensions::{ExtensionMismatch, PlatformExtension, UnsupportedExtensionPolicy},
    profile::TargetProfile,
};
use serde::{Deserialize, Serialize};
use thiserror::Error;

// ---------------------------------------------------------------------------
// Pipeline entry point
// ---------------------------------------------------------------------------

/// The result of the full lowering pipeline.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ResolvedDocument {
    /// The (potentially rewritten) IR document.
    pub document: UiDocument,
    /// Semantic derivations produced during resolution.
    pub derived: Vec<DerivedPolicy>,
    /// Extension validation report.
    pub extension_report: ExtensionReport,
    /// HIG violations (non-fatal warnings).
    pub hig_warnings: Vec<HigViolation>,
    /// Rules that fired during legalization.
    pub rule_hits: Vec<String>,
}

/// A policy derived from semantic traits by the resolver.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct DerivedPolicy {
    pub node_key: String,
    pub policy: String,
    pub derived_by: Vec<String>,
}

/// Extension validation summary.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct ExtensionReport {
    pub total_extensions: usize,
    pub mismatches: Vec<ExtensionMismatch>,
    pub policy: UnsupportedExtensionPolicy,
}

/// A single HIG guideline violation.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct HigViolation {
    pub rule_id: String,
    pub severity: ViolationSeverity,
    pub node_key: Option<String>,
    pub message: String,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ViolationSeverity {
    Warning,
    Error,
}

#[derive(Debug, Error)]
pub enum LegalizerError {
    #[error("document validation failed")]
    Document(#[from] DocumentError),
    #[error("extension mismatch: expected {expected:?}, got {actual:?}")]
    ExtensionMismatch {
        expected: crossui_ir::profile::PlatformIdentity,
        actual: crossui_ir::profile::PlatformIdentity,
    },
    #[error("HIG rule check failed: {0}")]
    HigViolation(String),
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
}

// ---------------------------------------------------------------------------
// Pipeline stages
// ---------------------------------------------------------------------------

pub fn compile(
    document: &UiDocument,
    target: &TargetProfile,
    rule_set: Option<&RuleSet>,
) -> Result<ResolvedDocument, LegalizerError> {
    // 1. Schema validation.
    document
        .validate()
        .map_err(|e| LegalizerError::Document(DocumentError::Validation(e)))?;

    // 2. Extension validation.
    let ext_report = validate_document_extensions(document, target);

    // 3. Semantic resolution.
    let derived = resolve_semantics(document);

    // 4. Capability + HIG check.
    let (hig_warnings, rule_hits) = if let Some(rules) = rule_set {
        check_hig_rules(document, target, rules)
    } else {
        (vec![], vec![])
    };

    Ok(ResolvedDocument {
        document: document.clone(),
        derived,
        extension_report: ext_report,
        hig_warnings,
        rule_hits,
    })
}

// ---------------------------------------------------------------------------
// Extension validation
// ---------------------------------------------------------------------------

fn validate_document_extensions(document: &UiDocument, target: &TargetProfile) -> ExtensionReport {
    let mut total = 0;
    let mut mismatches = Vec::new();
    collect_extensions(&document.root, &mut total, &mut |ext| {
        if ext.platform() != target.platform {
            mismatches.push(ExtensionMismatch {
                expected: ext.platform(),
                actual: target.platform,
            });
        }
    });
    ExtensionReport {
        total_extensions: total,
        mismatches,
        policy: UnsupportedExtensionPolicy::Error,
    }
}

fn collect_extensions(
    node: &crossui_ir::Node,
    total: &mut usize,
    check: &mut impl FnMut(&PlatformExtension),
) {
    *total += node.extensions.len();
    for ext in &node.extensions {
        check(ext);
    }
    for child in &node.children {
        collect_extensions(child, total, check);
    }
}

// ---------------------------------------------------------------------------
// Semantic resolution
// ---------------------------------------------------------------------------

fn resolve_semantics(document: &UiDocument) -> Vec<DerivedPolicy> {
    let mut derived = Vec::new();
    resolve_node(&document.root, &mut derived);
    derived
}

fn resolve_node(node: &crossui_ir::Node, derived: &mut Vec<DerivedPolicy>) {
    let traits = &node.semantics.traits;

    // Rule: irreversible + critical → confirmation required.
    if traits.irreversible && traits.importance == crossui_ir::Importance::Critical {
        derived.push(DerivedPolicy {
            node_key: node.key.0.clone(),
            policy: "confirmation.required".into(),
            derived_by: vec!["semantic.irreversible-critical-confirmation".into()],
        });
    }

    // Rule: irreversible + rare → confirmation required.
    if traits.irreversible && traits.frequency == crossui_ir::ActionFrequency::Rare {
        derived.push(DerivedPolicy {
            node_key: node.key.0.clone(),
            policy: "confirmation.required".into(),
            derived_by: vec!["semantic.irreversible-rare-confirmation".into()],
        });
    }

    for child in &node.children {
        resolve_node(child, derived);
    }
}

// ---------------------------------------------------------------------------
// HIG rule engine
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, Default, Deserialize)]
pub struct RuleSet {
    pub rules: Vec<HigRule>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct HigRule {
    pub id: String,
    pub applies_to: RuleTarget,
    #[serde(default)]
    pub when: Vec<RuleCondition>,
    pub result: RuleResult,
    #[serde(default)]
    pub rewrites: Vec<String>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct RuleTarget {
    pub platform: String,
    #[serde(default)]
    pub os_version: Option<String>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(tag = "field")]
pub enum RuleCondition {
    #[serde(rename = "node_kind")]
    NodeKind { equals: String },
    #[serde(rename = "columns")]
    Columns { greater_than: usize },
    #[serde(rename = "interaction_duration")]
    InteractionDuration { equals: String },
    #[serde(rename = "glanceable")]
    Glanceable { equals: bool },
}

#[derive(Clone, Debug, Deserialize)]
pub struct RuleResult {
    pub legality: String,
    #[serde(default)]
    pub message: Option<String>,
}

fn check_hig_rules(
    document: &UiDocument,
    target: &TargetProfile,
    rules: &RuleSet,
) -> (Vec<HigViolation>, Vec<String>) {
    let mut warnings = Vec::new();
    let mut hits = Vec::new();

    for rule in &rules.rules {
        let platform_name = format!("{:?}", target.platform).to_lowercase();
        if rule.applies_to.platform != platform_name {
            continue;
        }

        // Profile-level conditions.
        let mut profile_matches = true;
        for cond in &rule.when {
            if !evaluate_profile_condition(cond, target) {
                profile_matches = false;
                break;
            }
        }
        if !profile_matches {
            continue;
        }

        // If rule has node-level conditions, traverse the document tree.
        let node_conditions: Vec<_> = rule
            .when
            .iter()
            .filter(|c| {
                matches!(
                    c,
                    RuleCondition::NodeKind { .. } | RuleCondition::Columns { .. }
                )
            })
            .collect();
        if node_conditions.is_empty() {
            // Pure profile-level rule matched.
            hits.push(rule.id.clone());
            if rule.result.legality == "illegal" || rule.result.legality == "error" {
                warnings.push(HigViolation {
                    rule_id: rule.id.clone(),
                    severity: ViolationSeverity::Error,
                    node_key: None,
                    message: rule
                        .result
                        .message
                        .clone()
                        .unwrap_or_else(|| "HIG rule violation".into()),
                });
            }
        } else {
            // Evaluate node-level conditions on every node.
            check_nodes(
                &document.root,
                rule,
                &node_conditions,
                &mut warnings,
                &mut hits,
            );
        }
    }

    (warnings, hits)
}

fn evaluate_profile_condition(cond: &RuleCondition, target: &TargetProfile) -> bool {
    match cond {
        RuleCondition::Glanceable { equals } => target.interaction.glanceable == *equals,
        RuleCondition::InteractionDuration { equals } => {
            format!("{:?}", target.interaction.expected_duration).to_lowercase() == *equals
        }
        _ => true, // Node-level conditions handled separately.
    }
}

fn check_nodes(
    node: &crossui_ir::Node,
    rule: &HigRule,
    conditions: &[&RuleCondition],
    warnings: &mut Vec<HigViolation>,
    hits: &mut Vec<String>,
) {
    let all_match = conditions
        .iter()
        .all(|cond| evaluate_node_condition(cond, node));
    if all_match {
        hits.push(rule.id.clone());
        if rule.result.legality == "illegal" || rule.result.legality == "error" {
            warnings.push(HigViolation {
                rule_id: rule.id.clone(),
                severity: ViolationSeverity::Error,
                node_key: Some(node.key.0.clone()),
                message: rule
                    .result
                    .message
                    .clone()
                    .unwrap_or_else(|| "HIG rule violation".into()),
            });
        }
    }
    for child in &node.children {
        check_nodes(child, rule, conditions, warnings, hits);
    }
}

fn evaluate_node_condition(cond: &RuleCondition, node: &crossui_ir::Node) -> bool {
    match cond {
        RuleCondition::NodeKind { equals } => kind_name(node) == *equals,
        RuleCondition::Columns { greater_than } => count_columns(node) > *greater_than,
        _ => true,
    }
}

fn kind_name(node: &crossui_ir::Node) -> &'static str {
    match &node.kind {
        crossui_ir::NodeKind::Text { .. } => "text",
        crossui_ir::NodeKind::Button { .. } => "button",
        crossui_ir::NodeKind::Input { .. } => "input",
        crossui_ir::NodeKind::Stack { .. } => "stack",
        crossui_ir::NodeKind::List { .. } => "list",
        crossui_ir::NodeKind::Form { .. } => "form",
        crossui_ir::NodeKind::Loading { .. } => "loading",
        crossui_ir::NodeKind::Navigation { .. } => "navigation",
        crossui_ir::NodeKind::Route { .. } => "route",
        crossui_ir::NodeKind::PlatformView { .. } => "platform_view",
        crossui_ir::NodeKind::Toggle { .. } => "toggle",
        crossui_ir::NodeKind::Image { .. } => "image",
        crossui_ir::NodeKind::Dialog { .. } => "dialog",
        crossui_ir::NodeKind::Slider { .. } => "slider",
        crossui_ir::NodeKind::Picker { .. } => "picker",
        crossui_ir::NodeKind::DatePicker { .. } => "date_picker",
        crossui_ir::NodeKind::Checkbox { .. } => "checkbox",
        crossui_ir::NodeKind::Divider { .. } => "divider",
        crossui_ir::NodeKind::Card { .. } => "card",
        crossui_ir::NodeKind::Chip { .. } => "chip",
    }
}

fn count_columns(_node: &crossui_ir::Node) -> usize {
    // Simplified: count immediate children in a horizontal stack or card.
    match &_node.kind {
        crossui_ir::NodeKind::Stack { axis, .. } if *axis == crossui_ir::Axis::Horizontal => {
            _node.children.len()
        }
        crossui_ir::NodeKind::Card { .. } => _node.children.len(),
        _ => 0,
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crossui_ir::{
        Node, NodeKind, TextStyle,
        extensions::{HapticType, IosExtension, PlatformExtension as Ext, PresentationStyle},
        profile::TargetProfile,
    };

    #[test]
    fn extension_validation_on_wrong_platform_reports_mismatch() {
        let mut node = Node::new(
            "btn",
            NodeKind::Button {
                label: "Delete".into(),
                action: "delete".into(),
                variant: crossui_ir::ButtonVariant::Destructive,
            },
        );
        node.extensions.push(Ext::Ios(IosExtension::HapticFeedback {
            feedback_type: HapticType::Error,
        }));

        let doc = UiDocument::new(node);
        let target = TargetProfile::mac_desktop();
        let result = compile(&doc, &target, None).unwrap();
        assert_eq!(result.extension_report.total_extensions, 1);
        assert_eq!(result.extension_report.mismatches.len(), 1);
    }

    #[test]
    fn extension_on_correct_platform_passes() {
        let mut node = Node::new(
            "btn",
            NodeKind::Button {
                label: "OK".into(),
                action: "ok".into(),
                variant: crossui_ir::ButtonVariant::Primary,
            },
        );
        node.extensions
            .push(Ext::Ios(IosExtension::PresentationStyle {
                style: PresentationStyle::Sheet,
            }));

        let doc = UiDocument::new(node);
        let target = TargetProfile::iphone();
        let result = compile(&doc, &target, None).unwrap();
        assert!(result.extension_report.mismatches.is_empty());
    }

    #[test]
    fn irreversible_critical_action_gets_confirmation_policy() {
        let mut node = Node::new(
            "delete",
            NodeKind::Button {
                label: "Delete".into(),
                action: "delete".into(),
                variant: crossui_ir::ButtonVariant::Destructive,
            },
        );
        node.semantics.traits.irreversible = true;
        node.semantics.traits.importance = crossui_ir::Importance::Critical;
        let doc = UiDocument::new(node);

        let derived = resolve_semantics(&doc);
        assert!(derived.iter().any(|d| d.policy == "confirmation.required"));
    }

    #[test]
    fn regular_button_gets_no_derived_confirmation() {
        let node = Node::new(
            "save",
            NodeKind::Button {
                label: "Save".into(),
                action: "save".into(),
                variant: crossui_ir::ButtonVariant::Primary,
            },
        );
        let doc = UiDocument::new(node);
        let derived = resolve_semantics(&doc);
        assert!(derived.is_empty());
    }

    #[test]
    fn profile_convenience_factories_work() {
        assert!(
            TargetProfile::apple_watch()
                .capabilities
                .input
                .digital_crown
        );
        assert!(!TargetProfile::iphone().capabilities.input.keyboard);
        assert!(TargetProfile::mac_desktop().capabilities.windowing.menu_bar);
    }

    #[test]
    fn rule_node_kind_condition_fires_on_matching_node() {
        let node = Node::new(
            "btn",
            NodeKind::Button {
                label: "Click".into(),
                action: "click".into(),
                variant: crossui_ir::ButtonVariant::Primary,
            },
        );
        let doc = UiDocument::new(node);
        let target = TargetProfile::mac_desktop();
        let rules = RuleSet {
            rules: vec![HigRule {
                id: "mac.button".into(),
                applies_to: RuleTarget {
                    platform: "macos".into(),
                    os_version: None,
                },
                when: vec![RuleCondition::NodeKind {
                    equals: "button".into(),
                }],
                result: RuleResult {
                    legality: "warning".into(),
                    message: Some("add shortcut".into()),
                },
                rewrites: vec![],
            }],
        };
        let (_w, hits) = check_hig_rules(&doc, &target, &rules);
        assert!(hits.contains(&"mac.button".to_string()));
    }

    #[test]
    fn rule_ignores_wrong_platform() {
        let node = Node::new(
            "btn",
            NodeKind::Button {
                label: "X".into(),
                action: "x".into(),
                variant: crossui_ir::ButtonVariant::Primary,
            },
        );
        let doc = UiDocument::new(node);
        let rules = RuleSet {
            rules: vec![HigRule {
                id: "mac-only".into(),
                applies_to: RuleTarget {
                    platform: "macos".into(),
                    os_version: None,
                },
                when: vec![],
                result: RuleResult {
                    legality: "warning".into(),
                    message: None,
                },
                rewrites: vec![],
            }],
        };
        let (_w, hits) = check_hig_rules(&doc, &TargetProfile::iphone(), &rules);
        assert!(hits.is_empty());
    }

    #[test]
    fn columns_condition_counts_horizontal_children() {
        let node = Node::new(
            "row",
            NodeKind::Stack {
                axis: crossui_ir::Axis::Horizontal,
                spacing: None,
                alignment: crossui_ir::Alignment::Center,
            },
        )
        .with_children(vec![
            Node::new(
                "c0",
                NodeKind::Text {
                    text: "0".into(),
                    style: TextStyle::Body,
                },
            ),
            Node::new(
                "c1",
                NodeKind::Text {
                    text: "1".into(),
                    style: TextStyle::Body,
                },
            ),
            Node::new(
                "c2",
                NodeKind::Text {
                    text: "2".into(),
                    style: TextStyle::Body,
                },
            ),
            Node::new(
                "c3",
                NodeKind::Text {
                    text: "3".into(),
                    style: TextStyle::Body,
                },
            ),
            Node::new(
                "c4",
                NodeKind::Text {
                    text: "4".into(),
                    style: TextStyle::Body,
                },
            ),
        ]);
        let doc = UiDocument::new(node);
        let rules = RuleSet {
            rules: vec![HigRule {
                id: "watch.cols".into(),
                applies_to: RuleTarget {
                    platform: "watchos".into(),
                    os_version: None,
                },
                when: vec![RuleCondition::Columns { greater_than: 3 }],
                result: RuleResult {
                    legality: "illegal".into(),
                    message: Some("too wide".into()),
                },
                rewrites: vec!["summary".into()],
            }],
        };
        let (warnings, hits) = check_hig_rules(&doc, &TargetProfile::apple_watch(), &rules);
        assert!(hits.contains(&"watch.cols".to_string()));
        assert!(!warnings.is_empty());
    }

    #[test]
    fn fixture_wide_row_violates_watchos_rule() {
        let json = std::fs::read_to_string("fixtures/wide-row.in.json").unwrap();
        let doc = UiDocument::from_json(&json).unwrap();
        let yaml = std::fs::read_to_string("fixtures/watchos-hig.yaml").unwrap();
        let rules: RuleSet = serde_yaml::from_str(&yaml).unwrap();
        let (warnings, hits) = check_hig_rules(&doc, &TargetProfile::apple_watch(), &rules);
        assert!(
            hits.contains(&"watchos.data-table-density".to_string()),
            "hits: {hits:?}"
        );
        assert!(!warnings.is_empty());
    }

    #[test]
    fn fixture_iphone_ignores_watchos_rules() {
        let json = std::fs::read_to_string("fixtures/wide-row.in.json").unwrap();
        let doc = UiDocument::from_json(&json).unwrap();
        let yaml = std::fs::read_to_string("fixtures/watchos-hig.yaml").unwrap();
        let rules: RuleSet = serde_yaml::from_str(&yaml).unwrap();
        let (_w, hits) = check_hig_rules(&doc, &TargetProfile::iphone(), &rules);
        assert!(hits.is_empty());
    }
}
