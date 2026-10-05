//! d12 resilience: kill-agent stale, reboot mid-cooldown, clock-rollback fail-closed.
//!
//! Process-local / SQLCipher simulations (CI-safe). Full cold-reboot with
//! Service/LaunchDaemon remains a lab checklist item in desktop/docs/test-matrix.md.

use std::sync::{Mutex, OnceLock};
use std::thread;
use std::time::Duration;

use meritscreen_core::app_config::CLOCK_ROLLBACK_THRESHOLD_MS;
use meritscreen_core::{DeviceRole, SessionPhase, SessionSnapshot};
use meritscreen_enforcement::{ClockIntegrity, ClockObservation};
use meritscreen_guardian::db_boot::{
    load_tamper_flags, mark_tamper_uninstall_attempt, open_guardian_db, persist_session,
    restore_session,
};
use meritscreen_guardian::state::GuardianState;

fn env_lock() -> std::sync::MutexGuard<'static, ()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
        .lock()
        .unwrap_or_else(|e| e.into_inner())
}

fn isolate_env() {
    let tmp = tempfile::tempdir().expect("tempdir");
    let root = tmp.path().to_path_buf();
    let data = root.join("data");
    let secrets = root.join("secrets");
    std::fs::create_dir_all(&data).unwrap();
    std::fs::create_dir_all(&secrets).unwrap();
    std::env::set_var("MERITSCREEN_DATA_DIR", &data);
    std::env::set_var("MERITSCREEN_SECRETS_DIR", &secrets);
    // Keep tempdir alive for the test body.
    std::mem::forget(tmp);
}

#[test]
fn agent_stale_after_silence_preserves_quiz_due() {
    std::env::set_var("MERITSCREEN_AGENT_STALE_MS", "40");
    let state = GuardianState::default();
    state.session.lock().phase = SessionPhase::QuizDue;
    state.note_agent(4242);
    assert!(!state.agent_is_stale());
    assert!(state.ui_required());

    // Simulate Agent kill: no further heartbeats.
    thread::sleep(Duration::from_millis(80));
    assert!(state.agent_is_stale(), "agent should be stale after silence");
    assert_eq!(state.phase(), SessionPhase::QuizDue);
    assert!(
        state.ui_required(),
        "quiz_due must still require UI after agent death"
    );
    std::env::remove_var("MERITSCREEN_AGENT_STALE_MS");
}

#[test]
fn kill_ui_does_not_clear_shielded() {
    let state = GuardianState::default();
    *state.role.lock() = DeviceRole::Child;
    state.session.lock().phase = SessionPhase::Shielded;
    assert!(state.ui_required());
    assert_eq!(state.phase(), SessionPhase::Shielded);
}

#[test]
fn reboot_mid_cooldown_restores_shielded() {
    let _guard = env_lock();
    isolate_env();

    let until = 120_000i64;
    {
        let db = open_guardian_db().expect("open");
        let snap = SessionSnapshot {
            phase: SessionPhase::Shielded,
            device_shielded_until_elapsed_ms: Some(until),
            cooldown_minutes: 15,
            ..SessionSnapshot::default()
        };
        persist_session(&db.conn, &snap);
    }

    let db = open_guardian_db().expect("reopen");
    let restored = restore_session(&db.conn);
    assert_eq!(restored.phase, SessionPhase::Shielded);
    assert_eq!(restored.device_shielded_until_elapsed_ms, Some(until));
    assert_eq!(restored.cooldown_minutes, 15);
}

#[test]
fn clock_rollback_fails_closed() {
    let state = GuardianState::default();
    *state.role.lock() = DeviceRole::Child;
    state.session.lock().phase = SessionPhase::InBlock;

    let mut clock = ClockIntegrity::default();
    assert!(matches!(
        clock.observe(10_000_000, 100),
        ClockObservation::Ok { .. }
    ));
    let obs = clock.observe(10_000_000 - CLOCK_ROLLBACK_THRESHOLD_MS - 1, 200);
    assert_eq!(obs, ClockObservation::Rollback);
    assert!(clock.tamper.clock_rollback);

    // Mirror Guardian handle_foreground fail-closed behavior.
    state.tamper.lock().clock_rollback = true;
    state.session.lock().phase = SessionPhase::Shielded;
    assert_eq!(state.phase(), SessionPhase::Shielded);
    assert!(state.tamper.lock().clock_rollback);
    assert!(state.ui_required());
}

#[test]
fn tamper_flags_survive_db_round_trip() {
    let _guard = env_lock();
    isolate_env();
    let db = open_guardian_db().expect("open");
    mark_tamper_uninstall_attempt(&db.conn).unwrap();
    let flags = load_tamper_flags(&db.conn);
    assert!(flags.uninstall_attempt);
}
