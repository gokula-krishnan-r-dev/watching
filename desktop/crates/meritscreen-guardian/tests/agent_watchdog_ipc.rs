//! Integration test: Agent ↔ Guardian watchdog, heartbeat, foreground reporting, and stop.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use meritscreen_core::SessionPhase;
use meritscreen_guardian::db_boot::{load_tamper_flags, open_guardian_db};
use meritscreen_guardian::runtime::{self, RuntimeOptions};
use meritscreen_ipc::{IpcConn, IpcRequest, IpcResponse, IPC_PROTOCOL_VERSION};

#[test]
fn agent_watchdog_and_gate_decision_flow() {
    let tmp = tempfile::tempdir().expect("tempdir");
    let data = tmp.path().join("data");
    let secrets = tmp.path().join("secrets");
    let sock = tmp.path().join("test-agent-watchdog.sock");
    std::fs::create_dir_all(&data).expect("create data dir");
    std::fs::create_dir_all(&secrets).expect("create secrets dir");

    std::env::set_var("MERITSCREEN_DATA_DIR", &data);
    std::env::set_var("MERITSCREEN_SECRETS_DIR", &secrets);
    std::env::set_var("MERITSCREEN_IPC_PEER_RELAX", "1");
    #[cfg(not(windows))]
    std::env::set_var("MERITSCREEN_IPC_SOCK", &sock);

    let running = Arc::new(AtomicBool::new(true));
    let run_flag = Arc::clone(&running);

    let guardian_thread = thread::spawn(move || {
        runtime::run_with_stop(
            RuntimeOptions {
                console_mode: true,
                disable_agent_spawn: true,
                mark_tamper_on_stop: true,
            },
            run_flag,
        )
    });

    let mut conn = None;
    for _ in 0..50 {
        thread::sleep(Duration::from_millis(100));
        if guardian_thread.is_finished() {
            panic!("Guardian thread terminated prematurely");
        }
        if let Ok(c) = IpcConn::connect() {
            conn = Some(c);
            break;
        }
    }
    let mut conn = conn.expect("Guardian IPC listener should be ready");

    // 1. Agent Hello
    let resp = conn
        .call(&IpcRequest::AgentHello { pid: 9876 })
        .expect("hello call");
    assert_eq!(
        resp,
        IpcResponse::HelloAck {
            version: IPC_PROTOCOL_VERSION
        }
    );

    // 2. Agent Heartbeat
    let resp = conn
        .call(&IpcRequest::AgentHeartbeat { pid: 9876 })
        .expect("heartbeat call");
    assert_eq!(resp, IpcResponse::Ok);

    // 3. UI watchdog poll when idle
    let resp = conn
        .call(&IpcRequest::UiWatchdogPoll)
        .expect("watchdog poll");
    match resp {
        IpcResponse::UiRequired {
            required, phase, ..
        } => {
            assert!(!required, "UI should not be required while idle");
            assert_eq!(phase, SessionPhase::Idle);
        }
        other => panic!("unexpected ui poll response: {other:?}"),
    }

    // 4. Trigger quiz due
    let resp = conn.call(&IpcRequest::QuizDue).expect("quiz due call");
    assert_eq!(resp, IpcResponse::Ok);

    // 5. Watchdog poll reflects quiz due requirement
    let resp = conn
        .call(&IpcRequest::UiWatchdogPoll)
        .expect("watchdog poll post-quiz");
    match resp {
        IpcResponse::UiRequired {
            required, phase, ..
        } => {
            assert!(required, "UI must be required when QuizDue");
            assert_eq!(phase, SessionPhase::QuizDue);
        }
        other => panic!("unexpected ui poll response: {other:?}"),
    }

    // 6. Foreground sample report under quiz lock
    let resp = conn
        .call(&IpcRequest::ReportForeground {
            app_id: Some("win:c:/games/roblox.exe".into()),
            pid: Some(5555),
            process_name: Some("roblox.exe".into()),
            idle_seconds: 0,
            is_screen_locked: false,
            wall_ms: 1_700_000_000_000,
            mono_ms: 50_000,
        })
        .expect("report foreground");
    match resp {
        IpcResponse::GateDecision { action, phase, .. } => {
            assert!(
                action == "terminate" || action == "cover",
                "Non-allowed app must be covered or terminated during quiz: got {action}"
            );
            assert_eq!(phase, SessionPhase::QuizDue);
        }
        other => panic!("unexpected gate decision response: {other:?}"),
    }

    // 7. Clean service stop sets tamperFlags.serviceStopped
    running.store(false, Ordering::SeqCst);
    // Wake IPC accept loop
    let _ = IpcConn::connect();
    let _ = guardian_thread.join();

    let db = open_guardian_db().expect("open db to check tamper flags");
    let flags = load_tamper_flags(&db.conn);
    assert!(
        flags.service_stopped,
        "tamperFlags.serviceStopped must be true after service stop"
    );
}
