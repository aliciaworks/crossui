//! Versioned, portable UI document shared by Rust and native hosts.
//!
//! # HIG alignment
//!
//! Each IR version targets a minimum component set that all three platform
//! HIGs (Apple HIG, Material Design 3, WinUI 3 / Fluent) can render natively
//! or with a documented adaptation. Versions are pinned so hosts reject
//! unknown IR before decoding.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use thiserror::Error;

pub const IR_VERSION: u32 = 2;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct UiDocument {
    pub version: u32,
    pub root: Node,
    #[serde(default)]
    pub theme: Theme,
    /// Cached key→node index rebuilt after structural changes.
    #[serde(skip, default)]
    index: BTreeMap<NodeKey, Node>,
}

impl UiDocument {
    pub fn new(root: Node) -> Self {
        let mut doc = Self {
            version: IR_VERSION,
            root,
            theme: Theme::default(),
            index: BTreeMap::new(),
        };
        doc.rebuild_index();
        doc
    }

    pub fn rebuild_index(&mut self) {
        self.index.clear();
        collect_nodes(&self.root, &mut self.index);
    }

    pub fn validate(&self) -> Result<(), ValidationError> {
        if self.version != IR_VERSION {
            return Err(ValidationError::UnsupportedVersion(self.version));
        }
        let mut keys = BTreeSet::new();
        self.root.validate(&mut keys)
    }

    pub fn find_node(&self, target: &NodeKey) -> Option<&Node> {
        self.index.get(target)
    }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string_pretty(self)
    }

    pub fn from_json(json: &str) -> Result<Self, DocumentError> {
        let mut document: Self = serde_json::from_str(json)?;
        document.validate()?;
        document.rebuild_index();
        Ok(document)
    }
}

fn collect_nodes(node: &Node, index: &mut BTreeMap<NodeKey, Node>) {
    index.insert(node.key.clone(), node.clone());
    for child in &node.children {
        collect_nodes(child, index);
    }
}

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct Theme {
    #[serde(default)]
    pub color_scheme: ColorScheme,
    #[serde(default)]
    pub tokens: BTreeMap<String, TokenValue>,
    #[serde(default)]
    pub android: AndroidTheme,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ColorScheme {
    #[default]
    System,
    Light,
    Dark,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(untagged)]
pub enum TokenValue {
    Color(String),
    Number(f64),
    Text(String),
}

impl Eq for TokenValue {}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct AndroidTheme {
    #[serde(default)]
    pub material3_expressive: bool,
    #[serde(default)]
    pub dynamic_color: bool,
}

// ---------------------------------------------------------------------------
// Node tree
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(transparent)]
pub struct NodeKey(pub String);

