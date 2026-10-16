use serde::{Deserialize, Serialize};

/// Fail lock is always device-wide. Never add a per-app-only fail.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum FailLockScope {
    #[default]
    AllNonEmergency,
}

impl FailLockScope {
    pub const fn as_storage_str(self) -> &'static str {
        match self {
            Self::AllNonEmergency => "all_non_emergency",
        }
    }
}

/// Quiz trigger modes. Desktop recommended default is [`QuizMode::DeviceInterval`].
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum QuizMode {
    #[default]
    AppBlock,
    DeviceInterval,
    EverySession,
    DailyCeiling,
}

impl QuizMode {
    pub const fn as_storage_str(self) -> &'static str {
        match self {
            Self::AppBlock => "app_block",
            Self::DeviceInterval => "device_interval",
            Self::EverySession => "every_session",
            Self::DailyCeiling => "daily_ceiling",
        }
    }

    /// Older mobile clients that do not know `device_interval` fall back to app_block.
    pub fn from_storage(raw: Option<&str>) -> Self {
        match raw.map(|s| s.trim().to_ascii_lowercase()).as_deref() {
            Some("device_interval") => Self::DeviceInterval,
            Some("every_session") => Self::EverySession,
            Some("daily_ceiling") => Self::DailyCeiling,
            Some("app_block") | None => Self::AppBlock,
            Some(_) => Self::AppBlock,
        }
    }
}
