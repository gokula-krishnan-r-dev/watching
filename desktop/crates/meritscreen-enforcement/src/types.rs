use serde::{Deserialize, Serialize};

/// Parent-facing / heartbeat guardian process health.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum GuardianRuntimeState {
    #[default]
    Running,
    Stopped,
    Degraded,
}

impl GuardianRuntimeState {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Stopped => "stopped",
            Self::Degraded => "degraded",
        }
    }
}

/// Stacked enforcement strength (docs/15 §7).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum EnforcementTier {
    /// Launcher only (should not ship alone).
    L1,
    /// Default Win/macOS: launcher + process/window gating.
    #[default]
    L1L3,
    /// Windows Strict / Linux kiosk when available and enabled.
    L1L2L3,
}

impl EnforcementTier {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::L1 => "L1",
            Self::L1L3 => "L1_L3",
            Self::L1L2L3 => "L1_L2_L3",
        }
    }

    pub fn parse(raw: &str) -> Self {
        match raw.trim() {
            "L1" => Self::L1,
            "L1_L2_L3" => Self::L1L2L3,
            _ => Self::L1L3,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct TamperFlags {
    #[serde(default)]
    pub service_stopped: bool,
    #[serde(default)]
    pub clock_rollback: bool,
    #[serde(default)]
    pub admin_account: bool,
    #[serde(default)]
    pub binary_mismatch: bool,
    #[serde(default)]
    pub uninstall_attempt: bool,
}

impl TamperFlags {
    pub fn any(&self) -> bool {
        self.service_stopped
            || self.clock_rollback
            || self.admin_account
            || self.binary_mismatch
            || self.uninstall_attempt
    }

    pub fn as_flag_names(&self) -> Vec<&'static str> {
        let mut out = Vec::new();
        if self.service_stopped {
            out.push("serviceStopped");
        }
        if self.clock_rollback {
            out.push("clockRollback");
        }
        if self.admin_account {
            out.push("adminAccount");
        }
        if self.binary_mismatch {
            out.push("binaryMismatch");
        }
        if self.uninstall_attempt {
            out.push("uninstallAttempt");
        }
        out
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ForegroundSample {
    pub app_id: Option<String>,
    pub pid: Option<u32>,
    pub process_name: Option<String>,
    pub idle_seconds: u32,
    pub wall_ms: i64,
    pub mono_ms: i64,
}

/// Honest parent copy when child is admin.
pub const DEGRADED_ADMIN: &str =
    "This child account is an administrator. MeritScreen cannot fully enforce rules — use a standard (non-admin) account.";

/// Honest parent copy when Strict (L2) is unavailable.
pub const DEGRADED_STRICT_UNAVAILABLE: &str =
    "Strict shell mode is not available on this Windows edition. Launcher + app gating (L1+L3) still apply.";
