//! Inventory scan + device_runtime merge for heartbeat / tamper / tier.

use meritscreen_core::AppRule;
use meritscreen_db::{
    get_inventory_hash, replace_installed_apps, set_inventory_hash, StoredInstalledApp,
};
use meritscreen_enforcement::{
    hash_inventory, scan_installed_apps, strict_mode_availability, EnforcementTier,
    GuardianRuntimeState, InstalledApp, StrictAvailability, TamperFlags,
};
use rusqlite::Connection;
use tracing::{info, warn};

use crate::state::GuardianState;

pub fn compute_tier(strict_enabled: bool) -> EnforcementTier {
    match strict_mode_availability() {
        StrictAvailability::Available if strict_enabled => EnforcementTier::L1L2L3,
        _ => EnforcementTier::L1L3,
    }
}

pub fn degraded_message(tamper: &TamperFlags, strict_enabled: bool) -> Option<String> {
    if tamper.admin_account {
        return Some(meritscreen_enforcement::DEGRADED_ADMIN.into());
    }
    if tamper.uninstall_attempt {
        return Some(
            "Uninstall was attempted without a verified Parent PIN. Ask a parent to check MeritScreen."
                .into(),
        );
    }
    if strict_enabled {
        if let StrictAvailability::Unavailable { reason } = strict_mode_availability() {
            return Some(reason);
        }
    }
    match strict_mode_availability() {
        StrictAvailability::NotApplicable { reason } => Some(reason),
        _ => None,
    }
}

/// Scan filesystem, update SQLCipher cache when hash changes.
pub fn maybe_rescan_inventory(conn: &Connection) -> Result<bool, String> {
    let apps = scan_installed_apps();
    let hash = hash_inventory(&apps);
    let prev = get_inventory_hash(conn).map_err(|e| e.to_string())?;
    if prev.as_deref() == Some(hash.as_str()) {
        return Ok(false);
    }
    let stored: Vec<StoredInstalledApp> = apps
        .iter()
        .map(|a| StoredInstalledApp {
            app_id: a.app_id.clone(),
            label: a.label.clone(),
            icon_hash: a.icon_hash.clone(),
        })
        .collect();
    replace_installed_apps(conn, &stored).map_err(|e| e.to_string())?;
    set_inventory_hash(conn, &hash).map_err(|e| e.to_string())?;
    info!(count = apps.len(), %hash, "inventory cache updated");
    let _ = apps;
    Ok(true)
}

pub fn merge_device_runtime(
    conn: &Connection,
    state: &GuardianState,
    guardian_state: GuardianRuntimeState,
) -> Result<(), String> {
    let tamper = state.tamper.lock().clone();
    let tier = *state.tier.lock();
    let degraded = degraded_message(&tamper, *state.strict_enabled.lock());
    let mut flags = serde_json::Map::new();
    for name in tamper.as_flag_names() {
        flags.insert(name.into(), serde_json::Value::Bool(true));
    }
    let json = serde_json::json!({
        "guardianState": guardian_state.as_str(),
        "enforcementTier": tier.as_str(),
        "tamperFlags": flags,
        "degradedMessage": degraded,
        "strictAvailable": matches!(strict_mode_availability(), StrictAvailability::Available),
    });
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);
    conn.execute(
        "UPDATE device_runtime SET json = ?1, updated_at_ms = ?2 WHERE id = 1",
        rusqlite::params![json.to_string(), now],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn default_demo_rules() -> Vec<AppRule> {
    let mut browser = AppRule::new("browser", "mac:com.apple.Safari");
    browser.display_name = "Safari".into();
    let mut notes = AppRule::new("notes", "mac:com.apple.Notes");
    notes.display_name = "Notes".into();
    let mut calc = AppRule::new(
        "calculator",
        "win:Microsoft.WindowsCalculator_8wekyb3d8bbwe!App",
    );
    calc.display_name = "Calculator".into();
    let mut phone = AppRule::new("messages", "mac:com.apple.MobileSMS");
    phone.display_name = "Messages".into();
    phone.is_emergency = true;
    vec![browser, notes, calc, phone]
}

pub fn apps_for_upload(apps: &[InstalledApp]) -> Vec<meritscreen_firebase::InstalledAppUpload> {
    apps.iter()
        .map(|a| meritscreen_firebase::InstalledAppUpload {
            package_name: a.app_id.clone(),
            label: a.label.clone(),
            icon_hash: a.icon_hash.clone(),
        })
        .collect()
}

pub fn warn_inventory(err: String) {
    warn!(error = %err, "inventory rescan failed");
}

#[cfg(test)]
mod tests {
    use super::*;
    use meritscreen_db::open_with_key;
    use meritscreen_enforcement::TamperFlags;
    use tempfile::tempdir;

    #[test]
    fn inventory_rescan_and_cache() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("guardian.db");
        let conn = open_with_key(&path, b"test-key-32-bytes-long-padding!").unwrap();

        let updated = maybe_rescan_inventory(&conn).unwrap();
        assert!(updated, "Initial scan must populate cache");

        let second = maybe_rescan_inventory(&conn).unwrap();
        assert!(!second, "Second scan with unchanged apps must be a no-op");
    }

    #[test]
    fn degraded_message_admin_and_tamper() {
        let mut tamper = TamperFlags::default();
        #[cfg(not(target_os = "macos"))]
        let _ = &tamper;
        #[cfg(target_os = "macos")]
        assert_eq!(
            degraded_message(&tamper, false).as_deref(),
            Some("Consumer macOS cannot replace Finder/Dock — L1+L3 only.")
        );

        tamper.admin_account = true;
        assert_eq!(
            degraded_message(&tamper, false).as_deref(),
            Some(meritscreen_enforcement::DEGRADED_ADMIN)
        );

        tamper.admin_account = false;
        tamper.uninstall_attempt = true;
        assert!(degraded_message(&tamper, false)
            .unwrap()
            .contains("Uninstall was attempted"));
    }

    #[test]
    fn device_runtime_merge() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("guardian.db");
        let conn = open_with_key(&path, b"test-key-32-bytes-long-padding!").unwrap();
        let state = GuardianState::default();

        merge_device_runtime(&conn, &state, GuardianRuntimeState::Running).unwrap();
        let json: String = conn
            .query_row("SELECT json FROM device_runtime WHERE id = 1", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert!(json.contains("\"guardianState\":\"running\""));
        assert!(json.contains("\"enforcementTier\":\"L1_L3\""));
    }
}
