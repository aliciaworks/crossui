use crossui_core::Application;
use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::jstring,
};
use std::sync::{LazyLock, Mutex};

/// Replace this with your own `create_app` when building with
/// `--no-default-features` and your own feature.
#[cfg(feature = "login-app")]
fn create_app() -> Box<dyn Application> {
    crossui_login_app::create_app()
}

#[cfg(not(feature = "login-app"))]
fn create_app() -> Box<dyn Application> {
    compile_error!("no application feature enabled; enable 'login-app' or provide your own")
}

static APP: LazyLock<Mutex<Box<dyn Application>>> = LazyLock::new(|| Mutex::new(create_app()));

fn document_json() -> Result<String, String> {
    APP.lock()
        .map_err(|_| "CrossUI application lock was poisoned".to_string())?
        .document()
        .to_json()
        .map_err(|error| error.to_string())
}

fn dispatch_json(event_json: &str) -> Result<String, String> {
    let mut app = APP
        .lock()
        .map_err(|_| "CrossUI application lock was poisoned".to_string())?;
    app.dispatch_json(event_json)
        .map_err(|error| error.to_string())
        .and_then(|update| serde_json::to_string(&update).map_err(|error| error.to_string()))
}

fn java_string(env: &mut JNIEnv<'_>, value: Result<String, String>) -> jstring {
    let text = value.unwrap_or_else(|error| {
        format!(
            r#"{{"error":{}}}"#,
            serde_json::to_string(&error).unwrap_or_default()
        )
    });
    env.new_string(text)
        .map(|string| string.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_myapplication_CrossUiBridge_initialDocument(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    java_string(&mut env, document_json())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_myapplication_CrossUiBridge_dispatchEvent(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    event: JString<'_>,
) -> jstring {
    let event_json: Result<String, String> = env
        .get_string(&event)
        .map(String::from)
        .map_err(|error| error.to_string());
    java_string(&mut env, event_json.and_then(|json| dispatch_json(&json)))
}
