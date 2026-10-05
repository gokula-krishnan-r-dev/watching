//! Session Agent runtime — IPC to Guardian, foreground sample, UI watchdog, L3 apply.

use std::path::PathBuf;
use std::process::Child;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use anyhow::{Context, Result};
use meritscreen_core::app_config::QUIZ_PREWARM_SECONDS;
use meritscreen_core::SessionPhase;
use meritscreen_enforcement::platform::{apply_gate_action, sample_foreground};
use meritscreen_enforcement::GateAction;
use meritscreen_ipc::{IpcConn, IpcRequest, IpcResponse, IPC_PROTOCOL_VERSION};
use tracing::{info, warn};

use crate::overlay::show_overlay_proof;

struct UiProcess {
    child: Child,
    prewarm: bool,
    spawned_at: Instant,
}

pub fn run(running: Arc<AtomicBool>) -> Result<()> {
    let mut conn = connect_with_retry(running.clone())?;
    let pid = std::process::id();

    match conn.call(&IpcRequest::AgentHello { pid })? {
        IpcResponse::HelloAck { version } => {
            info!(
                version,
                ipc_v = IPC_PROTOCOL_VERSION,
                "agent connected to guardian"
            );
        }
        other => warn!(?other, "unexpected hello response"),
    }

    let mut ui: Option<UiProcess> = None;
    let mut ticks = 0u64;

    while running.load(Ordering::SeqCst) {
        thread::sleep(Duration::from_secs(1));
        ticks += 1;

        if let Err(e) = conn.call(&IpcRequest::AgentHeartbeat { pid }) {
            warn!(error = %e, "heartbeat failed — reconnecting");
            match connect_with_retry(Arc::clone(&running)) {
                Ok(c) => {
                    conn = c;
                    let _ = conn.call(&IpcRequest::AgentHello { pid });
                }
                Err(_) => break,
            }
            continue;
        }

        // Foreground / idle sample → Guardian L3 + active-use clock (~1 Hz while session alive).
        let sample = sample_foreground();
        match conn.call(&IpcRequest::ReportForeground {
            app_id: sample.app_id,
            pid: sample.pid,
            process_name: sample.process_name,
            idle_seconds: sample.idle_seconds,
            wall_ms: sample.wall_ms,
            mono_ms: sample.mono_ms,
        }) {
            Ok(IpcResponse::GateDecision {
                action,
                app_id,
                pid: target_pid,
                reason,
                phase,
            }) => {
                let gate = match action.as_str() {
                    "terminate" => GateAction::Terminate {
                        app_id,
                        pid: target_pid,
                    },
                    "cover" => {
                        ensure_ui(&mut ui, phase, false);
                        GateAction::Cover {
                            app_id,
                            pid: target_pid,
                        }
                    }
                    _ => GateAction::Allow,
                };
                if !matches!(gate, GateAction::Allow) {
                    info!(%reason, ?gate, "l3 gate");
                    if let Err(e) = apply_gate_action(&gate) {
                        warn!(error = %e, "gate apply failed");
                    }
                }
            }
            Ok(other) => warn!(?other, "unexpected foreground response"),
            Err(e) => warn!(error = %e, "foreground report failed"),
        }

        // UI watchdog every other tick (~2s)
        if ticks % 2 == 0 {
            match conn.call(&IpcRequest::UiWatchdogPoll) {
                Ok(IpcResponse::UiRequired {
                    required,
                    phase,
                    prewarm,
                }) => {
                    if required {
                        ensure_ui(&mut ui, phase, prewarm);
                    } else {
                        quit_ui_if_idle(&mut ui);
                    }
                    // Cap pre-warm residency — never leave WebView forever.
                    if let Some(proc) = ui.as_ref() {
                        if proc.prewarm
                            && !matches!(phase, SessionPhase::QuizDue | SessionPhase::Shielded)
                            && proc.spawned_at.elapsed()
                                > Duration::from_secs(u64::from(QUIZ_PREWARM_SECONDS) + 30)
                        {
                            quit_ui_if_idle(&mut ui);
                        }
                    }
                }
                Ok(other) => warn!(?other, "unexpected ui poll response"),
                Err(e) => warn!(error = %e, "ui poll failed"),
            }
        }
    }

    quit_ui_if_idle(&mut ui);
    info!("agent stopped");
    Ok(())
}

fn connect_with_retry(running: Arc<AtomicBool>) -> Result<IpcConn> {
    for attempt in 1..=30 {
        if !running.load(Ordering::SeqCst) {
            anyhow::bail!("stopped while connecting");
        }
        match IpcConn::connect() {
            Ok(c) => return Ok(c),
            Err(e) => {
                warn!(attempt, error = %e, "waiting for guardian IPC");
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    Err(anyhow::anyhow!("could not connect to guardian IPC"))
}

fn quit_ui_if_idle(ui: &mut Option<UiProcess>) {
    if let Some(mut proc) = ui.take() {
        let _ = proc.child.kill();
        info!("quit UI (idle / post pre-warm)");
    }
}

fn ensure_ui(ui: &mut Option<UiProcess>, phase: SessionPhase, prewarm: bool) {
    let dead = ui
        .as_mut()
        .map(|p| matches!(p.child.try_wait(), Ok(Some(_))))
        .unwrap_or(true);
    if !dead {
        // Upgrade prewarm → live overlay without respawn when quiz becomes due.
        if let Some(proc) = ui.as_mut() {
            if proc.prewarm && !prewarm {
                proc.prewarm = false;
            }
        }
        return;
    }
    let reason = phase.as_str();
    if !prewarm {
        show_overlay_proof(reason);
    }

    match spawn_ui_stub(reason, prewarm) {
        Ok(c) => {
            info!(phase = reason, prewarm, "spawned UI host");
            *ui = Some(UiProcess {
                child: c,
                prewarm,
                spawned_at: Instant::now(),
            });
        }
        Err(e) => warn!(error = %e, "UI spawn failed (overlay proof still attempted)"),
    }
}

fn ui_binary_path() -> PathBuf {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            #[cfg(windows)]
            let candidate = dir.join("meritscreen-ui.exe");
            #[cfg(not(windows))]
            let candidate = dir.join("meritscreen-ui");
            if candidate.exists() {
                return candidate;
            }
        }
    }
    PathBuf::from("meritscreen-ui")
}

fn spawn_ui_stub(reason: &str, prewarm: bool) -> Result<Child> {
    let path = ui_binary_path();
    let mut cmd = std::process::Command::new(&path);
    cmd.arg("--overlay").arg(reason);
    if prewarm {
        cmd.arg("--prewarm");
    }
    cmd.spawn()
        .with_context(|| format!("spawn {}", path.display()))
}
