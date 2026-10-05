//! Open encrypted SQLCipher database with a caller-supplied key.
//!
//! Licensing: SQLCipher Community Edition (BSD) via `rusqlite` feature
//! `bundled-sqlcipher-vendored-openssl`. See `desktop/docs/sqlcipher.md`.

use std::path::Path;

use rusqlite::{Connection, OptionalExtension};
use tracing::debug;
use zeroize::Zeroizing;

use crate::error::{DbError, DbResult};

pub const SCHEMA_VERSION: u32 = 3;

const SCHEMA_SQL: &str = include_str!("schema.sql");

/// Opens (or creates) a SQLCipher database at `path`, applying `key`.
///
/// Wrong keys fail closed ([`DbError::InvalidKey`]) after schema probe.
pub fn open_with_key(path: &Path, key: &[u8]) -> DbResult<Connection> {
    if key.is_empty() {
        return Err(DbError::InvalidKey);
    }

    let conn = Connection::open(path).map_err(DbError::Open)?;
    apply_key(&conn, key)?;
    // Fail closed if the key cannot decrypt an existing file.
    match conn.query_row("SELECT count(*) FROM sqlite_master", [], |r| {
        r.get::<_, i64>(0)
    }) {
        Ok(_) => {}
        Err(e) => {
            let msg = e.to_string().to_ascii_lowercase();
            if msg.contains("file is not a database")
                || msg.contains("not a database")
                || msg.contains("hmac")
                || msg.contains("encrypt")
            {
                return Err(DbError::InvalidKey);
            }
            return Err(DbError::Open(e));
        }
    }

    migrate(&conn)?;
    Ok(conn)
}

fn apply_key(conn: &Connection, key: &[u8]) -> DbResult<()> {
    // Hex passphrase is stable across platforms; zeroized after use.
    let hex = Zeroizing::new(hex_encode(key));
    conn.pragma_update(None, "key", hex.as_str())
        .map_err(DbError::Open)?;
    // Reasonable SQLCipher defaults for laptop disks.
    let _ = conn.pragma_update(None, "cipher_page_size", 4096);
    let _ = conn.pragma_update(None, "kdf_iter", 256_000);
    Ok(())
}

fn migrate(conn: &Connection) -> DbResult<()> {
    conn.execute_batch(SCHEMA_SQL)
        .map_err(|e| DbError::Migration(e.to_string()))?;

    let version: Option<String> = conn
        .query_row(
            "SELECT value FROM meta WHERE key = 'schema_version'",
            [],
            |r| r.get(0),
        )
        .optional()
        .map_err(DbError::Query)?;

    match version.as_deref() {
        None => {
            conn.execute(
                "INSERT INTO meta(key, value) VALUES ('schema_version', ?1)",
                [SCHEMA_VERSION.to_string()],
            )
            .map_err(DbError::Query)?;
            debug!(version = SCHEMA_VERSION, "initialized desktop schema");
        }
        Some(v) if v.parse::<u32>().ok() == Some(SCHEMA_VERSION) => {}
        Some("1") | Some("2") => {
            // Incremental tables created by SCHEMA_SQL IF NOT EXISTS above.
            conn.execute(
                "UPDATE meta SET value = ?1 WHERE key = 'schema_version'",
                [SCHEMA_VERSION.to_string()],
            )
            .map_err(DbError::Query)?;
            debug!(version = SCHEMA_VERSION, "migrated desktop schema");
        }
        Some(v) => {
            return Err(DbError::Migration(format!(
                "unsupported schema_version {v}; expected {SCHEMA_VERSION}"
            )));
        }
    }

    // Ensure singleton rows exist.
    let now = now_ms();
    conn.execute(
        "INSERT OR IGNORE INTO sync_state(id, json) VALUES (1, '{}')",
        [],
    )
    .map_err(DbError::Query)?;
    conn.execute(
        "INSERT OR IGNORE INTO device_runtime(id, role, json, updated_at_ms) VALUES (1, 'unassigned', '{}', ?1)",
        [now],
    )
    .map_err(DbError::Query)?;

    Ok(())
}

fn hex_encode(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        out.push(HEX[(b >> 4) as usize] as char);
        out.push(HEX[(b & 0xf) as usize] as char);
    }
    out
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
    use tempfile::tempdir;

    #[test]
    fn open_with_key_creates_schema() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("ms.db");
        let conn = open_with_key(&path, b"test-key-32-bytes-padding!!!!!!").unwrap();
        let version: String = conn
            .query_row(
                "SELECT value FROM meta WHERE key = 'schema_version'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(version, "3");
        let role: String = conn
            .query_row("SELECT role FROM device_runtime WHERE id = 1", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert_eq!(role, "unassigned");
    }

    #[test]
    fn wrong_key_fails_closed() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("ms.db");
        {
            let _ = open_with_key(&path, b"correct-key-aaaaaaaaaaaaaaaaaa").unwrap();
        }
        let err = open_with_key(&path, b"wrong-key-bbbbbbbbbbbbbbbbbbbb").unwrap_err();
        assert!(matches!(err, DbError::InvalidKey | DbError::Open(_)));
    }

    #[test]
    fn empty_key_rejected() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("ms.db");
        assert!(matches!(
            open_with_key(&path, b"").unwrap_err(),
            DbError::InvalidKey
        ));
    }
}
