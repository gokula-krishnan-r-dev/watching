//! Local encrypted store. Child launcher + quiz read this store only — never Firebase.

#![forbid(unsafe_code)]

mod error;
mod inventory_store;
mod open;
mod prefs;
mod session_store;
mod sync_store;

pub use error::{DbError, DbResult};
pub use inventory_store::{
    get_inventory_hash, list_installed_apps, load_app_rules_json, replace_installed_apps,
    set_inventory_hash, StoredInstalledApp,
};
pub use open::{open_with_key, SCHEMA_VERSION};
pub use prefs::{get_pref, set_pref};
pub use session_store::{load_session, save_policy_json, save_session};
pub use sync_store::{
    clear_pairing_ids, enqueue_quiz_upload, list_pending_quiz_uploads, list_usage_dirty,
    load_app_rules, load_pairing_ids, load_policy, load_sync_timestamps, mark_quiz_uploaded,
    mark_usage_clean, quiz_bank_count, replace_app_rules, save_pairing_ids, save_policy,
    save_sync_timestamps, upsert_usage_dirty, DevicePairingIds, SyncTimestamps,
};

pub const APPEARANCE_PREF_KEY: &str = "appearance";
