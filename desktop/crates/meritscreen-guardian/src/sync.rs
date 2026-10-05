//! Guardian cloud sync schedulers — policy / usage / heartbeat / revoke (poll-first).
//!
//! Offline: last SQLCipher policy stays in force. UI remains local-only.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use meritscreen_core::app_config::{
    HEARTBEAT_INTERVAL_MINUTES, POLICY_PULL_INTERVAL_HOURS, USAGE_FLUSH_INTERVAL_SECONDS,
    USAGE_UPLOAD_INTERVAL_HOURS,
};
use meritscreen_core::{DeviceRole, SessionPhase};
use meritscreen_db::{
    clear_pairing_ids, get_inventory_hash, list_installed_apps, list_pending_quiz_uploads,
    list_usage_dirty, load_pairing_ids, load_sync_timestamps, mark_quiz_uploaded, mark_usage_clean,
    quiz_bank_count, replace_app_rules, save_pairing_ids, save_policy, save_sync_timestamps,
    upsert_usage_dirty, DevicePairingIds,
};
use meritscreen_firebase::{
    AuthClient, AuthTokens, ChildRemoteClient, DeviceHeartbeatPatch, DeviceIds, InstalledAppUpload,
    QuizAttemptUpload, UsageDayUpload,
};
use meritscreen_security::{accounts, SecretStore};
use parking_lot::Mutex;
use rusqlite::Connection;
use tracing::{info, warn};

use crate::db_boot::GuardianDb;
use crate::state::GuardianState;

/// Minimum quiz bank size before pack refresh is considered "low".
const PACK_LOW_THRESHOLD: u32 = 5;

#[derive(Clone)]
pub struct SyncFlags {
    pub expedite_policy: Arc<AtomicBool>,
    pub expedite_usage: Arc<AtomicBool>,
    pub inventory_dirty: Arc<AtomicBool>,
}

impl Default for SyncFlags {
    fn default() -> Self {
        Self {
            expedite_policy: Arc::new(AtomicBool::new(false)),
            expedite_usage: Arc::new(AtomicBool::new(false)),
            inventory_dirty: Arc::new(AtomicBool::new(false)),
        }
    }
}

pub struct SyncBackends {
    pub remote: Arc<dyn ChildRemoteClient>,
    pub auth: Option<Arc<dyn AuthClient>>,
}

struct TokenCache {
    tokens: Mutex<Option<AuthTokens>>,
}

impl TokenCache {
    fn new() -> Self {
        Self {
            tokens: Mutex::new(None),
        }
    }

    fn id_token(&self, auth: Option<&Arc<dyn AuthClient>>, store: &SecretStore) -> Option<String> {
        if let Some(t) = self.tokens.lock().as_ref() {
            return Some(t.id_token.clone());
        }
        if let Ok(id) = store.get(accounts::CHILD_ID_TOKEN) {
            return Some(id.as_str().to_string());
        }
        let refresh = store.get(accounts::CHILD_REFRESH).ok()?;
        let Some(auth) = auth else {
            return Some(refresh.as_str().to_string());
        };
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .ok()?;
        let tokens = rt.block_on(auth.refresh_id_token(refresh.as_str())).ok()?;
        let _ = store.set(accounts::CHILD_ID_TOKEN, &tokens.id_token);
        let _ = store.set(accounts::CHILD_REFRESH, &tokens.refresh_token);
        let id = tokens.id_token.clone();
        *self.tokens.lock() = Some(tokens);
        Some(id)
    }
}

/// Long-lived sync worker owning its own SQLCipher connection (same key/path as Guardian).
pub fn run_sync_loop(
    db_path: std::path::PathBuf,
    db_key: Vec<u8>,
    state: Arc<GuardianState>,
    backends: Arc<SyncBackends>,
    flags: SyncFlags,
    running: Arc<AtomicBool>,
) {
    let conn = match meritscreen_db::open_with_key(&db_path, &db_key) {
        Ok(c) => c,
        Err(e) => {
            warn!(error = %e, "sync: could not open db");
            return;
        }
    };
    let store = SecretStore::platform();
    let tokens = TokenCache::new();
    let mut was_online = true;
    let mut ticks = 0u64;

    while running.load(Ordering::SeqCst) && state.is_running() {
        std::thread::sleep(Duration::from_secs(5));
        ticks += 1;
        let _ = ticks;
        sync_tick(
            &conn,
            &state,
            backends.as_ref(),
            &flags,
            &store,
            &tokens,
            &mut was_online,
        );
    }
}

