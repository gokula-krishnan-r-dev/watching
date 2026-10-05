//! Key/value prefs inside the encrypted DB (Appearance, etc.).

use rusqlite::Connection;

use crate::error::{DbError, DbResult};

pub fn get_pref(conn: &Connection, key: &str) -> DbResult<Option<String>> {
    use rusqlite::OptionalExtension;
    conn.query_row("SELECT value FROM prefs WHERE key = ?1", [key], |r| {
        r.get(0)
    })
    .optional()
    .map_err(DbError::Query)
}

pub fn set_pref(conn: &Connection, key: &str, value: &str) -> DbResult<()> {
    conn.execute(
        "INSERT INTO prefs(key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        [key, value],
    )
    .map_err(DbError::Query)?;
    Ok(())
}
