//! State management and host-facing contracts for CrossUI.

use crossui_ir::{DiffOp, Platform, UiDocument, diff};
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::collections::{BTreeMap, BTreeSet};
use thiserror::Error;

pub trait Reducer: Sized {
    type Action: Clone;
    type Effect;
    fn reduce(&mut self, action: Self::Action) -> Vec<Self::Effect>;
    fn view(&self) -> UiDocument;
}

pub struct Store<R: Reducer> {
    state: R,
    document: UiDocument,
}
impl<R: Reducer> Store<R> {
    pub fn new(state: R) -> Self {
        let document = state.view();
        Self { state, document }
    }
    pub fn document(&self) -> &UiDocument {
        &self.document
    }
    pub fn dispatch(&mut self, action: R::Action) -> Update<R::Effect> {
        let effects = self.state.reduce(action);
        let next = self.state.view();
        let patch = diff(&self.document, &next);
        self.document = next;
        Update { patch, effects }
    }
}
pub struct Update<E> {
    pub patch: Vec<DiffOp>,
    pub effects: Vec<E>,
}

pub trait Application: Send {
    fn document(&self) -> &UiDocument;
    fn dispatch_json(&mut self, event_json: &str) -> Result<ApplicationUpdate, ApplicationError>;
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ApplicationUpdate {
    pub document: UiDocument,
    pub patch: Vec<DiffOp>,
    pub effects: Vec<serde_json::Value>,
}

pub struct ReducerApplication<R: Reducer> {
    store: Store<R>,
}

impl<R: Reducer> ReducerApplication<R> {
    pub fn new(state: R) -> Self {
        Self {
            store: Store::new(state),
        }
    }
}

impl<R> Application for ReducerApplication<R>
where
    R: Reducer + Send,
    R::Action: DeserializeOwned + Send,
    R::Effect: Serialize,
{
    fn document(&self) -> &UiDocument {
        self.store.document()
    }

    fn dispatch_json(&mut self, event_json: &str) -> Result<ApplicationUpdate, ApplicationError> {
        let event: RuntimeEvent<R::Action> = serde_json::from_str(event_json)
            .map_err(|error| ApplicationError::InvalidEvent(error.to_string()))?;
        let update = self.store.dispatch(event.action);
        let effects = update
            .effects
            .into_iter()
            .map(|effect| {
                serde_json::to_value(effect)
                    .map_err(|error| ApplicationError::EffectSerialization(error.to_string()))
            })
            .collect::<Result<Vec<_>, _>>()?;
        Ok(ApplicationUpdate {
            document: self.store.document().clone(),
            patch: update.patch,
            effects,
        })
    }
}

#[derive(Debug, Error)]
pub enum ApplicationError {
    #[error("invalid application event: {0}")]
    InvalidEvent(String),
    #[error("effect serialization failed: {0}")]
    EffectSerialization(String),
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PlatformCapability {
    Camera,
    FilePicker,
    PushNotifications,
    Maps,
    SecureStorage,
}

/// A portable component exposed by the stable IR contract.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Component {
    Text,
    Button,
    Input,
    Stack,
    List,
    Form,
    Loading,
    Navigation,
    Route,
    PlatformView,
}

/// Hosts must make unsupported components visible to the application instead of
/// silently substituting a different platform behavior.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ComponentSupport {
    Native,
    Adapted,
    Unsupported,
}

pub struct ComponentCatalog;
impl ComponentCatalog {
    pub fn support(platform: Platform, component: Component) -> ComponentSupport {
        match component {
            Component::PlatformView => ComponentSupport::Unsupported,
            Component::Form => match platform {
                Platform::Android => ComponentSupport::Adapted,
                Platform::Ios | Platform::Windows => ComponentSupport::Native,
            },
            Component::Navigation | Component::Route => ComponentSupport::Native,
            Component::Text
            | Component::Button
            | Component::Input
            | Component::Stack
            | Component::List
            | Component::Loading => ComponentSupport::Native,
        }
    }
}
#[derive(Clone, Debug, Default)]
pub struct CapabilitySet(BTreeSet<PlatformCapability>);
impl CapabilitySet {
    pub fn new(capabilities: impl IntoIterator<Item = PlatformCapability>) -> Self {
        Self(capabilities.into_iter().collect())
    }

    pub fn with(mut self, capability: PlatformCapability) -> Self {
        self.0.insert(capability);
        self
    }

    pub fn supports(&self, capability: PlatformCapability) -> bool {
        self.0.contains(&capability)
    }
}
pub trait NativeModule {
    fn platform(&self) -> Platform;
    fn capabilities(&self) -> CapabilitySet;
    fn invoke(
        &mut self,
        name: &str,
        payload: serde_json::Value,
    ) -> Result<serde_json::Value, NativeError>;
}

/// A serializable request for an explicitly registered platform module.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct NativeRequest {
    pub module: String,
    pub operation: String,
    pub payload: serde_json::Value,
    pub required_capability: Option<PlatformCapability>,
}

/// Owns host-provided native modules and rejects platform or capability
/// mismatches before an operation reaches a platform adapter.
pub struct NativeModuleRegistry {
    platform: Platform,
    modules: BTreeMap<String, Box<dyn NativeModule>>,
}

impl NativeModuleRegistry {
    pub fn new(platform: Platform) -> Self {
        Self {
            platform,
            modules: BTreeMap::new(),
        }
    }

