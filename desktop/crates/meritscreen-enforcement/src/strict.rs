//! Windows Strict (L2) availability — opt-in only when SKU supports it.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "status", rename_all = "snake_case")]
pub enum StrictAvailability {
    Available,
    Unavailable { reason: String },
    NotApplicable { reason: String },
}

pub fn strict_mode_availability() -> StrictAvailability {
    #[cfg(windows)]
    {
        windows_strict_probe()
    }
    #[cfg(target_os = "macos")]
    {
        StrictAvailability::NotApplicable {
            reason: "Consumer macOS cannot replace Finder/Dock — L1+L3 only.".into(),
        }
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        StrictAvailability::Unavailable {
            reason: "Linux Strict/kiosk ships in d11 beta.".into(),
        }
    }
    #[cfg(not(any(windows, unix)))]
    {
        StrictAvailability::NotApplicable {
            reason: "Unsupported platform.".into(),
        }
    }
}

#[cfg(windows)]
fn windows_strict_probe() -> StrictAvailability {
    // Product decision (spike windows-shell.md): Home → L1+L3 only; Pro+ can enable Strict.
    // Probe ProductName from registry when available; fail open to Unavailable with honest copy.
    match read_windows_product_name() {
        Some(name) if name.to_ascii_lowercase().contains("home") => {
            StrictAvailability::Unavailable {
                reason: crate::types::DEGRADED_STRICT_UNAVAILABLE.into(),
            }
        }
        Some(_) => StrictAvailability::Available,
        None => StrictAvailability::Unavailable {
            reason: crate::types::DEGRADED_STRICT_UNAVAILABLE.into(),
        },
    }
}

#[cfg(windows)]
fn read_windows_product_name() -> Option<String> {
    use std::os::windows::process::CommandExt;
    use std::process::Command;
    // Avoid pulling winreg; a tiny PowerShell/reg query is fine for d7 probe.
    let output = Command::new("reg")
        .args([
            "query",
            r"HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion",
            "/v",
            "ProductName",
        ])
        .creation_flags(0x08000000) // CREATE_NO_WINDOW
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&output.stdout);
    for line in text.lines() {
        if let Some(rest) = line.split("ProductName").nth(1) {
            let value = rest
                .split_whitespace()
                .skip(1)
                .collect::<Vec<_>>()
                .join(" ");
            if !value.is_empty() {
                return Some(value);
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn macos_not_applicable() {
        let a = strict_mode_availability();
        #[cfg(target_os = "macos")]
        assert!(matches!(a, StrictAvailability::NotApplicable { .. }));
        #[cfg(not(target_os = "macos"))]
        let _ = a;
    }
}
