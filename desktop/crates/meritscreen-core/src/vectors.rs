//! JSON golden-vector runner for session / quiz engines.
//!
//! Files live under `desktop/vectors/`. Android/iOS should eventually share them.

use std::fs;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::policy::{AppRule, ChildPolicy};
use crate::quiz_mode::QuizMode;
use crate::session::{SessionEngine, SessionPhase, SessionSnapshot};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SessionVector {
    pub id: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub policy: ChildPolicy,
    #[serde(default)]
    pub rule: Option<AppRule>,
    #[serde(default)]
    pub initial: SessionSnapshot,
    pub steps: Vec<VectorStep>,
    pub expect: ExpectSnapshot,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum VectorStep {
    OpenApp {
        #[serde(rename = "nowMs")]
        now_ms: i64,
    },
    StartInterval {
        #[serde(rename = "nowMs")]
        now_ms: i64,
    },
    Tick {
        #[serde(rename = "nowMs")]
        now_ms: i64,
        #[serde(default = "default_true")]
        is_app_active: bool,
    },
    QuizPass {
        #[serde(rename = "nowMs")]
        now_ms: i64,
        #[serde(default)]
        is_weekend: bool,
    },
    QuizFail {
        #[serde(rename = "nowMs")]
        now_ms: i64,
    },
    ExpireShield {
        #[serde(rename = "nowMs")]
        now_ms: i64,
    },
    EndFailLock,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExpectSnapshot {
    pub phase: SessionPhase,
    #[serde(default)]
    pub minutes_used_today: Option<u32>,
    #[serde(default)]
    pub minutes_accrued_in_block: Option<f32>,
    #[serde(default)]
    pub block_duration_minutes: Option<u32>,
    #[serde(default)]
    pub can_launch_rule: Option<bool>,
    #[serde(default)]
    pub shielded_until_ms: Option<i64>,
}

pub fn vectors_root() -> PathBuf {
    // desktop/crates/meritscreen-core → desktop/vectors
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../vectors")
        .canonicalize()
        .unwrap_or_else(|_| Path::new(env!("CARGO_MANIFEST_DIR")).join("../../vectors"))
}

pub fn load_session_vectors() -> Result<Vec<SessionVector>, String> {
    let dir = vectors_root().join("session");
    if !dir.exists() {
        return Err(format!("missing vectors dir {}", dir.display()));
    }
    let mut out = Vec::new();
    for entry in fs::read_dir(&dir).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        let raw = fs::read_to_string(&path).map_err(|e| e.to_string())?;
        let v: SessionVector =
            serde_json::from_str(&raw).map_err(|e| format!("{}: {e}", path.display()))?;
        out.push(v);
    }
    out.sort_by(|a, b| a.id.cmp(&b.id));
    Ok(out)
}

pub fn run_session_vector(v: &SessionVector) -> Result<(), String> {
    let mut state = v.initial.clone();
    // Stable day key so on_new_day does not wipe mid-vector.
    if state.day_key.is_empty() {
        state.day_key = "2026-10-05".into();
    }
    let rule = v
        .rule
        .clone()
        .unwrap_or_else(|| AppRule::new("app", "com.example.app"));
    let policy = v.policy.clone();

    let day = "2026-10-05";
    state.day_key = day.into();

    for step in &v.steps {
        state = match step {
            VectorStep::OpenApp { now_ms } => {
                SessionEngine::open_app(state, *now_ms, &rule, &policy)
            }
            VectorStep::StartInterval { now_ms } => {
                SessionEngine::start_device_interval(state, *now_ms, &policy)
            }
            VectorStep::Tick {
                now_ms,
                is_app_active,
            } => SessionEngine::tick_on_day(state, *now_ms, &policy, *is_app_active, day),
            VectorStep::QuizPass { now_ms, is_weekend } => {
                SessionEngine::on_quiz_passed(state, *now_ms, Some(&rule), &policy, *is_weekend)
            }
            VectorStep::QuizFail { now_ms } => {
                SessionEngine::on_quiz_failed(state, *now_ms, &policy, Some(&rule))
            }
            VectorStep::ExpireShield { now_ms } => {
                SessionEngine::expire_shield_if_needed(state, *now_ms)
            }
            VectorStep::EndFailLock => SessionEngine::end_fail_lock(state),
        };
        state.day_key = day.into();
    }

    if state.phase != v.expect.phase {
        return Err(format!(
            "{}: phase got {:?} want {:?}",
            v.id, state.phase, v.expect.phase
        ));
    }
    if let Some(m) = v.expect.minutes_used_today {
        if state.minutes_used_today != m {
            return Err(format!(
                "{}: minutes_used_today got {} want {}",
                v.id, state.minutes_used_today, m
            ));
        }
    }
    if let Some(m) = v.expect.minutes_accrued_in_block {
        if (state.minutes_accrued_in_block - m).abs() > 0.05 {
            return Err(format!(
                "{}: minutes_accrued_in_block got {} want {}",
                v.id, state.minutes_accrued_in_block, m
            ));
        }
    }
    if let Some(b) = v.expect.block_duration_minutes {
        if state.block_duration_minutes != b {
            return Err(format!(
                "{}: block_duration_minutes got {} want {}",
                v.id, state.block_duration_minutes, b
            ));
        }
    }
    if let Some(want) = v.expect.can_launch_rule {
        let got = SessionEngine::can_launch(&state, &rule, &policy);
        if got != want {
            return Err(format!("{}: can_launch got {got} want {want}", v.id));
        }
    }
    if let Some(until) = v.expect.shielded_until_ms {
        if state.device_shielded_until_elapsed_ms != Some(until) {
            return Err(format!(
                "{}: shielded_until got {:?} want {}",
                v.id, state.device_shielded_until_elapsed_ms, until
            ));
        }
    }
    let _ = QuizMode::AppBlock; // keep import useful for vector authors
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_session_vectors_pass() {
        let vectors = load_session_vectors().expect("load vectors");
        assert!(
            !vectors.is_empty(),
            "expected session vectors under desktop/vectors/session"
        );
        for v in &vectors {
            run_session_vector(v).unwrap_or_else(|e| panic!("{e}"));
        }
    }
}
