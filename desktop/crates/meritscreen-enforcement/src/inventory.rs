//! Installed-app inventory scan (`win:` / `mac:` / `linux:` appIds).

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};

use meritscreen_core::app_config::INVENTORY_UPLOAD_MAX_APPS;
use meritscreen_core::DevicePlatform;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tracing::debug;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledApp {
    /// Stable id: `win:…` / `mac:…` / `linux:…`
    pub app_id: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub icon_hash: Option<String>,
}

pub fn scan_installed_apps() -> Vec<InstalledApp> {
    let mut apps = match DevicePlatform::current() {
        DevicePlatform::Windows => scan_windows(),
        DevicePlatform::Macos => scan_macos(),
        DevicePlatform::Linux => scan_linux(),
        _ => Vec::new(),
    };
    apps.sort_by(|a, b| a.app_id.cmp(&b.app_id));
    apps.dedup_by(|a, b| a.app_id == b.app_id);
    if apps.len() > INVENTORY_UPLOAD_MAX_APPS as usize {
        apps.truncate(INVENTORY_UPLOAD_MAX_APPS as usize);
    }
    debug!(count = apps.len(), "inventory scan complete");
    apps
}

pub fn hash_inventory(apps: &[InstalledApp]) -> String {
    let mut hasher = Sha256::new();
    for a in apps {
        hasher.update(a.app_id.as_bytes());
        hasher.update([0]);
        hasher.update(a.label.as_bytes());
        hasher.update([0]);
        if let Some(h) = &a.icon_hash {
            hasher.update(h.as_bytes());
        }
        hasher.update([0xff]);
    }
    hex::encode(hasher.finalize())
}

fn scan_macos() -> Vec<InstalledApp> {
    let mut out = Vec::new();
    let roots = [
        PathBuf::from("/Applications"),
        dirs_home()
            .map(|h| h.join("Applications"))
            .unwrap_or_default(),
    ];
    for root in roots {
        if root.as_os_str().is_empty() || !root.is_dir() {
            continue;
        }
        walk_apps(&root, &mut out, 2);
    }
    out
}

fn walk_apps(dir: &Path, out: &mut Vec<InstalledApp>, depth: u32) {
    if depth == 0 {
        return;
    }
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) == Some("app") {
            if let Some(app) = macos_app_from_bundle(&path) {
                out.push(app);
            }
        } else if path.is_dir() {
            walk_apps(&path, out, depth - 1);
        }
    }
}

fn macos_app_from_bundle(bundle: &Path) -> Option<InstalledApp> {
    let plist = bundle.join("Contents/Info.plist");
    let text = fs::read_to_string(&plist).ok()?;
    let bundle_id = plist_string(&text, "CFBundleIdentifier")?;
    let label = plist_string(&text, "CFBundleDisplayName")
        .or_else(|| plist_string(&text, "CFBundleName"))
        .unwrap_or_else(|| {
            bundle
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("App")
                .to_string()
        });
    let icon_hash = fs::metadata(bundle.join("Contents/Resources"))
        .ok()
        .map(|m| {
            let mut h = Sha256::new();
            h.update(bundle_id.as_bytes());
            h.update(m.len().to_le_bytes());
            hex::encode(h.finalize())[..16].to_string()
        });
    Some(InstalledApp {
        app_id: format!("mac:{bundle_id}"),
        label,
        icon_hash,
    })
}

/// Minimal plist string scrape (XML plists only; binary skipped).
fn plist_string(plist: &str, key: &str) -> Option<String> {
    let needle = format!("<key>{key}</key>");
    let idx = plist.find(&needle)?;
    let after = &plist[idx + needle.len()..];
    let start = after.find("<string>")? + "<string>".len();
    let end = after[start..].find("</string>")? + start;
    let value = after[start..end].trim();
    if value.is_empty() {
        None
    } else {
        Some(value.to_string())
    }
}

