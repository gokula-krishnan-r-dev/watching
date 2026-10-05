//! Pure session engine — identical transitions to Kotlin/Swift (+ device_interval).
//! No Firebase / OS types. Call sites supply monotonic elapsed ms and policy.

use serde::{Deserialize, Serialize};

use crate::app_config;
use crate::policy::{AppRule, ChildPolicy};
use crate::quiz_mode::QuizMode;

/// Runtime phase of the child session engine (docs/07). Fail is always device-wide.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum SessionPhase {
    #[default]
    Idle,
    InBlock,
    QuizDue,
    Shielded,
}

impl SessionPhase {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Idle => "idle",
            Self::InBlock => "in_block",
            Self::QuizDue => "quiz_due",
            Self::Shielded => "shielded",
        }
    }

    pub fn parse(raw: &str) -> Self {
        match raw.trim().to_ascii_lowercase().as_str() {
            "in_block" | "inblock" => Self::InBlock,
            "quiz_due" | "quizdue" => Self::QuizDue,
            "shielded" => Self::Shielded,
            _ => Self::Idle,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct SessionSnapshot {
    pub phase: SessionPhase,
    pub active_package: Option<String>,
    pub active_app_id: Option<String>,
    pub block_started_elapsed_ms: Option<i64>,
    pub block_duration_minutes: u32,
    pub minutes_accrued_in_block: f32,
    pub device_shielded_until_elapsed_ms: Option<i64>,
    pub cooldown_minutes: u32,
    pub day_key: String,
    pub minutes_used_today: u32,
    pub last_tick_elapsed_ms: Option<i64>,
    pub quiz_grace_until_elapsed_ms: Option<i64>,
}

impl Default for SessionSnapshot {
    fn default() -> Self {
        Self {
            phase: SessionPhase::Idle,
            active_package: None,
            active_app_id: None,
            block_started_elapsed_ms: None,
            block_duration_minutes: app_config::DEFAULT_BLOCK_MINUTES,
            minutes_accrued_in_block: 0.0,
            device_shielded_until_elapsed_ms: None,
            cooldown_minutes: app_config::DEFAULT_COOLDOWN_MINUTES,
            day_key: today_key(),
            minutes_used_today: 0,
            last_tick_elapsed_ms: None,
            quiz_grace_until_elapsed_ms: None,
        }
    }
}

impl SessionSnapshot {
    pub fn remaining_block_minutes(&self) -> u32 {
        let rem = self.block_duration_minutes as f32 - self.minutes_accrued_in_block;
        rem.max(0.0) as u32
    }

    pub fn remaining_cooldown_seconds(&self, now_elapsed_ms: i64) -> u32 {
        let until = match self.device_shielded_until_elapsed_ms {
            Some(v) => v,
            None => return 0,
        };
        ((until - now_elapsed_ms).max(0) / 1000) as u32
    }

    /// Seconds until the current block expires and a quiz is due (`InBlock` only).
    pub fn seconds_until_quiz_due(&self) -> Option<u32> {
        if self.phase != SessionPhase::InBlock {
            return None;
        }
        let rem_min = self.block_duration_minutes as f32 - self.minutes_accrued_in_block;
        Some((rem_min.max(0.0) * 60.0) as u32)
    }

    /// True when the UI should pre-warm before quiz due (docs/15 §15).
    pub fn quiz_prewarm_due(&self) -> bool {
        match self.seconds_until_quiz_due() {
            Some(secs) => secs <= app_config::QUIZ_PREWARM_SECONDS,
            None => false,
        }
    }

    pub fn daily_remaining_minutes(&self, policy: &ChildPolicy) -> u32 {
        let ceiling = policy
            .daily_ceiling_minutes
            .unwrap_or(app_config::DEFAULT_DAILY_CEILING_MINUTES);
        ceiling.saturating_sub(self.minutes_used_today)
    }
}

pub fn today_key() -> String {
    chrono::Utc::now().date_naive().to_string()
}

/// Pure session engine. Fail lock is always device-wide for all non-emergency apps.
pub struct SessionEngine;

impl SessionEngine {
    pub fn on_new_day(snapshot: SessionSnapshot, day_key: &str) -> SessionSnapshot {
        if snapshot.day_key == day_key {
            return snapshot;
        }
        let mut next = snapshot;
        let was_in_block = next.phase == SessionPhase::InBlock;
        next.day_key = day_key.to_string();
        next.minutes_used_today = 0;
        next.minutes_accrued_in_block = 0.0;
        if was_in_block {
            next.phase = SessionPhase::Idle;
            next.active_package = None;
            next.active_app_id = None;
        }
        next.block_started_elapsed_ms = None;
        next.quiz_grace_until_elapsed_ms = None;
        next
    }

    pub fn tick(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        policy: &ChildPolicy,
        is_app_active: bool,
    ) -> SessionSnapshot {
        Self::tick_on_day(
            snapshot,
            now_elapsed_ms,
            policy,
            is_app_active,
            &today_key(),
        )
    }

    /// Same as [`tick`] but with an explicit calendar day (for golden vectors / tests).
    pub fn tick_on_day(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        policy: &ChildPolicy,
        is_app_active: bool,
        day_key: &str,
    ) -> SessionSnapshot {
        let mut state = Self::on_new_day(snapshot, day_key);
        state = Self::expire_shield_if_needed(state, now_elapsed_ms);

        if state.phase != SessionPhase::InBlock {
            return state;
        }

        let last_tick = state.last_tick_elapsed_ms;
        let delta_ms = if let (Some(last), true) = (last_tick, is_app_active) {
            (now_elapsed_ms - last).max(0)
        } else if is_app_active
            && state.block_started_elapsed_ms.is_some()
            && state.minutes_accrued_in_block == 0.0
        {
            (now_elapsed_ms - state.block_started_elapsed_ms.unwrap_or(now_elapsed_ms)).max(0)
        } else {
            0
        };

        if is_app_active && delta_ms > 0 {
            let previous_accrued_ms =
                (state.minutes_accrued_in_block as f64 * 60_000.0).round() as i64;
            let new_accrued_ms = previous_accrued_ms + delta_ms;
            let new_accrued_minutes = new_accrued_ms as f64 / 60_000.0;

            let previous_completed = (previous_accrued_ms / 60_000) as u32;
            let new_completed = (new_accrued_ms / 60_000) as u32;
            let newly = new_completed.saturating_sub(previous_completed);
            let today = state.minutes_used_today + newly;

            state.minutes_accrued_in_block = new_accrued_minutes as f32;
            state.minutes_used_today = today;
            state.last_tick_elapsed_ms = Some(now_elapsed_ms);

            let ceiling = policy
                .daily_ceiling_minutes
                .unwrap_or(app_config::DEFAULT_DAILY_CEILING_MINUTES);
            let under_grace = state
                .quiz_grace_until_elapsed_ms
                .is_some_and(|g| now_elapsed_ms < g);
            let ceiling_hit = today >= ceiling && !under_grace;
            let block_hit =
                new_accrued_minutes >= f64::from(state.block_duration_minutes) && !under_grace;

            if ceiling_hit
                || (block_hit
                    && matches!(
                        policy.quiz_mode,
                        QuizMode::AppBlock | QuizMode::DeviceInterval
                    ))
            {
                state.phase = SessionPhase::QuizDue;
                state.block_started_elapsed_ms = None;
            }
        } else {
            state.last_tick_elapsed_ms = Some(now_elapsed_ms);
        }

        state
    }

    pub fn open_app(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        rule: &AppRule,
        policy: &ChildPolicy,
    ) -> SessionSnapshot {
        let day = if snapshot.day_key.is_empty() {
            today_key()
        } else {
            snapshot.day_key.clone()
        };
        let mut state = Self::tick_on_day(snapshot, now_elapsed_ms, policy, false, &day);
        if rule.is_emergency || Self::is_emergency_package(&rule.package_or_bundle_id, policy) {
            return state;
        }
        if !rule.allowed || policy.paused {
            return state;
        }
        if matches!(state.phase, SessionPhase::Shielded | SessionPhase::QuizDue) {
            return state;
        }

        let under_grace = state
            .quiz_grace_until_elapsed_ms
            .is_some_and(|g| now_elapsed_ms < g);
        if let Some(ceiling) = policy.daily_ceiling_minutes {
            if state.minutes_used_today >= ceiling && !under_grace {
                state.phase = SessionPhase::QuizDue;
                return state;
            }
        }

        // Device interval: app switches do not reset accrued active-use time.
        if policy.quiz_mode == QuizMode::DeviceInterval
            && state.phase == SessionPhase::InBlock
            && state.active_package.is_some()
        {
            state.active_package = Some(rule.package_or_bundle_id.clone());
            state.active_app_id = Some(rule.app_id.clone());
            state.last_tick_elapsed_ms = Some(now_elapsed_ms);
            return state;
        }

        if state.active_package.as_deref() == Some(rule.package_or_bundle_id.as_str())
            && state.phase == SessionPhase::InBlock
        {
            state.last_tick_elapsed_ms = Some(now_elapsed_ms);
            return state;
        }

        let block_minutes = if policy.quiz_mode == QuizMode::DeviceInterval {
            app_config::coerce_chunk_minutes(policy.quiz_interval_minutes)
        } else {
            let raw = if rule.block_minutes > 0 {
                rule.block_minutes
            } else {
                policy.default_block_minutes
            };
            raw.clamp(1, 240)
        };

        match policy.quiz_mode {
            QuizMode::EverySession => {
                state.phase = SessionPhase::QuizDue;
                state.active_package = Some(rule.package_or_bundle_id.clone());
                state.active_app_id = Some(rule.app_id.clone());
                state.block_duration_minutes = block_minutes;
                state.minutes_accrued_in_block = 0.0;
                state.block_started_elapsed_ms = None;
                state.last_tick_elapsed_ms = Some(now_elapsed_ms);
            }
            _ => {
                state.phase = SessionPhase::InBlock;
                state.active_package = Some(rule.package_or_bundle_id.clone());
                state.active_app_id = Some(rule.app_id.clone());
                state.block_duration_minutes = block_minutes;
                state.minutes_accrued_in_block = 0.0;
                state.block_started_elapsed_ms = Some(now_elapsed_ms);
                state.last_tick_elapsed_ms = Some(now_elapsed_ms);
                state.cooldown_minutes = rule.cooldown_minutes.clamp(1, 180);
                state.quiz_grace_until_elapsed_ms = None;
            }
        }
        state
    }

    /// Begin device-interval active-use clock without a specific app (desktop).
    pub fn start_device_interval(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        policy: &ChildPolicy,
    ) -> SessionSnapshot {
        if policy.quiz_mode != QuizMode::DeviceInterval {
            return snapshot;
        }
        let mut state = Self::expire_shield_if_needed(snapshot, now_elapsed_ms);
        if matches!(state.phase, SessionPhase::Shielded | SessionPhase::QuizDue) {
            return state;
        }
        if state.phase == SessionPhase::InBlock {
            state.last_tick_elapsed_ms = Some(now_elapsed_ms);
            return state;
        }
        let minutes = app_config::coerce_chunk_minutes(policy.quiz_interval_minutes);
        state.phase = SessionPhase::InBlock;
        state.block_duration_minutes = minutes;
        state.minutes_accrued_in_block = 0.0;
        state.block_started_elapsed_ms = Some(now_elapsed_ms);
        state.last_tick_elapsed_ms = Some(now_elapsed_ms);
        state.quiz_grace_until_elapsed_ms = None;
        state
    }

    pub fn on_quiz_passed(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        rule: Option<&AppRule>,
        policy: &ChildPolicy,
        is_weekend: bool,
    ) -> SessionSnapshot {
        let grant = rule
            .map(|r| r.grant_on_pass_minutes)
            .unwrap_or(policy.default_block_minutes)
            .clamp(
                app_config::SESSION_CHUNK_MIN_MINUTES,
                app_config::SESSION_CHUNK_MAX_MINUTES,
            );

        let mut extra = if policy.rewards_enabled {
            policy.extra_minutes_on_pass
        } else {
            0
        };
        if policy.rewards_enabled && policy.weekend_bonus_enabled && is_weekend {
            extra += app_config::WEEKEND_BONUS_EXTRA_MINUTES;
        }

        let block = if policy.quiz_mode == QuizMode::DeviceInterval {
            app_config::coerce_chunk_minutes(policy.quiz_interval_minutes + extra)
        } else {
            (grant + extra).clamp(
                app_config::SESSION_CHUNK_MIN_MINUTES,
                app_config::SESSION_CHUNK_MAX_MINUTES,
            )
        };

        let grace_until = now_elapsed_ms + i64::from(block) * 60_000;

        SessionSnapshot {
            phase: SessionPhase::InBlock,
            block_duration_minutes: block,
            minutes_accrued_in_block: 0.0,
            block_started_elapsed_ms: Some(now_elapsed_ms),
            last_tick_elapsed_ms: Some(now_elapsed_ms),
            device_shielded_until_elapsed_ms: None,
            quiz_grace_until_elapsed_ms: Some(grace_until),
            active_package: rule
                .map(|r| r.package_or_bundle_id.clone())
                .or(snapshot.active_package),
            active_app_id: rule.map(|r| r.app_id.clone()).or(snapshot.active_app_id),
            ..snapshot
        }
    }

    pub fn on_quiz_failed(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        policy: &ChildPolicy,
        rule: Option<&AppRule>,
    ) -> SessionSnapshot {
        // Failed retry during cooldown restarts the cooldown (docs/07).
        let cooldown = rule
            .map(|r| r.cooldown_minutes)
            .unwrap_or(policy.default_cooldown_minutes)
            .clamp(1, 180);
        SessionSnapshot {
            phase: SessionPhase::Shielded,
            device_shielded_until_elapsed_ms: Some(now_elapsed_ms + i64::from(cooldown) * 60_000),
            cooldown_minutes: cooldown,
            block_started_elapsed_ms: None,
            minutes_accrued_in_block: 0.0,
            last_tick_elapsed_ms: Some(now_elapsed_ms),
            quiz_grace_until_elapsed_ms: None,
            ..snapshot
        }
    }

    pub fn expire_shield_if_needed(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
    ) -> SessionSnapshot {
        if snapshot.phase != SessionPhase::Shielded {
            return snapshot;
        }
        match snapshot.device_shielded_until_elapsed_ms {
            None => SessionSnapshot {
                phase: SessionPhase::Idle,
                ..snapshot
            },
            Some(until) if now_elapsed_ms >= until => SessionSnapshot {
                phase: SessionPhase::Idle,
                device_shielded_until_elapsed_ms: None,
                active_package: None,
                active_app_id: None,
                ..snapshot
            },
            Some(_) => snapshot,
        }
    }

    pub fn end_fail_lock(snapshot: SessionSnapshot) -> SessionSnapshot {
        if snapshot.phase != SessionPhase::Shielded
            && snapshot.device_shielded_until_elapsed_ms.is_none()
        {
            return snapshot;
        }
        SessionSnapshot {
            phase: SessionPhase::Idle,
            device_shielded_until_elapsed_ms: None,
            active_package: None,
            active_app_id: None,
            block_started_elapsed_ms: None,
            minutes_accrued_in_block: 0.0,
            quiz_grace_until_elapsed_ms: None,
            ..snapshot
        }
    }

    pub fn can_launch(snapshot: &SessionSnapshot, rule: &AppRule, policy: &ChildPolicy) -> bool {
        if rule.is_emergency || Self::is_emergency_package(&rule.package_or_bundle_id, policy) {
            return true;
        }
        if !rule.allowed || policy.paused {
            return false;
        }
        if snapshot.phase == SessionPhase::Shielded {
            return false;
        }
        if let Some(ceiling) = policy.daily_ceiling_minutes {
            if snapshot.minutes_used_today >= ceiling {
                let under_grace = snapshot.quiz_grace_until_elapsed_ms.is_some();
                if !under_grace {
                    return false;
                }
            }
        }
        true
    }

    pub fn is_emergency_package(package_or_bundle_id: &str, policy: &ChildPolicy) -> bool {
        let lower = package_or_bundle_id.to_ascii_lowercase();
        if lower.contains("dialer") || lower.contains("telecom") || lower.contains("phone") {
            return true;
        }
        policy
            .emergency_apps
            .iter()
            .any(|e| e.eq_ignore_ascii_case(package_or_bundle_id))
    }

    pub fn is_weekend_local() -> bool {
        use chrono::{Datelike, Local, Weekday};
        matches!(Local::now().weekday(), Weekday::Sat | Weekday::Sun)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn youtube() -> AppRule {
        AppRule {
            app_id: "yt".into(),
            package_or_bundle_id: "com.google.android.youtube".into(),
            display_name: "YouTube".into(),
            allowed: true,
            block_minutes: 30,
            grant_on_pass_minutes: 30,
            cooldown_minutes: 15,
            is_emergency: false,
        }
    }

    #[test]
    fn open_app_starts_block() {
        let next = SessionEngine::open_app(
            SessionSnapshot::default(),
            1_000,
            &youtube(),
            &ChildPolicy::default(),
        );
        assert_eq!(next.phase, SessionPhase::InBlock);
        assert_eq!(next.block_duration_minutes, 30);
    }

    #[test]
    fn tick_reaches_quiz_due() {
        let started = SessionEngine::open_app(
            SessionSnapshot::default(),
            0,
            &youtube(),
            &ChildPolicy::default(),
        );
        let after = SessionEngine::tick(started, 30 * 60_000 + 1, &ChildPolicy::default(), true);
        assert_eq!(after.phase, SessionPhase::QuizDue);
    }

    #[test]
    fn fail_shields_device_wide() {
        let in_block = SessionEngine::open_app(
            SessionSnapshot::default(),
            0,
            &youtube(),
            &ChildPolicy::default(),
        );
        let failed = SessionEngine::on_quiz_failed(
            in_block,
            1_000,
            &ChildPolicy::default(),
            Some(&youtube()),
        );
        assert_eq!(failed.phase, SessionPhase::Shielded);
        assert!(!SessionEngine::can_launch(
            &failed,
            &youtube(),
            &ChildPolicy::default()
        ));
        let phone = AppRule {
            is_emergency: true,
            ..AppRule::new("phone", "com.android.dialer")
        };
        assert!(SessionEngine::can_launch(
            &failed,
            &phone,
            &ChildPolicy::default()
        ));
    }

    #[test]
    fn retry_fail_restarts_cooldown() {
        let shielded = SessionEngine::on_quiz_failed(
            SessionSnapshot::default(),
            1_000,
            &ChildPolicy::default(),
            Some(&youtube()),
        );
        let first_until = shielded.device_shielded_until_elapsed_ms.unwrap();
        let again = SessionEngine::on_quiz_failed(
            shielded,
            5_000,
            &ChildPolicy::default(),
            Some(&youtube()),
        );
        let second_until = again.device_shielded_until_elapsed_ms.unwrap();
        assert!(second_until > first_until);
        assert_eq!(again.phase, SessionPhase::Shielded);
    }

    #[test]
    fn device_interval_ticks_to_quiz() {
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 15,
            ..ChildPolicy::default()
        };
        let started = SessionEngine::start_device_interval(SessionSnapshot::default(), 0, &policy);
        assert_eq!(started.phase, SessionPhase::InBlock);
        assert_eq!(started.block_duration_minutes, 15);
        let due = SessionEngine::tick(started, 15 * 60_000 + 1, &policy, true);
        assert_eq!(due.phase, SessionPhase::QuizDue);
    }

    #[test]
    fn device_interval_app_switch_keeps_accrual() {
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 15,
            ..ChildPolicy::default()
        };
        let yt = youtube();
        let other = AppRule::new("chrome", "com.google.Chrome");
        let mut state = SessionEngine::open_app(SessionSnapshot::default(), 0, &yt, &policy);
        state = SessionEngine::tick(state, 5 * 60_000, &policy, true);
        assert_eq!(state.minutes_used_today, 5);
        state = SessionEngine::open_app(state, 5 * 60_000, &other, &policy);
        assert!((state.minutes_accrued_in_block - 5.0).abs() < 0.01);
        assert_eq!(state.active_package.as_deref(), Some("com.google.Chrome"));
    }
}
