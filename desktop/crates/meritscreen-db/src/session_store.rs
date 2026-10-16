//! Persist session phase + cooldown across process restart (SQLCipher only).

use meritscreen_core::{SessionPhase, SessionSnapshot};
use rusqlite::Connection;
use serde_json::json;

use crate::error::{DbError, DbResult};

pub fn load_session(conn: &Connection) -> DbResult<SessionSnapshot> {
    let row = conn.query_row(
        "SELECT phase, json FROM session_state WHERE id = 1",
        [],
        |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)),
    );
    match row {
        Ok((phase, json_raw)) => {
            let mut snap: SessionSnapshot =
                serde_json::from_str(&json_raw).map_err(|e| DbError::Migration(e.to_string()))?;
            // Phase column is authoritative if JSON drifts.
            snap.phase = SessionPhase::parse(&phase);
            Ok(snap)
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(SessionSnapshot::default()),
        Err(e) => Err(DbError::Query(e)),
    }
}

pub fn save_session(conn: &Connection, snapshot: &SessionSnapshot) -> DbResult<()> {
    let now = now_ms();
    let json_raw =
        serde_json::to_string(snapshot).map_err(|e| DbError::Migration(e.to_string()))?;
    conn.execute(
        "INSERT INTO session_state(id, phase, json, updated_at_ms) VALUES (1, ?1, ?2, ?3)
         ON CONFLICT(id) DO UPDATE SET
           phase = excluded.phase,
           json = excluded.json,
           updated_at_ms = excluded.updated_at_ms",
        rusqlite::params![snapshot.phase.as_str(), json_raw, now],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

/// Convenience: ensure a policy row exists (engines read policy elsewhere in d5+).
pub fn save_policy_json(conn: &Connection, policy_json: &str) -> DbResult<()> {
    let now = now_ms();
    conn.execute(
        "INSERT INTO policy(id, json, updated_at_ms) VALUES (1, ?1, ?2)
         ON CONFLICT(id) DO UPDATE SET json = excluded.json, updated_at_ms = excluded.updated_at_ms",
        rusqlite::params![policy_json, now],
    )
    .map_err(DbError::Query)?;
    let _ = json!({ "ok": true });
    Ok(())
}

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::open_with_key;
    use tempfile::tempdir;

    #[test]
    fn session_survives_reopen() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("s.db");
        let key = b"session-persist-key-32-bytes!!!!";
        {
            let conn = open_with_key(&path, key).unwrap();
            let snap = SessionSnapshot {
                phase: SessionPhase::Shielded,
                device_shielded_until_elapsed_ms: Some(99_000),
                cooldown_minutes: 15,
                day_key: "2026-10-05".into(),
                ..SessionSnapshot::default()
            };
            save_session(&conn, &snap).unwrap();
        }
        let conn = open_with_key(&path, key).unwrap();
        let loaded = load_session(&conn).unwrap();
        assert_eq!(loaded.phase, SessionPhase::Shielded);
        assert_eq!(loaded.device_shielded_until_elapsed_ms, Some(99_000));
        assert_eq!(loaded.cooldown_minutes, 15);
    }
}
