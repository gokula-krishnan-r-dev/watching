//! Integration: Guardian IPC server answers Ping (no agent spawn).

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use meritscreen_guardian::runtime::{self, RuntimeOptions};
use meritscreen_ipc::{IpcConn, IpcRequest, IpcResponse, IPC_PROTOCOL_VERSION};

#[test]
fn guardian_answers_ping() {
    let tmp = tempfile::tempdir().unwrap();
    let data = tmp.path().join("data");
    let secrets = tmp.path().join("secrets");
    let sock = tmp.path().join("test-guardian.sock");
    std::fs::create_dir_all(&data).unwrap();
    std::fs::create_dir_all(&secrets).unwrap();

    // Isolate DB + secrets from the developer machine / keychain.
    std::env::set_var("MERITSCREEN_DATA_DIR", &data);
    std::env::set_var("MERITSCREEN_SECRETS_DIR", &secrets);
    // Test harness binary is not named meritscreen-* (Windows path check).
    std::env::set_var("MERITSCREEN_IPC_PEER_RELAX", "1");
    #[cfg(not(windows))]
    std::env::set_var("MERITSCREEN_IPC_SOCK", &sock);

    let running = Arc::new(AtomicBool::new(true));
    let run = Arc::clone(&running);
    let handle = thread::spawn(move || {
        runtime::run_with_stop(
            RuntimeOptions {
                console_mode: true,
                disable_agent_spawn: true,
                mark_tamper_on_stop: false,
            },
            run,
        )
    });

    let mut last_err = String::new();
    let mut conn = None;
    for i in 0..50 {
        thread::sleep(Duration::from_millis(100));
        if handle.is_finished() {
            let result = handle.join();
            panic!(
                "guardian runtime exited early (iter={i}): {:?}",
                result.map(|r| r.map_err(|e| e.to_string()))
            );
        }
        match IpcConn::connect() {
            Ok(c) => {
                conn = Some(c);
                break;
            }
            Err(e) => last_err = e.to_string(),
        }
    }
    let mut conn = conn.unwrap_or_else(|| {
        panic!("guardian IPC should accept connections; last_err={last_err}; sock={sock:?}")
    });
    let resp = conn.call(&IpcRequest::Ping).expect("ping");
    match resp {
        IpcResponse::Pong { version } => assert_eq!(version, IPC_PROTOCOL_VERSION),
        other => panic!("unexpected {other:?}"),
    }

    running.store(false, Ordering::SeqCst);
    // Wake the blocking accept loop so the runtime thread can exit.
    let _ = IpcConn::connect();
    let _ = handle.join();
}
