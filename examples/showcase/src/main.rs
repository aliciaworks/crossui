use crossui_core::{Reducer, Store};
use crossui_dsl::{
    Accessible, HapticType, InputExt, InputType, KeyModifier, PlatformExt, PresentationStyle,
    ReturnKey, SemanticRole, button, caption, checkbox, destructive_button, dialog, display,
    divider, email_input, filter_chip, footnote, hstack, image, input_chip, picker, picker_option,
    route, search_input, secure_input, slider, tab_navigation, text, toggle, ui, vstack,
};
use crossui_export::{ExportTarget, export};
use crossui_ir::{ColorScheme, Theme, TokenValue, UiDocument};
use std::collections::BTreeMap;

#[derive(Clone)]
enum Action {
    EmailChanged(String),
    PasswordChanged(String),
    RememberMeToggled,
    VolumeChanged(f64),
    LanguageChanged(String),
    StartDateChanged(String),
    Submit,
    ShowDeleteDialog,
    ConfirmDelete,
    CancelDelete,
}

struct Login {
    email: String,
    password: String,
    remember_me: bool,
    volume: f64,
    language: String,
    start_date: Option<String>,
    error: Option<String>,
    show_delete: bool,
}

impl Reducer for Login {
    type Action = Action;
    type Effect = String;

    fn reduce(&mut self, action: Action) -> Vec<Self::Effect> {
        match action {
            Action::EmailChanged(v) => {
                self.email = v;
                self.error = None;
                vec![]
            }
            Action::PasswordChanged(v) => {
                self.password = v;
                self.error = None;
                vec![]
            }
            Action::RememberMeToggled => {
                self.remember_me = !self.remember_me;
                vec![]
            }
            Action::VolumeChanged(v) => {
                self.volume = v;
                vec![]
            }
            Action::LanguageChanged(v) => {
                self.language = v;
                vec![]
            }
            Action::StartDateChanged(v) => {
                self.start_date = Some(v);
                vec![]
            }
            Action::Submit if self.email.contains('@') => vec!["login.submit".into()],
            Action::Submit => {
                self.error = Some("Enter a valid email address".into());
                vec![]
            }
            Action::ShowDeleteDialog => {
                self.show_delete = true;
                vec![]
            }
            Action::ConfirmDelete => {
                self.show_delete = false;
                vec!["account.deleted".into()]
            }
            Action::CancelDelete => {
                self.show_delete = false;
                vec![]
            }
        }
    }

    fn view(&self) -> UiDocument {
        let login_page = route(
            "login",
            "Sign in",
            vec![vstack("login_content", {
                let mut c = vec![
                    display("heading", "Welcome"),
                    image("logo", "https://example.com/logo.png", "App logo"),
                    email_input("email", &self.email, "you@example.com", "email_changed")
                        .accessibility("Email", SemanticRole::TextField),
                    secure_input("password", "", "Password", "password_changed")
                        .input_type(InputType::Password)
                        .return_key(ReturnKey::Go)
                        .accessibility("Password", SemanticRole::TextField),
                    search_input("search", "", "Search...", "search_changed")
                        .accessibility("Search", SemanticRole::TextField),
                    toggle(
                        "remember_me",
                        Some("Remember me"),
                        self.remember_me,
                        "remember_me_toggle",
                    ),
                    caption("vol_label", &format!("Volume: {:.0}%", self.volume * 100.0)),
                    slider(
                        "volume",
                        self.volume,
                        0.0,
                        1.0,
                        Some(0.05),
                        "volume_changed",
                    )
                    .accessibility("Volume", SemanticRole::Slider),
                    checkbox(
                        "agree_terms",
                        Some("I agree to the Terms"),
                        false,
                        "agree_changed",
                    )
                    .accessibility("Terms agreement", SemanticRole::Checkbox),
                    hstack(
                        "chips",
                        ui![
                            input_chip("chip_ios", "iOS"),
                            input_chip("chip_android", "Android"),
                            filter_chip("chip_active", "Active", "chip_active_remove"),
                        ],
                    ),
                    divider("sep1"),
                    picker(
                        "language",
                        &self.language,
                        vec![
                            picker_option("English", "en"),
                            picker_option("中文", "zh"),
                            picker_option("日本語", "ja"),
                        ],
                        "language_changed",
                    )
                    .accessibility("Language", SemanticRole::Picker),
                ];
                if let Some(err) = &self.error {
                    c.push(text("email-error", err));
                }
                c.push(hstack(
                    "actions",
                    ui![
                        button("submit", "Continue", "submit")
                            .accessibility("Continue", SemanticRole::Button)
                            .mac_shortcut("↩", vec![KeyModifier::Command]),
                        destructive_button("delete_account", "Delete", "show_delete_dialog")
                            .accessibility("Delete account", SemanticRole::Button)
                            .irreversible()
                            .critical()
                            .ios_haptic(HapticType::Error)
                            .ios_presentation(PresentationStyle::Sheet)
                            .mac_shortcut("⌫", vec![KeyModifier::Command, KeyModifier::Shift])
                            .android_elevation(8.0),
                    ],
                ));
                c.push(caption("version", "v0.2.0 – CrossUI v2.1 components"));
                c.push(footnote("legal", "By continuing you agree to our Terms"));
                if self.show_delete {
                    c.push(
                        dialog(
                            "delete_dialog",
                            "Delete Account?",
                            Some("Delete"),
                            Some("confirm_delete"),
                            Some("Cancel"),
                            Some("cancel_delete"),
                        )
                        .with_children(ui![text("dialog_body", "This action cannot be undone.")]),
                    );
                }
                c
            })],
        );

        let settings_page = route(
            "settings",
            "Settings",
            vec![vstack(
                "settings_content",
                vec![
                    text("settings_title", "App Preferences"),
                    toggle("dark_mode", Some("Dark Mode"), false, "toggle_dark_mode"),
                ],
            )],
        );

        // Use tab navigation so both pages are listed as tabs.
        let mut doc = UiDocument::new(tab_navigation(
            "app",
            "login",
            vec![login_page, settings_page],
        ));
        doc.theme = Theme {
            color_scheme: ColorScheme::System,
            tokens: BTreeMap::from([
                ("primary".into(), TokenValue::Color("#6750A4".into())),
                ("error".into(), TokenValue::Color("#B3261E".into())),
                ("spacing.md".into(), TokenValue::Number(16.0)),
            ]),
            android: crossui_ir::AndroidTheme {
                material3_expressive: true,
                dynamic_color: true,
            },
        };
        doc
    }
}

fn main() {
    let mut store = Store::new(Login {
        email: String::new(),
        password: String::new(),
        remember_me: false,
        volume: 0.5,
        language: "en".into(),
        start_date: None,
        error: None,
        show_delete: false,
    });
    store.dispatch(Action::EmailChanged("ada@example.com".into()));
    store.dispatch(Action::Submit);

    let output = match std::env::args().nth(1).as_deref() {
        Some("swiftui") => export(store.document(), ExportTarget::SwiftUi).expect("exportable"),
        Some("compose") => {
            export(store.document(), ExportTarget::JetpackCompose).expect("exportable")
        }
        Some("winui3") => export(store.document(), ExportTarget::WinUi3).expect("exportable"),
        Some(t) => panic!("unsupported export target: {t}; use swiftui, compose, or winui3"),
        None => store.document().to_json().expect("serializable"),
    };
    println!("{output}");
}
