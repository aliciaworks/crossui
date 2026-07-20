//! Experimental, one-way source export from the CrossUI IR.

use crossui_ir::{
    Axis, ButtonVariant, DatePickerMode, InputType, NavigationMode, Node, NodeKind, TextStyle,
    UiDocument,
};
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
        NodeKind::Text { text, style } => {
            let font = match style {
                TextStyle::Display => ".largeTitle",
                TextStyle::Headline => ".title",
                TextStyle::Title => ".title2",
                TextStyle::Caption => ".caption",
                TextStyle::Footnote => ".footnote",
                _ => ".body",
            };
            format!("{indent}Text(\"{}\").font({font})", escape(text))
        }
        NodeKind::Button { label, variant, .. } => match variant {
            ButtonVariant::Destructive => format!(
                "{indent}Button(\"{}\", role: .destructive) {{\n{indent}    // Dispatch\n{indent}}}",
                escape(label)
            ),
            ButtonVariant::Secondary => format!(
                "{indent}Button(\"{}\") {{ // Secondary\n{indent}    // Dispatch\n{indent}}}",
                escape(label)
            ),
            _ => format!(
                "{indent}Button(\"{}\") {{\n{indent}    // Dispatch\n{indent}}}",
                escape(label)
            ),
        },
        NodeKind::Input {
            placeholder,
            secure,
            input_type,
            ..
        } => {
            let ph = escape(placeholder.as_deref().unwrap_or("Input"));
            let base = if *secure {
                format!("{indent}SecureField(\"{ph}\", text: .constant(\"\"))")
            } else {
                format!("{indent}TextField(\"{ph}\", text: .constant(\"\"))")
            };
            match input_type {
                InputType::Email => format!(
                    "{base}\n{indent}    .keyboardType(.emailAddress).textContentType(.emailAddress).autocapitalization(.none)"
                ),
                InputType::Number => format!("{base}\n{indent}    .keyboardType(.decimalPad)"),
                InputType::Phone => format!("{base}\n{indent}    .keyboardType(.phonePad)"),
                InputType::Url => {
                    format!("{base}\n{indent}    .keyboardType(.URL).autocapitalization(.none)")
                }
                _ => base,
            }
        }
        NodeKind::Toggle { label, checked, .. } => {
            let lbl = label
                .as_ref()
                .map(|l| format!("\"{}\"", escape(l)))
                .unwrap_or_default();
            format!("{indent}Toggle({lbl}, isOn: .constant({checked}))\n{indent}    // Dispatch")
        }
        NodeKind::Image { src, alt } => format!(
            "{indent}AsyncImage(url: URL(string: \"{}\"))\n{indent}.accessibilityLabel(\"{}\")",
            escape(src),
            escape(alt.as_deref().unwrap_or(""))
        ),
        NodeKind::Slider {
            value,
            min,
            max,
            step,
            ..
        } => {
            let step_arg = step.map(|s| format!(", step: {s}")).unwrap_or_default();
            format!(
                "{indent}Slider(value: .constant({value}), in: {min}...{max}{step_arg})\n{indent}    // Dispatch"
            )
        }
        NodeKind::Picker {
            selected, options, ..
        } => format!(
            "{indent}Picker(\"\", selection: .constant(\"{}\")) {{\n{}\n{indent}    // Dispatch\n{indent}}}",
            escape(selected),
            options
                .iter()
                .map(|o| format!(
                    "{indent}    Text(\"{}\").tag(\"{}\")",
                    escape(&o.label),
                    escape(&o.value)
                ))
                .collect::<Vec<_>>()
                .join("\n")
        ),
        NodeKind::DatePicker { value, mode, .. } => {
            let v = value.as_deref().unwrap_or("");
            let m = match mode {
                DatePickerMode::Date => ".datePickerStyle(.graphical)",
                DatePickerMode::Time => ".datePickerStyle(.compact)",
                DatePickerMode::DateTime => "",
            };
            format!("{indent}DatePicker(\"\", selection: .constant({v})){m}")
        }
        NodeKind::Checkbox { label, checked, .. } => {
            let lbl = label.as_deref().unwrap_or("");
            format!("{indent}Toggle(\"{lbl}\", isOn: .constant({checked})) // Checkbox")
        }
        NodeKind::Divider {} => format!("{indent}Divider()"),
        NodeKind::Card {} => format!("{indent}Card {{\n{}\n{indent}}}", children()?),
        NodeKind::Chip { label, .. } => format!("{indent}// Chip(\"{}\")", escape(label)),
        NodeKind::Dialog {
            title,
            confirm_label,
            cancel_label,
            ..
        } => {
            let confirm = confirm_label
                .as_ref()
                .map(|l| format!("\"{}\"", escape(l)))
                .unwrap_or("nil".into());
            let cancel = cancel_label
                .as_ref()
                .map(|l| format!("\"{}\"", escape(l)))
                .unwrap_or("nil".into());
            format!(
                "{indent}// Dialog \"{}\" – confirm {confirm}, cancel {cancel}",
                escape(title)
            )
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
        NodeKind::Navigation { mode, .. } => {
            let c = match mode {
                NavigationMode::Tab => "TabView",
                NavigationMode::Stack => "NavigationStack",
            };
            format!("{indent}{c} {{\n{}\n{indent}}}", children()?)
        }
        NodeKind::Route {
            respect_safe_area, ..
        } => {
            let m = if !respect_safe_area {
                "\n    .ignoresSafeArea()"
            } else {
                ""
            };
            format!(
                "{indent}NavigationStack {{\n{indent}    ScrollView {{\n{}\n{indent}    }}{m}\n{indent}}}",
                children()?
            )
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
        NodeKind::Text { text, style } => {
            let fs = match style {
                TextStyle::Display => "36.sp",
                TextStyle::Headline => "28.sp",
                TextStyle::Title => "24.sp",
                TextStyle::Caption => "12.sp",
                TextStyle::Footnote => "11.sp",
                _ => "16.sp",
            };
            format!("{indent}Text(\"{}\", fontSize = {fs})", escape(text))
        }
        NodeKind::Button { label, variant, .. } => match variant {
            ButtonVariant::Destructive => format!(
                "{indent}Button(onClick = {{ /* Dispatch */ }}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {{ Text(\"{}\") }}",
                escape(label)
            ),
            ButtonVariant::Secondary => format!(
                "{indent}OutlinedButton(onClick = {{ /* Dispatch */ }}) {{ Text(\"{}\") }}",
                escape(label)
            ),
            _ => format!(
                "{indent}Button(onClick = {{ /* Dispatch */ }}) {{ Text(\"{}\") }}",
                escape(label)
            ),
        },
        NodeKind::Input {
            placeholder,
            secure,
            input_type,
            ..
        } => {
            let kb = match input_type {
                InputType::Email => "KeyboardType.Email",
                InputType::Number => "KeyboardType.Number",
                InputType::Phone => "KeyboardType.Phone",
                InputType::Url => "KeyboardType.Uri",
                InputType::Password => "KeyboardType.Password",
                _ => "KeyboardType.Text",
            };
            let vt = if *secure {
                "PasswordVisualTransformation()"
            } else {
                "VisualTransformation.None"
            };
            format!(
                "{indent}OutlinedTextField(value = \"\", onValueChange = {{ }}, label = {{ Text(\"{}\") }}, visualTransformation = {}, keyboardOptions = KeyboardOptions(keyboardType = {}))",
                escape(placeholder.as_deref().unwrap_or("Input")),
                vt,
                kb
            )
        }
        NodeKind::Toggle { label, checked, .. } => {
            let lbl = label
                .as_ref()
                .map(|l| format!("  Text(\"{}\")", escape(l)))
                .unwrap_or_default();
            format!(
                "{indent}Row(verticalAlignment = Alignment.CenterVertically) {{\n{indent}    Switch(checked = {checked}, onCheckedChange = {{ /* Dispatch */ }})\n{indent}{lbl}\n{indent}}}"
            )
        }
        NodeKind::Image { src, alt } => format!(
            "{indent}AsyncImage(model = \"{}\", contentDescription = \"{}\")",
            escape(src),
            escape(alt.as_deref().unwrap_or(""))
        ),
        NodeKind::Slider {
            value, min, max, ..
        } => format!(
            "{indent}Slider(value = {value}f, onValueChange = {{ /* Dispatch */ }}, valueRange = {min}f..{max}f)"
        ),
        NodeKind::Picker {
            selected, options, ..
        } => format!(
            "{indent}var selected = remember {{ mutableStateOf(\"{}\") }}\n{indent}DropdownMenu(selected = selected.value, onSelect = {{ /* Dispatch */ }}) {{\n{}\n{indent}}}",
            escape(selected),
            options
                .iter()
                .map(|o| format!(
                    "{indent}    DropdownMenuItem(\"{}\", value = \"{}\")",
                    escape(&o.label),
                    escape(&o.value)
                ))
                .collect::<Vec<_>>()
                .join("\n")
        ),
        NodeKind::DatePicker { value, mode, .. } => {
            let v = value.as_deref().unwrap_or("2026-01-01");
            let p = match mode {
                DatePickerMode::Date => "DatePickerDialog",
                DatePickerMode::Time => "TimePickerDialog",
                DatePickerMode::DateTime => "DatePickerDialog",
            };
            format!("{indent}// {p}(initialDate = \"{v}\", onDateChange = {{ /* Dispatch */ }})")
        }
        NodeKind::Checkbox { label, checked, .. } => {
            let lbl = label
                .as_ref()
                .map(|l| format!("  Text(\"{}\")", escape(l)))
                .unwrap_or_default();
            format!(
                "{indent}Row(verticalAlignment = Alignment.CenterVertically) {{\n{indent}    Checkbox(checked = {checked}, onCheckedChange = {{ /* Dispatch */ }})\n{indent}{lbl}\n{indent}}}"
            )
        }
        NodeKind::Divider {} => format!("{indent}HorizontalDivider()"),
        NodeKind::Card {} => format!("{indent}Card {{\n{}\n{indent}}}", children()?),
        NodeKind::Chip {
            label, on_dismiss, ..
        } => {
            if on_dismiss.is_some() {
                format!(
                    "{indent}InputChip(selected = false, onClick = {{ }}, label = {{ Text(\"{}\") }}, trailingIcon = {{ Icon(Icons.Default.Close, \"Dismiss\") }})",
                    escape(label)
                )
            } else {
                format!(
                    "{indent}SuggestionChip(onClick = {{ }}, label = {{ Text(\"{}\") }})",
                    escape(label)
                )
            }
        }
        NodeKind::Dialog {
            title,
            confirm_label,
            cancel_label,
            ..
        } => {
            let confirm = confirm_label
                .as_ref()
                .map(|l| escape(l))
                .unwrap_or_default();
            let cancel = cancel_label.as_ref().map(|l| escape(l)).unwrap_or_default();
            format!(
                "{indent}// AlertDialog title=\"{}\" confirm=\"{confirm}\" cancel=\"{cancel}\"",
                escape(title)
            )
        }
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
        NodeKind::Text { text, style } => {
            let s = match style {
                TextStyle::Display => "HeaderTextBlockStyle",
                TextStyle::Headline => "SubheaderTextBlockStyle",
                TextStyle::Title => "TitleTextBlockStyle",
                TextStyle::Caption | TextStyle::Footnote => "CaptionTextBlockStyle",
                _ => "BodyTextBlockStyle",
            };
            format!(
                "{indent}<TextBlock Text=\"{}\" Style=\"{{StaticResource {s}}}\" />",
                escape_xml(text)
            )
        }
        NodeKind::Button { label, variant, .. } => match variant {
            ButtonVariant::Destructive => format!(
                "{indent}<Button Content=\"{}\" Background=\"IndianRed\" />",
                escape_xml(label)
            ),
            _ => format!("{indent}<Button Content=\"{}\" />", escape_xml(label)),
        },
        NodeKind::Input {
            placeholder,
            secure,
            input_type,
            ..
        } => {
            let ph = escape_xml(placeholder.as_deref().unwrap_or("Input"));
            if *secure {
                format!("{indent}<PasswordBox PlaceholderText=\"{ph}\" />")
            } else {
                let scope = match input_type {
                    InputType::Email => r#" InputScope="EmailSmtpAddress""#,
                    InputType::Number => r#" InputScope="Number""#,
                    InputType::Phone => r#" InputScope="TelephoneNumber""#,
                    InputType::Url => r#" InputScope="Url""#,
                    _ => "",
                };
                format!("{indent}<TextBox PlaceholderText=\"{ph}\"{scope} />")
            }
        }
        NodeKind::Toggle { label, checked, .. } => {
            if let Some(l) = label {
                format!(
                    "{indent}<StackPanel Orientation=\"Horizontal\" Spacing=\"12\">\n{indent}    <ToggleSwitch IsOn=\"{checked}\" />\n{indent}    <TextBlock Text=\"{}\" />\n{indent}</StackPanel>",
                    escape_xml(l)
                )
            } else {
                format!("{indent}<ToggleSwitch IsOn=\"{checked}\" />")
            }
        }
        NodeKind::Image { src, alt } => format!(
            "{indent}<Image Source=\"{}\" AutomationProperties.Name=\"{}\" />",
            escape_xml(src),
            escape_xml(alt.as_deref().unwrap_or(""))
        ),
        NodeKind::Slider {
            value,
            min,
            max,
            step,
            ..
        } => {
            let st = step
                .map(|s| format!(" StepFrequency=\"{s}\""))
                .unwrap_or_default();
            format!("{indent}<Slider Value=\"{value}\" Minimum=\"{min}\" Maximum=\"{max}\"{st} />")
        }
        NodeKind::Picker { options, .. } => format!(
            "{indent}<ComboBox>\n{}\n{indent}</ComboBox>",
            options
                .iter()
                .map(|o| format!(
                    "{indent}    <ComboBoxItem Content=\"{}\" />",
                    escape_xml(&o.label)
                ))
                .collect::<Vec<_>>()
                .join("\n")
        ),
        NodeKind::DatePicker { value, mode, .. } => {
            let v = value.as_deref().unwrap_or("");
            match mode {
                DatePickerMode::Date => format!("{indent}<CalendarDatePicker Date=\"{v}\" />"),
                DatePickerMode::Time => format!("{indent}<TimePicker Time=\"{v}\" />"),
                DatePickerMode::DateTime => format!("{indent}<CalendarDatePicker Date=\"{v}\" />"),
            }
        }
        NodeKind::Checkbox { label, checked, .. } => {
            if let Some(l) = label {
                format!(
                    "{indent}<CheckBox Content=\"{}\" IsChecked=\"{checked}\" />",
                    escape_xml(l)
                )
            } else {
                format!("{indent}<CheckBox IsChecked=\"{checked}\" />")
            }
        }
        NodeKind::Divider {} => format!(
            "{indent}<Border Height=\"1\" Background=\"{{ThemeResource SystemControlForegroundBaseLowBrush}}\" />"
        ),
        NodeKind::Card {} => format!(
            "{indent}<Border CornerRadius=\"8\" Padding=\"16\" Background=\"{{ThemeResource CardBackgroundFillColorDefaultBrush}}\">\n{}\n{indent}</Border>",
            children()?
        ),
        NodeKind::Chip {
            label, on_dismiss, ..
        } => {
            if on_dismiss.is_some() {
                format!(
                    "{indent}<StackPanel Orientation=\"Horizontal\" Spacing=\"4\">\n{indent}    <Border CornerRadius=\"12\" Background=\"{{ThemeResource AccentFillColorDefaultBrush}}\" Padding=\"8,4\">\n{indent}        <TextBlock Text=\"{}\" />\n{indent}    </Border>\n{indent}    <Button Content=\"✕\" />\n{indent}</StackPanel>",
                    escape_xml(label)
                )
            } else {
                format!(
                    "{indent}<Border CornerRadius=\"12\" Background=\"{{ThemeResource AccentFillColorDefaultBrush}}\" Padding=\"8,4\">\n{indent}    <TextBlock Text=\"{}\" />\n{indent}</Border>",
                    escape_xml(label)
                )
            }
        }
        NodeKind::Dialog {
            title,
            confirm_label,
            cancel_label,
            ..
        } => {
            let confirm = confirm_label
                .as_ref()
                .map(|l| escape_xml(l))
                .unwrap_or_default();
            let cancel = cancel_label
                .as_ref()
                .map(|l| escape_xml(l))
                .unwrap_or_default();
            format!(
                "{indent}<!-- ContentDialog title=\"{}\" primary=\"{confirm}\" close=\"{cancel}\" -->",
                escape_xml(title)
            )
        }
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
        .map(|c| swift_node(c, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|l| l.join("\n"))
}
fn compose_children(children: &[Node], depth: usize) -> Result<String, ExportError> {
    children
        .iter()
        .map(|c| compose_node(c, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|l| l.join("\n"))
}
fn xaml_children(children: &[Node], depth: usize) -> Result<String, ExportError> {
    children
        .iter()
        .map(|c| xaml_node(c, depth))
        .collect::<Result<Vec<_>, _>>()
        .map(|l| l.join("\n"))
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
    use crossui_ir::{ChipVariant, Node, NodeKind, PickerOption, Platform};

    #[test]
    fn exports_text() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "r",
                    NodeKind::Text {
                        text: "Hi".into(),
                        style: TextStyle::Body
                    }
                )),
                ExportTarget::SwiftUi
            )
            .unwrap()
            .contains("Text")
        );
    }
    #[test]
    fn exports_slider() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "s",
                    NodeKind::Slider {
                        value: 0.5,
                        min: 0.0,
                        max: 1.0,
                        step: Some(0.1),
                        on_change: "v".into()
                    }
                )),
                ExportTarget::SwiftUi
            )
            .unwrap()
            .contains("Slider")
        );
    }
    #[test]
    fn exports_picker() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "p",
                    NodeKind::Picker {
                        selected: "en".into(),
                        options: vec![PickerOption {
                            label: "EN".into(),
                            value: "en".into()
                        }],
                        on_change: "l".into()
                    }
                )),
                ExportTarget::JetpackCompose
            )
            .unwrap()
            .contains("DropdownMenu")
        );
    }
    #[test]
    fn exports_date_picker() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "d",
                    NodeKind::DatePicker {
                        value: Some("2026-01-01".into()),
                        mode: DatePickerMode::Date,
                        on_change: "dt".into()
                    }
                )),
                ExportTarget::WinUi3
            )
            .unwrap()
            .contains("CalendarDatePicker")
        );
    }
    #[test]
    fn exports_nav_modes() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "n",
                    NodeKind::Navigation {
                        active: "h".into(),
                        mode: NavigationMode::Tab
                    }
                )),
                ExportTarget::SwiftUi
            )
            .unwrap()
            .contains("TabView")
        );
    }
    #[test]
    fn exports_fullscreen() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "s",
                    NodeKind::Route {
                        title: "S".into(),
                        respect_safe_area: false
                    }
                )),
                ExportTarget::SwiftUi
            )
            .unwrap()
            .contains("ignoresSafeArea")
        );
    }
    #[test]
    fn rejects_platform_view() {
        assert!(matches!(
            export(
                &UiDocument::new(Node::new(
                    "m",
                    NodeKind::PlatformView {
                        platform: Platform::Ios,
                        name: "m".into(),
                        payload: serde_json::Value::Null
                    }
                )),
                ExportTarget::SwiftUi
            ),
            Err(ExportError::PlatformView { .. })
        ));
    }
    #[test]
    fn exports_checkbox() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "c",
                    NodeKind::Checkbox {
                        label: Some("OK".into()),
                        checked: true,
                        on_change: "ch".into()
                    }
                )),
                ExportTarget::JetpackCompose
            )
            .unwrap()
            .contains("Checkbox")
        );
    }
    #[test]
    fn exports_divider() {
        assert!(
            export(
                &UiDocument::new(Node::new("d", NodeKind::Divider {})),
                ExportTarget::WinUi3
            )
            .unwrap()
            .contains("Border")
        );
    }
    #[test]
    fn exports_card() {
        let c = export(
            &UiDocument::new(
                Node::new("c", NodeKind::Card {}).with_children(vec![Node::new(
                    "i",
                    NodeKind::Text {
                        text: "hi".into(),
                        style: TextStyle::Body,
                    },
                )]),
            ),
            ExportTarget::SwiftUi,
        )
        .unwrap();
        assert!(c.contains("Card"));
    }
    #[test]
    fn exports_chip() {
        assert!(
            export(
                &UiDocument::new(Node::new(
                    "ch",
                    NodeKind::Chip {
                        label: "Tag".into(),
                        variant: ChipVariant::Input,
                        on_dismiss: None
                    }
                )),
                ExportTarget::JetpackCompose
            )
            .unwrap()
            .contains("SuggestionChip")
        );
    }
}
