//! Honest uninstall protection (d9).
//!
//! - PIN-gated path we own (`--uninstall-with-pin`)
//! - Detection path: mark `tamperFlags.uninstallAttempt` when uninstall starts
//!   without a verified PIN (or when service removal is observed)
//! - Never claim silent MDM-grade uninstall blocks on consumer SKUs

use anyhow::{bail, Context, Result};
use meritscreen_security::{accounts, verify_parent_pin, SecretStore};
use tracing::info;

use crate::db_boot::{mark_tamper_uninstall_attempt, open_guardian_db};

/// Verify parent PIN from the secret store, then run platform uninstall hooks.
pub fn uninstall_with_pin(pin: &str) -> Result<()> {
    let pin = pin.trim();
    if pin.is_empty() {
        bail!("Parent PIN required to uninstall MeritScreen.");
    }
    let store = SecretStore::platform();
    let encoded = store
        .get(accounts::PARENT_PIN_HASH)
        .context("Parent PIN is not set on this device. Sign in as parent first.")?;
    if !verify_parent_pin(pin, encoded.as_str()) {
        // Detection: wrong / spoofed uninstall attempts still raise the flag.
        let _ = mark_uninstall_attempt_flag();
        bail!("Incorrect Parent PIN. Uninstall cancelled.");
    }
    platform_uninstall()?;
    info!("PIN-gated uninstall completed");
    Ok(())
}

/// Persist `uninstallAttempt` for the next Guardian sync / parent alert.
pub fn mark_uninstall_attempt_flag() -> Result<()> {
    let db = open_guardian_db()?;
    mark_tamper_uninstall_attempt(&db.conn)?;
    Ok(())
}

fn platform_uninstall() -> Result<()> {
    #[cfg(windows)]
    {
        crate::windows_service::uninstall_service()
            .map_err(|e| anyhow::anyhow!("Windows service uninstall: {e}"))?;
    }
    #[cfg(target_os = "macos")]
    {
        // Prefer system daemon removal when root; else user LaunchAgents.
        if unsafe { libc::geteuid() } == 0 {
            crate::macos_service::uninstall_daemon()?;
        } else {
            crate::macos_service::uninstall_user_agents()?;
        }
    }
    #[cfg(not(any(windows, target_os = "macos")))]
    {
        bail!("PIN uninstall helper is Windows/macOS only in v1");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use meritscreen_security::hash_parent_pin;
    use tempfile::tempdir;

    #[test]
    fn wrong_pin_marks_attempt_and_fails() {
        let _guard = crate::test_env::lock();
        let tmp = tempdir().unwrap();
        std::env::set_var("MERITSCREEN_DATA_DIR", tmp.path());
        std::env::set_var("MERITSCREEN_SECRETS_DIR", tmp.path().join("secrets"));
        let store = SecretStore::platform();
        store
            .set(accounts::PARENT_PIN_HASH, &hash_parent_pin("1234"))
            .unwrap();
        let err = uninstall_with_pin("9999").unwrap_err();
        assert!(
            err.to_string().contains("Incorrect"),
            "unexpected err: {err}"
        );
        let db = open_guardian_db().unwrap();
        let json: String = db
            .conn
            .query_row("SELECT json FROM device_runtime WHERE id = 1", [], |r| {
                r.get(0)
            })
            .unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["tamperFlags"]["uninstallAttempt"], true);
    }
}
