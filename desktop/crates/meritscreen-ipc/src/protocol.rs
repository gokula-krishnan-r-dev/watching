use meritscreen_core::{Appearance, DeviceRole, SessionPhase};
use serde::{Deserialize, Serialize};

/// Bump when message schema is incompatible.
pub const IPC_PROTOCOL_VERSION: u16 = 3;

/// Well-known local socket / named pipe name (namespaced on Windows).
pub const ENDPOINT_NAME: &str = "meritscreen.guardian.v1";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "cmd", rename_all = "snake_case")]
pub enum IpcRequest {
    Ping,
    AgentHello {
        pid: u32,
    },
    AgentHeartbeat {
        pid: u32,
    },
    GetSnapshot,
    SetAppearance {
        appearance: Appearance,
    },
    SetRole {
        role: DeviceRole,
    },
    QuizDue,
    RaiseLock,
    LaunchApp {
        app_id: String,
    },
    PinOk,
    /// Agent asks Guardian whether UI overlay should be up.
    UiWatchdogPoll,
    /// Agent reports foreground + idle for active-use clock and L3.
    ReportForeground {
        app_id: Option<String>,
        pid: Option<u32>,
        process_name: Option<String>,
        idle_seconds: u32,
        wall_ms: i64,
        mono_ms: i64,
    },
    /// Force inventory rescan (debounced inside Guardian).
    InventoryRescan,
    /// Expedite policy pull (manual refresh / parent nudge).
    PolicyRefresh,
    /// After quiz finalize — expedite usage/quiz upload.
    QuizCompleted {
        attempt_json: String,
    },
    /// Persist child pairing credentials for Guardian sync.
    BindPairing {
        family_id: String,
        child_id: String,
        device_id: String,
        platform: String,
        refresh_token: String,
    },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum IpcResponse {
    Pong {
        version: u16,
    },
    HelloAck {
        version: u16,
    },
    Snapshot(UiSnapshot),
    /// When true, Agent must ensure UI overlay process is running.
    /// `prewarm` means load quiz WebView early; quit-when-idle still applies after due.
    UiRequired {
        required: bool,
        phase: SessionPhase,
        #[serde(default)]
        prewarm: bool,
    },
    /// L3 decision for the Agent to apply (cover / terminate).
    GateDecision {
        action: String,
        app_id: Option<String>,
        pid: Option<u32>,
        reason: String,
        phase: SessionPhase,
    },
    Ok,
    Error {
        message: String,
    },
}

/// UI-facing snapshot. No Firebase types — local state only.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UiSnapshot {
    pub role: DeviceRole,
    pub appearance: Appearance,
    pub phase: SessionPhase,
    #[serde(default)]
    pub enforcement_tier: String,
    #[serde(default)]
    pub guardian_state: String,
    #[serde(default)]
    pub tamper_flags: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub degraded_message: Option<String>,
}

impl Default for UiSnapshot {
    fn default() -> Self {
        Self {
            role: DeviceRole::Unassigned,
            appearance: Appearance::System,
            phase: SessionPhase::Idle,
            enforcement_tier: "L1_L3".into(),
            guardian_state: "running".into(),
            tamper_flags: Vec::new(),
            degraded_message: None,
        }
    }
}
