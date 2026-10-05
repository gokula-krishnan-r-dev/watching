//! Local installed-apps cache + inventory hash on device_runtime.

use rusqlite::{params, Connection, OptionalExtension};

use crate::error::{DbError, DbResult};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredInstalledApp {
    pub app_id: String,
    pub label: String,
    pub icon_hash: Option<String>,
}

pub fn replace_installed_apps(conn: &Connection, apps: &[StoredInstalledApp]) -> DbResult<()> {
    let tx = conn.unchecked_transaction().map_err(DbError::Query)?;
    tx.execute("DELETE FROM installed_apps", [])
        .map_err(DbError::Query)?;
    let now = now_ms();
    for app in apps {
        tx.execute(
            "INSERT INTO installed_apps(app_id, label, icon_hash, json, updated_at_ms)
             VALUES (?1, ?2, ?3, '{}', ?4)",
            params![app.app_id, app.label, app.icon_hash, now],
        )
        .map_err(DbError::Query)?;
    }
    tx.commit().map_err(DbError::Query)?;
    Ok(())
}

pub fn list_installed_apps(conn: &Connection) -> DbResult<Vec<StoredInstalledApp>> {
    let mut stmt = conn
        .prepare(
            "SELECT app_id, label, icon_hash FROM installed_apps ORDER BY label COLLATE NOCASE",
        )
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| {
            Ok(StoredInstalledApp {
                app_id: r.get(0)?,
                label: r.get(1)?,
                icon_hash: r.get(2)?,
            })
        })
        .map_err(DbError::Query)?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(DbError::Query)?);
    }
    Ok(out)
}

pub fn load_app_rules_json(conn: &Connection) -> DbResult<Vec<String>> {
    let mut stmt = conn
        .prepare("SELECT json FROM app_rules WHERE allowed = 1 OR emergency = 1")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(0))
        .map_err(DbError::Query)?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(DbError::Query)?);
    }
    Ok(out)
}

pub fn set_inventory_hash(conn: &Connection, hash: &str) -> DbResult<()> {
    let now = now_ms();
    conn.execute(
        "UPDATE device_runtime SET inventory_hash = ?1, updated_at_ms = ?2 WHERE id = 1",
        params![hash, now],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

pub fn get_inventory_hash(conn: &Connection) -> DbResult<Option<String>> {
    conn.query_row(
        "SELECT inventory_hash FROM device_runtime WHERE id = 1",
        [],
        |r| r.get(0),
    )
    .optional()
    .map_err(DbError::Query)
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
    fn replace_and_list() {
        let dir = tempdir().unwrap();
        let key = vec![b'k'; 32];
        let conn = open_with_key(&dir.path().join("t.db"), &key).unwrap();
        replace_installed_apps(
            &conn,
            &[StoredInstalledApp {
                app_id: "mac:com.apple.Safari".into(),
                label: "Safari".into(),
                icon_hash: Some("ab".into()),
            }],
        )
        .unwrap();
        let list = list_installed_apps(&conn).unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].app_id, "mac:com.apple.Safari");
        set_inventory_hash(&conn, "deadbeef").unwrap();
        assert_eq!(
            get_inventory_hash(&conn).unwrap().as_deref(),
            Some("deadbeef")
        );
    }
}