fn sync_tick(
    conn: &Connection,
    state: &GuardianState,
    backends: &SyncBackends,
    flags: &SyncFlags,
    store: &SecretStore,
    tokens: &TokenCache,
    was_online: &mut bool,
) {
    let pairing = match load_pairing_ids(conn) {
        Ok(p) => p,
        Err(e) => {
            warn!(error = %e, "sync: load pairing failed");
            return;
        }
    };
    let Some(ids) = pairing_to_ids(&pairing) else {
        return;
    };

    let Some(id_token) = tokens.id_token(backends.auth.as_ref(), store) else {
        return;
    };

    let mut ts = load_sync_timestamps(conn).unwrap_or_default();
    let now = now_ms();
    let online = probe_online(backends.remote.as_ref(), &ids, &id_token);
    if online && !*was_online {
        info!("network reconnect — expedited policy pull");
        flags.expedite_policy.store(true, Ordering::SeqCst);
        flags.expedite_usage.store(true, Ordering::SeqCst);
    }
    *was_online = online;
    ts.online = online;
    let _ = save_sync_timestamps(conn, &ts);

    if !online {
        return;
    }

    let rt = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "sync runtime");
            return;
        }
    };

    let policy_due = flags.expedite_policy.swap(false, Ordering::SeqCst)
        || due(
            ts.last_policy_pull_ms,
            now,
            i64::from(POLICY_PULL_INTERVAL_HOURS) * 3_600_000,
        );
    if policy_due {
        match rt.block_on(backends.remote.pull_policy(&ids, &id_token)) {
            Ok(Some(bundle)) => {
                let _ = save_policy(conn, &bundle.policy);
                let _ = replace_app_rules(conn, &bundle.app_rules);
                *state.policy.lock() = bundle.policy;
                *state.rules.lock() = bundle.app_rules;
                if let Some(hash) = bundle.parent_pin_hash {
                    let _ = store.set(accounts::PARENT_PIN_HASH, &hash);
                }
                ts.last_policy_pull_ms = Some(now);
                let _ = save_sync_timestamps(conn, &ts);
                info!("policy pull ok");
            }
            Ok(None) => {
                warn!("policy missing — family/child deleted?");
                handle_unpair(conn, state, store);
                return;
            }
            Err(e) => warn!(error = %e, "policy pull failed"),
        }

        match rt.block_on(backends.remote.family_exists(&ids.family_id, &id_token)) {
            Ok(false) => {
                warn!("family deleted — unpairing");
                handle_unpair(conn, state, store);
                return;
            }
            Ok(true) => {}
            Err(e) => warn!(error = %e, "family exists probe failed"),
        }
    }

    let hb_due = due(
        ts.last_heartbeat_ms,
        now,
        i64::from(HEARTBEAT_INTERVAL_MINUTES) * 60_000,
    );
    if hb_due {
        let tamper = state.tamper.lock().clone();
        let mut flags_map = serde_json::Map::new();
        for name in tamper.as_flag_names() {
            flags_map.insert(name.into(), serde_json::Value::Bool(true));
        }
        let patch = DeviceHeartbeatPatch {
            guardian_state: if tamper.any() {
                "degraded".into()
            } else {
                "running".into()
            },
            enforcement_tier: state.tier.lock().as_str().into(),
            tamper_flags: serde_json::Value::Object(flags_map),
            agent_version: Some(env!("CARGO_PKG_VERSION").into()),
            os_build: Some(std::env::consts::OS.into()),
        };
        if let Err(e) = rt.block_on(backends.remote.patch_heartbeat(&ids, patch, &id_token)) {
            warn!(error = %e, "cloud heartbeat failed");
        } else {
            ts.last_heartbeat_ms = Some(now);
            let _ = save_sync_timestamps(conn, &ts);
        }

        match rt.block_on(backends.remote.fetch_device_status(&ids, &id_token)) {
            Ok(status) if status.exists && status.revoked => {
                warn!("device revoked — unpairing");
                handle_unpair(conn, state, store);
                return;
            }
            Ok(_) => {}
            Err(e) => warn!(error = %e, "device status failed"),
        }
    }

    if flags.inventory_dirty.swap(false, Ordering::SeqCst)
        || due(ts.last_inventory_upload_ms, now, 30 * 60_000)
    {
        if let (Ok(apps), Ok(Some(hash))) = (list_installed_apps(conn), get_inventory_hash(conn)) {
            let uploads: Vec<InstalledAppUpload> = apps
                .iter()
                .map(|a| InstalledAppUpload {
                    package_name: a.app_id.clone(),
                    label: a.label.clone(),
                    icon_hash: a.icon_hash.clone(),
                })
                .collect();
            match rt.block_on(
                backends
                    .remote
                    .upload_installed_apps(&ids, &uploads, &hash, &id_token),
            ) {
                Ok(()) => {
                    ts.last_inventory_upload_ms = Some(now);
                    let _ = save_sync_timestamps(conn, &ts);
                    info!(count = uploads.len(), "inventory uploaded");
                }
                Err(e) => warn!(error = %e, "inventory upload failed"),
            }
        }
    }

    // Steady-state ~45s when dirty rows exist; outer 2h bound; immediate on phase change.
    let has_dirty_usage = list_usage_dirty(conn)
        .map(|d| !d.is_empty())
        .unwrap_or(false);
    let usage_due = flags.expedite_usage.swap(false, Ordering::SeqCst)
        || (has_dirty_usage
            && due(
                ts.last_usage_upload_ms,
                now,
                i64::from(USAGE_FLUSH_INTERVAL_SECONDS) * 1_000,
            ))
        || due(
            ts.last_usage_upload_ms,
            now,
            i64::from(USAGE_UPLOAD_INTERVAL_HOURS) * 3_600_000,
        );
    if usage_due {
        let minutes = state.session.lock().minutes_used_today;
        let day = state.session.lock().day_key.clone();
        if !day.is_empty() {
            let _ = upsert_usage_dirty(conn, &day, minutes);
        }
        if let Ok(dirty) = list_usage_dirty(conn) {
            for (day_key, mins) in dirty {
                let upload = UsageDayUpload {
                    day: day_key.clone(),
                    minutes_used: mins,
                    minutes_by_app: Default::default(),
                };
                match rt.block_on(backends.remote.upload_usage_day(&ids, &upload, &id_token)) {
                    Ok(()) => {
                        let _ = mark_usage_clean(conn, &day_key);
                    }
                    Err(e) => warn!(error = %e, day = %day_key, "usage upload failed"),
                }
            }
        }
        if let Ok(pending) = list_pending_quiz_uploads(conn) {
            for (attempt_id, json_raw) in pending {
                match serde_json::from_str::<QuizAttemptUpload>(&json_raw) {
                    Ok(upload) => {
                        match rt.block_on(
                            backends
                                .remote
                                .upload_quiz_attempt(&ids, &upload, &id_token),
                        ) {
                            Ok(_) => {
                                let _ = mark_quiz_uploaded(conn, &attempt_id);
                            }
                            Err(e) => warn!(error = %e, "quiz upload failed"),
                        }
                    }
                    Err(e) => warn!(error = %e, "bad quiz queue json"),
                }
            }
        }
        ts.last_usage_upload_ms = Some(now);
        let _ = save_sync_timestamps(conn, &ts);
    }

    if due(
        ts.last_pack_refresh_ms,
        now,
        i64::from(POLICY_PULL_INTERVAL_HOURS) * 3_600_000,
    ) {
        if let Ok(count) = quiz_bank_count(conn) {
            if count < PACK_LOW_THRESHOLD {
                info!(
                    count,
                    "quiz pack low — builtin remains; cloud pack refresh later"
                );
            }
        }
        ts.last_pack_refresh_ms = Some(now);
        let _ = save_sync_timestamps(conn, &ts);
    }
}

