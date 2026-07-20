//! Typed constructors for the portable CrossUI component catalog.
//!
//! Every constructor emits a versioned [`crossui_ir::Node`]. Hosts never need
//! to construct IR by hand — use these functions (or the `ui!` macro) instead.

pub use crossui_ir::{
    ActionFrequency, Alignment, Axis, ButtonVariant, ChipVariant, DatePickerMode, Importance,
    InputType, NavigationMode, Node, NodeKind, PickerOption, Platform, ReturnKey, SemanticRole,
    SemanticTraits, TextStyle,
};

// -- Text ---------------------------------------------------------------

pub fn text(key: impl Into<String>, value: impl Into<String>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Text {
            text: value.into(),
            style: TextStyle::Body,
        },
    )
}

pub fn text_with_style(key: impl Into<String>, value: impl Into<String>, style: TextStyle) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Text {
            text: value.into(),
            style,
        },
    )
}

pub fn display(key: impl Into<String>, value: impl Into<String>) -> Node {
    text_with_style(key, value, TextStyle::Display)
}

pub fn headline(key: impl Into<String>, value: impl Into<String>) -> Node {
    text_with_style(key, value, TextStyle::Headline)
}

pub fn title(key: impl Into<String>, value: impl Into<String>) -> Node {
    let mut node = text_with_style(key, value, TextStyle::Title);
    node.semantics.role = Some(SemanticRole::Header);
    node
}

pub fn caption(key: impl Into<String>, value: impl Into<String>) -> Node {
    text_with_style(key, value, TextStyle::Caption)
}

pub fn footnote(key: impl Into<String>, value: impl Into<String>) -> Node {
    text_with_style(key, value, TextStyle::Footnote)
}

// -- Buttons ------------------------------------------------------------

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

// -- Text input ---------------------------------------------------------

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
            input_type: InputType::default(),
            return_key: None,
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
            input_type: InputType::default(),
            return_key: None,
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
            input_type: InputType::default(),
            return_key: None,
        },
    )
}

pub fn email_input(
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
            input_type: InputType::Email,
            return_key: Some(ReturnKey::Go),
        },
    )
}

pub fn number_input(
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
            input_type: InputType::Number,
            return_key: Some(ReturnKey::Done),
        },
    )
}

pub fn search_input(
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
            input_type: InputType::Text,
            return_key: Some(ReturnKey::Search),
        },
    )
}

pub trait InputExt {
    fn input_type(self, ty: InputType) -> Self;
    fn return_key(self, key: ReturnKey) -> Self;
}

impl InputExt for Node {
    fn input_type(mut self, ty: InputType) -> Self {
        if let NodeKind::Input {
            ref mut input_type, ..
        } = self.kind
        {
            *input_type = ty;
        }
        self
    }

    fn return_key(mut self, key: ReturnKey) -> Self {
        if let NodeKind::Input {
            ref mut return_key, ..
        } = self.kind
        {
            *return_key = Some(key);
        }
        self
    }
}

// -- Layout containers --------------------------------------------------

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

/// Tab-style navigation: routes are siblings; the active route is rendered
/// inside a platform-native tab bar / bottom navigation.
pub fn tab_navigation(
    key: impl Into<String>,
    active: impl Into<String>,
    routes: Vec<Node>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Navigation {
            active: active.into(),
            mode: NavigationMode::Tab,
        },
    )
    .with_children(routes)
}

/// Stack navigation: routes form a LIFO stack with platform-native back
/// behaviour. Only the active route is visible.
pub fn stack_navigation(
    key: impl Into<String>,
    active: impl Into<String>,
    routes: Vec<Node>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Navigation {
            active: active.into(),
            mode: NavigationMode::Stack,
        },
    )
    .with_children(routes)
}

/// Legacy convenience – defaults to tab mode for backwards compatibility.
pub fn navigation(key: impl Into<String>, active: impl Into<String>, routes: Vec<Node>) -> Node {
    tab_navigation(key, active, routes)
}

/// A route that respects safe-area insets (default).
pub fn route(key: impl Into<String>, title: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Route {
            title: title.into(),
            respect_safe_area: true,
        },
    )
    .with_children(children)
}

/// A route drawn edge-to-edge, ignoring safe-area insets (splash screens,
/// full-bleed media).
pub fn fullscreen_route(
    key: impl Into<String>,
    title: impl Into<String>,
    children: Vec<Node>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Route {
            title: title.into(),
            respect_safe_area: false,
        },
    )
    .with_children(children)
}

// -- v2 components -----------------------------------------------------

pub fn toggle(
    key: impl Into<String>,
    label: Option<impl Into<String>>,
    checked: bool,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Toggle {
            label: label.map(|l| l.into()),
            checked,
            on_change: on_change.into(),
        },
    )
}

pub fn image(key: impl Into<String>, src: impl Into<String>, alt: impl Into<String>) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Image {
            src: src.into(),
            alt: Some(alt.into()),
        },
    )
}

