//! Namespaced, strongly-typed platform extensions.
//!
//! Extensions carry platform-specific hints that a host renderer may use,
//! but that have no portable equivalent.  A node may carry zero or more
//! extensions; they are validated against the target profile during
//! lowering.  By default, a platform extension applied to the wrong
//! target is an error (not a silent ignore).

use crate::profile::PlatformIdentity;
use serde::{Deserialize, Serialize};

/// A typed extension scoped to exactly one platform.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "platform", content = "data", rename_all = "snake_case")]
pub enum PlatformExtension {
    Ios(IosExtension),
    IpadOs(IpadOsExtension),
    WatchOs(WatchOsExtension),
    MacOs(MacOsExtension),
    Android(AndroidExtension),
    Windows(WindowsExtension),
}

impl PlatformExtension {
    pub fn platform(&self) -> PlatformIdentity {
        match self {
            Self::Ios(_) => PlatformIdentity::Ios,
            Self::IpadOs(_) => PlatformIdentity::IpadOs,
            Self::WatchOs(_) => PlatformIdentity::WatchOs,
            Self::MacOs(_) => PlatformIdentity::MacOs,
            Self::Android(_) => PlatformIdentity::Android,
            Self::Windows(_) => PlatformIdentity::Windows,
        }
    }
}

// ---- iOS ----------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum IosExtension {
    /// Modal presentation style.
    PresentationStyle { style: PresentationStyle },
    /// Haptic feedback trigger.
    HapticFeedback { feedback_type: HapticType },
    /// A context-menu attached to this node.
    ContextMenu { actions: Vec<String> },
    /// Pull-to-refresh action name.
    Refreshable { action: String },
    /// Swipe-to-delete action name.
    SwipeAction { action: String },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PresentationStyle {
    Sheet,
    FullScreenCover,
    Popover,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HapticType {
    Light,
    Medium,
    Heavy,
    Success,
    Warning,
    Error,
}

// ---- iPadOS -------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum IpadOsExtension {
    MulticolumnLayout {
        columns: u32,
    },
    StageManager {
        preferred_width: Option<u32>,
        preferred_height: Option<u32>,
    },
    Sidebar {
        visible: bool,
    },
}

// ---- watchOS ------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum WatchOsExtension {
    /// Digital Crown input binding.
    CrownInput { sensitivity: CrownSensitivity },
    /// Complication configuration.
    Complication { family: ComplicationFamily },
    /// Priority for the Smart Stack / Siri watch face.
    GlancePriority { priority: GlancePriority },
    /// Compact navigation hint.
    CompactNavigation { max_depth: u32 },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CrownSensitivity {
    Low,
    Normal,
    High,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ComplicationFamily {
    Circular,
    Rectangular,
    Corner,
    ExtraLarge,
    Graphic,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum GlancePriority {
    Low,
    Normal,
    High,
}

// ---- macOS --------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum MacOsExtension {
    /// Keyboard shortcut.
    KeyboardShortcut {
        key: String,
        #[serde(default)]
        modifiers: Vec<KeyModifier>,
    },
    /// Toolbar item placement.
    ToolbarItem { item_id: String },
    /// Menu-bar command registration.
    MenuCommand { menu_path: String },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum KeyModifier {
    Command,
    Shift,
    Option,
    Control,
}

// ---- Android ------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum AndroidExtension {
    /// Material 3 elevation override (dp).
    Elevation { dp: f32 },
    /// Dynamic color behaviour hint.
    DynamicColorHint { hue: Option<f32> },
}

// ---- Windows ------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum WindowsExtension {
    /// Connected animation identifier.
    ConnectedAnimation { key: String },
    /// Corner radius preference.
    CornerPreference { radius: f32 },
}

// ---------------------------------------------------------------------------
// Extension validation
// ---------------------------------------------------------------------------

/// What to do when a platform extension targets a different platform
/// than the host profile.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum UnsupportedExtensionPolicy {
    /// Reject with an error (default).
    Error,
    /// Warn but continue.
    Warning,
    /// Silently strip the extension.
    Strip,
}

impl Default for UnsupportedExtensionPolicy {
    fn default() -> Self {
        Self::Error
    }
}

/// Validate extensions against a target profile. Returns a list of
/// unmatched extensions and their expected platform.
pub fn validate_extensions(
    extensions: &[PlatformExtension],
    target: &crate::profile::TargetProfile,
    policy: UnsupportedExtensionPolicy,
) -> Vec<ExtensionMismatch> {
    let mut mismatches = Vec::new();
    for ext in extensions {
        if ext.platform() != target.platform {
            mismatches.push(ExtensionMismatch {
                expected: ext.platform(),
                actual: target.platform,
            });
            if policy == UnsupportedExtensionPolicy::Error {
                // caller should handle the error
            }
        }
    }
    mismatches
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ExtensionMismatch {
    pub expected: PlatformIdentity,
    pub actual: PlatformIdentity,
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::profile::TargetProfile;

    #[test]
    fn ios_extension_matches_iphone_profile() {
        let ext = PlatformExtension::Ios(IosExtension::HapticFeedback {
            feedback_type: HapticType::Success,
        });
        let profile = TargetProfile::iphone();
        let mismatches = validate_extensions(&[ext], &profile, UnsupportedExtensionPolicy::Error);
        assert!(mismatches.is_empty());
    }

    #[test]
    fn ios_extension_on_watch_target_is_mismatch() {
        let ext = PlatformExtension::Ios(IosExtension::PresentationStyle {
            style: PresentationStyle::Sheet,
        });
        let profile = TargetProfile::apple_watch();
        let mismatches = validate_extensions(&[ext], &profile, UnsupportedExtensionPolicy::Error);
        assert_eq!(mismatches.len(), 1);
        assert_eq!(mismatches[0].expected, PlatformIdentity::Ios);
        assert_eq!(mismatches[0].actual, PlatformIdentity::WatchOs);
    }

    #[test]
    fn strip_policy_still_reports_mismatches() {
        let ext = PlatformExtension::Ios(IosExtension::SwipeAction {
            action: "delete".into(),
        });
        let profile = TargetProfile::mac_desktop();
        let mismatches = validate_extensions(&[ext], &profile, UnsupportedExtensionPolicy::Strip);
        assert_eq!(mismatches.len(), 1);
    }

    #[test]
    fn crown_extension_on_watch_is_valid() {
        let ext = PlatformExtension::WatchOs(WatchOsExtension::CrownInput {
            sensitivity: CrownSensitivity::High,
        });
        let profile = TargetProfile::apple_watch();
        let mismatches = validate_extensions(&[ext], &profile, UnsupportedExtensionPolicy::Error);
        assert!(mismatches.is_empty());
    }

    #[test]
    fn keyboard_shortcut_on_mac_is_valid() {
        let ext = PlatformExtension::MacOs(MacOsExtension::KeyboardShortcut {
            key: "n".into(),
            modifiers: vec![KeyModifier::Command],
        });
        let profile = TargetProfile::mac_desktop();
        let mismatches = validate_extensions(&[ext], &profile, UnsupportedExtensionPolicy::Error);
        assert!(mismatches.is_empty());
    }
}
