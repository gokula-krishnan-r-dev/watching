//! OS secret store wrappers: DPAPI (Windows), Keychain (macOS), Secret Service (Linux).
//! Falls back to a restricted file store when the OS backend is unavailable (tests / Linux stub).

use std::fs;
use std::path::{Path, PathBuf};

use thiserror::Error;
use zeroize::Zeroizing;

const SERVICE: &str = "com.meritscreen.desktop";

#[derive(Debug, Error)]
pub enum SecretStoreError {
    #[error("secret store unavailable: {0}")]
    Backend(String),
    #[error("secret not found")]
    NotFound,
}

pub type SecretResult<T> = Result<T, SecretStoreError>;

#[derive(Debug, Clone)]
enum Backend {
    Keyring,
    File(PathBuf),
}

/// Thin wrapper over the platform credential store.
#[derive(Debug, Clone)]
pub struct SecretStore {
    service: String,
    backend: Backend,
}

impl Default for SecretStore {
    fn default() -> Self {
        Self::platform()
    }
}

impl SecretStore {
    /// Prefer OS keyring; fall back to file store under config dir.
    ///
    /// Override with `MERITSCREEN_SECRETS_DIR` (integration tests / headless CI).
    pub fn platform() -> Self {
        if let Ok(dir) = std::env::var("MERITSCREEN_SECRETS_DIR") {
            return Self::file_backed(dir);
        }
        match try_keyring_roundtrip() {
            Ok(()) => Self {
                service: SERVICE.into(),
                backend: Backend::Keyring,
            },
            Err(_) => Self::file_backed(default_file_secrets_dir()),
        }
    }

    /// Explicit file-backed store (unit tests / Linux Secret Service stub).
    pub fn file_backed(dir: impl AsRef<Path>) -> Self {
        let dir = dir.as_ref().to_path_buf();
        let _ = fs::create_dir_all(&dir);
        Self {
            service: SERVICE.into(),
            backend: Backend::File(dir),
        }
    }

    pub fn set(&self, account: &str, secret: &str) -> SecretResult<()> {
        match &self.backend {
            Backend::Keyring => {
                let entry = keyring::Entry::new(&self.service, account)
                    .map_err(|e| SecretStoreError::Backend(e.to_string()))?;
                entry
                    .set_password(secret)
                    .map_err(|e| SecretStoreError::Backend(e.to_string()))
            }
            Backend::File(dir) => {
                let path = dir.join(sanitize_account(account));
                fs::write(path, secret).map_err(|e| SecretStoreError::Backend(e.to_string()))
            }
        }
    }

    pub fn get(&self, account: &str) -> SecretResult<Zeroizing<String>> {
        match &self.backend {
            Backend::Keyring => {
                let entry = keyring::Entry::new(&self.service, account)
                    .map_err(|e| SecretStoreError::Backend(e.to_string()))?;
                match entry.get_password() {
                    Ok(p) => Ok(Zeroizing::new(p)),
                    Err(keyring::Error::NoEntry) => Err(SecretStoreError::NotFound),
                    Err(e) => Err(SecretStoreError::Backend(e.to_string())),
                }
            }
            Backend::File(dir) => {
                let path = dir.join(sanitize_account(account));
                match fs::read_to_string(path) {
                    Ok(p) => Ok(Zeroizing::new(p)),
                    Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                        Err(SecretStoreError::NotFound)
                    }
                    Err(e) => Err(SecretStoreError::Backend(e.to_string())),
                }
            }
        }
    }

    pub fn delete(&self, account: &str) -> SecretResult<()> {
        match &self.backend {
            Backend::Keyring => {
                let entry = keyring::Entry::new(&self.service, account)
                    .map_err(|e| SecretStoreError::Backend(e.to_string()))?;
                match entry.delete_credential() {
                    Ok(()) => Ok(()),
                    Err(keyring::Error::NoEntry) => Ok(()),
                    Err(e) => Err(SecretStoreError::Backend(e.to_string())),
                }
            }
            Backend::File(dir) => {
                let path = dir.join(sanitize_account(account));
                match fs::remove_file(path) {
                    Ok(()) => Ok(()),
                    Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
                    Err(e) => Err(SecretStoreError::Backend(e.to_string())),
                }
            }
        }
    }
}

fn default_file_secrets_dir() -> PathBuf {
    directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen")
        .map(|d| d.data_local_dir().join("secrets"))
        .unwrap_or_else(|| PathBuf::from(".meritscreen-secrets"))
}

/// Probe must actually read/write — `Entry::new` alone succeeds on macOS even when
/// `set_password` is denied (tests with overridden HOME, headless agents).
fn try_keyring_roundtrip() -> Result<(), String> {
    let entry = keyring::Entry::new(SERVICE, "__probe__").map_err(|e| e.to_string())?;
    entry.set_password("ok").map_err(|e| e.to_string())?;
    let got = entry.get_password().map_err(|e| e.to_string())?;
    let _ = entry.delete_credential();
    if got != "ok" {
        return Err("keyring probe mismatch".into());
    }
    Ok(())
}

fn sanitize_account(account: &str) -> String {
    account
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '_' || c == '-' {
                c
            } else {
                '_'
            }
        })
        .collect()
}

/// Well-known account names.
pub mod accounts {
    pub const DB_KEY: &str = "db_key";
    pub const CHILD_REFRESH: &str = "child_refresh_token";
    pub const CHILD_ID_TOKEN: &str = "child_id_token";
    pub const PARENT_REFRESH: &str = "parent_refresh_token";
    pub const PARENT_PIN_HASH: &str = "parent_pin_hash";
    /// Shared Guardian ↔ Agent ↔ UI frame MAC key (d9).
    pub const IPC_HMAC: &str = "ipc_hmac_key";
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn file_backed_round_trip() {
        let dir = tempdir().unwrap();
        let store = SecretStore::file_backed(dir.path());
        store.set(accounts::DB_KEY, "secret-value").unwrap();
        assert_eq!(
            store.get(accounts::DB_KEY).unwrap().as_str(),
            "secret-value"
        );
        store.delete(accounts::DB_KEY).unwrap();
        assert!(matches!(
            store.get(accounts::DB_KEY).unwrap_err(),
            SecretStoreError::NotFound
        ));
    }
}
