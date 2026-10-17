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
    /// Daily ceiling (parent limit + today's bonus + any earned extension) reached.
    /// Hard lock: only emergency apps, Parent PIN unlock, or — when the parent
    /// allows earning beyond the limit — a quiz can lift it. Distinct from
    /// `QuizDue` so a passed interval quiz can never silently exceed the cap.
    CeilingReached,
}

impl SessionPhase {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Idle => "idle",
            Self::InBlock => "in_block",
            Self::QuizDue => "quiz_due",
            Self::Shielded => "shielded",
            Self::CeilingReached => "ceiling_reached",
        }
    }

    pub fn parse(raw: &str) -> Self {
        match raw.trim().to_ascii_lowercase().as_str() {
            "in_block" | "inblock" => Self::InBlock,
            "quiz_due" | "quizdue" => Self::QuizDue,
            "shielded" => Self::Shielded,
            "ceiling_reached" | "ceilingreached" | "daily_limit" => Self::CeilingReached,
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
    /// Latest child-local day (`YYYY-MM-DD`) observed; guards bonus time against clock rollback.
    pub max_local_day: String,
    /// Extra minutes earned today beyond the daily ceiling (quiz earn-more when the
    /// parent allows it, or a Parent PIN unlock). Today-only: cleared at midnight,
    /// capped by `app_config::CEILING_EXTENSION_MAX_MINUTES_PER_DAY`.
    #[serde(default)]
    pub ceiling_extension_minutes: u32,
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
            max_local_day: local_today_key(),
            ceiling_extension_minutes: 0,
        }
    }
}

impl SessionSnapshot {
    pub fn remaining_block_minutes(&self) -> u32 {
        let rem = self.block_duration_minutes as f32 - self.minutes_accrued_in_block;
        rem.max(0.0).ceil() as u32
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
        Some(((rem_min.max(0.0) * 60.0).round()) as u32)
    }

    /// True when the UI should pre-warm before quiz due (docs/15 §15).
    pub fn quiz_prewarm_due(&self) -> bool {
        match self.seconds_until_quiz_due() {
            Some(secs) => secs <= app_config::QUIZ_PREWARM_SECONDS,
            None => false,
        }
    }

    /// Minutes left today, or 0 when the parent set no daily limit
    /// (check [`effective_ceiling_opt`] to distinguish "no limit" from "0 left").
    pub fn daily_remaining_minutes(&self, policy: &ChildPolicy) -> u32 {
        effective_ceiling_opt(policy, self)
            .map(|c| c.saturating_sub(self.minutes_used_today))
            .unwrap_or(0)
    }

    /// Parent bonus minutes in force right now (today only, rollback-safe).
    pub fn active_bonus_minutes(&self, policy: &ChildPolicy) -> u32 {
        policy.active_bonus_minutes(&local_today_key(), &self.max_local_day)
    }

    /// True while a quiz-pass grace window is still running at `now_elapsed_ms`.
    /// (Expiry is time-checked; merely having a stale timestamp is not grace.)
    pub fn under_grace(&self, now_elapsed_ms: i64) -> bool {
        self.quiz_grace_until_elapsed_ms
            .is_some_and(|g| now_elapsed_ms < g)
    }

    /// True when today's usage has reached the effective daily ceiling at `now`.
    pub fn ceiling_reached(&self, policy: &ChildPolicy, now_elapsed_ms: i64) -> bool {
        if self.under_grace(now_elapsed_ms) {
            return false;
        }
        effective_ceiling_opt(policy, self)
            .map(|c| self.minutes_used_today >= c)
            .unwrap_or(false)
    }
}

pub fn today_key() -> String {
    chrono::Utc::now().date_naive().to_string()
}

/// Child-local calendar day; bonus time resets at local midnight.
pub fn local_today_key() -> String {
    chrono::Local::now().date_naive().to_string()
}

