use crossui_core::Application;
use std::{
    ffi::{CStr, CString, c_char},
    sync::{LazyLock, Mutex},
};

/// Replace this with your own `create_app` when building with
/// `--no-default-features --features custom-app` or by patching the
/// active feature.
#[cfg(feature = "login-app")]
fn create_app() -> Box<dyn Application> {
    crossui_login_app::create_app()
}

/// Stub factory when no application feature is active. The native host
/// must register an application before calling any FFI entry point.
#[cfg(not(feature = "login-app"))]
fn create_app() -> Box<dyn Application> {
    compile_error!("no application feature enabled; enable 'login-app' or provide your own")
}

static APP: LazyLock<Mutex<Box<dyn Application>>> = LazyLock::new(|| Mutex::new(create_app()));

fn response(value: Result<String, String>) -> *mut c_char {
    let json = value.unwrap_or_else(|error| {
        format!(
            r#"{{"error":{}}}"#,
            serde_json::to_string(&error).unwrap_or_default()
        )
    });
    CString::new(json).map_or(std::ptr::null_mut(), CString::into_raw)
}

fn document() -> Result<String, String> {
    APP.lock()
        .map_err(|_| "CrossUI application lock was poisoned".to_string())?
        .document()
        .to_json()
        .map_err(|error| error.to_string())
}

fn dispatch(event_json: &str) -> Result<String, String> {
    let mut app = APP
        .lock()
        .map_err(|_| "CrossUI application lock was poisoned".to_string())?;
    app.dispatch_json(event_json)
        .map_err(|error| error.to_string())
        .and_then(|update| serde_json::to_string(&update).map_err(|error| error.to_string()))
}

#[unsafe(no_mangle)]
pub extern "C" fn crossui_initial_document() -> *mut c_char {
    response(document())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn crossui_dispatch_event(event_json: *const c_char) -> *mut c_char {
    if event_json.is_null() {
        return response(Err("event JSON cannot be null".into()));
    }
    let event_json = unsafe { CStr::from_ptr(event_json) }
        .to_str()
        .map_err(|error| error.to_string());
    response(event_json.and_then(dispatch))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn crossui_string_free(value: *mut c_char) {
    if !value.is_null() {
        drop(unsafe { CString::from_raw(value) });
    }
}
