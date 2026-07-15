//! State management and host-facing contracts for CrossUI.

use crossui_ir::{DiffOp, Platform, UiDocument, diff};
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::collections::BTreeSet;
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
    fn dispatch_json(&mut self, event_json: &str) -> Result<Vec<DiffOp>, ApplicationError>;
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
{
    fn document(&self) -> &UiDocument {
        self.store.document()
    }

    fn dispatch_json(&mut self, event_json: &str) -> Result<Vec<DiffOp>, ApplicationError> {
        let event: RuntimeEvent<R::Action> = serde_json::from_str(event_json)
            .map_err(|error| ApplicationError::InvalidEvent(error.to_string()))?;
        Ok(self.store.dispatch(event.action).patch)
    }
}

#[derive(Debug, Error)]
pub enum ApplicationError {
    #[error("invalid application event: {0}")]
    InvalidEvent(String),
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
#[derive(Clone, Debug, Default)]
pub struct CapabilitySet(BTreeSet<PlatformCapability>);
impl CapabilitySet {
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
#[derive(Debug, Error)]
pub enum NativeError {
    #[error("native operation is unavailable: {0}")]
    Unavailable(String),
    #[error("native operation failed: {0}")]
    Failed(String),
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
        let patch = app
            .dispatch_json(r#"{"node_key":"count","action":null}"#)
            .unwrap();
        assert_eq!(patch.len(), 1);
    }
}