    pub fn register(
        &mut self,
        name: impl Into<String>,
        module: Box<dyn NativeModule>,
    ) -> Result<(), NativeError> {
        if module.platform() != self.platform {
            return Err(NativeError::PlatformMismatch {
                expected: self.platform,
                actual: module.platform(),
            });
        }
        self.modules.insert(name.into(), module);
        Ok(())
    }

    pub fn invoke(&mut self, request: NativeRequest) -> Result<serde_json::Value, NativeError> {
        let module = self
            .modules
            .get_mut(&request.module)
            .ok_or_else(|| NativeError::Unavailable(request.module.clone()))?;
        if let Some(capability) = request.required_capability
            && !module.capabilities().supports(capability)
        {
            return Err(NativeError::Unavailable(format!(
                "{} requires capability {capability:?}",
                request.module
            )));
        }
        module.invoke(&request.operation, request.payload)
    }
}

#[derive(Debug, Error)]
pub enum NativeError {
    #[error("native operation is unavailable: {0}")]
    Unavailable(String),
    #[error("native operation failed: {0}")]
    Failed(String),
    #[error("native module platform mismatch: expected {expected:?}, got {actual:?}")]
    PlatformMismatch {
        expected: Platform,
        actual: Platform,
    },
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RuntimeEvent<A> {
    pub node_key: String,
    pub action: A,
}
pub struct JsonBridge;
impl JsonBridge {
    pub fn document(document: &UiDocument) -> Result<String, serde_json::Error> {
        document.to_json()
    }
    pub fn event<A: DeserializeOwned>(json: &str) -> Result<RuntimeEvent<A>, serde_json::Error> {
        serde_json::from_str(json)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossui_ir::{Node, NodeKind, TextStyle};
    struct Counter(u32);
    impl Reducer for Counter {
        type Action = ();
        type Effect = &'static str;
        fn reduce(&mut self, _: ()) -> Vec<Self::Effect> {
            self.0 += 1;
            vec!["changed"]
        }
        fn view(&self) -> UiDocument {
            UiDocument::new(Node::new(
                "count",
                NodeKind::Text {
                    text: self.0.to_string(),
                    style: TextStyle::Body,
                },
            ))
        }
    }
    #[test]
    fn dispatch_emits_keyed_patch() {
        let mut store = Store::new(Counter(0));
        let update = store.dispatch(());
        assert_eq!(update.effects, vec!["changed"]);
        assert_eq!(update.patch.len(), 1);
    }

    #[test]
    fn application_adapter_decodes_events() {
        let mut app = ReducerApplication::new(Counter(0));
        let update = app
            .dispatch_json(r#"{"node_key":"count","action":null}"#)
            .unwrap();
        assert_eq!(update.patch.len(), 1);
        assert_eq!(
            update.effects,
            vec![serde_json::Value::String("changed".into())]
        );
    }

    #[test]
    fn catalog_never_claims_platform_views_are_portable() {
        assert_eq!(
            ComponentCatalog::support(Platform::Windows, Component::PlatformView),
            ComponentSupport::Unsupported
        );
    }

    #[test]
    fn capability_set_is_explicitly_constructed_by_hosts() {
        let capabilities = CapabilitySet::new([PlatformCapability::Camera])
            .with(PlatformCapability::SecureStorage);
        assert!(capabilities.supports(PlatformCapability::Camera));
        assert!(capabilities.supports(PlatformCapability::SecureStorage));
        assert!(!capabilities.supports(PlatformCapability::Maps));
    }

    struct CameraModule;
    impl NativeModule for CameraModule {
        fn platform(&self) -> Platform {
            Platform::Windows
        }
        fn capabilities(&self) -> CapabilitySet {
            CapabilitySet::new([PlatformCapability::Camera])
        }
        fn invoke(
            &mut self,
            name: &str,
            payload: serde_json::Value,
        ) -> Result<serde_json::Value, NativeError> {
            if name == "capture" {
                Ok(payload)
            } else {
                Err(NativeError::Unavailable(name.into()))
            }
        }
    }

    struct AndroidModule;
    impl NativeModule for AndroidModule {
        fn platform(&self) -> Platform {
            Platform::Android
        }
        fn capabilities(&self) -> CapabilitySet {
            CapabilitySet::default()
        }
        fn invoke(
            &mut self,
            _: &str,
            _: serde_json::Value,
        ) -> Result<serde_json::Value, NativeError> {
            Ok(serde_json::Value::Null)
        }
    }

    #[test]
    fn registry_enforces_platform_and_capability() {
        let mut registry = NativeModuleRegistry::new(Platform::Windows);
        registry.register("camera", Box::new(CameraModule)).unwrap();
        assert_eq!(
            registry
                .invoke(NativeRequest {
                    module: "camera".into(),
                    operation: "capture".into(),
                    payload: serde_json::json!({"quality": "high"}),
                    required_capability: Some(PlatformCapability::Camera),
                })
                .unwrap(),
            serde_json::json!({"quality": "high"})
        );
        assert!(matches!(
            registry.invoke(NativeRequest {
                module: "camera".into(),
                operation: "capture".into(),
                payload: serde_json::Value::Null,
                required_capability: Some(PlatformCapability::Maps),
            }),
            Err(NativeError::Unavailable(_))
        ));
        assert!(matches!(
            registry.register("android", Box::new(AndroidModule)),
            Err(NativeError::PlatformMismatch {
                expected: Platform::Windows,
                actual: Platform::Android
            })
        ));
    }
}
