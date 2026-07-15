//! Versioned, portable UI document shared by Rust and native hosts.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use thiserror::Error;

pub const IR_VERSION: u32 = 1;

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct UiDocument {
    pub version: u32,
    pub root: Node,
    #[serde(default)]
    pub theme: Theme,
}

impl UiDocument {
    pub fn new(root: Node) -> Self {
        Self {
            version: IR_VERSION,
            root,
            theme: Theme::default(),
        }
    }
    pub fn validate(&self) -> Result<(), ValidationError> {
        if self.version != IR_VERSION {
            return Err(ValidationError::UnsupportedVersion(self.version));
        }
        let mut keys = BTreeSet::new();
        self.root.validate(&mut keys)
    }
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string_pretty(self)
    }
    pub fn from_json(json: &str) -> Result<Self, DocumentError> {
        let document: Self = serde_json::from_str(json)?;
        document.validate()?;
        Ok(document)
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct Theme {
    #[serde(default)]
    pub tokens: BTreeMap<String, TokenValue>,
    #[serde(default)]
    pub android: AndroidTheme,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(untagged)]
pub enum TokenValue {
    Color(String),
    Number(i32),
    Text(String),
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct AndroidTheme {
    #[serde(default)]
    pub material3_expressive: bool,
    #[serde(default)]
    pub dynamic_color: bool,
}

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(transparent)]
pub struct NodeKey(pub String);
impl From<&str> for NodeKey {
    fn from(value: &str) -> Self {
        Self(value.into())
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
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

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
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
        placeholder: Option<String>,
        on_change: String,
        secure: bool,
    },
    Stack {
        axis: Axis,
        spacing: Option<String>,
        alignment: Alignment,
    },
    List {
        on_select: Option<String>,
    },
    Form {},
    Loading {
        label: Option<String>,
    },
    Navigation {
        active: String,
    },
    Route {
        title: String,
    },
    PlatformView {
        platform: Platform,
        name: String,
        payload: serde_json::Value,
    },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum TextStyle {
    #[default]
    Body,
    Title,
    Caption,
}
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

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Semantics {
    #[serde(default)]
    pub label: Option<String>,
    #[serde(default)]
    pub hint: Option<String>,
    #[serde(default)]
    pub role: Option<SemanticRole>,
    #[serde(default = "enabled_by_default")]
    pub enabled: bool,
}
fn enabled_by_default() -> bool {
    true
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
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum DiffOp {
    Insert { key: NodeKey },
    Remove { key: NodeKey },
    Update { key: NodeKey },
}

pub fn diff(previous: &UiDocument, next: &UiDocument) -> Vec<DiffOp> {
    let mut old = BTreeMap::new();
    let mut new = BTreeMap::new();
    flatten(&previous.root, &mut old);
    flatten(&next.root, &mut new);
    let mut ops = Vec::new();
    for (key, before) in &old {
        match new.get(key) {
            None => ops.push(DiffOp::Remove { key: key.clone() }),
            Some(after) if *before != *after => ops.push(DiffOp::Update { key: key.clone() }),
            _ => {}
        }
    }
    for key in new.keys() {
        if !old.contains_key(key) {
            ops.push(DiffOp::Insert { key: key.clone() });
        }
    }
    ops
}
fn flatten<'a>(node: &'a Node, nodes: &mut BTreeMap<NodeKey, &'a Node>) {
    nodes.insert(node.key.clone(), node);
    for child in &node.children {
        flatten(child, nodes);
    }
}

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
    #[error(transparent)]
    Json(#[from] serde_json::Error),
    #[error(transparent)]
    Validation(#[from] ValidationError),
}

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
}
