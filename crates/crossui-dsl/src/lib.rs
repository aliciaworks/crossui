//! Typed constructors for the portable CrossUI component catalog.

pub use crossui_ir::{
    Alignment, Axis, ButtonVariant, Node, NodeKind, Platform, SemanticRole, TextStyle,
};

pub fn text(key: impl Into<String>, value: impl Into<String>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Text {
            text: value.into(),
            style: TextStyle::Body,
        },
    )
}
pub fn title(key: impl Into<String>, value: impl Into<String>) -> Node {
    let mut node = Node::new(
        key.into().as_str(),
        NodeKind::Text {
            text: value.into(),
            style: TextStyle::Title,
        },
    );
    node.semantics.role = Some(SemanticRole::Header);
    node
}
pub fn button(key: impl Into<String>, label: impl Into<String>, action: impl Into<String>) -> Node {
    button_with_variant(key, label, action, ButtonVariant::Primary)
}
pub fn button_with_variant(
    key: impl Into<String>,
    label: impl Into<String>,
    action: impl Into<String>,
    variant: ButtonVariant,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Button {
            label: label.into(),
            action: action.into(),
            variant,
        },
    )
}
pub fn secondary_button(
    key: impl Into<String>,
    label: impl Into<String>,
    action: impl Into<String>,
) -> Node {
    button_with_variant(key, label, action, ButtonVariant::Secondary)
}
pub fn destructive_button(
    key: impl Into<String>,
    label: impl Into<String>,
    action: impl Into<String>,
) -> Node {
    button_with_variant(key, label, action, ButtonVariant::Destructive)
}
pub fn input(
    key: impl Into<String>,
    value: impl Into<String>,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Input {
            value: value.into(),
            placeholder: None,
            on_change: on_change.into(),
            secure: false,
        },
    )
}
pub fn input_with_placeholder(
    key: impl Into<String>,
    value: impl Into<String>,
    placeholder: impl Into<String>,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Input {
            value: value.into(),
            placeholder: Some(placeholder.into()),
            on_change: on_change.into(),
            secure: false,
        },
    )
}

pub fn secure_input(
    key: impl Into<String>,
    value: impl Into<String>,
    placeholder: impl Into<String>,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Input {
            value: value.into(),
            placeholder: Some(placeholder.into()),
            on_change: on_change.into(),
            secure: true,
        },
    )
}
pub fn vstack(key: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Stack {
            axis: Axis::Vertical,
            spacing: Some("spacing.md".into()),
            alignment: Alignment::Stretch,
        },
    )
    .with_children(children)
}
pub fn hstack(key: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Stack {
            axis: Axis::Horizontal,
            spacing: Some("spacing.md".into()),
            alignment: Alignment::Center,
        },
    )
    .with_children(children)
}
pub fn list(key: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(key.into().as_str(), NodeKind::List { on_select: None }).with_children(children)
}
pub fn selectable_list(
    key: impl Into<String>,
    on_select: impl Into<String>,
    children: Vec<Node>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::List {
            on_select: Some(on_select.into()),
        },
    )
    .with_children(children)
}
pub fn form(key: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(key.into().as_str(), NodeKind::Form {}).with_children(children)
}
pub fn loading(key: impl Into<String>, label: impl Into<String>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Loading {
            label: Some(label.into()),
        },
    )
}
pub fn navigation(key: impl Into<String>, active: impl Into<String>, routes: Vec<Node>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Navigation {
            active: active.into(),
        },
    )
    .with_children(routes)
}
pub fn route(key: impl Into<String>, title: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Route {
            title: title.into(),
        },
    )
    .with_children(children)
}
/// Declares a native-only extension point. Hosts render it only when the target
/// platform matches; other hosts must report it as unsupported.
pub fn platform_view(
    key: impl Into<String>,
    platform: Platform,
    name: impl Into<String>,
    payload: serde_json::Value,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::PlatformView {
            platform,
            name: name.into(),
            payload,
        },
    )
}

pub trait Accessible: Sized {
    fn accessibility(self, label: impl Into<String>, role: SemanticRole) -> Self;
    fn disabled(self) -> Self;
}
impl Accessible for Node {
    fn accessibility(mut self, label: impl Into<String>, role: SemanticRole) -> Self {
        self.semantics.label = Some(label.into());
        self.semantics.role = Some(role);
        self
    }
    fn disabled(mut self) -> Self {
        self.semantics.enabled = false;
        self
    }
}

#[macro_export]
macro_rules! ui { ($($node:expr),* $(,)?) => { vec![$($node),*] }; }

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dsl_creates_portable_tree() {
        let view = vstack(
            "screen",
            ui![
                title("title", "Hello"),
                button("go", "Continue", "continue")
            ],
        );
        assert_eq!(view.children.len(), 2);
    }
    #[test]
    fn form_and_loading_are_portable_nodes() {
        let view = form("form", ui![loading("loading", "Loading")]);
        assert!(matches!(view.kind, NodeKind::Form {}));
        assert!(matches!(view.children[0].kind, NodeKind::Loading { .. }));
    }
    #[test]
    fn navigation_retains_the_active_route() {
        let view = navigation("nav", "home", ui![route("home", "Home", vec![])]);
        assert!(matches!(view.kind, NodeKind::Navigation { ref active } if active == "home"));
    }
    #[test]
    fn platform_view_is_explicit_in_the_ir() {
        let view = platform_view(
            "map",
            Platform::Ios,
            "map",
            serde_json::json!({"latitude": 1}),
        );
        assert!(matches!(
            view.kind,
            NodeKind::PlatformView {
                platform: Platform::Ios,
                ..
            }
        ));
    }
    #[test]
    fn secure_input_preserves_its_semantics_in_the_ir() {
        let field = secure_input("password", "", "Password", "password_changed");
        assert!(matches!(
            field.kind,
            NodeKind::Input {
                secure: true,
                placeholder: Some(_),
                ..
            }
        ));
    }
    #[test]
    fn button_variants_are_available_without_manual_ir() {
        let button = destructive_button("delete", "Delete", "delete");
        assert!(matches!(
            button.kind,
            NodeKind::Button {
                variant: ButtonVariant::Destructive,
                ..
            }
        ));
    }
    #[test]
    fn titles_are_accessibility_headers() {
        assert!(matches!(
            title("heading", "Welcome").semantics.role,
            Some(SemanticRole::Header)
        ));
    }
}
