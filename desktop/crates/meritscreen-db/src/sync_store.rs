//! Sync timestamps, pairing ids, usage dirty, quiz upload queue, policy cache.

use meritscreen_core::{AppRule, ChildPolicy};
use rusqlite::{params, Connection, OptionalExtension};
use serde_json::Value;

use crate::error::{DbError, DbResult};

#[derive(Debug, Clone, Default)]
pub struct SyncTimestamps {
    pub last_policy_pull_ms: Option<i64>,
    pub last_usage_upload_ms: Option<i64>,
    pub last_heartbeat_ms: Option<i64>,
    pub last_inventory_upload_ms: Option<i64>,
    pub last_pack_refresh_ms: Option<i64>,
    pub online: bool,
}

#[derive(Debug, Clone, Default)]
pub struct DevicePairingIds {
    pub family_id: Option<String>,
    pub child_id: Option<String>,
    pub device_id: Option<String>,
    pub platform: Option<String>,
}

pub fn load_sync_timestamps(conn: &Connection) -> DbResult<SyncTimestamps> {
    let row = conn.query_row(
        "SELECT last_policy_pull_ms, last_usage_upload_ms, last_heartbeat_ms, json FROM sync_state WHERE id = 1",
        [],
        |r| {
            Ok((
                r.get::<_, Option<i64>>(0)?,
                r.get::<_, Option<i64>>(1)?,
                r.get::<_, Option<i64>>(2)?,
                r.get::<_, String>(3)?,
            ))
        },
    );
    match row {
        Ok((policy, usage, hb, json_raw)) => {
            let extra: Value = serde_json::from_str(&json_raw).unwrap_or_default();
            Ok(SyncTimestamps {
                last_policy_pull_ms: policy,
                last_usage_upload_ms: usage,
                last_heartbeat_ms: hb,
                last_inventory_upload_ms: extra
                    .get("lastInventoryUploadMs")
                    .and_then(|v| v.as_i64()),
                last_pack_refresh_ms: extra.get("lastPackRefreshMs").and_then(|v| v.as_i64()),
                online: extra
                    .get("online")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(true),
            })
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(SyncTimestamps::default()),
        Err(e) => Err(DbError::Query(e)),
    }
}

pub fn save_sync_timestamps(conn: &Connection, ts: &SyncTimestamps) -> DbResult<()> {
    let json = serde_json::json!({
        "lastInventoryUploadMs": ts.last_inventory_upload_ms,
        "lastPackRefreshMs": ts.last_pack_refresh_ms,
        "online": ts.online,
    });
    conn.execute(
        "UPDATE sync_state SET
            last_policy_pull_ms = ?1,
            last_usage_upload_ms = ?2,
            last_heartbeat_ms = ?3,
            json = ?4
         WHERE id = 1",
        params![
            ts.last_policy_pull_ms,
            ts.last_usage_upload_ms,
            ts.last_heartbeat_ms,
            json.to_string()
        ],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn load_pairing_ids(conn: &Connection) -> DbResult<DevicePairingIds> {
    conn.query_row(
        "SELECT family_id, child_id, device_id, platform FROM device_runtime WHERE id = 1",
        [],
        |r| {
            Ok(DevicePairingIds {
                family_id: r.get(0)?,
                child_id: r.get(1)?,
                device_id: r.get(2)?,
                platform: r.get(3)?,
            })
        },
    )
    .optional()
    .map_err(DbError::Query)
    .map(|o| o.unwrap_or_default())
}

pub fn save_pairing_ids(conn: &Connection, ids: &DevicePairingIds) -> DbResult<()> {
    let now = now_ms();
    conn.execute(
        "UPDATE device_runtime SET
            family_id = ?1,
            child_id = ?2,
            device_id = ?3,
            platform = ?4,
            role = CASE WHEN ?2 IS NOT NULL THEN 'child' ELSE role END,
            updated_at_ms = ?5
         WHERE id = 1",
        params![
            ids.family_id,
            ids.child_id,
            ids.device_id,
            ids.platform,
            now
        ],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn clear_pairing_ids(conn: &Connection) -> DbResult<()> {
    save_pairing_ids(
        conn,
        &DevicePairingIds {
            family_id: None,
            child_id: None,
            device_id: None,
            platform: None,
        },
    )?;
    conn.execute(
        "UPDATE device_runtime SET role = 'unassigned', updated_at_ms = ?1 WHERE id = 1",
        [now_ms()],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn load_policy(conn: &Connection) -> DbResult<Option<ChildPolicy>> {
    let json: Option<String> = conn
        .query_row("SELECT json FROM policy WHERE id = 1", [], |r| r.get(0))
        .optional()
        .map_err(DbError::Query)?;
    match json {
        Some(raw) => Ok(Some(
            serde_json::from_str(&raw).map_err(|e| DbError::Migration(e.to_string()))?,
        )),
        None => Ok(None),
    }
}

pub fn save_policy(conn: &Connection, policy: &ChildPolicy) -> DbResult<()> {
    let raw = serde_json::to_string(policy).map_err(|e| DbError::Migration(e.to_string()))?;
    crate::session_store::save_policy_json(conn, &raw)
}

pub fn replace_app_rules(conn: &Connection, rules: &[AppRule]) -> DbResult<()> {
    let tx = conn.unchecked_transaction().map_err(DbError::Query)?;
    tx.execute("DELETE FROM app_rules", [])
        .map_err(DbError::Query)?;
    let now = now_ms();
    for r in rules {
        let json = serde_json::to_string(r).map_err(|e| DbError::Migration(e.to_string()))?;
        tx.execute(
            "INSERT INTO app_rules(app_id, label, allowed, block_minutes, grant_on_pass_minutes, emergency, json, updated_at_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                r.app_id,
                r.display_name,
                r.allowed as i32,
                r.block_minutes,
                r.grant_on_pass_minutes,
                r.is_emergency as i32,
                json,
                now
            ],
        )
        .map_err(DbError::Query)?;
    }
    tx.commit().map_err(DbError::Query)?;
    Ok(())
}

pub fn load_app_rules(conn: &Connection) -> DbResult<Vec<AppRule>> {
    let mut stmt = conn
        .prepare("SELECT json FROM app_rules")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(0))
        .map_err(DbError::Query)?;
    let mut out = Vec::new();
    for row in rows {
        let raw = row.map_err(DbError::Query)?;
        if let Ok(rule) = serde_json::from_str(&raw) {
            out.push(rule);
        }
    }
    Ok(out)
}

pub fn upsert_usage_dirty(conn: &Connection, day_key: &str, minutes: u32) -> DbResult<()> {
    let now = now_ms();
    conn.execute(
        "INSERT INTO usage_dirty(day_key, minutes, dirty, json, updated_at_ms)
         VALUES (?1, ?2, 1, '{}', ?3)
         ON CONFLICT(day_key) DO UPDATE SET
           minutes = excluded.minutes,
           dirty = 1,
           updated_at_ms = excluded.updated_at_ms",
        params![day_key, minutes, now],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn list_usage_dirty(conn: &Connection) -> DbResult<Vec<(String, u32)>> {
    let mut stmt = conn
        .prepare("SELECT day_key, minutes FROM usage_dirty WHERE dirty = 1")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, u32>(1)?)))
        .map_err(DbError::Query)?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(DbError::Query)?);
    }
    Ok(out)
}

pub fn mark_usage_clean(conn: &Connection, day_key: &str) -> DbResult<()> {
    conn.execute(
        "UPDATE usage_dirty SET dirty = 0, updated_at_ms = ?1 WHERE day_key = ?2",
        params![now_ms(), day_key],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn enqueue_quiz_upload(conn: &Connection, attempt_id: &str, json: &str) -> DbResult<()> {
    conn.execute(
        "INSERT OR IGNORE INTO quiz_upload_queue(attempt_id, json, uploaded, updated_at_ms)
         VALUES (?1, ?2, 0, ?3)",
        params![attempt_id, json, now_ms()],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn list_pending_quiz_uploads(conn: &Connection) -> DbResult<Vec<(String, String)>> {
    let mut stmt = conn
        .prepare("SELECT attempt_id, json FROM quiz_upload_queue WHERE uploaded = 0")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
        .map_err(DbError::Query)?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(DbError::Query)?);
    }
    Ok(out)
}

pub fn mark_quiz_uploaded(conn: &Connection, attempt_id: &str) -> DbResult<()> {
    conn.execute(
        "UPDATE quiz_upload_queue SET uploaded = 1, updated_at_ms = ?1 WHERE attempt_id = ?2",
        params![now_ms(), attempt_id],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn quiz_bank_count(conn: &Connection) -> DbResult<u32> {
    let n: i64 = conn
        .query_row("SELECT COUNT(*) FROM quiz_items", [], |r| r.get(0))
        .map_err(DbError::Query)?;
    Ok(n as u32)
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
    use crate::open_with_key;
    use tempfile::tempdir;

    #[test]
    fn pairing_and_usage_round_trip() {
        let dir = tempdir().unwrap();
        let key = vec![b'k'; 32];
        let conn = open_with_key(&dir.path().join("s.db"), &key).unwrap();
        save_pairing_ids(
            &conn,
            &DevicePairingIds {
                family_id: Some("fam".into()),
                child_id: Some("child".into()),
                device_id: Some("dev".into()),
                platform: Some("macos".into()),
            },
        )
        .unwrap();
        let ids = load_pairing_ids(&conn).unwrap();
        assert_eq!(ids.family_id.as_deref(), Some("fam"));
        upsert_usage_dirty(&conn, "2026-10-05", 12).unwrap();
        assert_eq!(list_usage_dirty(&conn).unwrap()[0].1, 12);
        mark_usage_clean(&conn, "2026-10-05").unwrap();
        assert!(list_usage_dirty(&conn).unwrap().is_empty());
    }
}
