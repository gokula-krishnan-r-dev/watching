//! L3 process/window gating decisions — never touch protected/emergency.

use meritscreen_core::{AppRule, SessionPhase};

use crate::protected::{is_protected_app_id, is_protected_process_name};
use crate::types::ForegroundSample;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum GateAction {
    Allow,
    /// Raise cover / overlay; do not kill.
    Cover {
        app_id: Option<String>,
        pid: Option<u32>,
    },
    /// Terminate process after cover when safe.
    Terminate {
        app_id: Option<String>,
        pid: Option<u32>,
    },
}

#[derive(Debug, Clone)]
pub struct GateContext<'a> {
    pub phase: SessionPhase,
    pub sample: &'a ForegroundSample,
    pub rules: &'a [AppRule],
    /// Prefer terminate over cover when true (fail lock / quiz due).
    pub prefer_terminate: bool,
}

pub fn decide_gate(ctx: GateContext<'_>) -> GateAction {
    let Some(pid) = ctx.sample.pid else {
        return GateAction::Allow;
    };
    if let Some(name) = ctx.sample.process_name.as_deref() {
        if is_protected_process_name(name) {
            return GateAction::Allow;
        }
    }
    if let Some(app_id) = ctx.sample.app_id.as_deref() {
        if is_protected_app_id(app_id) {
            return GateAction::Allow;
        }
        if is_emergency(app_id, ctx.rules) {
            return GateAction::Allow;
        }
    }

    let allowed = match ctx.sample.app_id.as_deref() {
        Some(id) => is_allowlisted(id, ctx.rules),
        None => false,
    };

    let _ = pid;
    match ctx.phase {
        SessionPhase::QuizDue | SessionPhase::Shielded => {
            // Device-wide fail: non-emergency cannot stay usable.
            block(ctx.sample, ctx.prefer_terminate)
        }
        SessionPhase::Idle | SessionPhase::InBlock => {
            if allowed {
                GateAction::Allow
            } else {
                block(ctx.sample, ctx.prefer_terminate)
            }
        }
    }
}

fn block(sample: &ForegroundSample, prefer_terminate: bool) -> GateAction {
    if prefer_terminate {
        GateAction::Terminate {
            app_id: sample.app_id.clone(),
            pid: sample.pid,
        }
    } else {
        GateAction::Cover {
            app_id: sample.app_id.clone(),
            pid: sample.pid,
        }
    }
}

fn is_emergency(app_id: &str, rules: &[AppRule]) -> bool {
    rules
        .iter()
        .any(|r| (r.app_id == app_id || r.package_or_bundle_id == app_id) && r.is_emergency)
}

fn is_allowlisted(app_id: &str, rules: &[AppRule]) -> bool {
    rules
        .iter()
        .any(|r| (r.app_id == app_id || r.package_or_bundle_id == app_id) && r.allowed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use meritscreen_core::AppRule;

    fn sample(app_id: &str, pid: u32, proc: &str) -> ForegroundSample {
        ForegroundSample {
            app_id: Some(app_id.into()),
            pid: Some(pid),
            process_name: Some(proc.into()),
            idle_seconds: 0,
            wall_ms: 0,
            mono_ms: 0,
        }
    }

    #[test]
    fn never_touches_protected_or_emergency() {
        let mut phone = AppRule::new("phone", "mac:com.apple.MobileSMS");
        phone.is_emergency = true;
        let rules = vec![phone];
        let s = sample("mac:com.apple.finder", 9, "Finder");
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::Shielded,
                sample: &s,
                rules: &rules,
                prefer_terminate: true,
            }),
            GateAction::Allow
        );
        let s = sample("mac:com.apple.MobileSMS", 42, "Messages");
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::Shielded,
                sample: &s,
                rules: &rules,
                prefer_terminate: true,
            }),
            GateAction::Allow
        );
    }

    #[test]
    fn blocks_non_allowed_during_block() {
        let browser = AppRule::new("browser", "mac:com.apple.Safari");
        let rules = vec![browser];
        let s = sample("mac:com.spotify.client", 100, "Spotify");
        assert!(matches!(
            decide_gate(GateContext {
                phase: SessionPhase::InBlock,
                sample: &s,
                rules: &rules,
                prefer_terminate: false,
            }),
            GateAction::Cover { .. }
        ));
        let s = sample("mac:com.apple.Safari", 101, "Safari");
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::InBlock,
                sample: &s,
                rules: &rules,
                prefer_terminate: false,
            }),
            GateAction::Allow
        );
    }

    #[test]
    fn fail_lock_terminates_non_emergency() {
        let browser = AppRule::new("browser", "win:browser");
        let rules = vec![browser];
        let s = sample("win:browser", 55, "chrome.exe");
        assert!(matches!(
            decide_gate(GateContext {
                phase: SessionPhase::Shielded,
                sample: &s,
                rules: &rules,
                prefer_terminate: true,
            }),
            GateAction::Terminate { .. }
        ));
    }
}
