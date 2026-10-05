//! Guardian runtime loop — DB, IPC server, heartbeat, agent watchdog, L3.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use anyhow::Result;
use meritscreen_core::app_config::{
    GUARDIAN_IDLE_POLL_SECONDS, INVENTORY_RESCAN_DEBOUNCE_SECONDS, SESSION_PERSIST_INTERVAL_SECONDS,
};
use meritscreen_core::{Appearance, DeviceRole, SessionEngine, SessionPhase};
use meritscreen_enforcement::{
    decide_gate, is_active_use, ClockObservation, ForegroundSample, GateAction, GateContext,
    GuardianRuntimeState,
};
use meritscreen_ipc::{
    authorize_peer, note_accepted_peer, peer_identity_from_stream, IpcRequest, IpcResponse,
    IpcServer, UiSnapshot, IPC_PROTOCOL_VERSION,
};
use tracing::{error, info, warn};

use crate::agent_spawn::{spawn_agent_console, spawn_agent_in_user_session};
use crate::db_boot::{
    db_path, load_or_create_db_key, load_tamper_flags, mark_tamper_service_stopped, open_guardian_db,
    persist_session, restore_session, touch_heartbeat, GuardianDb,
};
use crate::enforce::{
    compute_tier, degraded_message, maybe_rescan_inventory, merge_device_runtime, warn_inventory,
};
use crate::state::GuardianState;
use crate::sync::{run_sync_loop, SyncBackends};
use meritscreen_security::SecretStore;

pub type StopFlag = Arc<AtomicBool>;

#[derive(Default)]
pub struct RuntimeOptions {
    /// When true, spawn agent as a normal child (dev / macOS console).
    pub console_mode: bool,
    /// Skip agent spawn (unit/integration tests that only need IPC).
    pub disable_agent_spawn: bool,
    /// When true on shutdown, persist `tamperFlags.serviceStopped`.
    pub mark_tamper_on_stop: bool,
}

pub fn run(opts: RuntimeOptions) -> Result<()> {
    run_with_stop(opts, Arc::new(AtomicBool::new(true)))
}

pub fn run_with_stop(opts: RuntimeOptions, running: StopFlag) -> Result<()> {
    let db = open_guardian_db()?;
    info!(path = %db.path.display(), "guardian database open");

    let state = Arc::new(GuardianState::default());
    {
        let restored = restore_session(&db.conn);
        *state.session.lock() = restored;
        *state.tamper.lock() = load_tamper_flags(&db.conn);
        *state.tier.lock() = compute_tier(*state.strict_enabled.lock());
        if let Ok(true) = maybe_rescan_inventory(&db.conn) {
            state
                .sync_flags
                .inventory_dirty
                .store(true, Ordering::SeqCst);
        }
        let _ = merge_device_runtime(&db.conn, &state, GuardianRuntimeState::Running);
    }

    let server = IpcServer::bind()?;
    info!(
        endpoint = meritscreen_ipc::ENDPOINT_NAME,
        "IPC listener ready"
    );

    let state_accept = Arc::clone(&state);
    let running_accept = Arc::clone(&running);
    let accept = thread::Builder::new()
        .name("ms-ipc-accept".into())
        .spawn(move || accept_loop(server, state_accept, running_accept))?;

    let state_watch = Arc::clone(&state);
    let running_watch = Arc::clone(&running);
    let mark_tamper = opts.mark_tamper_on_stop;
    let watch = thread::Builder::new()
        .name("ms-watchdog".into())
        .spawn(move || watchdog_loop(state_watch, db, opts, running_watch, mark_tamper))?;

    let sync_join = spawn_sync_if_configured(Arc::clone(&state), Arc::clone(&running));

    while running.load(Ordering::SeqCst) {
        thread::sleep(Duration::from_millis(200));
    }
    state.stop();

    let _ = accept.join();
    let _ = watch.join();
    if let Some(j) = sync_join {
        let _ = j.join();
    }
    info!("guardian runtime stopped");
    Ok(())
}

fn spawn_sync_if_configured(
    state: Arc<GuardianState>,
    running: StopFlag,
) -> Option<thread::JoinHandle<()>> {
    let backends = build_sync_backends()?;
    let store = SecretStore::platform();
    let key = load_or_create_db_key(&store).ok()?.to_vec();
    let path = db_path().ok()?;
    let flags = state.sync_flags.clone();
    Some(
        thread::Builder::new()
            .name("ms-sync".into())
            .spawn(move || run_sync_loop(path, key, state, backends, flags, running))
            .expect("spawn sync"),
    )
}

