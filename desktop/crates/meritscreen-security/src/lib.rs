//! Security helpers: secret store, LogSanitizer, Appearance persistence, PIN hash.

#![forbid(unsafe_code)]

mod appearance_store;
mod log_sanitizer;
mod pin;
mod secret_store;

pub use appearance_store::{AppearanceStore, AppearanceStoreError};
pub use log_sanitizer::{init_tracing, sanitize_for_log};
pub use pin::{hash_parent_pin, verify_parent_pin};
pub use secret_store::{accounts, SecretResult, SecretStore, SecretStoreError};
