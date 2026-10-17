//! Child UI DTOs — local only, no Firebase types.

use meritscreen_core::{
    AppRule, ChildPolicy, ExplorerProgress, NurseryTeachItem, NurseryVideo, QuizAnswerFeedback,
    QuizChoice, QuizSessionResult, SessionPhase, SessionSnapshot, StickerUnlock,
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
    /// TAP_TEXT | TAP_IMAGE | COUNT_AND_TAP | MATCH_COLOR_SHAPE (Android interactionType).
    #[serde(default = "default_interaction")]
    pub interaction_type: String,
    #[serde(default)]
    pub prompt_tag: Option<String>,
    #[serde(default)]
    pub prompt_count: Option<i32>,
}

fn default_interaction() -> String {
    "TAP_TEXT".into()
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizChoiceDto {
    pub id: String,
    pub text: String,
    #[serde(default)]
    pub image_tag: Option<String>,
    #[serde(default)]
    pub audio_key: Option<String>,
}

impl From<&QuizChoice> for QuizChoiceDto {
    fn from(c: &QuizChoice) -> Self {
        Self {
            id: c.id.clone(),
            text: c.text.clone(),
            image_tag: c.image_tag.clone(),
            audio_key: c.audio_key.clone(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizLockDto {
    /// Seconds remaining on the 30 s Learn-More lock after a wrong answer.
    pub remaining_seconds: u32,
    pub concept_line: String,
    /// Why the picked answer was wrong (Android whyWrongByChoice).
    #[serde(default)]
    pub why_line: String,
    /// The correct choice, named so the lock teaches rather than just waits.
    #[serde(default)]
    pub correct_choice_text: String,
    /// Mini-lesson shown during the lock (question miniLesson or concept explainer).
    #[serde(default)]
    pub lesson_title: String,
    #[serde(default)]
    pub lesson_body: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ChildScreen {
    Pairing,
    Checklist,
    Launcher,
    Quiz,
    Nursery,
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
    /// Parent's daily limit incl. today's bonus; `None` = no daily limit set
    /// (display "No daily limit", never a made-up 120).
    #[serde(default)]
    pub daily_limit_minutes: Option<u32>,
    /// Parent's quiz interval (minutes) currently in force on this device.
    #[serde(default)]
    pub quiz_interval_minutes: u32,
    pub ceiling_hit: bool,
    /// Parent bonus minutes in force today (already included in `daily_remaining_minutes`).
    #[serde(default)]
    pub bonus_minutes_today: u32,
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
    /// Set when no (distinct) questions are available for this child's age band.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub quiz_error: Option<String>,
    #[serde(default)]
    pub show_explanations: bool,
    #[serde(default)]
    pub early_learner: bool,
    #[serde(default)]
    pub explorer: ExplorerProgress,
    #[serde(default)]
    pub stickers: Vec<StickerUnlock>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_award: Option<LastAwardDto>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub nursery: Option<NurseryDto>,
    /// Parent policy allows retrying the quiz during the fail-lock cooldown.
    #[serde(default)]
    pub can_retry_during_cooldown: bool,
}

/// One sticker/XP award to celebrate (Android `Completed` step fields).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LastAwardDto {
    pub sticker_title: Option<String>,
    pub sticker_emoji: Option<String>,
    pub sticker_stage_label: Option<String>,
    pub xp_gained: u32,
    pub leveled_up: bool,
    pub weekend_bonus_applied: bool,
}

/// Teach-first content for early learners (Android NurseryVideo / NurseryTeach steps).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryDto {
    pub video: Option<NurseryVideo>,
    pub video_category: Option<String>,
    pub items: Vec<NurseryTeachItem>,
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
            daily_limit_minutes: None,
            quiz_interval_minutes: meritscreen_core::app_config::DEFAULT_QUIZ_INTERVAL_MINUTES,
            ceiling_hit: false,
            bonus_minutes_today: 0,
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
            quiz_error: None,
            show_explanations: true,
            early_learner: false,
            explorer: ExplorerProgress::default(),
            stickers: Vec::new(),
            last_award: None,
            nursery: None,
            can_retry_during_cooldown: false,
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
    // `None` ceiling = parent set no daily limit: never invent 120 minutes.
    let (daily_rem, ceiling_hit) = match policy_ceiling {
        Some(ceiling) => (
            ceiling.saturating_sub(snap.minutes_used_today),
            snap.minutes_used_today >= ceiling,
        ),
        None => (0, false),
    };
    (rem_block, rem_seconds, cooldown, daily_rem, ceiling_hit)
}


/// Credentials from `consumePairingToken` (resolved by the UI host via
/// `meritscreen-parent`; this crate stays Firebase-free).
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingCredential {
    pub family_id: String,
    pub child_id: String,
    pub device_id: String,
    pub display_name: String,
    pub age_band: String,
    pub parent_pin_hash: Option<String>,
    /// The paired child's actual policy from Firestore `policy/current`,
    /// fetched by the UI host right after `consumePairingToken`. When present
    /// it replaces every local default before the first timer starts.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub policy: Option<ChildPolicy>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub app_rules: Vec<AppRule>,
    /// Never logged or serialized to the WebView.
    #[serde(skip_serializing)]
    pub refresh_token: String,
}
