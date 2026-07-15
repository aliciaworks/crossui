//! Small host-independent test helpers.

use crossui_core::ApplicationUpdate;
use crossui_ir::{DiffOp, Node, NodeKey, UiDocument};
pub fn find_node<'a>(document: &'a UiDocument, key: &str) -> Option<&'a Node> {
    find(&document.root, &NodeKey(key.into()))
}
fn find<'a>(node: &'a Node, key: &NodeKey) -> Option<&'a Node> {
    if &node.key == key {
        return Some(node);
    }
    node.children.iter().find_map(|child| find(child, key))
}
pub fn assert_valid(document: &UiDocument) {
    document.validate().expect("document must be valid");
}

/// Asserts that a state transition can be applied without rebuilding a native
/// tree: exactly one keyed leaf update and no effects.
pub fn assert_leaf_update(update: &ApplicationUpdate, key: &str) {
    assert_eq!(
        update.effects.len(),
        0,
        "leaf updates must not emit effects"
    );
    assert_eq!(
        update.patch,
        vec![DiffOp::Update {
            key: NodeKey(key.into())
        }],
        "expected a single keyed leaf update"
    );
}

/// Asserts that a transition emitted precisely one structured effect.
pub fn assert_effect(update: &ApplicationUpdate, expected: serde_json::Value) {
    assert_eq!(update.effects, vec![expected], "unexpected runtime effects");
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossui_ir::{NodeKind, TextStyle};

    #[test]
    fn leaf_update_helper_accepts_a_single_keyed_update() {
        let update = ApplicationUpdate {
            document: UiDocument::new(Node::new(
                "email",
                NodeKind::Text {
                    text: "ada@example.com".into(),
                    style: TextStyle::Body,
                },
            )),
            patch: vec![DiffOp::Update {
                key: NodeKey("email".into()),
            }],
            effects: vec![],
        };
        assert_leaf_update(&update, "email");
    }
}
