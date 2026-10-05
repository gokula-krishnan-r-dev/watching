//! HMAC-SHA256 channel authentication for IPC frames (d9).
//!
//! Key lives in the OS/file secret store (`ipc_hmac_key`). Guardian creates it on
//! first boot; Agent/UI load the same key. Frames without a valid MAC fail closed
//! when a key is configured (unless `MERITSCREEN_IPC_HMAC_DISABLE=1`).

use hmac::{Hmac, Mac};
use sha2::Sha256;
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop};

type HmacSha256 = Hmac<Sha256>;

pub const HMAC_LEN: usize = 32;

#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct IpcHmacKey {
    bytes: [u8; 32],
}

impl IpcHmacKey {
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        Self { bytes }
    }

    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.bytes
    }

    /// Load from secret store, or create + persist a new key.
    pub fn load_or_create() -> Result<Self, IpcAuthError> {
        use meritscreen_security::{accounts, SecretStore};
        let store = SecretStore::platform();
        match store.get(accounts::IPC_HMAC) {
            Ok(secret) => Self::from_hex(secret.as_str()),
            Err(_) => {
                let mut bytes = [0u8; 32];
                rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut bytes);
                let hex_key = hex::encode(bytes);
                store
                    .set(accounts::IPC_HMAC, &hex_key)
                    .map_err(|e| IpcAuthError::Store(e.to_string()))?;
                Ok(Self { bytes })
            }
        }
    }

    pub fn from_hex(hex_key: &str) -> Result<Self, IpcAuthError> {
        let raw = hex::decode(hex_key.trim()).map_err(|_| IpcAuthError::InvalidKey)?;
        if raw.len() != 32 {
            return Err(IpcAuthError::InvalidKey);
        }
        let mut bytes = [0u8; 32];
        bytes.copy_from_slice(&raw);
        Ok(Self { bytes })
    }
}

#[derive(Debug, Error)]
pub enum IpcAuthError {
    #[error("ipc hmac key invalid")]
    InvalidKey,
    #[error("ipc hmac missing — refuse unsigned frame")]
    MissingMac,
    #[error("ipc hmac verification failed")]
    BadMac,
    #[error("secret store: {0}")]
    Store(String),
}

pub fn sign(key: &IpcHmacKey, payload: &[u8]) -> [u8; HMAC_LEN] {
    let mut mac =
        HmacSha256::new_from_slice(key.as_bytes()).expect("HMAC_SHA256 accepts 32-byte key");
    mac.update(payload);
    let result = mac.finalize().into_bytes();
    let mut out = [0u8; HMAC_LEN];
    out.copy_from_slice(&result);
    out
}

pub fn verify(key: &IpcHmacKey, payload: &[u8], mac: &[u8]) -> Result<(), IpcAuthError> {
    if mac.len() != HMAC_LEN {
        return Err(IpcAuthError::BadMac);
    }
    let mut verifier =
        HmacSha256::new_from_slice(key.as_bytes()).expect("HMAC_SHA256 accepts 32-byte key");
    verifier.update(payload);
    verifier
        .verify_slice(mac)
        .map_err(|_| IpcAuthError::BadMac)
}

/// When true, unsigned frames are accepted (lab only).
pub fn allow_insecure_ipc() -> bool {
    std::env::var("MERITSCREEN_IPC_HMAC_DISABLE").ok().as_deref() == Some("1")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sign_verify_round_trip() {
        let key = IpcHmacKey::from_bytes([7u8; 32]);
        let mac = sign(&key, b"ping");
        assert!(verify(&key, b"ping", &mac).is_ok());
        assert!(verify(&key, b"pong", &mac).is_err());
        let mut bad = mac;
        bad[0] ^= 0xff;
        assert!(verify(&key, b"ping", &bad).is_err());
    }
}
