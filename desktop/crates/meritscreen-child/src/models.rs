//! Child UI DTOs — local only, no Firebase types.

use meritscreen_core::{
    QuizAnswerFeedback, QuizChoice, QuizSessionResult, SessionPhase, SessionSnapshot,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherApp {
    pub app_id: String,
    pub display_name: String,
    pub package_or_bundle_id: String,
    pub is_emergency: bool,
    /// Optional icon color token name (UI maps to CSS vars — no hex in logic).
    pub icon_tone: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChecklistItem {
    pub id: String,
    pub label: String,
    pub done: bool,
    pub recommended: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizPromptDto {
    pub id: String,
    pub prompt: String,
    pub choices: Vec<QuizChoiceDto>,
    pub index: u32,
    pub total: u32,
    pub teach_title: Option<String>,
    pub teach_body: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizChoiceDto {
    pub id: String,
    pub text: String,
}

impl From<&QuizChoice> for QuizChoiceDto {
    fn from(c: &QuizChoice) -> Self {
        Self {
            id: c.id.clone(),
            text: c.text.clone(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizLockDto {
    /// Seconds remaining on C09c lock after a wrong answer.
    pub remaining_seconds: u32,
    pub concept_line: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ChildScreen {
    Pairing,
    Checklist,
    Launcher,
    Quiz,
    FailLock,
    Ceiling,
    PinMenu,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChildUiState {
    pub paired: bool,
    pub screen: ChildScreen,
    pub phase: SessionPhase,
    pub child_name: String,
    pub apps: Vec<LauncherApp>,
    pub checklist: Vec<ChecklistItem>,
    pub checklist_complete: bool,
    pub minutes_used_today: u32,
    pub minutes_remaining_block: u32,
    #[serde(default)]
    pub seconds_remaining_block: u32,
    #[serde(default)]
    pub block_duration_minutes: u32,
    pub cooldown_seconds: u32,
    pub daily_remaining_minutes: u32,
    pub ceiling_hit: bool,
    pub quiz: Option<QuizPromptDto>,
    pub quiz_lock: Option<QuizLockDto>,
    pub last_feedback: Option<QuizAnswerFeedback>,
    pub last_result: Option<QuizSessionResult>,
    pub pack_source: String,
    pub elapsed_ms: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub active_app_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub active_app_name: Option<String>,
    #[serde(default)]
    pub minutes_by_app: std::collections::HashMap<String, u32>,
    #[serde(default)]
    pub app_labels: std::collections::HashMap<String, String>,
    #[serde(default)]
    pub quiz_minutes_today: u32,
    #[serde(default)]
    pub quiz_attempts_today: u32,
    #[serde(default)]
    pub quiz_passes_today: u32,
}

impl Default for ChildUiState {
    fn default() -> Self {
        Self {
            paired: false,
            screen: ChildScreen::Pairing,
            phase: SessionPhase::Idle,
            child_name: String::new(),
            apps: Vec::new(),
            checklist: default_checklist(),
            checklist_complete: false,
            minutes_used_today: 0,
            minutes_remaining_block: 0,
            seconds_remaining_block: 0,
            block_duration_minutes: meritscreen_core::app_config::DEFAULT_BLOCK_MINUTES,
            cooldown_seconds: 0,
            daily_remaining_minutes: 0,
            ceiling_hit: false,
            quiz: None,
            quiz_lock: None,
            last_feedback: None,
            last_result: None,
            pack_source: "builtin".into(),
            elapsed_ms: 0,
            active_app_id: None,
            active_app_name: None,
            minutes_by_app: std::collections::HashMap::new(),
            app_labels: std::collections::HashMap::new(),
            quiz_minutes_today: 0,
            quiz_attempts_today: 0,
            quiz_passes_today: 0,
        }
    }
}

pub fn default_checklist() -> Vec<ChecklistItem> {
    vec![
        ChecklistItem {
            id: "standard_account".into(),
            label: "Use a standard (non-admin) child account".into(),
            done: false,
            recommended: true,
        },
        ChecklistItem {
            id: "notifications".into(),
            label: "Allow notifications for quiz reminders".into(),
            done: false,
            recommended: false,
        },
        ChecklistItem {
            id: "accessibility_ok".into(),
            label: "Understand MeritScreen cannot replace the macOS Dock/Finder".into(),
            done: false,
            recommended: true,
        },
    ]
}

pub fn snapshot_metrics(
    snap: &SessionSnapshot,
    policy_ceiling: Option<u32>,
    now_ms: i64,
) -> (u32, u32, u32, u32, bool) {
    let rem_block = snap.remaining_block_minutes();
    let rem_seconds = snap.seconds_until_quiz_due().unwrap_or(rem_block * 60);
    let cooldown = snap.remaining_cooldown_seconds(now_ms);
    let ceiling =
        policy_ceiling.unwrap_or(meritscreen_core::app_config::DEFAULT_DAILY_CEILING_MINUTES);
    let daily_rem = ceiling.saturating_sub(snap.minutes_used_today);
    let ceiling_hit = snap.minutes_used_today >= ceiling;
    (rem_block, rem_seconds, cooldown, daily_rem, ceiling_hit)
}

