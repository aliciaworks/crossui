//! Target profile: platform identity + device capabilities.
//!
//! Platform tells you *which* cultural conventions apply (iOS HIG vs
//! Material 3 vs WinUI). Capabilities tell you *what* the device can
//! do.  They are orthogonal dimensions of the lowering pipeline.

use serde::{Deserialize, Serialize};

// ---------------------------------------------------------------------------
// Platform identity
// ---------------------------------------------------------------------------

/// The broad platform family.  OS version lives in [`TargetProfile`]
/// for runtime availability checks.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PlatformIdentity {
    Ios,
    IpadOs,
    WatchOs,
    MacOs,
    Android,
    Windows,
}

impl PlatformIdentity {
    pub fn vendor(&self) -> &'static str {
        match self {
            Self::Ios | Self::IpadOs | Self::WatchOs | Self::MacOs => "apple",
            Self::Android => "google",
            Self::Windows => "microsoft",
        }
    }
}

// ---------------------------------------------------------------------------
// Target profile – the lowering target
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct TargetProfile {
    pub platform: PlatformIdentity,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub os_version: Option<String>,
    #[serde(default)]
    pub capabilities: Capabilities,
    #[serde(default)]
    pub interaction: InteractionProfile,
}

impl TargetProfile {
    pub fn iphone() -> Self {
        Self {
            platform: PlatformIdentity::Ios,
            os_version: Some("17.0".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: true,
                    pointer: false,
                    keyboard: false,
                    digital_crown: false,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::Phone,
                    width_points: Some(390),
                    height_points: Some(844),
                    resizable: false,
                },
                windowing: WindowingCapabilities {
                    single_scene: true,
                    multi_window: false,
                    menu_bar: false,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Normal,
                glanceable: false,
            },
        }
    }

    pub fn ipad() -> Self {
        Self {
            platform: PlatformIdentity::IpadOs,
            os_version: Some("17.0".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: true,
                    pointer: true,
                    keyboard: true,
                    digital_crown: false,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::Tablet,
                    width_points: Some(1024),
                    height_points: Some(1366),
                    resizable: true,
                },
                windowing: WindowingCapabilities {
                    single_scene: false,
                    multi_window: true,
                    menu_bar: false,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Normal,
                glanceable: false,
            },
        }
    }

    pub fn apple_watch() -> Self {
        Self {
            platform: PlatformIdentity::WatchOs,
            os_version: Some("10.0".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: true,
                    pointer: false,
                    keyboard: false,
                    digital_crown: true,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::WristCompact,
                    width_points: None,
                    height_points: None,
                    resizable: false,
                },
                windowing: WindowingCapabilities {
                    single_scene: true,
                    multi_window: false,
                    menu_bar: false,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Brief,
                glanceable: true,
            },
        }
    }

    pub fn mac_desktop() -> Self {
        Self {
            platform: PlatformIdentity::MacOs,
            os_version: Some("14.0".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: false,
                    pointer: true,
                    keyboard: true,
                    digital_crown: false,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::Desktop,
                    width_points: None,
                    height_points: None,
                    resizable: true,
                },
                windowing: WindowingCapabilities {
                    single_scene: false,
                    multi_window: true,
                    menu_bar: true,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Extended,
                glanceable: false,
            },
        }
    }

    pub fn android_phone() -> Self {
        Self {
            platform: PlatformIdentity::Android,
            os_version: Some("14".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: true,
                    pointer: false,
                    keyboard: false,
                    digital_crown: false,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::Phone,
                    width_points: None,
                    height_points: None,
                    resizable: false,
                },
                windowing: WindowingCapabilities {
                    single_scene: true,
                    multi_window: false,
                    menu_bar: false,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Normal,
                glanceable: false,
            },
        }
    }

    pub fn windows_desktop() -> Self {
        Self {
            platform: PlatformIdentity::Windows,
            os_version: Some("11".into()),
            capabilities: Capabilities {
                input: InputCapabilities {
                    touch: false,
                    pointer: true,
                    keyboard: true,
                    digital_crown: false,
                },
                display: DisplayCapabilities {
                    class: DisplayClass::Desktop,
                    width_points: None,
                    height_points: None,
                    resizable: true,
                },
                windowing: WindowingCapabilities {
                    single_scene: false,
                    multi_window: true,
                    menu_bar: false,
                },
            },
            interaction: InteractionProfile {
                expected_duration: DurationClass::Extended,
                glanceable: false,
            },
        }
    }
}

// ---------------------------------------------------------------------------
// Capabilities
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize, Default)]
pub struct Capabilities {
    #[serde(default)]
    pub input: InputCapabilities,
    #[serde(default)]
    pub display: DisplayCapabilities,
    #[serde(default)]
    pub windowing: WindowingCapabilities,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize, Default)]
pub struct InputCapabilities {
    #[serde(default)]
    pub touch: bool,
    #[serde(default)]
    pub pointer: bool,
    #[serde(default)]
    pub keyboard: bool,
    #[serde(default)]
    pub digital_crown: bool,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize, Default)]
pub struct DisplayCapabilities {
    pub class: DisplayClass,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub width_points: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub height_points: Option<u32>,
    #[serde(default)]
    pub resizable: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum DisplayClass {
    WristCompact,
    Phone,
    Tablet,
    #[default]
    Desktop,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize, Default)]
pub struct WindowingCapabilities {
    #[serde(default)]
    pub single_scene: bool,
    #[serde(default)]
    pub multi_window: bool,
    #[serde(default)]
    pub menu_bar: bool,
}

// ---------------------------------------------------------------------------
// Interaction profile
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize, Default)]
pub struct InteractionProfile {
    #[serde(default)]
    pub expected_duration: DurationClass,
    #[serde(default)]
    pub glanceable: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum DurationClass {
    Brief,
    #[default]
    Normal,
    Extended,
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn iphone_profile_has_touch() {
        let profile = TargetProfile::iphone();
        assert!(profile.capabilities.input.touch);
        assert!(!profile.capabilities.input.keyboard);
    }

    #[test]
    fn watch_profile_is_glanceable() {
        let profile = TargetProfile::apple_watch();
        assert!(profile.interaction.glanceable);
        assert!(profile.capabilities.input.digital_crown);
    }

    #[test]
    fn mac_profile_has_menu_bar() {
        let profile = TargetProfile::mac_desktop();
        assert!(profile.capabilities.windowing.menu_bar);
        assert_eq!(profile.capabilities.display.class, DisplayClass::Desktop);
    }

    #[test]
    fn ipad_profile_has_pointer_and_keyboard() {
        let profile = TargetProfile::ipad();
        assert!(profile.capabilities.input.pointer);
        assert!(profile.capabilities.input.keyboard);
        assert!(profile.capabilities.windowing.multi_window);
        assert_eq!(profile.platform, PlatformIdentity::IpadOs);
    }

    #[test]
    fn profiles_serialize_roundtrip() {
        let profile = TargetProfile::iphone();
        let json = serde_json::to_string_pretty(&profile).unwrap();
        let parsed: TargetProfile = serde_json::from_str(&json).unwrap();
        assert_eq!(profile.platform, parsed.platform);
        assert!(parsed.capabilities.input.touch);
    }
}
