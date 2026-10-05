use thiserror::Error;

#[derive(Debug, Error)]
pub enum DbError {
    #[error("database open failed")]
    Open(#[source] rusqlite::Error),
    #[error("database query failed")]
    Query(#[source] rusqlite::Error),
    #[error("invalid database key")]
    InvalidKey,
    #[error("schema migration failed: {0}")]
    Migration(String),
    #[error("integrity check failed — fail closed")]
    Integrity,
}

pub type DbResult<T> = Result<T, DbError>;
