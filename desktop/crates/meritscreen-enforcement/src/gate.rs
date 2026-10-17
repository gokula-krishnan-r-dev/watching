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
    /// Parent paused this child: every non-emergency app is blocked.
    pub paused: bool,
}

pub fn decide_gate(ctx: GateContext<'_>) -> GateAction {
    let Some(pid) = ctx.sample.pid else {
        return GateAction::Allow;
    };
    if let Some(name) = ctx.sample.process_name.as_deref() {
        if is_protected_process_name(name) {
            return GateAction::Allow;
        }
        if is_emergency(name, ctx.rules) {
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
        None => match ctx.sample.process_name.as_deref() {
            Some(name) => is_allowlisted(name, ctx.rules),
            None => false,
        },
    };

    let _ = pid;
    if ctx.paused {
        return block(ctx.sample, ctx.prefer_terminate);
    }
    match ctx.phase {
        // Daily-limit lock is device-wide, exactly like the fail lock.
        SessionPhase::QuizDue | SessionPhase::Shielded | SessionPhase::CeilingReached => {
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

fn strip_platform_prefix(s: &str) -> &str {
    if let Some(rest) = s.strip_prefix("mac:") {
        rest
    } else if let Some(rest) = s.strip_prefix("win:") {
        rest
    } else if let Some(rest) = s.strip_prefix("linux:") {
        rest
    } else {
        s
    }
}

fn id_matches(rule_val: &str, sample_val: &str) -> bool {
    if rule_val.eq_ignore_ascii_case(sample_val) {
        return true;
    }
    let r_stripped = strip_platform_prefix(rule_val);
    let s_stripped = strip_platform_prefix(sample_val);
    if r_stripped.eq_ignore_ascii_case(s_stripped) {
        return true;
    }
    // Check if filename stem matches (e.g., "calc.exe" vs "win:c:/windows/system32/calc.exe")
    let r_base = r_stripped.rsplit(['/', '\\']).next().unwrap_or(r_stripped);
    let s_base = s_stripped.rsplit(['/', '\\']).next().unwrap_or(s_stripped);
    r_base.eq_ignore_ascii_case(s_base)
}

fn is_emergency(id: &str, rules: &[AppRule]) -> bool {
    rules.iter().any(|r| {
        (id_matches(&r.app_id, id) || id_matches(&r.package_or_bundle_id, id)) && r.is_emergency
    })
}

fn is_allowlisted(id: &str, rules: &[AppRule]) -> bool {
    rules.iter().any(|r| {
        (id_matches(&r.app_id, id) || id_matches(&r.package_or_bundle_id, id)) && r.allowed
    })
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
            is_screen_locked: false,
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
                paused: false,
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
                paused: false,
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
                paused: false,
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
                paused: false,
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
                paused: false,
            }),
            GateAction::Terminate { .. }
        ));
    }

    #[test]
    fn case_insensitive_and_prefix_matching() {
        let calc = AppRule::new("calc", "win:c:/windows/system32/calc.exe");
        let safari = AppRule::new("safari", "com.apple.Safari");
        let rules = vec![calc, safari];

        // Mixed case and prefix stripping
        let s = sample("win:C:/Windows/System32/calc.exe", 102, "calc.exe");
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::InBlock,
                sample: &s,
                rules: &rules,
                prefer_terminate: false,
                paused: false,
            }),
            GateAction::Allow
        );

        // Rule has no prefix, sample has "mac:"
        let s2 = sample("mac:com.apple.safari", 103, "Safari");
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::InBlock,
                sample: &s2,
                rules: &rules,
                prefer_terminate: false,
                paused: false,
            }),
            GateAction::Allow
        );
    }

    #[test]
    fn process_name_fallback_matching() {
        let mut notes = AppRule::new("notes", "Notes.exe");
        notes.allowed = true;
        let rules = vec![notes];

        // Sample has no app_id, but matching process_name
        let mut s = sample("unknown_app", 200, "Notes.exe");
        s.app_id = None;
        assert_eq!(
            decide_gate(GateContext {
                phase: SessionPhase::InBlock,
                sample: &s,
                rules: &rules,
                prefer_terminate: false,
                paused: false,
            }),
            GateAction::Allow
        );
    }
}

#[cfg(test)]
mod ceiling_gate_tests {
    use super::*;
    fn sample(app_id: &str, pid: u32, proc: &str) -> ForegroundSample {
        ForegroundSample { app_id: Some(app_id.into()), pid: Some(pid), process_name: Some(proc.into()), idle_seconds: 0, wall_ms: 0, mono_ms: 0, is_screen_locked: false }
    }
    #[test]
    fn ceiling_lock_terminates_non_emergency_but_not_emergency() {
        let mut phone = AppRule::new("phone", "mac:com.apple.MobileSMS");
        phone.is_emergency = true;
        let rules = vec![phone, AppRule::new("browser", "mac:com.apple.Safari")];
        let s = sample("mac:com.apple.Safari", 77, "Safari");
        assert!(matches!(decide_gate(GateContext { phase: SessionPhase::CeilingReached, sample: &s, rules: &rules, prefer_terminate: true, paused: false }), GateAction::Terminate { .. }));
        let s = sample("mac:com.apple.MobileSMS", 78, "Messages");
        assert_eq!(decide_gate(GateContext { phase: SessionPhase::CeilingReached, sample: &s, rules: &rules, prefer_terminate: true, paused: false }), GateAction::Allow);
    }
    #[test]
    fn parent_pause_blocks_even_allowed_apps() {
        let rules = vec![AppRule::new("browser", "mac:com.apple.Safari")];
        let s = sample("mac:com.apple.Safari", 79, "Safari");
        assert!(matches!(decide_gate(GateContext { phase: SessionPhase::InBlock, sample: &s, rules: &rules, prefer_terminate: false, paused: true }), GateAction::Cover { .. }));
    }
}
