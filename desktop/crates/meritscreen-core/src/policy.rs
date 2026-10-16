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
    /// Bonus minutes granted by a parent for [`Self::bonus_day`] only (Android `bonusMinutesToday`).
    /// Added on top of `daily_ceiling_minutes`; the permanent limit is never modified.
    #[serde(default)]
    pub bonus_minutes_today: u32,
    /// Child-local calendar day (`YYYY-MM-DD`) the bonus applies to (`bonusDay`). Missing/other day ⇒ no bonus.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bonus_day: Option<String>,
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
            bonus_minutes_today: 0,
            bonus_day: None,
        }
    }
}

impl ChildPolicy {
    /// Bonus minutes in force on `local_day`.
    ///
    /// `high_water_day` is the latest local day this device has observed; if the clock now reads an
    /// earlier day (rollback), no bonus applies — an old grant can't be revived by winding the clock back.
    pub fn active_bonus_minutes(&self, local_day: &str, high_water_day: &str) -> u32 {
        if !high_water_day.is_empty() && local_day < high_water_day {
            return 0;
        }
        match &self.bonus_day {
            Some(d) if d == local_day => self
                .bonus_minutes_today
                .min(app_config::BONUS_MAX_MINUTES_PER_DAY),
            _ => 0,
        }
    }

    /// Add a one-off bonus for `local_day` (resets any bonus from another day). Returns the new total.
    pub fn grant_bonus(&mut self, minutes: u32, local_day: &str) -> Result<u32, String> {
        if minutes == 0 {
            return Err("Bonus must be at least 1 minute.".into());
        }
        let current = match &self.bonus_day {
            Some(d) if d == local_day => self.bonus_minutes_today,
            _ => 0,
        };
        let max = app_config::BONUS_MAX_MINUTES_PER_DAY;
        if current >= max {
            return Err(format!("Today’s bonus is already at the {max}-minute maximum."));
        }
        let total = (current + minutes).min(max);
        self.bonus_minutes_today = total;
        self.bonus_day = Some(local_day.to_string());
        Ok(total)
    }

    /// Remove today's bonus.
    pub fn clear_bonus(&mut self) {
        self.bonus_minutes_today = 0;
        self.bonus_day = None;
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

#[cfg(test)]
mod bonus_tests {
    use super::*;

    #[test]
    fn bonus_applies_only_on_its_day_and_never_changes_limit() {
        let mut p = ChildPolicy { daily_ceiling_minutes: Some(120), ..Default::default() };
        assert_eq!(p.grant_bonus(15, "2026-10-16").unwrap(), 15);
        assert_eq!(p.grant_bonus(30, "2026-10-16").unwrap(), 45);
        assert_eq!(p.daily_ceiling_minutes, Some(120));
        assert_eq!(p.active_bonus_minutes("2026-10-16", "2026-10-16"), 45);
        assert_eq!(p.active_bonus_minutes("2026-10-17", "2026-10-16"), 0, "resets at midnight");
        // A grant on a new day starts from zero.
        assert_eq!(p.grant_bonus(15, "2026-10-17").unwrap(), 15);
    }

    #[test]
    fn clock_rollback_does_not_revive_old_bonus() {
        let mut p = ChildPolicy::default();
        p.grant_bonus(60, "2026-10-16").unwrap();
        // Device has already seen the 17th; clock wound back to the 16th.
        assert_eq!(p.active_bonus_minutes("2026-10-16", "2026-10-17"), 0);
    }

    #[test]
    fn bonus_is_capped() {
        let mut p = ChildPolicy::default();
        for _ in 0..4 {
            p.grant_bonus(60, "d").unwrap();
        }
        assert!(p.grant_bonus(15, "d").is_err());
        assert_eq!(p.bonus_minutes_today, app_config::BONUS_MAX_MINUTES_PER_DAY);
    }

    #[test]
    fn old_policy_json_without_bonus_fields_still_loads() {
        let mut v = serde_json::to_value(ChildPolicy::default()).unwrap();
        let o = v.as_object_mut().unwrap();
        o.remove("bonusMinutesToday");
        o.remove("bonusDay");
        let p: ChildPolicy = serde_json::from_value(v).unwrap();
        assert_eq!(p.bonus_minutes_today, 0);
        assert!(p.bonus_day.is_none());
    }
}
