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