pub fn dialog(
    key: impl Into<String>,
    title: impl Into<String>,
    confirm_label: Option<impl Into<String>>,
    confirm_action: Option<impl Into<String>>,
    cancel_label: Option<impl Into<String>>,
    cancel_action: Option<impl Into<String>>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Dialog {
            title: title.into(),
            confirm_label: confirm_label.map(|l| l.into()),
            confirm_action: confirm_action.map(|a| a.into()),
            cancel_label: cancel_label.map(|l| l.into()),
            cancel_action: cancel_action.map(|a| a.into()),
        },
    )
}

// -- v2.1 components ----------------------------------------------------

/// A slider for continuous or discrete values.
pub fn slider(
    key: impl Into<String>,
    value: f64,
    min: f64,
    max: f64,
    step: Option<f64>,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Slider {
            value,
            min,
            max,
            step,
            on_change: on_change.into(),
        },
    )
}

/// A single-selection picker / dropdown.
pub fn picker(
    key: impl Into<String>,
    selected: impl Into<String>,
    options: Vec<PickerOption>,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Picker {
            selected: selected.into(),
            options,
            on_change: on_change.into(),
        },
    )
}

/// Convenience: build `PickerOption` values.
pub fn picker_option(label: impl Into<String>, value: impl Into<String>) -> PickerOption {
    PickerOption {
        label: label.into(),
        value: value.into(),
    }
}

/// A date / time / datetime picker. `value` is ISO 8601 when set; `None`
/// means no date selected yet.
pub fn date_picker(
    key: impl Into<String>,
    value: Option<impl Into<String>>,
    mode: DatePickerMode,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::DatePicker {
            value: value.map(|v| v.into()),
            mode,
            on_change: on_change.into(),
        },
    )
}

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

// -- v2.2 components ----------------------------------------------------

/// A checkbox for boolean selection.
pub fn checkbox(
    key: impl Into<String>,
    label: Option<impl Into<String>>,
    checked: bool,
    on_change: impl Into<String>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Checkbox {
            label: label.map(|l| l.into()),
            checked,
            on_change: on_change.into(),
        },
    )
}

/// A horizontal rule / separator.
pub fn divider(key: impl Into<String>) -> Node {
    Node::new(key.into().as_str(), NodeKind::Divider {})
}

/// An elevated card container.
pub fn card(key: impl Into<String>, children: Vec<Node>) -> Node {
    Node::new(key.into().as_str(), NodeKind::Card {}).with_children(children)
}

/// A compact chip / tag.
pub fn chip(
    key: impl Into<String>,
    label: impl Into<String>,
    variant: ChipVariant,
    on_dismiss: Option<impl Into<String>>,
) -> Node {
    Node::new(
        key.into().as_str(),
        NodeKind::Chip {
            label: label.into(),
            variant,
            on_dismiss: on_dismiss.map(|a| a.into()),
        },
    )
}

/// Convenience: input chip.
pub fn input_chip(key: impl Into<String>, label: impl Into<String>) -> Node {
    chip(key, label, ChipVariant::Input, None::<&str>)
}

/// Convenience: filter chip with optional dismiss.
pub fn filter_chip(
    key: impl Into<String>,
    label: impl Into<String>,
    on_dismiss: impl Into<String>,
) -> Node {
    chip(key, label, ChipVariant::Filter, Some(on_dismiss))
}

// -- Accessibility -------------------------------------------------------

pub trait Accessible: Sized {
    fn accessibility(self, label: impl Into<String>, role: SemanticRole) -> Self;
    fn disabled(self) -> Self;
    fn irreversible(self) -> Self;
    fn frequent(self) -> Self;
    fn critical(self) -> Self;
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

    fn irreversible(mut self) -> Self {
        self.semantics.traits.irreversible = true;
        self
    }

    fn frequent(mut self) -> Self {
        self.semantics.traits.frequency = ActionFrequency::Frequent;
        self
    }

    fn critical(mut self) -> Self {
        self.semantics.traits.importance = Importance::Critical;
        self
    }
}

// -- Macro ---------------------------------------------------------------

#[macro_export]
macro_rules! ui {
    ($($node:expr),* $(,)?) => { vec![$($node),*] };
}