fn build_sync_backends() -> Option<Arc<SyncBackends>> {
    if std::env::var("MERITSCREEN_SYNC_DISABLE").is_ok() {
        return None;
    }
    if std::env::var("MERITSCREEN_SYNC_MOCK").is_ok()
        || std::env::var("MERITSCREEN_FIREBASE_API_KEY").is_err()
    {
        // Default lab: mock remote so schedulers exercise without cloud credentials.
        let mock = meritscreen_firebase::MockChildRemoteClient::default();
        return Some(Arc::new(SyncBackends {
            remote: Arc::new(mock),
            auth: None,
        }));
    }
    let api_key = std::env::var("MERITSCREEN_FIREBASE_API_KEY").ok()?;
    let project_id = std::env::var("MERITSCREEN_FIREBASE_PROJECT_ID").ok()?;
    let config = meritscreen_firebase::FirebaseConfig {
        api_key,
        project_id: project_id.clone(),
    };
    let firestore = meritscreen_firebase::RestFirestoreClient::new(config.clone());
    let auth = meritscreen_firebase::RestAuthClient::new(config);
    let remote = meritscreen_firebase::OwnedRestChildRemoteClient::new(firestore, project_id);
    Some(Arc::new(SyncBackends {
        remote: Arc::new(remote),
        auth: Some(Arc::new(auth)),
    }))
}

fn accept_loop(server: IpcServer, state: Arc<GuardianState>, running: StopFlag) {
    while running.load(Ordering::SeqCst) && state.is_running() {
        match server.accept() {
            Ok(mut conn) => {
                let peer = match peer_identity_from_stream(conn.stream()) {
                    Ok(p) => p,
                    Err(e) => {
                        warn!(error = %e, "ipc peer credentials unavailable — dropping");
                        continue;
                    }
                };
                note_accepted_peer(peer);
                if !authorize_peer(peer) {
                    continue;
                }
                let st = Arc::clone(&state);
                let run = Arc::clone(&running);
                thread::spawn(move || {
                    while run.load(Ordering::SeqCst) && st.is_running() {
                        match conn.recv_request() {
                            Ok(req) => {
                                let resp = dispatch(req, &st);
                                if conn.send_response(&resp).is_err() {
                                    break;
                                }
                            }
                            Err(_) => break,
                        }
                    }
                });
            }
            Err(e) => {
                if running.load(Ordering::SeqCst) {
                    warn!(error = %e, "ipc accept failed");
                    thread::sleep(Duration::from_millis(100));
                }
            }
        }
    }
}

