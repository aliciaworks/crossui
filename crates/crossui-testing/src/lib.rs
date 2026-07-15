//! Small host-independent test helpers.

use crossui_ir::{Node, NodeKey, UiDocument};
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
