use serde::{Deserialize, Serialize};

/// Platform identity written to `devices/{deviceId}.platform`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DevicePlatform {
    Android,
    Ios,
    Windows,
    Macos,
    Linux,
}

impl DevicePlatform {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Android => "android",
            Self::Ios => "ios",
            Self::Windows => "windows",
            Self::Macos => "macos",
            Self::Linux => "linux",
        }
    }

    pub fn parse(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "android" => Some(Self::Android),
            "ios" => Some(Self::Ios),
            "windows" => Some(Self::Windows),
            "macos" | "mac" | "osx" => Some(Self::Macos),
            "linux" => Some(Self::Linux),
            _ => None,
        }
    }

    pub const fn is_desktop(self) -> bool {
        matches!(self, Self::Windows | Self::Macos | Self::Linux)
    }

    /// Host platform for this binary.
    pub fn current() -> Self {
        if cfg!(target_os = "windows") {
            Self::Windows
        } else if cfg!(target_os = "macos") {
            Self::Macos
        } else {
            // linux and other unix-like hosts map to linux for device platform.
            Self::Linux
        }
    }
}