fn dispatch(req: IpcRequest, state: &GuardianState) -> IpcResponse {
    match req {
        IpcRequest::Ping => IpcResponse::Pong {
            version: IPC_PROTOCOL_VERSION,
        },
        IpcRequest::AgentHello { pid } => {
            state.note_agent(pid);
            info!(pid, "agent hello");
            IpcResponse::HelloAck {
                version: IPC_PROTOCOL_VERSION,
            }
        }
        IpcRequest::AgentHeartbeat { pid } => {
            state.note_agent(pid);
            IpcResponse::Ok
        }
        IpcRequest::GetSnapshot => IpcResponse::Snapshot(snapshot_from(state)),
        IpcRequest::SetRole { role } => {
            *state.role.lock() = role;
            IpcResponse::Ok
        }
        IpcRequest::SetAppearance { .. } => IpcResponse::Ok,
        IpcRequest::UiWatchdogPoll => IpcResponse::UiRequired {
            required: state.ui_required(),
            phase: state.phase(),
            prewarm: state.ui_prewarm(),
        },
        IpcRequest::QuizDue => {
            state.session.lock().phase = SessionPhase::QuizDue;
            state
                .sync_flags
                .expedite_usage
                .store(true, Ordering::SeqCst);
            IpcResponse::Ok
        }
        IpcRequest::RaiseLock => {
            state.session.lock().phase = SessionPhase::Shielded;
            state
                .sync_flags
                .expedite_usage
                .store(true, Ordering::SeqCst);
            IpcResponse::Ok
        }
        IpcRequest::LaunchApp { .. } | IpcRequest::PinOk => IpcResponse::Ok,
        IpcRequest::ReportForeground {
            app_id,
            pid,
            process_name,
            idle_seconds,
            wall_ms,
            mono_ms,
        } => handle_foreground(
            state,
            ForegroundSample {
                app_id,
                pid,
                process_name,
                idle_seconds,
                wall_ms,
                mono_ms,
            },
        ),
        IpcRequest::InventoryRescan => {
            state
                .sync_flags
                .inventory_dirty
                .store(true, Ordering::SeqCst);
            IpcResponse::Ok
        }
        IpcRequest::PolicyRefresh => {
            state
                .sync_flags
                .expedite_policy
                .store(true, Ordering::SeqCst);
            IpcResponse::Ok
        }
        IpcRequest::QuizCompleted { attempt_json } => {
            if let Ok(db) = open_guardian_db() {
                if let Ok(v) = serde_json::from_str::<serde_json::Value>(&attempt_json) {
                    let id = v
                        .get("attemptId")
                        .and_then(|x| x.as_str())
                        .unwrap_or("unknown");
                    let _ = meritscreen_db::enqueue_quiz_upload(&db.conn, id, &attempt_json);
                }
            }
            state
                .sync_flags
                .expedite_usage
                .store(true, Ordering::SeqCst);
            IpcResponse::Ok
        }
        IpcRequest::BindPairing {
            family_id,
            child_id,
            device_id,
            platform,
            refresh_token,
        } => match crate::sync::bind_pairing_paths(
            &family_id,
            &child_id,
            &device_id,
            &platform,
            &refresh_token,
        ) {
            Ok(()) => {
                *state.role.lock() = DeviceRole::Child;
                state
                    .sync_flags
                    .expedite_policy
                    .store(true, Ordering::SeqCst);
                IpcResponse::Ok
            }
            Err(e) => IpcResponse::Error { message: e },
        },
    }
}

fn handle_foreground(state: &GuardianState, sample: ForegroundSample) -> IpcResponse {
    let observation = state.clock.lock().observe(sample.wall_ms, sample.mono_ms);
    match observation {
        ClockObservation::Rollback => {
            state.tamper.lock().clock_rollback = true;
            state.session.lock().phase = SessionPhase::Shielded;
            info!("clock rollback detected — fail closed (shielded)");
        }
        ClockObservation::Ok { mono_elapsed_ms } => {
            let phase = state.phase();
            let counts = matches!(phase, SessionPhase::InBlock | SessionPhase::Idle)
                && sample.app_id.is_some();
            let active = is_active_use(sample.idle_seconds, counts);
            if active && mono_elapsed_ms > 0 {
                let mut elapsed = state.session_elapsed_ms.lock();
                *elapsed = elapsed.saturating_add(mono_elapsed_ms);
                let policy = state.policy.lock().clone();
                let mut session = state.session.lock();
                *session = SessionEngine::tick(session.clone(), *elapsed, &policy, active);
            }
        }
    }

    *state.last_foreground.lock() = Some(sample.clone());
    let phase = state.phase();
    let rules = state.rules.lock().clone();
    let prefer_terminate = matches!(phase, SessionPhase::QuizDue | SessionPhase::Shielded);
    let decision = decide_gate(GateContext {
        phase,
        sample: &sample,
        rules: &rules,
        prefer_terminate,
    });

    let (action, app_id, pid, reason) = match &decision {
        GateAction::Allow => {
            state.last_gate_cover.store(false, Ordering::SeqCst);
            ("allow", None, None, "ok")
        }
        GateAction::Cover { app_id, pid } => {
            state.last_gate_cover.store(true, Ordering::SeqCst);
            ("cover", app_id.clone(), *pid, "non_allowed_or_locked")
        }
        GateAction::Terminate { app_id, pid } => {
            state.last_gate_cover.store(true, Ordering::SeqCst);
            ("terminate", app_id.clone(), *pid, "fail_lock_or_blocked")
        }
    };

    IpcResponse::GateDecision {
        action: action.into(),
        app_id,
        pid,
        reason: reason.into(),
        phase,
    }
}