fn handle_unpair(conn: &Connection, state: &GuardianState, store: &SecretStore) {
    let _ = clear_pairing_ids(conn);
    let _ = store.delete(accounts::CHILD_REFRESH);
    let _ = store.delete(accounts::CHILD_ID_TOKEN);
    let _ = store.delete(accounts::PARENT_PIN_HASH);
    *state.role.lock() = DeviceRole::Unassigned;
    state.session.lock().phase = SessionPhase::Idle;
    info!("local unpair complete (revoke / family deleted)");
}

fn pairing_to_ids(p: &DevicePairingIds) -> Option<DeviceIds> {
    Some(DeviceIds {
        family_id: p.family_id.clone()?,
        child_id: p.child_id.clone()?,
        device_id: p.device_id.clone()?,
    })
}

fn due(last: Option<i64>, now: i64, interval_ms: i64) -> bool {
    match last {
        None => true,
        Some(t) => now.saturating_sub(t) >= interval_ms,
    }
}

fn probe_online(remote: &dyn ChildRemoteClient, ids: &DeviceIds, token: &str) -> bool {
    let rt = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(r) => r,
        Err(_) => return false,
    };
    rt.block_on(remote.family_exists(&ids.family_id, token))
        .is_ok()
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Bind pairing credentials after successful `consumePairingToken` (or demo).
pub fn bind_pairing(
    db: &GuardianDb,
    family_id: &str,
    child_id: &str,
    device_id: &str,
    platform: &str,
    refresh_token: &str,
) -> Result<(), String> {
    let store = SecretStore::platform();
    store
        .set(accounts::CHILD_REFRESH, refresh_token)
        .map_err(|e| e.to_string())?;
    save_pairing_ids(
        &db.conn,
        &DevicePairingIds {
            family_id: Some(family_id.into()),
            child_id: Some(child_id.into()),
            device_id: Some(device_id.into()),
            platform: Some(platform.into()),
        },
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

/// Open Guardian DB and bind pairing (IPC path).
pub fn bind_pairing_paths(
    family_id: &str,
    child_id: &str,
    device_id: &str,
    platform: &str,
    refresh_token: &str,
) -> Result<(), String> {
    let db = crate::db_boot::open_guardian_db().map_err(|e| e.to_string())?;
    bind_pairing(&db, family_id, child_id, device_id, platform, refresh_token)
}

#[cfg(test)]
mod tests {
    use super::*;
    use meritscreen_core::{ChildPolicy, QuizMode};
    use meritscreen_db::{open_with_key, SyncTimestamps};
    use meritscreen_firebase::{MockChildRemoteClient, PolicyBundle};
    use tempfile::tempdir;

    #[test]
    fn policy_pull_and_revoke() {
        let _guard = crate::test_env::lock();
        let dir = tempdir().unwrap();
        std::env::set_var("MERITSCREEN_SECRETS_DIR", dir.path().join("secrets"));
        let key = vec![b'k'; 32];
        let path = dir.path().join("g.db");
        let conn = open_with_key(&path, &key).unwrap();
        let db = GuardianDb {
            conn,
            path: path.clone(),
        };
        bind_pairing(&db, "fam1", "child1", "dev1", "macos", "refresh-demo").unwrap();

        let mock = Arc::new(MockChildRemoteClient::with_family("fam1"));
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 22,
            ..ChildPolicy::default()
        };
        mock.policies.lock().unwrap().insert(
            "fam1/child1".into(),
            PolicyBundle {
                policy,
                app_rules: vec![],
                parent_pin_hash: Some("pinhash".into()),
                child_display_name: Some("Sam".into()),
            },
        );
        mock.devices.lock().unwrap().insert(
            "fam1/child1/dev1".into(),
            serde_json::json!({ "revoked": false }),
        );

        let state = GuardianState::default();
        *state.role.lock() = DeviceRole::Child;
        let backends = SyncBackends {
            remote: mock.clone(),
            auth: None,
        };
        let flags = SyncFlags::default();
        flags.expedite_policy.store(true, Ordering::SeqCst);
        let store = SecretStore::file_backed(dir.path().join("secrets"));
        let _ = store.set(accounts::CHILD_REFRESH, "refresh-demo");
        let tokens = TokenCache::new();
        let mut was_online = true;
        sync_tick(
            &db.conn,
            &state,
            &backends,
            &flags,
            &store,
            &tokens,
            &mut was_online,
        );

        let loaded = meritscreen_db::load_policy(&db.conn).unwrap().unwrap();
        assert_eq!(loaded.quiz_interval_minutes, 22);
        assert_eq!(state.policy.lock().quiz_interval_minutes, 22);

        // Heartbeat + revoke
        mock.devices.lock().unwrap().insert(
            "fam1/child1/dev1".into(),
            serde_json::json!({ "revoked": true }),
        );
        let ts = SyncTimestamps {
            online: true,
            ..SyncTimestamps::default()
        };
        save_sync_timestamps(&db.conn, &ts).unwrap();
        sync_tick(
            &db.conn,
            &state,
            &backends,
            &flags,
            &store,
            &tokens,
            &mut was_online,
        );
        assert_eq!(*state.role.lock(), DeviceRole::Unassigned);
        assert!(load_pairing_ids(&db.conn).unwrap().family_id.is_none());
    }

    #[test]
    fn usage_and_quiz_idempotent() {
        let _guard = crate::test_env::lock();
        let dir = tempdir().unwrap();
        std::env::set_var("MERITSCREEN_SECRETS_DIR", dir.path().join("secrets"));
        let key = vec![b'u'; 32];
        let conn = open_with_key(&dir.path().join("u.db"), &key).unwrap();
        let db = GuardianDb {
            conn,
            path: dir.path().join("u.db"),
        };
        bind_pairing(&db, "fam1", "child1", "dev1", "windows", "r").unwrap();
        upsert_usage_dirty(&db.conn, "2026-10-05", 40).unwrap();
        let attempt = QuizAttemptUpload {
            attempt_id: "a1".into(),
            created_at_epoch_ms: 1,
            topics: vec!["math".into()],
            score: 2,
            total: 3,
            passed: true,
            extra_minutes_granted: 0,
        };
        meritscreen_db::enqueue_quiz_upload(
            &db.conn,
            "a1",
            &serde_json::to_string(&attempt).unwrap(),
        )
        .unwrap();

        let mock = Arc::new(MockChildRemoteClient::with_family("fam1"));
        mock.devices.lock().unwrap().insert(
            "fam1/child1/dev1".into(),
            serde_json::json!({ "revoked": false }),
        );
        let state = GuardianState::default();
        let backends = SyncBackends {
            remote: mock.clone(),
            auth: None,
        };
        let flags = SyncFlags::default();
        flags.expedite_usage.store(true, Ordering::SeqCst);
        let store = SecretStore::file_backed(dir.path().join("secrets"));
        let _ = store.set(accounts::CHILD_REFRESH, "r");
        let tokens = TokenCache::new();
        let mut was_online = true;
        // Seed policy timestamp so policy path doesn't clear pairing
        let mut ts = SyncTimestamps {
            last_policy_pull_ms: Some(now_ms()),
            online: true,
            ..SyncTimestamps::default()
        };
        save_sync_timestamps(&db.conn, &ts).unwrap();
        // Provide empty policy so pull is not None
        mock.policies.lock().unwrap().insert(
            "fam1/child1".into(),
            PolicyBundle {
                policy: ChildPolicy::default(),
                app_rules: vec![],
                parent_pin_hash: None,
                child_display_name: None,
            },
        );

        sync_tick(
            &db.conn,
            &state,
            &backends,
            &flags,
            &store,
            &tokens,
            &mut was_online,
        );
        assert!(list_usage_dirty(&db.conn).unwrap().is_empty());
        assert_eq!(mock.usage.lock().unwrap().len(), 1);
        assert!(mock.quiz.lock().unwrap().contains("a1"));

        // Second upload is idempotent
        meritscreen_db::enqueue_quiz_upload(
            &db.conn,
            "a1",
            &serde_json::to_string(&attempt).unwrap(),
        )
        .unwrap();
        // already uploaded flag — enqueue OR IGNORE keeps uploaded=1
        flags.expedite_usage.store(true, Ordering::SeqCst);
        ts.last_usage_upload_ms = None;
        save_sync_timestamps(&db.conn, &ts).unwrap();
        sync_tick(
            &db.conn,
            &state,
            &backends,
            &flags,
            &store,
            &tokens,
            &mut was_online,
        );
        assert_eq!(mock.quiz.lock().unwrap().len(), 1);
    }
}
