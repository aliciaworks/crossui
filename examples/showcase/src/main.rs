use crossui_core::{Reducer, Store};
use crossui_dsl::{Accessible, SemanticRole, button, input, route, text, title, vstack};
use crossui_export::{ExportTarget, export};
use crossui_ir::{Theme, TokenValue, UiDocument};
use std::collections::BTreeMap;

#[derive(Clone)]
enum Action {
    EmailChanged(String),
    Submit,
}
struct Login {
    email: String,
    error: Option<String>,
}
impl Reducer for Login {
    type Action = Action;
    type Effect = String;
    fn reduce(&mut self, action: Action) -> Vec<Self::Effect> {
        match action {
            Action::EmailChanged(value) => {
                self.email = value;
                self.error = None;
                vec![]
            }
            Action::Submit if self.email.contains('@') => vec!["login.submit".into()],
            Action::Submit => {
                self.error = Some("Enter a valid email address".into());
                vec![]
            }
        }
    }
    fn view(&self) -> UiDocument {
        let mut children = vec![
            title("heading", "Welcome"),
            input("email", &self.email, "email_changed")
                .accessibility("Email address", SemanticRole::TextField),
        ];
        if let Some(error) = &self.error {
            children.push(text("email-error", error));
        }
        children.push(
            button("submit", "Continue", "submit").accessibility("Continue", SemanticRole::Button),
        );
        let mut document =
            UiDocument::new(route("login", "Sign in", vec![vstack("content", children)]));
        document.theme = Theme {
            tokens: BTreeMap::from([
                ("primary".into(), TokenValue::Color("#6750A4".into())),
                ("spacing.md".into(), TokenValue::Number(16)),
            ]),
            android: crossui_ir::AndroidTheme {
                material3_expressive: true,
                dynamic_color: true,
            },
        };
        document
    }
}
fn main() {
    let mut store = Store::new(Login {
        email: String::new(),
        error: None,
    });
    store.dispatch(Action::EmailChanged("ada@example.com".into()));
    store.dispatch(Action::Submit);
    let output = match std::env::args().nth(1).as_deref() {
        Some("swiftui") => {
            export(store.document(), ExportTarget::SwiftUi).expect("exportable document")
        }
        Some("compose") => {
            export(store.document(), ExportTarget::JetpackCompose).expect("exportable document")
        }
        Some("winui3") => {
            export(store.document(), ExportTarget::WinUi3).expect("exportable document")
        }
        Some(target) => {
            panic!("unsupported export target: {target}; use swiftui, compose, or winui3")
        }
        None => store.document().to_json().expect("serializable document"),
    };
    println!("{output}");
}
