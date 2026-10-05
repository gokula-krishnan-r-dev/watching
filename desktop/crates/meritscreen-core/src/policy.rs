//! Local policy + allowlist types used by the session engine (no Firebase).

use serde::{Deserialize, Serialize};

use crate::app_config;
use crate::quiz_mode::QuizMode;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChildPolicy {
    pub quiz_mode: QuizMode,
    /// Used when [`QuizMode::DeviceInterval`]. Default 15.
    #[serde(default = "default_quiz_interval")]
    pub quiz_interval_minutes: u32,
    #[serde(default)]
    pub allow_retry_during_cooldown: bool,
    pub daily_ceiling_minutes: Option<u32>,
    #[serde(default = "default_questions")]
    pub questions_per_quiz: u32,
    #[serde(default = "default_pass_score")]
    pub pass_score_percent: u32,
    #[serde(default = "default_true")]
    pub rewards_enabled: bool,
    #[serde(default)]
    pub weekend_bonus_enabled: bool,
    #[serde(default)]
    pub extra_minutes_on_pass: u32,
    #[serde(default = "default_block")]
    pub default_block_minutes: u32,
    #[serde(default = "default_cooldown")]
    pub default_cooldown_minutes: u32,
    #[serde(default)]
    pub emergency_apps: Vec<String>,
    #[serde(default)]
    pub paused: bool,
}

fn default_quiz_interval() -> u32 {
    app_config::DEFAULT_QUIZ_INTERVAL_MINUTES
}
fn default_questions() -> u32 {
    app_config::DEFAULT_QUESTIONS_PER_QUIZ
}
fn default_pass_score() -> u32 {
    app_config::DEFAULT_PASS_SCORE_PERCENT
}
fn default_block() -> u32 {
    app_config::DEFAULT_BLOCK_MINUTES
}
fn default_cooldown() -> u32 {
    app_config::DEFAULT_COOLDOWN_MINUTES
}
fn default_true() -> bool {
    true
}

impl Default for ChildPolicy {
    fn default() -> Self {
        Self {
            quiz_mode: QuizMode::AppBlock,
            quiz_interval_minutes: app_config::DEFAULT_QUIZ_INTERVAL_MINUTES,
            allow_retry_during_cooldown: true,
            daily_ceiling_minutes: None,
            questions_per_quiz: app_config::DEFAULT_QUESTIONS_PER_QUIZ,
            pass_score_percent: app_config::DEFAULT_PASS_SCORE_PERCENT,
            rewards_enabled: true,
            weekend_bonus_enabled: false,
            extra_minutes_on_pass: 0,
            default_block_minutes: app_config::DEFAULT_BLOCK_MINUTES,
            default_cooldown_minutes: app_config::DEFAULT_COOLDOWN_MINUTES,
            emergency_apps: Vec::new(),
            paused: false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppRule {
    pub app_id: String,
    pub package_or_bundle_id: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default = "default_true")]
    pub allowed: bool,
    #[serde(default = "default_block")]
    pub block_minutes: u32,
    #[serde(default = "default_block")]
    pub grant_on_pass_minutes: u32,
    #[serde(default = "default_cooldown")]
    pub cooldown_minutes: u32,
    #[serde(default)]
    pub is_emergency: bool,
}

impl AppRule {
    pub fn new(app_id: impl Into<String>, package_or_bundle_id: impl Into<String>) -> Self {
        Self {
            app_id: app_id.into(),
            package_or_bundle_id: package_or_bundle_id.into(),
            display_name: String::new(),
            allowed: true,
            block_minutes: app_config::DEFAULT_BLOCK_MINUTES,
            grant_on_pass_minutes: app_config::DEFAULT_BLOCK_MINUTES,
            cooldown_minutes: app_config::DEFAULT_COOLDOWN_MINUTES,
            is_emergency: false,
        }
    }
}
