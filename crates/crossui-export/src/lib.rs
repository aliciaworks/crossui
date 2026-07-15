//! Experimental, one-way source export from the CrossUI IR.
//!
//! Output is intended as a native project starting point, not a synchronised
//! replacement for the IR runtime.

use crossui_ir::{Axis, ButtonVariant, Node, NodeKind, TextStyle, UiDocument};
use thiserror::Error;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ExportTarget {
    SwiftUi,
    JetpackCompose,
    WinUi3,
}

#[derive(Debug, Error)]
pub enum ExportError {
    #[error("cannot export platform view {name}; implement it in the target project")]
    PlatformView { name: String },
}

pub fn export(document: &UiDocument, target: ExportTarget) -> Result<String, ExportError> {
    let body = match target {
        ExportTarget::SwiftUi => swift_node(&document.root, 2)?,
        ExportTarget::JetpackCompose => compose_node(&document.root, 2)?,
        ExportTarget::WinUi3 => xaml_node(&document.root, 2)?,
    };
    Ok(match target {
        ExportTarget::SwiftUi => format!(
            "import SwiftUI\n\nstruct ExportedView: View {{\n    var body: some View {{\n{body}\n    }}\n}}\n"
        ),
        ExportTarget::JetpackCompose => format!("@Composable\nfun ExportedView() {{\n{body}\n}}\n"),
        ExportTarget::WinUi3 => format!(
            "<Grid xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\">\n{body}\n</Grid>\n"
        ),
    })
}

fn swift_node(node: &Node, depth: usize) -> Result<String, ExportError> {
    let indent = " ".repeat(depth * 4);
    let children = || swift_children(&node.children, depth + 1);
    Ok(match &node.kind {
        NodeKind::Text { text, style } => format!(
            "{indent}Text(\"{}\").font(.{})",
            escape(text),
            if *style == TextStyle::Title {
                "title"
            } else {
                "body"
            }
        ),
        NodeKind::Button { label, variant, .. } => match variant {
            ButtonVariant::Destructive => format!(
                "{indent}Button(\"{}\", role: .destructive) {{\n{indent}    // Dispatch action\n{indent}}}",
                escape(label)
            ),
            _ => format!(
                "{indent}Button(\"{}\") {{\n{indent}    // Dispatch action\n{indent}}}",
                escape(label)
            ),
        },
        NodeKind::Input {
            placeholder,
            secure,
            ..
        } => {
            let placeholder = escape(placeholder.as_deref().unwrap_or("Input"));
            if *secure {
                format!("{indent}SecureField(\"{placeholder}\", text: .constant(\"\"))")
            } else {
                format!("{indent}TextField(\"{placeholder}\", text: .constant(\"\"))")
            }
        }
        NodeKind::Stack { axis, .. } => format!(
            "{indent}{} {{\n{}\n{indent}}}",
            if *axis == Axis::Horizontal {
                "HStack"
            } else {
                "VStack"
            },
            children()?
        ),
        NodeKind::List { .. } | NodeKind::Form {} => {
            format!("{indent}VStack {{\n{}\n{indent}}}", children()?)
        }
        NodeKind::Loading { label } => format!(
            "{indent}ProgressView(\"{}\")",
            escape(label.as_deref().unwrap_or("Loading"))
        ),
        NodeKind::Navigation { .. } | NodeKind::Route { .. } => {
            format!("{indent}NavigationStack {{\n{}\n{indent}}}", children()?)
        }
        NodeKind::PlatformView { name, .. } => {
            return Err(ExportError::PlatformView { name: name.clone() });
        }
    })
}