/// Daily limit including today's bonus; `None` when the policy has no limit.
pub fn effective_ceiling_opt(policy: &ChildPolicy, snap: &SessionSnapshot) -> Option<u32> {
    policy.daily_ceiling_minutes.map(|c| {
        c + snap.active_bonus_minutes(policy)
            + snap
                .ceiling_extension_minutes
                .min(crate::app_config::CEILING_EXTENSION_MAX_MINUTES_PER_DAY)
    })
}

/// Daily limit including today's bonus, falling back to the default limit.
/// Legacy display helper — do NOT use for enforcement or child UI: a null
/// parent ceiling is "no daily limit" (see [`effective_ceiling_opt`]).
pub fn effective_ceiling(policy: &ChildPolicy, snap: &SessionSnapshot) -> u32 {
    policy
        .daily_ceiling_minutes
        .unwrap_or(app_config::DEFAULT_DAILY_CEILING_MINUTES)
        + snap.active_bonus_minutes(policy)
}

/// Pure session engine. Fail lock is always device-wide for all non-emergency apps.
pub struct SessionEngine;

impl SessionEngine {
    pub fn on_new_day(snapshot: SessionSnapshot, day_key: &str) -> SessionSnapshot {
        if snapshot.day_key == day_key {
            return snapshot;
        }
        // Clock wound back to an earlier day than this device has already seen:
        // never reset today's usage (that would hand back a full day's limit).
        if !snapshot.max_local_day.is_empty() && day_key < snapshot.max_local_day.as_str() {
            return snapshot;
        }
        let mut next = snapshot;
        let was_locked = matches!(
            next.phase,
            SessionPhase::InBlock | SessionPhase::CeilingReached
        );
        next.day_key = day_key.to_string();
        if day_key > next.max_local_day.as_str() {
            next.max_local_day = day_key.to_string();
        }
        next.minutes_used_today = 0;
        next.minutes_accrued_in_block = 0.0;
        next.ceiling_extension_minutes = 0;
        if was_locked {
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
            &local_today_key(),
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
        let local = local_today_key();
        if local > state.max_local_day {
            state.max_local_day = local;
        }
        state = Self::expire_shield_if_needed(state, now_elapsed_ms);

        // Parent pause: time does not accrue and the lock (if any) stays.
        if policy.paused {
            if state.phase == SessionPhase::InBlock {
                state.last_tick_elapsed_ms = Some(now_elapsed_ms);
            }
            return state;
        }

        // Daily-limit lock: no accrual while locked; lift only when a live
        // policy change (bonus / raised limit / new day) covers usage again.
        if state.phase == SessionPhase::CeilingReached {
            return Self::lift_ceiling_if_covered(state, policy, now_elapsed_ms);
        }

        // A session restored or re-policy'd already at the cap locks at once,
        // even before the next active minute accrues.
        if matches!(state.phase, SessionPhase::Idle | SessionPhase::InBlock)
            && state.ceiling_reached(policy, now_elapsed_ms)
        {
            state.phase = SessionPhase::CeilingReached;
            state.block_started_elapsed_ms = None;
            state.quiz_grace_until_elapsed_ms = None;
            return state;
        }

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

            // No parent daily limit ⇒ the daily ceiling can never be hit.
            // The ceiling wins over the interval quiz: it is a hard daily cap,
            // not another chance to earn past it (unless earn-more is on and a
            // quiz pass explicitly extends it — see `on_quiz_passed`).
            if state.ceiling_reached(policy, now_elapsed_ms) {
                state.phase = SessionPhase::CeilingReached;
                state.block_started_elapsed_ms = None;
                state.quiz_grace_until_elapsed_ms = None;
            } else if new_accrued_minutes >= f64::from(state.block_duration_minutes)
                && matches!(
                    policy.quiz_mode,
                    QuizMode::AppBlock | QuizMode::DeviceInterval
                )
            {
                state.phase = SessionPhase::QuizDue;
                state.block_started_elapsed_ms = None;
                state.quiz_grace_until_elapsed_ms = None;
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
        if matches!(
            state.phase,
            SessionPhase::Shielded | SessionPhase::QuizDue | SessionPhase::CeilingReached
        ) {
            return Self::lift_ceiling_if_covered(state, policy, now_elapsed_ms);
        }

        if state.ceiling_reached(policy, now_elapsed_ms) {
            state.phase = SessionPhase::CeilingReached;
            state.block_started_elapsed_ms = None;
            return state;
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
        if state.phase == SessionPhase::CeilingReached {
            state = Self::lift_ceiling_if_covered(state, policy, now_elapsed_ms);
            if state.phase == SessionPhase::CeilingReached {
                return state;
            }
        }
        if state.ceiling_reached(policy, now_elapsed_ms) {
            state.phase = SessionPhase::CeilingReached;
            state.block_started_elapsed_ms = None;
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
        // At the daily ceiling a passed quiz only earns more time when the
        // parent explicitly allows earning beyond the daily limit. Otherwise
        // the hard cap stands and the device stays locked.
        let at_ceiling = effective_ceiling_opt(policy, &snapshot)
            .map(|c| snapshot.minutes_used_today >= c)
            .unwrap_or(false);
        let mut snapshot = snapshot;
        if at_ceiling {
            if !policy.allow_earn_beyond_daily_limit {
                snapshot.phase = SessionPhase::CeilingReached;
                snapshot.block_started_elapsed_ms = None;
                snapshot.quiz_grace_until_elapsed_ms = None;
                return snapshot;
            }
            let grant0 = rule
                .map(|r| r.grant_on_pass_minutes)
                .unwrap_or(policy.default_block_minutes)
                .clamp(
                    app_config::SESSION_CHUNK_MIN_MINUTES,
                    app_config::SESSION_CHUNK_MAX_MINUTES,
                );
            snapshot.ceiling_extension_minutes = (snapshot.ceiling_extension_minutes + grant0)
                .min(app_config::CEILING_EXTENSION_MAX_MINUTES_PER_DAY);
        }
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

    /// Lift the daily-limit lock when usage is covered again (parent bonus,
    /// raised limit, earn-more extension, or a new day). No-op otherwise.
    pub fn lift_ceiling_if_covered(
        snapshot: SessionSnapshot,
        policy: &ChildPolicy,
        now_elapsed_ms: i64,
    ) -> SessionSnapshot {
        if snapshot.phase != SessionPhase::CeilingReached {
            return snapshot;
        }
        if snapshot.ceiling_reached(policy, now_elapsed_ms) {
            return snapshot;
        }
        SessionSnapshot {
            phase: SessionPhase::Idle,
            block_started_elapsed_ms: None,
            minutes_accrued_in_block: 0.0,
            last_tick_elapsed_ms: Some(now_elapsed_ms),
            ..snapshot
        }
    }

    /// Re-evaluate the daily-limit lock against a freshly applied policy
    /// (live parent change): lock a running/idle session that is now over the
    /// ceiling, or release one that is covered again.
    pub fn reconcile_ceiling(
        snapshot: SessionSnapshot,
        policy: &ChildPolicy,
        now_elapsed_ms: i64,
    ) -> SessionSnapshot {
        if snapshot.phase == SessionPhase::CeilingReached {
            return Self::lift_ceiling_if_covered(snapshot, policy, now_elapsed_ms);
        }
        if matches!(snapshot.phase, SessionPhase::InBlock | SessionPhase::Idle)
            && snapshot.ceiling_reached(policy, now_elapsed_ms)
        {
            return SessionSnapshot {
                phase: SessionPhase::CeilingReached,
                block_started_elapsed_ms: None,
                quiz_grace_until_elapsed_ms: None,
                ..snapshot
            };
        }
        snapshot
    }

    /// Parent PIN unlock from the daily-limit lock (or fail lock): grants a
    /// bounded today-only extension and starts a fresh block. The permanent
    /// daily limit is never changed.
    pub fn parent_pin_unlock(
        snapshot: SessionSnapshot,
        now_elapsed_ms: i64,
        unlock_minutes: u32,
    ) -> SessionSnapshot {
        let minutes = unlock_minutes.clamp(1, 240);
        SessionSnapshot {
            phase: SessionPhase::InBlock,
            block_duration_minutes: minutes,
            minutes_accrued_in_block: 0.0,
            block_started_elapsed_ms: Some(now_elapsed_ms),
            last_tick_elapsed_ms: Some(now_elapsed_ms),
            device_shielded_until_elapsed_ms: None,
            quiz_grace_until_elapsed_ms: None,
            ceiling_extension_minutes: (snapshot.ceiling_extension_minutes + minutes)
                .min(app_config::CEILING_EXTENSION_MAX_MINUTES_PER_DAY),
            ..snapshot
        }
    }

    pub fn can_launch(snapshot: &SessionSnapshot, rule: &AppRule, policy: &ChildPolicy) -> bool {
        // Legacy callers (golden vectors) evaluate grace at the last tick.
        Self::can_launch_at(
            snapshot,
            rule,
            policy,
            snapshot.last_tick_elapsed_ms.unwrap_or(0),
        )
    }

    pub fn can_launch_at(
        snapshot: &SessionSnapshot,
        rule: &AppRule,
        policy: &ChildPolicy,
        now_elapsed_ms: i64,
    ) -> bool {
        if rule.is_emergency || Self::is_emergency_package(&rule.package_or_bundle_id, policy) {
            return true;
        }
        if !rule.allowed || policy.paused {
            return false;
        }
        if matches!(
            snapshot.phase,
            SessionPhase::Shielded | SessionPhase::CeilingReached
        ) {
            return false;
        }
        if snapshot.ceiling_reached(policy, now_elapsed_ms) {
            return false;
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

    #[test]
    fn session_continues_and_ticks_across_quiz_pass() {
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 15,
            ..ChildPolicy::default()
        };
        // 1. Initial interval begins at T = 0
        let mut state = SessionEngine::start_device_interval(SessionSnapshot::default(), 0, &policy);
        assert_eq!(state.phase, SessionPhase::InBlock);
        assert_eq!(state.seconds_until_quiz_due(), Some(900));

        // 2. Active usage for 15 minutes reaches quiz due
        state = SessionEngine::tick(state, 15 * 60_000 + 1, &policy, true);
        assert_eq!(state.phase, SessionPhase::QuizDue);

        // 3. Quiz passed at T = 15m + 30s
        let quiz_passed_time = 15 * 60_000 + 30_000;
        state = SessionEngine::on_quiz_passed(state, quiz_passed_time, None, &policy, false);
        assert_eq!(state.phase, SessionPhase::InBlock);
        assert_eq!(state.seconds_until_quiz_due(), Some(900));
        assert_eq!(state.minutes_accrued_in_block, 0.0);

        // 4. Timer continues ticking into the new session block!
        state = SessionEngine::tick(state, quiz_passed_time + 1_000, &policy, true);
        assert_eq!(state.seconds_until_quiz_due(), Some(899));

        state = SessionEngine::tick(state, quiz_passed_time + 60_000, &policy, true);
        assert_eq!(state.seconds_until_quiz_due(), Some(840));
        assert_eq!(state.minutes_used_today, 16);

        // 5. Advancing through the entire second block triggers the next quiz smoothly
        state = SessionEngine::tick(state, quiz_passed_time + 15 * 60_000 + 1, &policy, true);
        assert_eq!(state.phase, SessionPhase::QuizDue);
    }
}

#[cfg(test)]
mod bonus_engine_tests {
    use super::*;

    #[test]
    fn bonus_raises_effective_ceiling_today_only() {
        let mut policy = ChildPolicy { daily_ceiling_minutes: Some(60), ..ChildPolicy::default() };
        let snap = SessionSnapshot { minutes_used_today: 60, ..SessionSnapshot::default() };
        assert_eq!(snap.daily_remaining_minutes(&policy), 0);
        policy.grant_bonus(15, &local_today_key()).unwrap();
        assert_eq!(effective_ceiling_opt(&policy, &snap), Some(75));
        assert_eq!(snap.daily_remaining_minutes(&policy), 15);
        assert_eq!(policy.daily_ceiling_minutes, Some(60));

        // Yesterday's grant does nothing today.
        policy.bonus_day = Some("2000-01-01".into());
        assert_eq!(snap.daily_remaining_minutes(&policy), 0);
    }

    #[test]
    fn no_daily_limit_never_hits_a_fabricated_120_ceiling() {
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 240,
            daily_ceiling_minutes: None,
            ..ChildPolicy::default()
        };
        let started = SessionEngine::start_device_interval(SessionSnapshot::default(), 0, &policy);
        // 130 active minutes: past the old made-up 120, still in the first block.
        let after = SessionEngine::tick(started, 130 * 60_000, &policy, true);
        assert_eq!(after.phase, SessionPhase::InBlock);
        assert_eq!(effective_ceiling_opt(&policy, &after), None);
        assert_eq!(after.daily_remaining_minutes(&policy), 0);
    }

    #[test]
    fn rollback_high_water_blocks_bonus() {
        let mut policy = ChildPolicy { daily_ceiling_minutes: Some(60), ..ChildPolicy::default() };
        policy.grant_bonus(30, &local_today_key()).unwrap();
        // Device already observed a later day ⇒ clock was wound back.
        let snap = SessionSnapshot { max_local_day: "9999-12-31".into(), ..SessionSnapshot::default() };
        assert_eq!(snap.active_bonus_minutes(&policy), 0);
    }

    #[test]
    fn tick_advances_high_water_and_old_snapshots_load() {
        let mut v = serde_json::to_value(SessionSnapshot::default()).unwrap();
        v.as_object_mut().unwrap().remove("maxLocalDay");
        let mut old: SessionSnapshot = serde_json::from_value(v).unwrap();
        old.max_local_day.clear();
        let t = SessionEngine::tick(old, 0, &ChildPolicy::default(), false);
        assert_eq!(t.max_local_day, local_today_key());
    }
}

#[cfg(test)]
mod ceiling_tests {
    use super::*;

    fn policy_ceiling(min: u32) -> ChildPolicy {
        ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 30,
            daily_ceiling_minutes: Some(min),
            ..ChildPolicy::default()
        }
    }

    fn at_ceiling(min: u32) -> SessionSnapshot {
        let policy = policy_ceiling(min);
        let started = SessionEngine::start_device_interval(
            SessionSnapshot { day_key: local_today_key(), max_local_day: local_today_key(), ..Default::default() },
            0,
            &policy,
        );
        SessionEngine::tick_on_day(started, i64::from(min) * 60_000, &policy, true, &local_today_key())
    }

    #[test]
    fn limit_reached_triggers_ceiling_lock_not_quiz() {
        let st = at_ceiling(15);
        assert_eq!(st.phase, SessionPhase::CeilingReached);
        assert_eq!(st.minutes_used_today, 15);
        assert!(!SessionEngine::can_launch_at(&st, &AppRule::new("a", "app.a"), &policy_ceiling(15), 900_000));
        let emergency = AppRule { is_emergency: true, ..AppRule::new("p", "app.phone") };
        assert!(SessionEngine::can_launch_at(&st, &emergency, &policy_ceiling(15), 900_000));
    }

    #[test]
    fn quiz_pass_cannot_bypass_ceiling_unless_earn_more_allowed() {
        let st = at_ceiling(15);
        let locked = SessionEngine::on_quiz_passed(st.clone(), 900_000, None, &policy_ceiling(15), false);
        assert_eq!(locked.phase, SessionPhase::CeilingReached);
        let mut earn = policy_ceiling(15);
        earn.allow_earn_beyond_daily_limit = true;
        let freed = SessionEngine::on_quiz_passed(st, 900_000, None, &earn, false);
        assert_eq!(freed.phase, SessionPhase::InBlock);
        assert!(freed.ceiling_extension_minutes > 0);
        assert_eq!(effective_ceiling_opt(&earn, &freed), Some(15 + freed.ceiling_extension_minutes));
    }

    #[test]
    fn bonus_lifts_ceiling_lock_and_pin_unlock_is_today_only() {
        let st = at_ceiling(15);
        let mut policy = policy_ceiling(15);
        policy.grant_bonus(15, &local_today_key()).unwrap();
        let lifted = SessionEngine::lift_ceiling_if_covered(st.clone(), &policy, 900_000);
        assert_eq!(lifted.phase, SessionPhase::Idle);
        assert_eq!(policy.daily_ceiling_minutes, Some(15), "bonus never changes the permanent limit");

        let unlocked = SessionEngine::parent_pin_unlock(st, 900_000, app_config::PARENT_PIN_UNLOCK_MINUTES);
        assert_eq!(unlocked.phase, SessionPhase::InBlock);
        assert_eq!(unlocked.ceiling_extension_minutes, 15);
        // Midnight clears the extension and the lock.
        let tomorrow = (chrono::Local::now().date_naive() + chrono::Duration::days(1)).to_string();
        let next = SessionEngine::on_new_day(unlocked, &tomorrow);
        assert_eq!(next.minutes_used_today, 0);
        assert_eq!(next.ceiling_extension_minutes, 0);
        assert_eq!(next.phase, SessionPhase::Idle);
    }

    #[test]
    fn midnight_resets_and_clock_rollback_does_not() {
        let mut st = at_ceiling(15);
        st.phase = SessionPhase::CeilingReached;
        let tomorrow = (chrono::Local::now().date_naive() + chrono::Duration::days(1)).to_string();
        let reset = SessionEngine::on_new_day(st.clone(), &tomorrow);
        assert_eq!(reset.minutes_used_today, 0);
        assert_eq!(reset.phase, SessionPhase::Idle);
        // Winding the clock back to yesterday must not hand back the day.
        let yesterday = (chrono::Local::now().date_naive() - chrono::Duration::days(1)).to_string();
        let rolled = SessionEngine::on_new_day(st.clone(), &yesterday);
        assert_eq!(rolled.minutes_used_today, 15);
        assert_eq!(rolled.phase, SessionPhase::CeilingReached);
        // Nor does a stale grace timestamp count as grace.
        let mut grace = st.clone();
        grace.quiz_grace_until_elapsed_ms = Some(1);
        assert!(grace.ceiling_reached(&policy_ceiling(15), 900_000));
    }

    #[test]
    fn paused_time_does_not_accrue() {
        let mut policy = policy_ceiling(60);
        policy.paused = true;
        let started = SessionEngine::start_device_interval(
            SessionSnapshot { day_key: local_today_key(), max_local_day: local_today_key(), ..Default::default() },
            0, &policy);
        let after = SessionEngine::tick_on_day(started, 600_000, &policy, true, &local_today_key());
        assert_eq!(after.minutes_used_today, 0);
    }

    #[test]
    fn old_snapshot_without_extension_still_loads() {
        let mut v = serde_json::to_value(SessionSnapshot::default()).unwrap();
        v.as_object_mut().unwrap().remove("ceilingExtensionMinutes");
        let snap: SessionSnapshot = serde_json::from_value(v).unwrap();
        assert_eq!(snap.ceiling_extension_minutes, 0);
        assert_eq!(SessionPhase::parse("ceiling_reached"), SessionPhase::CeilingReached);
    }
}