impl From<&str> for NodeKey {
    fn from(value: &str) -> Self {
        Self(value.into())
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Node {
    pub key: NodeKey,
    #[serde(flatten)]
    pub kind: NodeKind,
    #[serde(default)]
    pub semantics: Semantics,
    #[serde(default)]
    pub children: Vec<Node>,
}

impl Node {
    pub fn new(key: impl Into<NodeKey>, kind: NodeKind) -> Self {
        Self {
            key: key.into(),
            kind,
            semantics: Semantics::default(),
            children: vec![],
        }
    }
    pub fn with_children(mut self, children: Vec<Node>) -> Self {
        self.children = children;
        self
    }
    pub fn validate(&self, keys: &mut BTreeSet<NodeKey>) -> Result<(), ValidationError> {
        if self.key.0.is_empty() {
            return Err(ValidationError::EmptyKey);
        }
        if !keys.insert(self.key.clone()) {
            return Err(ValidationError::DuplicateKey(self.key.0.clone()));
        }
        for child in &self.children {
            child.validate(keys)?;
        }
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Node kinds
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum NodeKind {
    Text {
        text: String,
        style: TextStyle,
    },
    Button {
        label: String,
        action: String,
        variant: ButtonVariant,
    },
    Input {
        value: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        placeholder: Option<String>,
        on_change: String,
        #[serde(default)]
        secure: bool,
        #[serde(default, skip_serializing_if = "InputType::is_default")]
        input_type: InputType,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        return_key: Option<ReturnKey>,
    },
    Stack {
        axis: Axis,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        spacing: Option<String>,
        alignment: Alignment,
    },
    List {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        on_select: Option<String>,
    },
    Form {},
    Loading {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        label: Option<String>,
    },
    Navigation {
        active: String,
        #[serde(default, skip_serializing_if = "NavigationMode::is_default")]
        mode: NavigationMode,
    },
    Route {
        title: String,
        #[serde(default = "return_true", skip_serializing_if = "is_true")]
        respect_safe_area: bool,
    },
    PlatformView {
        platform: Platform,
        name: String,
        payload: serde_json::Value,
    },

    // -- v2 components ----------------------------------------------------
    Toggle {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        checked: bool,
        on_change: String,
    },
    Image {
        src: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        alt: Option<String>,
    },
    Dialog {
        title: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        confirm_label: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        confirm_action: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        cancel_label: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        cancel_action: Option<String>,
    },

    // -- v2.1 components --------------------------------------------------
    Slider {
        value: f64,
        min: f64,
        max: f64,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        step: Option<f64>,
        on_change: String,
    },
    Picker {
        selected: String,
        options: Vec<PickerOption>,
        on_change: String,
    },
    DatePicker {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        value: Option<String>,
        #[serde(default, skip_serializing_if = "DatePickerMode::is_default")]
        mode: DatePickerMode,
        on_change: String,
    },

    // -- v2.2 components --------------------------------------------------
    /// A checkbox controlling a boolean state.
    Checkbox {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        checked: bool,
        on_change: String,
    },
    /// A horizontal rule / separator line.
    Divider {},
    /// An elevated card container.
    Card {},
    /// A compact label chip / tag.
    Chip {
        label: String,
        #[serde(default, skip_serializing_if = "ChipVariant::is_default")]
        variant: ChipVariant,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        on_dismiss: Option<String>,
    },
}

// ---- Chip variant

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ChipVariant {
    #[default]
    Input,
    Filter,
    Suggestion,
}

impl ChipVariant {
    fn is_default(&self) -> bool {
        matches!(self, Self::Input)
    }
}

// ---- Navigation mode ------------------------------------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum NavigationMode {
    #[default]
    Tab,
    Stack,
}

impl NavigationMode {
    fn is_default(&self) -> bool {
        matches!(self, Self::Tab)
    }
}

// ---- Picker option --------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PickerOption {
    pub label: String,
    pub value: String,
}

// ---- Date picker mode -----------------------------------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum DatePickerMode {
    Date,
    Time,
    #[default]
    DateTime,
}

impl DatePickerMode {
    fn is_default(&self) -> bool {
        matches!(self, Self::DateTime)
    }
}

fn return_true() -> bool {
    true
}

fn is_true(val: &bool) -> bool {
    *val
}

// ---------------------------------------------------------------------------
// Text styles
// ---------------------------------------------------------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum TextStyle {
    Display,
    Headline,
    Title,
    #[default]
    Body,
    Caption,
    Footnote,
}

// ---------------------------------------------------------------------------
// Input semantics
// ---------------------------------------------------------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum InputType {
    #[default]
    Text,
    Email,
    Number,
    Phone,
    Url,
    Password,
}

