//! Parent-facing DTOs (no Firebase SDK types).

use meritscreen_core::app_config::MAX_CHILDREN_PER_PARENT;
use meritscreen_core::{AppRule, ChildPolicy, DevicePlatform, FailLockScope, QuizMode};
use serde::{Deserialize, Serialize};

pub const MAX_CHILDREN: u32 = MAX_CHILDREN_PER_PARENT;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ParentProfile {
    pub uid: String,
    pub email: String,
    pub family_id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChildCard {
    pub child_id: String,
    pub display_name: String,
    pub age_band: String,
    /// Honest zeros when unknown — never invent activity.
    pub minutes_used_today: u32,
    pub minutes_remaining_today: Option<u32>,
    pub paired_device_count: u32,
    pub platform_hint: Option<String>,
    pub quiz_mode: QuizMode,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceEnforcementStatus {
    pub guardian_state: String,
    pub enforcement_tier: String,
    pub tamper_flags: Vec<String>,
    /// Honest copy when enforcement is degraded (admin, Strict unavailable, …).
    pub degraded_message: Option<String>,
    pub strict_available: bool,
    /// Desktop inventory for allowlist (P13). Empty until child syncs.
    pub installed_apps: Vec<InventoryApp>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InventoryApp {
    pub app_id: String,
    pub label: String,
    pub allowed: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChildDetail {
    pub card: ChildCard,
    pub policy: ChildPolicy,
    pub app_rules: Vec<AppRule>,
    /// Always device-wide; exposed so UI never offers per-app-only fail.
    pub fail_lock_scope: FailLockScope,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub enforcement: Option<DeviceEnforcementStatus>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingOffer {
    pub child_id: String,
    pub code: String,
    pub secret: String,
    pub qr_payload: String,
    pub expires_at_epoch_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationPrefs {
    pub quiz_results: bool,
    pub fail_lock_alerts: bool,
    pub tamper_alerts: bool,
}

impl Default for NotificationPrefs {
    fn default() -> Self {
        Self {
            quiz_results: true,
            fail_lock_alerts: true,
            tamper_alerts: true,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DashboardSnapshot {
    pub profile: ParentProfile,
    pub children: Vec<ChildCard>,
    pub max_children: u32,
}

impl DashboardSnapshot {
    pub fn empty(profile: ParentProfile) -> Self {
        Self {
            profile,
            children: Vec::new(),
            max_children: MAX_CHILDREN_PER_PARENT,
        }
    }
}

/// Whether device_interval controls should show for this child.
pub fn show_device_interval(platform_hint: Option<&str>) -> bool {
    platform_hint
        .and_then(DevicePlatform::parse)
        .map(|p| p.is_desktop())
        .unwrap_or(false)
}
