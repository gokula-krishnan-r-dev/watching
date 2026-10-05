//! Processes / apps MeritScreen must never terminate or cover.

/// Case-insensitive process image basenames (no path).
pub const PROTECTED_PROCESS_NAMES: &[&str] = &[
    // MeritScreen
    "meritscreen-guardian",
    "meritscreen-guardian.exe",
    "meritscreen-agent",
    "meritscreen-agent.exe",
    "meritscreen-ui",
    "meritscreen-ui.exe",
    // Windows
    "csrss.exe",
    "winlogon.exe",
    "wininit.exe",
    "lsass.exe",
    "services.exe",
    "smss.exe",
    "dwm.exe",
    "explorer.exe",
    "taskmgr.exe",
    "systemsettings.exe",
    "fontdrvhost.exe",
    "sihost.exe",
    "startmenuexperiencehost.exe",
    "searchhost.exe",
    "runtimebroker.exe",
    "conhost.exe",
    // macOS
    "kernel_task",
    "launchd",
    "WindowServer",
    "loginwindow",
    "Dock",
    "Finder",
    "SystemUIServer",
    "ControlCenter",
    "NotificationCenter",
    "UniversalControl",
    // Linux
    "systemd",
    "Xorg",
    "Xwayland",
    "gnome-shell",
    "plasmashell",
];

/// Bundle / app ids that are always treated as protected system surfaces.
const PROTECTED_APP_IDS: &[&str] = &[
    "mac:com.apple.finder",
    "mac:com.apple.dock",
    "mac:com.apple.systempreferences",
    "mac:com.apple.loginwindow",
    "win:explorer.exe",
    "linux:org.gnome.Shell.desktop",
];

pub fn is_protected_process_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    let base = lower.rsplit(['/', '\\']).next().unwrap_or(&lower);
    PROTECTED_PROCESS_NAMES
        .iter()
        .any(|p| p.eq_ignore_ascii_case(base))
}

pub fn is_protected_app_id(app_id: &str) -> bool {
    let lower = app_id.to_ascii_lowercase();
    PROTECTED_APP_IDS
        .iter()
        .any(|p| p.eq_ignore_ascii_case(&lower))
        || lower.contains("meritscreen")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn protects_merit_and_system() {
        assert!(is_protected_process_name("meritscreen-ui"));
        assert!(is_protected_process_name("Explorer.EXE"));
        assert!(is_protected_process_name("/usr/sbin/WindowServer"));
        assert!(!is_protected_process_name("firefox"));
        assert!(is_protected_app_id("mac:com.apple.finder"));
    }
}