fn snapshot_from(state: &GuardianState) -> UiSnapshot {
    let tamper = state.tamper.lock().clone();
    let tier = *state.tier.lock();
    let degraded = degraded_message(&tamper, *state.strict_enabled.lock());
    let guardian_state = if tamper.admin_account || tamper.clock_rollback {
        "degraded"
    } else if state.tamper_service_stopped.load(Ordering::SeqCst) {
        "stopped"
    } else {
        "running"
    };
    UiSnapshot {
        role: *state.role.lock(),
        appearance: Appearance::System,
        phase: state.phase(),
        enforcement_tier: tier.as_str().into(),
        guardian_state: guardian_state.into(),
        tamper_flags: tamper
            .as_flag_names()
            .into_iter()
            .map(str::to_string)
            .collect(),
        degraded_message: degraded,
    }
}

fn watchdog_loop(
    state: Arc<GuardianState>,
    db: GuardianDb,
    opts: RuntimeOptions,
    running: StopFlag,
    mark_tamper: bool,
) {
    let mut child = None;
    if !opts.disable_agent_spawn {
        child = try_spawn(&opts);
    }

    let mut elapsed_secs: u64 = 0;
    let mut last_persist: u64 = 0;
    let mut last_inventory: u64 = 0;
    let mut last_admin: u64 = 0;
    let persist_every = u64::from(SESSION_PERSIST_INTERVAL_SECONDS);
    // Inventory: debounce constant is 30s; full rescan cadence ~5 min while active.
    let inventory_every = u64::from(INVENTORY_RESCAN_DEBOUNCE_SECONDS).max(30) * 10;
    while running.load(Ordering::SeqCst) && state.is_running() {
        let sleep_secs = if state.session_active() {
            1u64
        } else {
            u64::from(GUARDIAN_IDLE_POLL_SECONDS)
        };
        thread::sleep(Duration::from_secs(sleep_secs));
        elapsed_secs = elapsed_secs.saturating_add(sleep_secs);

        if elapsed_secs.saturating_sub(last_persist) >= persist_every {
            last_persist = elapsed_secs;
            if let Err(e) = touch_heartbeat(&db.conn) {
                warn!(error = %e, "heartbeat write failed");
            }
            let _ = merge_device_runtime(&db.conn, &state, GuardianRuntimeState::Running);
            persist_session(&db.conn, &state.session.lock());
        }

        if elapsed_secs.saturating_sub(last_inventory) >= inventory_every {
            last_inventory = elapsed_secs;
            match maybe_rescan_inventory(&db.conn) {
                Ok(changed) => {
                    if changed {
                        state
                            .sync_flags
                            .inventory_dirty
                            .store(true, Ordering::SeqCst);
                    }
                }
                Err(e) => warn_inventory(e),
            }
        }

        // Detect admin account periodically (honest degraded copy).
        if elapsed_secs.saturating_sub(last_admin) >= 60 {
            last_admin = elapsed_secs;
            if meritscreen_enforcement::platform::detect_admin_account() {
                state.tamper.lock().admin_account = true;
                *state.tier.lock() = compute_tier(*state.strict_enabled.lock());
            }
        }

        if opts.disable_agent_spawn {
            continue;
        }

        let child_dead = child
            .as_mut()
            .map(|c| matches!(c.try_wait(), Ok(Some(_))))
            .unwrap_or(opts.console_mode);

        let stale = state.agent_is_stale();
        let should_respawn = if opts.console_mode {
            stale || child_dead
        } else {
            stale
        };

        if should_respawn {
            warn!("agent stale or exited — respawning");
            if let Some(mut c) = child.take() {
                let _ = c.kill();
            }
            child = try_spawn(&opts);
            state.note_agent(0);
        }
    }

    persist_session(&db.conn, &state.session.lock());
    if mark_tamper {
        if let Err(e) = mark_tamper_service_stopped(&db.conn) {
            error!(error = %e, "failed to persist tamper flag");
        }
    }

    if let Some(mut c) = child.take() {
        let _ = c.kill();
    }
}

fn try_spawn(opts: &RuntimeOptions) -> Option<std::process::Child> {
    if opts.console_mode {
        match spawn_agent_console() {
            Ok(c) => Some(c),
            Err(e) => {
                warn!(error = %e, "console agent spawn failed");
                None
            }
        }
    } else {
        match spawn_agent_in_user_session() {
            Ok(()) => None,
            Err(e) => {
                warn!(error = %e, "user-session agent spawn failed; trying console");
                spawn_agent_console().ok()
            }
        }
    }
}

#[allow(dead_code)]
pub fn set_role_for_tests(state: &GuardianState, role: DeviceRole) {
    *state.role.lock() = role;
}