fn compose_node(node: &Node, depth: usize) -> Result<String, ExportError> {
    let indent = " ".repeat(depth * 4);
    let children = || compose_children(&node.children, depth + 1);
    Ok(match &node.kind {
        NodeKind::Text { text, .. } => format!("{indent}Text(\"{}\")", escape(text)),
        NodeKind::Button { label, .. } => format!(
            "{indent}Button(onClick = {{ /* Dispatch action */ }}) {{ Text(\"{}\") }}",
            escape(label)
        ),
        NodeKind::Input {
            placeholder,
            secure,
            ..
        } => format!(
            "{indent}OutlinedTextField(value = \"\", onValueChange = {{ }}, label = {{ Text(\"{}\") }}, visualTransformation = {})",
            escape(placeholder.as_deref().unwrap_or("Input")),
            if *secure {
                "PasswordVisualTransformation()"
            } else {
                "VisualTransformation.None"
            }
        ),
        NodeKind::Stack { axis, .. } => format!(
            "{indent}{} {{\n{}\n{indent}}}",
            if *axis == Axis::Horizontal {
                "Row"
            } else {
                "Column"
            },
            children()?
        ),
        NodeKind::List { .. } | NodeKind::Form {} => {
            format!("{indent}Column {{\n{}\n{indent}}}", children()?)
        }
        NodeKind::Loading { .. } => format!("{indent}CircularProgressIndicator()"),
        NodeKind::Navigation { .. } | NodeKind::Route { .. } => {
            format!("{indent}Column {{\n{}\n{indent}}}", children()?)
        }
        NodeKind::PlatformView { name, .. } => {
            return Err(ExportError::PlatformView { name: name.clone() });
        }
    })
}

fn xaml_node(node: &Node, depth: usize) -> Result<String, ExportError> {
    let indent = " ".repeat(depth * 4);
    let children = || xaml_children(&node.children, depth + 1);
    Ok(match &node.kind {
        NodeKind::Text { text, .. } => {
            format!("{indent}<TextBlock Text=\"{}\" />", escape_xml(text))
        }
        NodeKind::Button { label, .. } => {
            format!("{indent}<Button Content=\"{}\" />", escape_xml(label))
        }
        NodeKind::Input {
            placeholder,
            secure,
            ..
        } => format!(
            "{indent}<{} PlaceholderText=\"{}\" />",
            if *secure { "PasswordBox" } else { "TextBox" },
            escape_xml(placeholder.as_deref().unwrap_or("Input"))
        ),
        NodeKind::Stack { axis, .. } => format!(
            "{indent}<StackPanel Orientation=\"{}\">\n{}\n{indent}</StackPanel>",
            if *axis == Axis::Horizontal {
                "Horizontal"
            } else {
                "Vertical"
            },
            children()?
        ),
        NodeKind::List { .. }
        | NodeKind::Form {}
        | NodeKind::Navigation { .. }
        | NodeKind::Route { .. } => format!(
            "{indent}<StackPanel>\n{}\n{indent}</StackPanel>",
            children()?
        ),
        NodeKind::Loading { .. } => format!("{indent}<ProgressRing IsActive=\"True\" />"),
        NodeKind::PlatformView { name, .. } => {
            return Err(ExportError::PlatformView { name: name.clone() });
        }
    })
}

fn swift_children(children: &[Node], depth: usize) -> Result<String, ExportError> {
    children
        .iter()
        .map(|child| swift_node(child, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|lines| lines.join("\n"))
}
fn compose_children(children: &[Node], depth: usize) -> Result<String, ExportError> {
    children
        .iter()
        .map(|child| compose_node(child, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|lines| lines.join("\n"))
}
fn xaml_children(children: &[Node], depth: usize) -> Result<String, ExportError> {
    children
        .iter()
        .map(|child| xaml_node(child, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|lines| lines.join("\n"))
}
fn escape(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}
fn escape_xml(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('"', "&quot;")
        .replace('<', "&lt;")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossui_ir::{Node, NodeKind};

    #[test]
    fn exports_each_native_starting_point() {
        let document = UiDocument::new(Node::new(
            "root",
            NodeKind::Text {
                text: "Hello".into(),
                style: TextStyle::Body,
            },
        ));
        assert!(
            export(&document, ExportTarget::SwiftUi)
                .unwrap()
                .contains("Text(\"Hello\")")
        );
        assert!(
            export(&document, ExportTarget::JetpackCompose)
                .unwrap()
                .contains("fun ExportedView")
        );
        assert!(
            export(&document, ExportTarget::WinUi3)
                .unwrap()
                .contains("TextBlock")
        );
    }

    #[test]
    fn rejects_platform_views_in_one_way_export() {
        let document = UiDocument::new(Node::new(
            "map",
            NodeKind::PlatformView {
                platform: crossui_ir::Platform::Ios,
                name: "map".into(),
                payload: serde_json::Value::Null,
            },
        ));
        assert!(matches!(
            export(&document, ExportTarget::SwiftUi),
            Err(ExportError::PlatformView { name }) if name == "map"
        ));
    }
}