fn scan_windows() -> Vec<InstalledApp> {
    let mut out = Vec::new();
    let mut seen = BTreeSet::new();
    let roots = windows_start_menu_roots();
    for root in roots {
        collect_lnk_labels(&root, &mut out, &mut seen, 3);
    }
    // Always include a few lab-friendly stubs so allowlist UI is never empty in fresh VMs.
    if out.is_empty() {
        out.push(InstalledApp {
            app_id: "win:Microsoft.WindowsCalculator_8wekyb3d8bbwe!App".into(),
            label: "Calculator".into(),
            icon_hash: None,
        });
        out.push(InstalledApp {
            app_id: "win:Microsoft.MicrosoftEdge_8wekyb3d8bbwe!App".into(),
            label: "Edge".into(),
            icon_hash: None,
        });
    }
    out
}

fn windows_start_menu_roots() -> Vec<PathBuf> {
    let mut roots = Vec::new();
    if let Ok(prog) = std::env::var("ProgramData") {
        roots.push(PathBuf::from(prog).join("Microsoft/Windows/Start Menu/Programs"));
    }
    if let Ok(appdata) = std::env::var("APPDATA") {
        roots.push(PathBuf::from(appdata).join("Microsoft/Windows/Start Menu/Programs"));
    }
    roots
}

fn collect_lnk_labels(
    dir: &Path,
    out: &mut Vec<InstalledApp>,
    seen: &mut BTreeSet<String>,
    depth: u32,
) {
    if depth == 0 || !dir.is_dir() {
        return;
    }
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_lnk_labels(&path, out, seen, depth - 1);
            continue;
        }
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
        if !ext.eq_ignore_ascii_case("lnk") && !ext.eq_ignore_ascii_case("exe") {
            continue;
        }
        let stem = path
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("App")
            .to_string();
        let norm = path
            .to_string_lossy()
            .replace('\\', "/")
            .to_ascii_lowercase();
        let app_id = format!("win:{norm}");
        if seen.insert(app_id.clone()) {
            out.push(InstalledApp {
                app_id,
                label: stem,
                icon_hash: None,
            });
        }
    }
}

fn scan_linux() -> Vec<InstalledApp> {
    let mut out = Vec::new();
    let roots = [
        PathBuf::from("/usr/share/applications"),
        PathBuf::from("/usr/local/share/applications"),
        dirs_home()
            .map(|h| h.join(".local/share/applications"))
            .unwrap_or_default(),
    ];
    for root in roots {
        if root.as_os_str().is_empty() || !root.is_dir() {
            continue;
        }
        let Ok(entries) = fs::read_dir(&root) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) != Some("desktop") {
                continue;
            }
            if let Some(app) = linux_desktop(&path) {
                out.push(app);
            }
        }
    }
    out
}

fn linux_desktop(path: &Path) -> Option<InstalledApp> {
    let text = fs::read_to_string(path).ok()?;
    if text.contains("NoDisplay=true") || text.contains("Hidden=true") {
        return None;
    }
    let id = path.file_name().and_then(|s| s.to_str())?.to_string();
    let label = text
        .lines()
        .find_map(|l| l.strip_prefix("Name="))
        .unwrap_or(id.trim_end_matches(".desktop"))
        .to_string();
    Some(InstalledApp {
        app_id: format!("linux:{id}"),
        label,
        icon_hash: None,
    })
}

fn dirs_home() -> Option<PathBuf> {
    std::env::var_os("HOME").map(PathBuf::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_stable() {
        let a = vec![InstalledApp {
            app_id: "mac:com.apple.Safari".into(),
            label: "Safari".into(),
            icon_hash: Some("abc".into()),
        }];
        let h1 = hash_inventory(&a);
        let h2 = hash_inventory(&a);
        assert_eq!(h1, h2);
        assert_eq!(h1.len(), 64);
    }

    #[test]
    fn plist_scrape() {
        let xml = r#"
        <dict>
          <key>CFBundleIdentifier</key>
          <string>com.example.App</string>
          <key>CFBundleName</key>
          <string>Example</string>
        </dict>"#;
        assert_eq!(
            plist_string(xml, "CFBundleIdentifier").as_deref(),
            Some("com.example.App")
        );
    }
}
