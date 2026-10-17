//! Runtime-tunable product limits. Call sites must not hard-code these numbers.
//! Mirrors Android `:core:common` `AppConfig`.

/// Maximum children per parent family.
pub const MAX_CHILDREN_PER_PARENT: u32 = 5;

pub const PARENT_PIN_MIN_LENGTH: u32 = 4;
pub const PARENT_PIN_MAX_LENGTH: u32 = 4;
pub const PARENT_PIN_MAX_ATTEMPTS: u32 = 5;
pub const PARENT_PIN_LOCKOUT_MINUTES: u32 = 5;

pub const PAIRING_TOKEN_TTL_MINUTES: u32 = 10;
pub const PAIRING_CODE_LENGTH: u32 = 6;
pub const PAIRING_CONSUME_MAX_ATTEMPTS: u32 = 8;

pub const EMAIL_OTP_LENGTH: u32 = 6;
pub const EMAIL_OTP_TTL_MINUTES: u32 = 10;
pub const EMAIL_OTP_RESEND_COOLDOWN_SECONDS: u32 = 42;
pub const EMAIL_OTP_MAX_ATTEMPTS: u32 = 5;

/// Default app allowance / block minutes (Android parity).
pub const DEFAULT_BLOCK_MINUTES: u32 = 15;
pub const DEFAULT_COOLDOWN_MINUTES: u32 = 15;
/// Desktop recommended quiz cadence when `quizMode=device_interval`.
pub const DEFAULT_QUIZ_INTERVAL_MINUTES: u32 = 15;

pub const SESSION_CHUNK_MIN_MINUTES: u32 = 2;
pub const SESSION_CHUNK_MAX_MINUTES: u32 = 240;

pub const DEFAULT_QUESTIONS_PER_QUIZ: u32 = 3;
pub const DEFAULT_PASS_SCORE_PERCENT: u32 = 70;

pub const USAGE_FLUSH_INTERVAL_SECONDS: u32 = 45;
pub const SESSION_PERSIST_INTERVAL_SECONDS: u32 = 45;

/// Pre-warm Tauri quiz UI this many seconds before block expiry (docs/15 §15).
pub const QUIZ_PREWARM_SECONDS: u32 = 45;

/// Guardian idle poll when no active child session (event-driven; 1 Hz only when active).
pub const GUARDIAN_IDLE_POLL_SECONDS: u32 = 5;

/// Idle threshold before active-use clock pauses (seconds).
pub const IDLE_THRESHOLD_SECONDS: u32 = 150;

/// Wall-clock jump backward past this → fail closed + `tamperFlags.clockRollback`.
pub const CLOCK_ROLLBACK_THRESHOLD_MS: i64 = 5 * 60 * 1000;

/// Debounce before rescanning installed apps after filesystem churn (seconds).
pub const INVENTORY_RESCAN_DEBOUNCE_SECONDS: u32 = 30;

/// Max apps uploaded per inventory sync (cost discipline).
pub const INVENTORY_UPLOAD_MAX_APPS: u32 = 400;

pub const POLICY_PULL_INTERVAL_HOURS: u32 = 6;
/// How often a paired child device re-pulls `policy/current` so parent changes
/// (desktop, Android, or console) show up live without re-pairing. One small
/// document read per interval per device.
pub const POLICY_PULL_INTERVAL_SECONDS: u32 = 30;
pub const USAGE_UPLOAD_INTERVAL_HOURS: u32 = 2;
pub const HEARTBEAT_INTERVAL_MINUTES: u32 = 30;

pub const DEVICE_ONLINE_THRESHOLD_MINUTES: u32 = 15;
pub const QUIZ_LOCKOUT_SECONDS: u32 = 30;

/// Guardian RSS budget (MB) — docs/15 §15 (release / production).
pub const GUARDIAN_RSS_BUDGET_MB: u32 = 25;

/// Debug/CI RSS ceiling (unoptimized builds carry symbols + larger allocator churn).
pub const GUARDIAN_RSS_BUDGET_DEBUG_MB: u32 = 120;

/// Legacy display fallback only. Enforcement/UI must use
/// `effective_ceiling_opt`: a null parent ceiling means "no daily limit",
/// never silently 120.
pub const DEFAULT_DAILY_CEILING_MINUTES: u32 = 120;

/// Extra minutes on pass when weekend bonus is enabled.
pub const WEEKEND_BONUS_EXTRA_MINUTES: u32 = 5;

/// Coerce interval/block minutes into the allowed range.
pub fn coerce_chunk_minutes(raw: u32) -> u32 {
    raw.clamp(SESSION_CHUNK_MIN_MINUTES, SESSION_CHUNK_MAX_MINUTES)
}

/// Parent "bonus time" grants (today only, reset at the child's local midnight).
pub const BONUS_STEP_SMALL_MINUTES: u32 = 15;
pub const BONUS_STEP_LARGE_MINUTES: u32 = 30;
/// Emergency unlock = a one-off +60 min bonus for today (never touches the permanent limit).
pub const EMERGENCY_UNLOCK_MINUTES: u32 = 60;
/// Hard cap on bonus minutes granted for a single day.
pub const BONUS_MAX_MINUTES_PER_DAY: u32 = 240;

/// Hard cap on minutes earned/unlocked beyond the daily ceiling in one day
/// (quiz earn-more extension + Parent PIN unlocks share this budget).
pub const CEILING_EXTENSION_MAX_MINUTES_PER_DAY: u32 = 240;

/// Minutes a Parent PIN unlock grants beyond the daily ceiling.
pub const PARENT_PIN_UNLOCK_MINUTES: u32 = 15;
