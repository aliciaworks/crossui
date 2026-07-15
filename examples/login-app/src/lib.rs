use crossui_core::{Application, Reducer, ReducerApplication};
use crossui_dsl::{
    Accessible, SemanticRole, button, form, input, navigation, route, selectable_list, text, title,
    vstack,
};
use crossui_ir::{AndroidTheme, Theme, TokenValue, UiDocument};
use serde::Deserialize;
use std::collections::BTreeMap;

pub fn create_app() -> Box<dyn Application> {
    Box::new(ReducerApplication::new(Login::default()))
}

#[derive(Default)]
struct Login {
    email: String,
    message: Option<String>,
    screen: Screen,
}

#[derive(Default)]
enum Screen {
    #[default]
    Login,
    Projects,
    Detail(String),
}

#[derive(Clone, Deserialize)]
#[serde(tag = "type", content = "value", rename_all = "snake_case")]
enum LoginAction {
    EmailChanged(String),
    Submit,
    ProjectSelected(String),
    Back,
}

impl Reducer for Login {
    type Action = LoginAction;
    type Effect = String;

    fn reduce(&mut self, action: Self::Action) -> Vec<Self::Effect> {
        match action {
            LoginAction::EmailChanged(value) => {
                self.email = value;
                self.message = None;
                vec![]
            }
            LoginAction::Submit if self.email.contains('@') => {
                self.screen = Screen::Projects;
                vec!["login.submit".into()]
            }
            LoginAction::Submit => {
                self.message = Some("Enter a valid email address".into());
                vec![]
            }
            LoginAction::ProjectSelected(key) => {
                self.screen = Screen::Detail(key);
                vec![]
            }
            LoginAction::Back => {
                self.screen = Screen::Projects;
                vec![]
            }
        }
    }

    fn view(&self) -> UiDocument {
        let active = match &self.screen {
            Screen::Login => "login",
            Screen::Projects => "projects",
            Screen::Detail(_) => "detail",
        };
        let detail_title = match &self.screen {
            Screen::Detail(key) => key.replace("project-", "Project "),
            _ => "Project".into(),
        };
        let mut form_children = vec![
            input("email", &self.email, "email_changed")
                .accessibility("Email address", SemanticRole::TextField),
        ];
        if let Some(message) = &self.message {
            form_children.push(text("message", message));
        }
        form_children.push(
            button("submit", "Continue", "submit").accessibility("Continue", SemanticRole::Button),
        );
        let login = vec![
            title("heading", "Welcome"),
            form("login-form", form_children),
        ];
        let projects = vec![
            title("projects-heading", "Choose a project"),
            selectable_list(
                "projects-list",
                "project_selected",
                vec![
                    text("project-alpha", "Project Alpha"),
                    text("project-beta", "Project Beta"),
                    text("project-gamma", "Project Gamma"),
                ],
            ),
        ];
        let detail = vec![
            title("detail-heading", &detail_title),
            text("detail-copy", "This screen is selected by Rust state."),
            button("back", "Back to projects", "back"),
        ];
        let mut document = UiDocument::new(navigation(
            "app-navigation",
            active,
            vec![
                route("login", "Sign in", vec![vstack("content", login)]),
                route(
                    "projects",
                    "Projects",
                    vec![vstack("projects-content", projects)],
                ),
                route(
                    "detail",
                    &detail_title,
                    vec![vstack("detail-content", detail)],
                ),
            ],
        ));
        document.theme = Theme {
            tokens: BTreeMap::from([
                ("primary".into(), TokenValue::Color("#6750A4".into())),
                ("spacing.md".into(), TokenValue::Number(16)),
            ]),
            android: AndroidTheme {
                material3_expressive: true,
                dynamic_color: true,
            },
        };
        document
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn successful_login_navigates_to_projects() {
        let mut app = create_app();
        app.dispatch_json(
            r#"{"node_key":"email","action":{"type":"email_changed","value":"ada@example.com"}}"#,
        )
        .unwrap();
        app.dispatch_json(r#"{"node_key":"submit","action":{"type":"submit"}}"#)
            .unwrap();
        assert!(
            app.document()
                .to_json()
                .unwrap()
                .contains("\"active\": \"projects\"")
        );
    }

    #[test]
    fn selecting_a_project_navigates_to_detail() {
        let mut app = create_app();
        app.dispatch_json(r#"{"node_key":"projects-list","action":{"type":"project_selected","value":"project-alpha"}}"#).unwrap();
        assert!(app.document().to_json().unwrap().contains("Project alpha"));
    }
}
