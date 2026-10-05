//! Shared Guardian runtime state (IPC handlers + watchdog + enforcement).

use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

use meritscreen_core::{AppRule, ChildPolicy, DeviceRole, SessionPhase, SessionSnapshot};
use meritscreen_enforcement::{ClockIntegrity, EnforcementTier, ForegroundSample, TamperFlags};
use parking_lot::Mutex;

use crate::sync::SyncFlags;

pub const AGENT_STALE_AFTER: Duration = Duration::from_secs(15);

/// Production: 15s. Tests may set `MERITSCREEN_AGENT_STALE_MS` for faster stale detection.
pub fn agent_stale_after() -> Duration {
    if let Ok(raw) = std::env::var("MERITSCREEN_AGENT_STALE_MS") {
        if let Ok(ms) = raw.parse::<u64>() {
            return Duration::from_millis(ms.max(1));
        }
    }
    AGENT_STALE_AFTER
}

pub struct GuardianState {
    pub running: AtomicBool,
    pub session: Mutex<SessionSnapshot>,
    pub role: Mutex<DeviceRole>,
    pub last_agent_heartbeat: Mutex<Option<Instant>>,
    pub agent_pid: Mutex<Option<u32>>,
    pub tamper_service_stopped: AtomicBool,
    pub tamper: Mutex<TamperFlags>,
    pub tier: Mutex<EnforcementTier>,
    pub strict_enabled: Mutex<bool>,
    pub clock: Mutex<ClockIntegrity>,
    pub session_elapsed_ms: Mutex<i64>,
    pub policy: Mutex<ChildPolicy>,
    pub rules: Mutex<Vec<AppRule>>,
    pub last_foreground: Mutex<Option<ForegroundSample>>,
    pub last_gate_cover: AtomicBool,
    pub sync_flags: SyncFlags,
}

impl Default for GuardianState {
    fn default() -> Self {
        Self {
            running: AtomicBool::new(true),
            session: Mutex::new(SessionSnapshot::default()),
            role: Mutex::new(DeviceRole::Unassigned),
            last_agent_heartbeat: Mutex::new(None),
            agent_pid: Mutex::new(None),
            tamper_service_stopped: AtomicBool::new(false),
            tamper: Mutex::new(TamperFlags::default()),
            tier: Mutex::new(EnforcementTier::L1L3),
            strict_enabled: Mutex::new(false),
            clock: Mutex::new(ClockIntegrity::default()),
            session_elapsed_ms: Mutex::new(0),
            policy: Mutex::new(ChildPolicy {
                quiz_mode: meritscreen_core::QuizMode::DeviceInterval,
                ..ChildPolicy::default()
            }),
            rules: Mutex::new(crate::enforce::default_demo_rules()),
            last_foreground: Mutex::new(None),
            last_gate_cover: AtomicBool::new(false),
            sync_flags: SyncFlags::default(),
        }
    }
}

impl GuardianState {
    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    pub fn note_agent(&self, pid: u32) {
        *self.agent_pid.lock() = Some(pid);
        *self.last_agent_heartbeat.lock() = Some(Instant::now());
    }

    pub fn agent_is_stale(&self) -> bool {
        match *self.last_agent_heartbeat.lock() {
            None => true,
            Some(t) => t.elapsed() > agent_stale_after(),
        }
    }

    pub fn phase(&self) -> SessionPhase {
        self.session.lock().phase
    }

    pub fn ui_required(&self) -> bool {
        matches!(self.phase(), SessionPhase::QuizDue | SessionPhase::Shielded)
            || self.last_gate_cover.load(Ordering::SeqCst)
            || self.ui_prewarm()
    }

    /// Pre-warm WebView ~30–60s before quiz due (docs/15 §15).
    pub fn ui_prewarm(&self) -> bool {
        self.session.lock().quiz_prewarm_due()
    }

    /// Child device or non-idle phase → 1 Hz watchdog; otherwise idle poll (d10).
    pub fn session_active(&self) -> bool {
        matches!(*self.role.lock(), DeviceRole::Child)
            || matches!(
                self.phase(),
                SessionPhase::InBlock | SessionPhase::QuizDue | SessionPhase::Shielded
            )
    }
}
