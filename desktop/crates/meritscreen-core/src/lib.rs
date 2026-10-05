//! MeritScreen desktop core — product tunables, session / quiz engines, shared types.
//!
//! Engines are pure Rust (no Firebase). Golden vectors: `desktop/vectors/`.

#![forbid(unsafe_code)]

pub mod app_config;
pub mod appearance;
pub mod error;
pub mod i18n;
pub mod platform;
pub mod policy;
pub mod quiz;
pub mod quiz_mode;
pub mod role;
pub mod session;
pub mod ui_state;
pub mod vectors;

pub use appearance::{Appearance, DesignTokens, DARK_TOKENS, LIGHT_TOKENS};
pub use error::AppError;
pub use i18n::{t as i18n_t, t_args as i18n_t_args};
pub use platform::DevicePlatform;
pub use policy::{AppRule, ChildPolicy};
pub use quiz::{
    load_builtin_bank, AdaptiveQuizEngine, AgeBand, QuizAnswerFeedback, QuizChoice, QuizQuestion,
    QuizSessionResult, TopicSkill, BUILTIN_QUIZ_BANK_JSON,
};
pub use quiz_mode::{FailLockScope, QuizMode};
pub use role::DeviceRole;
pub use session::{today_key, SessionEngine, SessionPhase, SessionSnapshot};
pub use ui_state::UiState;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fail_lock_scope_is_always_device_wide() {
        assert_eq!(
            FailLockScope::AllNonEmergency.as_storage_str(),
            "all_non_emergency"
        );
    }

    #[test]
    fn quiz_mode_device_interval_round_trips() {
        assert_eq!(
            QuizMode::from_storage(Some("device_interval")),
            QuizMode::DeviceInterval
        );
        assert_eq!(QuizMode::DeviceInterval.as_storage_str(), "device_interval");
    }

    #[test]
    fn quiz_mode_unknown_falls_back_to_app_block() {
        assert_eq!(QuizMode::from_storage(Some("unknown")), QuizMode::AppBlock);
        assert_eq!(QuizMode::from_storage(None), QuizMode::AppBlock);
    }

    #[test]
    fn desktop_platforms_parse() {
        assert_eq!(
            DevicePlatform::parse("windows"),
            Some(DevicePlatform::Windows)
        );
        assert_eq!(DevicePlatform::parse("macos"), Some(DevicePlatform::Macos));
        assert!(DevicePlatform::Windows.is_desktop());
        assert!(!DevicePlatform::Android.is_desktop());
    }

    #[test]
    fn default_interval_matches_android_block_default() {
        assert_eq!(
            app_config::DEFAULT_QUIZ_INTERVAL_MINUTES,
            app_config::DEFAULT_BLOCK_MINUTES
        );
    }

    #[test]
    fn appearance_round_trips() {
        assert_eq!(Appearance::parse("dark"), Appearance::Dark);
        assert_eq!(Appearance::Light.as_str(), "light");
        assert_eq!(Appearance::parse("nope"), Appearance::System);
    }

    #[test]
    fn ui_state_map_preserves_error() {
        let err = AppError::validation("bad");
        let state: UiState<u32> = UiState::Error(err.clone());
        let mapped: UiState<String> = state.map(|n| n.to_string());
        assert_eq!(mapped, UiState::Error(err));
    }

    #[test]
    fn coerce_chunk_minutes_clamps() {
        assert_eq!(app_config::coerce_chunk_minutes(1), 2);
        assert_eq!(app_config::coerce_chunk_minutes(15), 15);
        assert_eq!(app_config::coerce_chunk_minutes(999), 240);
    }

    #[test]
    fn quiz_prewarm_window() {
        let mut snap = SessionSnapshot::default();
        snap.phase = SessionPhase::InBlock;
        snap.block_duration_minutes = 15;
        snap.minutes_accrued_in_block = 14.4; // ~36s left
        assert!(snap.quiz_prewarm_due());
        snap.minutes_accrued_in_block = 10.0;
        assert!(!snap.quiz_prewarm_due());
    }
}
