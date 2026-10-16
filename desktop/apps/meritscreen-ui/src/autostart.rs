//! Native launch-at-login / auto-start manager for macOS, Windows, and Linux.

use std::path::PathBuf;

/// Check whether auto-start at login is currently enabled.
pub fn is_autostart_enabled() -> bool {
    #[cfg(target_os = "macos")]
    {
        if let Some(plist) = macos_launch_agent_path() {
            return plist.exists();
        }
        false
    }

    #[cfg(windows)]
    {
        use std::process::Command;
        let out = Command::new("reg")
            .args(["query", r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run", "/v", "MeritScreen"])
            .output();
        out.map(|o| o.status.success()).unwrap_or(false)
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        if let Some(desktop) = linux_autostart_path() {
            return desktop.exists();
        }
        false
    }

    #[cfg(not(any(windows, unix)))]
    {
        false
    }
}

/// Enable or disable auto-start at login.
pub fn set_autostart_enabled(enabled: bool) -> Result<(), String> {
    let current_exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let exe_str = current_exe.to_string_lossy();

    #[cfg(target_os = "macos")]
    {
        let plist_path = macos_launch_agent_path().ok_or_else(|| "Could not resolve LaunchAgents directory".to_string())?;
        if enabled {
            if let Some(parent) = plist_path.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            let plist_content = format!(
                r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.meritscreen.ui</string>
    <key>ProgramArguments</key>
    <array>
        <string>{}</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <dict>
        <key>SuccessfulExit</key>
        <false/>
    </dict>
    <key>ProcessType</key>
    <string>Interactive</string>
</dict>
</plist>
"#,
                exe_str
            );
            std::fs::write(&plist_path, plist_content).map_err(|e| format!("Failed to write LaunchAgent: {e}"))?;
            tracing::info!(path = ?plist_path, "Enabled macOS LaunchAgent for MeritScreen auto-start");
        } else if plist_path.exists() {
            let _ = std::fs::remove_file(&plist_path);
            tracing::info!(path = ?plist_path, "Removed macOS LaunchAgent for MeritScreen auto-start");
        }
        Ok(())
    }

    #[cfg(windows)]
    {
        use std::process::Command;
        if enabled {
            let status = Command::new("reg")
                .args([
                    "add",
                    r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run",
                    "/v",
                    "MeritScreen",
                    "/t",
                    "REG_SZ",
                    "/d",
                    &exe_str,
                    "/f",
                ])
                .status()
                .map_err(|e| e.to_string())?;
            if !status.success() {
                return Err("Failed to register Windows Run key in registry".to_string());
            }
            tracing::info!("Registered Windows HKCU Run key for MeritScreen auto-start");
        } else {
            let _ = Command::new("reg")
                .args([
                    "delete",
                    r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run",
                    "/v",
                    "MeritScreen",
                    "/f",
                ])
                .status();
            tracing::info!("Removed Windows HKCU Run key for MeritScreen auto-start");
        }
        Ok(())
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let desktop_path = linux_autostart_path().ok_or_else(|| "Could not resolve autostart path".to_string())?;
        if enabled {
            if let Some(parent) = desktop_path.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            let desktop_content = format!(
                "[Desktop Entry]\nType=Application\nName=Watching MeritScreen\nExec={}\nHidden=false\nNoDisplay=false\nX-GNOME-Autostart-enabled=true\n",
                exe_str
            );
            std::fs::write(&desktop_path, desktop_content).map_err(|e| format!("Failed to write desktop autostart: {e}"))?;
        } else if desktop_path.exists() {
            let _ = std::fs::remove_file(&desktop_path);
        }
        Ok(())
    }

    #[cfg(not(any(windows, unix)))]
    {
        Err("Auto-start is not supported on this platform".to_string())
    }
}

#[cfg(target_os = "macos")]
fn macos_launch_agent_path() -> Option<PathBuf> {
    directories::UserDirs::new().map(|u| {
        u.home_dir()
            .join("Library")
            .join("LaunchAgents")
            .join("com.meritscreen.ui.plist")
    })
}

#[cfg(all(unix, not(target_os = "macos")))]
fn linux_autostart_path() -> Option<PathBuf> {
    directories::UserDirs::new().map(|u| {
        u.home_dir()
            .join(".config")
            .join("autostart")
            .join("meritscreen-ui.desktop")
    })
}