// -- Tests ---------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dsl_creates_portable_tree() {
        let view = vstack(
            "screen",
            ui![
                title("heading", "Hello"),
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
        assert!(matches!(
            view.kind,
            NodeKind::Navigation { ref active, .. } if active == "home"
        ));
    }

    #[test]
    fn tab_navigation_defaults_to_tab_mode() {
        let view = tab_navigation("nav", "home", ui![route("home", "Home", vec![])]);
        assert!(matches!(
            view.kind,
            NodeKind::Navigation {
                mode: NavigationMode::Tab,
                ..
            }
        ));
    }

    #[test]
    fn stack_navigation_explicitly_sets_stack_mode() {
        let view = stack_navigation("nav", "detail", ui![route("detail", "Detail", vec![])]);
        assert!(matches!(
            view.kind,
            NodeKind::Navigation {
                mode: NavigationMode::Stack,
                ..
            }
        ));
    }

    #[test]
    fn fullscreen_route_disables_safe_area() {
        let view = fullscreen_route("splash", "Splash", vec![]);
        assert!(matches!(
            view.kind,
            NodeKind::Route {
                respect_safe_area: false,
                ..
            }
        ));
    }

    #[test]
    fn slider_defaults_step_to_none() {
        let node = slider("vol", 0.5, 0.0, 1.0, None, "vol_changed");
        assert!(matches!(node.kind, NodeKind::Slider { step: None, .. }));
    }

    #[test]
    fn picker_builder_works() {
        let options = vec![picker_option("Red", "red"), picker_option("Blue", "blue")];
        let node = picker("color", "red", options, "color_changed");
        assert!(matches!(node.kind, NodeKind::Picker { .. }));
    }

    #[test]
    fn date_picker_accepts_optional_value() {
        let node = date_picker(
            "start",
            Some("2026-01-01"),
            DatePickerMode::Date,
            "start_changed",
        );
        assert!(matches!(
            node.kind,
            NodeKind::DatePicker {
                mode: DatePickerMode::Date,
                ..
            }
        ));
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

    #[test]
    fn text_style_variants() {
        assert!(matches!(
            display("d", "Large").kind,
            NodeKind::Text {
                style: TextStyle::Display,
                ..
            }
        ));
        assert!(matches!(
            headline("h", "Head").kind,
            NodeKind::Text {
                style: TextStyle::Headline,
                ..
            }
        ));
        assert!(matches!(
            caption("c", "cap").kind,
            NodeKind::Text {
                style: TextStyle::Caption,
                ..
            }
        ));
        assert!(matches!(
            footnote("f", "fn").kind,
            NodeKind::Text {
                style: TextStyle::Footnote,
                ..
            }
        ));
    }

    #[test]
    fn email_input_has_keyboard_semantics() {
        let field = email_input("email", "", "you@example.com", "email_changed");
        assert!(matches!(
            field.kind,
            NodeKind::Input {
                input_type: InputType::Email,
                return_key: Some(ReturnKey::Go),
                ..
            }
        ));
    }

    #[test]
    fn input_ext_builder_overrides_defaults() {
        let field = input("q", "", "search_changed")
            .input_type(InputType::Text)
            .return_key(ReturnKey::Search);
        assert!(matches!(
            field.kind,
            NodeKind::Input {
                return_key: Some(ReturnKey::Search),
                ..
            }
        ));
    }

    #[test]
    fn toggle_emits_correct_node_kind() {
        let node = toggle("wifi", Some("WiFi"), true, "wifi_toggle");
        assert!(matches!(node.kind, NodeKind::Toggle { checked: true, .. }));
    }

    #[test]
    fn image_node_with_alt() {
        let node = image("avatar", "https://example.com/photo.png", "User avatar");
        assert!(matches!(node.kind, NodeKind::Image { alt: Some(_), .. }));
    }

    #[test]
    fn dialog_node_with_all_actions() {
        let node = dialog(
            "delete_dialog",
            "Delete item?",
            Some("Delete"),
            Some("delete_confirm"),
            Some("Cancel"),
            Some("delete_cancel"),
        );
        assert!(matches!(node.kind, NodeKind::Dialog { .. }));
    }

    #[test]
    fn dialog_node_minimal() {
        let node: Node = dialog(
            "info",
            "Saved",
            None::<&str>,
            None::<&str>,
            None::<&str>,
            None::<&str>,
        );
        assert!(matches!(
            node.kind,
            NodeKind::Dialog {
                confirm_label: None,
                confirm_action: None,
                cancel_label: None,
                cancel_action: None,
                ..
            }
        ));
    }

    // -- v2.2 tests -----------------------------------------------------

    #[test]
    fn checkbox_with_label() {
        let node = checkbox("agree", Some("I agree"), true, "agree_changed");
        assert!(matches!(
            node.kind,
            NodeKind::Checkbox { checked: true, .. }
        ));
    }

    #[test]
    fn divider_is_self_contained() {
        let node = divider("sep");
        assert!(matches!(node.kind, NodeKind::Divider {}));
    }

    #[test]
    fn card_wraps_children() {
        let node = card("c", vec![text("inner", "Hello")]);
        assert!(matches!(node.kind, NodeKind::Card {}));
        assert_eq!(node.children.len(), 1);
    }

    #[test]
    fn input_chip_is_default_variant() {
        let node = input_chip("t", "Tag");
        assert!(matches!(
            node.kind,
            NodeKind::Chip {
                variant: ChipVariant::Input,
                ..
            }
        ));
    }

    #[test]
    fn filter_chip_has_dismiss_action() {
        let node = filter_chip("f", "Active", "f_remove");
        assert!(matches!(
            node.kind,
            NodeKind::Chip {
                variant: ChipVariant::Filter,
                on_dismiss: Some(_),
                ..
            }
        ));
    }
}