impl InputType {
    fn is_default(&self) -> bool {
        matches!(self, Self::Text)
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ReturnKey {
    Done,
    Go,
    Search,
    Send,
    Next,
}

// ---------------------------------------------------------------------------
// Shared enums
// ---------------------------------------------------------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ButtonVariant {
    #[default]
    Primary,
    Secondary,
    Destructive,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Axis {
    Horizontal,
    Vertical,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Alignment {
    Start,
    #[default]
    Center,
    End,
    Stretch,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Platform {
    Ios,
    Android,
    Windows,
}

// ---------------------------------------------------------------------------
// Semantics
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Semantics {
    #[serde(default)]
    pub label: Option<String>,
    #[serde(default)]
    pub hint: Option<String>,
    #[serde(default)]
    pub role: Option<SemanticRole>,
    #[serde(default = "return_true")]
    pub enabled: bool,
}

impl Default for Semantics {
    fn default() -> Self {
        Self {
            label: None,
            hint: None,
            role: None,
            enabled: true,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SemanticRole {
    Button,
    Header,
    TextField,
    List,
    Form,
    Image,
    Slider,
    Picker,
    Checkbox,
    Link,
}

// ---------------------------------------------------------------------------
// Diff
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum DiffOp {
    Insert { key: NodeKey },
    Remove { key: NodeKey },
    Update { key: NodeKey },
}

pub fn diff(previous: &UiDocument, next: &UiDocument) -> Vec<DiffOp> {
    let mut ops = Vec::new();
    for (key, before) in &previous.index {
        match next.index.get(key) {
            None => ops.push(DiffOp::Remove { key: key.clone() }),
            Some(after) if node_fields_changed(before, after) => {
                ops.push(DiffOp::Update { key: key.clone() })
            }
            _ => {}
        }
    }
    for key in next.index.keys() {
        if !previous.index.contains_key(key) {
            ops.push(DiffOp::Insert { key: key.clone() });
        }
    }
    if previous.theme != next.theme
        && !ops
            .iter()
            .any(|op| matches!(op, DiffOp::Update { key } if key == &next.root.key))
    {
        ops.push(DiffOp::Update {
            key: next.root.key.clone(),
        });
    }
    ops
}

fn node_fields_changed(before: &Node, after: &Node) -> bool {
    before.key != after.key || before.kind != after.kind || before.semantics != after.semantics
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

#[derive(Debug, Error)]
pub enum ValidationError {
    #[error("unsupported IR version {0}")]
    UnsupportedVersion(u32),
    #[error("node key cannot be empty")]
    EmptyKey,
    #[error("duplicate node key: {0}")]
    DuplicateKey(String),
}

#[derive(Debug, Error)]
pub enum DocumentError {
    #[error("invalid JSON in UI document")]
    Json(#[from] serde_json::Error),
    #[error(transparent)]
    Validation(#[from] ValidationError),
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_duplicate_keys() {
        let doc = UiDocument::new(
            Node::new("root", NodeKind::Form {})
                .with_children(vec![Node::new("root", NodeKind::Form {})]),
        );
        assert!(matches!(
            doc.validate(),
            Err(ValidationError::DuplicateKey(_))
        ));
    }

    #[test]
    fn keys_drive_diff() {
        let old = UiDocument::new(Node::new(
            "root",
            NodeKind::Text {
                text: "A".into(),
                style: TextStyle::Body,
            },
        ));
        let new = UiDocument::new(Node::new(
            "root",
            NodeKind::Text {
                text: "B".into(),
                style: TextStyle::Body,
            },
        ));
        assert_eq!(
            diff(&old, &new),
            vec![DiffOp::Update {
                key: NodeKey("root".into())
            }]
        );
    }

    #[test]
    fn semantics_are_enabled_by_default() {
        assert!(Semantics::default().enabled);
    }

    #[test]
    fn rejects_unknown_document_version() {
        let json = r#"{"version":99,"root":{"key":"root","type":"form","semantics":{"enabled":true},"children":[]}}"#;
        assert!(matches!(
            UiDocument::from_json(json),
            Err(DocumentError::Validation(
                ValidationError::UnsupportedVersion(99)
            ))
        ));
    }

    #[test]
    fn child_update_does_not_invalidate_unchanged_ancestors() {
        let old =
            UiDocument::new(
                Node::new("root", NodeKind::Form {}).with_children(vec![Node::new(
                    "value",
                    NodeKind::Text {
                        text: "A".into(),
                        style: TextStyle::Body,
                    },
                )]),
            );
        let new =
            UiDocument::new(
                Node::new("root", NodeKind::Form {}).with_children(vec![Node::new(
                    "value",
                    NodeKind::Text {
                        text: "B".into(),
                        style: TextStyle::Body,
                    },
                )]),
            );
        assert_eq!(
            diff(&old, &new),
            vec![DiffOp::Update {
                key: NodeKey("value".into())
            }]
        );
    }

    #[test]
    fn theme_change_invalidates_the_root() {
        let old = UiDocument::new(Node::new("root", NodeKind::Form {}));
        let mut new = old.clone();
        new.theme
            .tokens
            .insert("primary".into(), TokenValue::Color("#112233".into()));
        assert_eq!(
            diff(&old, &new),
            vec![DiffOp::Update {
                key: NodeKey("root".into())
            }]
        );
    }

    #[test]
    fn roundtrip_document_to_json() {
        let mut doc = UiDocument::new(Node::new("root", NodeKind::Form {}).with_children(vec![
            Node::new(
                "heading",
                NodeKind::Text {
                    text: "Hello".into(),
                    style: TextStyle::Headline,
                },
            ),
            Node::new(
                "toggle",
                NodeKind::Toggle {
                    label: Some("Notifications".into()),
                    checked: true,
                    on_change: "toggle_notifications".into(),
                },
            ),
        ]));
        doc.theme.color_scheme = ColorScheme::Dark;
        doc.theme
            .tokens
            .insert("primary".into(), TokenValue::Color("#6750A4".into()));
        doc.theme
            .tokens
            .insert("spacing.md".into(), TokenValue::Number(16.0));

        let json = doc.to_json().expect("serialize");
        let parsed = UiDocument::from_json(&json).expect("deserialize");
        assert_eq!(doc.root.key, parsed.root.key);
        assert_eq!(doc.root.children.len(), parsed.root.children.len());
        assert_eq!(doc.theme.color_scheme, parsed.theme.color_scheme);
        assert_eq!(doc.theme.tokens.len(), parsed.theme.tokens.len());
    }

    #[test]
    fn find_node_uses_cached_index() {
        let doc =
            UiDocument::new(
                Node::new("root", NodeKind::Form {}).with_children(vec![Node::new(
                    "nested",
                    NodeKind::Text {
                        text: "found".into(),
                        style: TextStyle::Body,
                    },
                )]),
            );
        let node = doc.find_node(&NodeKey("nested".into()));
        assert!(node.is_some());
        assert_eq!(node.unwrap().key, NodeKey("nested".into()));
    }

    #[test]
    fn find_node_returns_none_for_missing_key() {
        let doc = UiDocument::new(Node::new("root", NodeKind::Form {}));
        assert!(doc.find_node(&NodeKey("missing".into())).is_none());
    }

    #[test]
    fn input_type_defaults_to_text() {
        let node = Node::new(
            "field",
            NodeKind::Input {
                value: "".into(),
                placeholder: None,
                on_change: "changed".into(),
                secure: false,
                input_type: InputType::default(),
                return_key: None,
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert!(!json.as_object().unwrap().contains_key("input_type"));
        let parsed: Node = serde_json::from_value(json).unwrap();
        assert_eq!(
            parsed.kind,
            NodeKind::Input {
                value: "".into(),
                placeholder: None,
                on_change: "changed".into(),
                secure: false,
                input_type: InputType::Text,
                return_key: None,
            }
        );
    }

    #[test]
    fn all_text_styles_serialize_roundtrip() {
        let styles = [
            TextStyle::Display,
            TextStyle::Headline,
            TextStyle::Title,
            TextStyle::Body,
            TextStyle::Caption,
            TextStyle::Footnote,
        ];
        for style in styles {
            let node = Node::new(
                "t",
                NodeKind::Text {
                    text: "x".into(),
                    style,
                },
            );
            let json = serde_json::to_string(&node).unwrap();
            let back: Node = serde_json::from_str(&json).unwrap();
            assert_eq!(back.kind, node.kind, "failed for {style:?}");
        }
    }

    #[test]
    fn dialog_serializes_all_fields() {
        let node = Node::new(
            "alert",
            NodeKind::Dialog {
                title: "Delete?".into(),
                confirm_label: Some("Delete".into()),
                confirm_action: Some("delete_confirm".into()),
                cancel_label: Some("Cancel".into()),
                cancel_action: Some("delete_cancel".into()),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["title"], "Delete?");
        assert_eq!(json["confirm_label"], "Delete");
    }

    #[test]
    fn image_node_with_alt() {
        let node = Node::new(
            "avatar",
            NodeKind::Image {
                src: "https://example.com/photo.png".into(),
                alt: Some("User avatar".into()),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["src"], "https://example.com/photo.png");
        assert_eq!(json["alt"], "User avatar");
    }

    #[test]
    fn toggle_node_is_self_contained() {
        let node = Node::new(
            "switch",
            NodeKind::Toggle {
                label: Some("WiFi".into()),
                checked: false,
                on_change: "wifi_toggle".into(),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["checked"], false);
    }

    #[test]
    fn color_scheme_defaults_to_system() {
        assert_eq!(ColorScheme::default(), ColorScheme::System);
    }

    #[test]
    fn token_value_number_is_f64() {
        let val = TokenValue::Number(0.5);
        let json = serde_json::to_value(&val).unwrap();
        assert_eq!(json.as_f64().unwrap(), 0.5);
    }

    #[test]
    fn navigation_defaults_to_tab_mode() {
        let node = Node::new(
            "nav",
            NodeKind::Navigation {
                active: "home".into(),
                mode: NavigationMode::default(),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert!(!json.as_object().unwrap().contains_key("mode"));
        let parsed: Node = serde_json::from_value(json).unwrap();
        assert!(matches!(
            parsed.kind,
            NodeKind::Navigation {
                mode: NavigationMode::Tab,
                ..
            }
        ));
    }

    #[test]
    fn navigation_stack_mode_serializes() {
        let node = Node::new(
            "nav",
            NodeKind::Navigation {
                active: "detail".into(),
                mode: NavigationMode::Stack,
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["mode"], "stack");
    }

    #[test]
    fn route_safe_area_defaults_to_true() {
        let node = Node::new(
            "page",
            NodeKind::Route {
                title: "Home".into(),
                respect_safe_area: true,
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert!(!json.as_object().unwrap().contains_key("respect_safe_area"));
        let parsed: Node = serde_json::from_value(json).unwrap();
        assert!(matches!(
            parsed.kind,
            NodeKind::Route {
                respect_safe_area: true,
                ..
            }
        ));
    }

    #[test]
    fn route_safe_area_false_serializes() {
        let node = Node::new(
            "page",
            NodeKind::Route {
                title: "Full".into(),
                respect_safe_area: false,
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["respect_safe_area"], false);
    }

    #[test]
    fn slider_serializes_full_bounds() {
        let node = Node::new(
            "vol",
            NodeKind::Slider {
                value: 0.5,
                min: 0.0,
                max: 1.0,
                step: Some(0.1),
                on_change: "vol_changed".into(),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["type"], "slider");
        assert_eq!(json["value"], 0.5);
        assert_eq!(json["step"], 0.1);
    }

    #[test]
    fn picker_with_options_roundtrip() {
        let node = Node::new(
            "lang",
            NodeKind::Picker {
                selected: "en".into(),
                options: vec![
                    PickerOption {
                        label: "English".into(),
                        value: "en".into(),
                    },
                    PickerOption {
                        label: "中文".into(),
                        value: "zh".into(),
                    },
                ],
                on_change: "lang_changed".into(),
            },
        );
        let json = serde_json::to_string(&node).unwrap();
        let back: Node = serde_json::from_str(&json).unwrap();
        assert_eq!(back.kind, node.kind);
    }

    #[test]
    fn date_picker_defaults_to_datetime() {
        let node = Node::new(
            "dt",
            NodeKind::DatePicker {
                value: None,
                mode: DatePickerMode::default(),
                on_change: "dt_changed".into(),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert!(!json.as_object().unwrap().contains_key("mode"));
        let parsed: Node = serde_json::from_value(json).unwrap();
        assert!(matches!(
            parsed.kind,
            NodeKind::DatePicker {
                mode: DatePickerMode::DateTime,
                ..
            }
        ));
    }

    #[test]
    fn document_errors_preserve_source_chain() {
        let err = DocumentError::Json(serde_json::from_str::<serde_json::Value>("{").unwrap_err());
        assert!(err.to_string().contains("JSON"));
    }

    // -- v2.2 component tests -----------------------------------------

    #[test]
    fn checkbox_serializes_correctly() {
        let node = Node::new(
            "agree",
            NodeKind::Checkbox {
                label: Some("I agree".into()),
                checked: true,
                on_change: "agree_changed".into(),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["type"], "checkbox");
        assert_eq!(json["checked"], true);
        assert_eq!(json["label"], "I agree");
    }

    #[test]
    fn divider_is_empty_node() {
        let node = Node::new("sep", NodeKind::Divider {});
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["type"], "divider");
    }

    #[test]
    fn card_is_container() {
        let node = Node::new("card", NodeKind::Card {}).with_children(vec![Node::new(
            "inner",
            NodeKind::Text {
                text: "x".into(),
                style: TextStyle::Body,
            },
        )]);
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["type"], "card");
        assert_eq!(json["children"][0]["type"], "text");
    }

    #[test]
    fn chip_defaults_to_input_variant() {
        let node = Node::new(
            "tag",
            NodeKind::Chip {
                label: "Tag".into(),
                variant: ChipVariant::default(),
                on_dismiss: None,
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert!(!json.as_object().unwrap().contains_key("variant"));
        let parsed: Node = serde_json::from_value(json).unwrap();
        assert!(matches!(
            parsed.kind,
            NodeKind::Chip {
                variant: ChipVariant::Input,
                ..
            }
        ));
    }

    #[test]
    fn chip_with_dismiss_serializes() {
        let node = Node::new(
            "filter",
            NodeKind::Chip {
                label: "Active".into(),
                variant: ChipVariant::Filter,
                on_dismiss: Some("filter_remove".into()),
            },
        );
        let json = serde_json::to_value(&node).unwrap();
        assert_eq!(json["variant"], "filter");
        assert_eq!(json["on_dismiss"], "filter_remove");
    }
}
