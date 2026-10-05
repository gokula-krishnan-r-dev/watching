//! Open SQLCipher + ensure DB key exists in the OS secret store.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use meritscreen_core::SessionSnapshot;
use meritscreen_db::{load_session, open_with_key, save_session};
use meritscreen_enforcement::TamperFlags;
use meritscreen_security::{accounts, SecretStore};
use rand::RngCore;
use rusqlite::Connection;
use tracing::{info, warn};
use zeroize::Zeroizing;

pub struct GuardianDb {
    pub conn: Connection,
    pub path: PathBuf,
}

pub fn data_dir() -> Result<PathBuf> {
    if let Ok(override_dir) = std::env::var("MERITSCREEN_DATA_DIR") {
        let dir = PathBuf::from(override_dir).join("guardian");
        std::fs::create_dir_all(&dir)?;
        return Ok(dir);
    }
    let dirs = directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen")
        .context("project dirs unavailable")?;
    let dir = dirs.data_local_dir().join("guardian");
    std::fs::create_dir_all(&dir)?;
    Ok(dir)
}

pub fn db_path() -> Result<PathBuf> {
    Ok(data_dir()?.join("meritscreen.db"))
}

pub fn load_or_create_db_key(store: &SecretStore) -> Result<Zeroizing<Vec<u8>>> {
    match store.get(accounts::DB_KEY) {
        Ok(secret) => {
            let bytes = hex::decode(secret.as_str()).unwrap_or_else(|_| secret.as_bytes().to_vec());
            Ok(Zeroizing::new(bytes))
        }
        Err(_) => {
            let mut key = [0u8; 32];
            rand::thread_rng().fill_bytes(&mut key);
            let hex_key = hex::encode(key);
            store
                .set(accounts::DB_KEY, &hex_key)
                .context("persist db key")?;
            info!("generated new SQLCipher database key");
            Ok(Zeroizing::new(key.to_vec()))
        }
    }
}

pub fn open_guardian_db() -> Result<GuardianDb> {
    let path = db_path()?;
    let store = SecretStore::platform();
    let key = load_or_create_db_key(&store)?;
    let conn = open_with_key(&path, &key).context("open SQLCipher")?;
    Ok(GuardianDb { conn, path })
}

pub fn persist_session(conn: &Connection, snapshot: &SessionSnapshot) {
    if let Err(e) = save_session(conn, snapshot) {
        warn!(error = %e, "failed to persist session_state");
    }
}

pub fn restore_session(conn: &Connection) -> SessionSnapshot {
    load_session(conn).unwrap_or_default()
}

pub fn mark_tamper_service_stopped(conn: &Connection) -> Result<()> {
    merge_tamper_flag(conn, "serviceStopped", Some("stopped"))?;
    info!("tamper flag set: serviceStopped");
    Ok(())
}

/// Mark `tamperFlags.uninstallAttempt` (PIN fail or unguarded uninstall start).
pub fn mark_tamper_uninstall_attempt(conn: &Connection) -> Result<()> {
    merge_tamper_flag(conn, "uninstallAttempt", None)?;
    info!("tamper flag set: uninstallAttempt");
    Ok(())
}

fn merge_tamper_flag(
    conn: &Connection,
    flag: &str,
    guardian_state: Option<&str>,
) -> Result<()> {
    let existing: String = conn
        .query_row(
            "SELECT json FROM device_runtime WHERE id = 1",
            [],
            |r| r.get(0),
        )
        .unwrap_or_else(|_| "{}".into());
    let mut v: serde_json::Value =
        serde_json::from_str(&existing).unwrap_or_else(|_| serde_json::json!({}));
    if !v.is_object() {
        v = serde_json::json!({});
    }
    let obj = v.as_object_mut().expect("object");
    let flags = obj
        .entry("tamperFlags")
        .or_insert_with(|| serde_json::json!({}));
    if let Some(map) = flags.as_object_mut() {
        map.insert(flag.into(), serde_json::Value::Bool(true));
    }
    if let Some(state) = guardian_state {
        obj.insert(
            "guardianState".into(),
            serde_json::Value::String(state.into()),
        );
    }
    let now = now_ms();
    conn.execute(
        "UPDATE device_runtime SET json = ?1, updated_at_ms = ?2 WHERE id = 1",
        rusqlite::params![v.to_string(), now],
    )?;
    Ok(())
}

/// Restore persisted tamper flags from `device_runtime` (survives reboot).
pub fn load_tamper_flags(conn: &Connection) -> TamperFlags {
    let Ok(json): Result<String, _> = conn.query_row(
        "SELECT json FROM device_runtime WHERE id = 1",
        [],
        |r| r.get(0),
    ) else {
        return TamperFlags::default();
    };
    let Ok(v): Result<serde_json::Value, _> = serde_json::from_str(&json) else {
        return TamperFlags::default();
    };
    let flags = v.get("tamperFlags").cloned().unwrap_or_default();
    TamperFlags {
        service_stopped: flags.get("serviceStopped").and_then(|x| x.as_bool()) == Some(true),
        clock_rollback: flags.get("clockRollback").and_then(|x| x.as_bool()) == Some(true),
        admin_account: flags.get("adminAccount").and_then(|x| x.as_bool()) == Some(true),
        binary_mismatch: flags.get("binaryMismatch").and_then(|x| x.as_bool()) == Some(true),
        uninstall_attempt: flags.get("uninstallAttempt").and_then(|x| x.as_bool()) == Some(true),
    }
}

pub fn touch_heartbeat(conn: &Connection) -> Result<()> {
    let now = now_ms();
    conn.execute(
        "UPDATE sync_state SET last_heartbeat_ms = ?1 WHERE id = 1",
        [now],
    )?;
    let json = serde_json::json!({ "guardianState": "running" });
    conn.execute(
        "UPDATE device_runtime SET json = ?1, updated_at_ms = ?2 WHERE id = 1",
        rusqlite::params![json.to_string(), now],
    )?;
    Ok(())
}

pub fn db_exists(path: &Path) -> bool {
    path.exists()
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn with_isolated_env(f: impl FnOnce()) {
        let _guard = crate::test_env::lock();
        let tmp = tempdir().unwrap();
        std::env::set_var("MERITSCREEN_DATA_DIR", tmp.path());
        std::env::set_var("MERITSCREEN_SECRETS_DIR", tmp.path().join("secrets"));
        f();
    }

    #[test]
    fn service_stop_sets_tamper_flag() {
        with_isolated_env(|| {
            let db = open_guardian_db().expect("open db");
            mark_tamper_service_stopped(&db.conn).expect("tamper");
            let json: String = db
                .conn
                .query_row("SELECT json FROM device_runtime WHERE id = 1", [], |r| {
                    r.get(0)
                })
                .unwrap();
            let v: serde_json::Value = serde_json::from_str(&json).unwrap();
            assert_eq!(v["tamperFlags"]["serviceStopped"], true);
            assert_eq!(v["guardianState"], "stopped");
        });
    }

    #[test]
    fn uninstall_attempt_merges_flags() {
        with_isolated_env(|| {
            let db = open_guardian_db().expect("open db");
            mark_tamper_service_stopped(&db.conn).unwrap();
            mark_tamper_uninstall_attempt(&db.conn).unwrap();
            let flags = load_tamper_flags(&db.conn);
            assert!(flags.service_stopped);
            assert!(flags.uninstall_attempt);
        });
    }
}
