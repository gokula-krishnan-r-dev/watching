//! macOS LaunchDaemon / LaunchAgent registration for MeritScreen Guardian.
//!
//! Production path: `.pkg` installs plists under `/Library/LaunchDaemons` and
//! `/Library/LaunchAgents`, then `launchctl bootstrap`. When the UI ships as an
//! `.app`, `SMAppService` can also register the same plists from
//! `Contents/Library/LaunchDaemons` (see `register_smappservice_daemon`).
#![cfg(target_os = "macos")]

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use tracing::info;

pub const GUARDIAN_LABEL: &str = "com.meritscreen.guardian";
pub const AGENT_LABEL: &str = "com.meritscreen.agent";

const SYSTEM_DAEMON_DIR: &str = "/Library/LaunchDaemons";
const SYSTEM_AGENT_DIR: &str = "/Library/LaunchAgents";
const INSTALL_BIN_DIR: &str = "/Library/MeritScreen";

/// Run Guardian as a launchd job (sets tamper flag if stopped uncleanly).
pub fn run_as_daemon(disable_agent_spawn: bool) -> Result<()> {
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;

    use crate::runtime::{self, RuntimeOptions};

    let running = Arc::new(AtomicBool::new(true));
    let r = Arc::clone(&running);
    let _ = ctrlc::set_handler(move || {
        r.store(false, Ordering::SeqCst);
    });

    info!(
        label = GUARDIAN_LABEL,
        "MeritScreen Guardian launchd daemon starting"
    );
    runtime::run_with_stop(
        RuntimeOptions {
            // System LaunchDaemon: spawn agent via LaunchAgent / console fallback.
            // User lab agents often pass `--no-agent` (LaunchAgent starts the agent).
            console_mode: disable_agent_spawn,
            disable_agent_spawn,
            mark_tamper_on_stop: true,
        },
        running,
    )
}

/// Install system LaunchDaemon (Guardian) + LaunchAgent (Session Agent).
/// Requires root. Binaries are expected under `/Library/MeritScreen/`.
pub fn install_daemon() -> Result<()> {
    ensure_root()?;
    let bin_dir = PathBuf::from(INSTALL_BIN_DIR);
    let guardian = bin_dir.join("meritscreen-guardian");
    let agent = bin_dir.join("meritscreen-agent");
    if !guardian.exists() {
        bail!("missing {guardian:?} — copy binaries before install");
    }

    fs::create_dir_all(SYSTEM_DAEMON_DIR)?;
    fs::create_dir_all(SYSTEM_AGENT_DIR)?;

    let daemon_plist = PathBuf::from(SYSTEM_DAEMON_DIR).join(format!("{GUARDIAN_LABEL}.plist"));
    let agent_plist = PathBuf::from(SYSTEM_AGENT_DIR).join(format!("{AGENT_LABEL}.plist"));

    fs::write(&daemon_plist, guardian_daemon_plist(&guardian))?;
    fs::write(&agent_plist, agent_launch_agent_plist(&agent))?;
    // Root-owned, not world-writable.
    let _ = Command::new("chmod")
        .args(["644", daemon_plist.to_str().unwrap()])
        .status();
    let _ = Command::new("chmod")
        .args(["644", agent_plist.to_str().unwrap()])
        .status();
    let _ = Command::new("chown")
        .args(["root:wheel", daemon_plist.to_str().unwrap()])
        .status();

    launchctl_bootout("system", &daemon_plist);
    launchctl_bootstrap("system", &daemon_plist)?;
    // LaunchAgent is per-user; bootstrap into the GUI session domain when possible.
    bootstrap_agent_for_console_user(&agent_plist)?;

    info!(
        daemon = %daemon_plist.display(),
        agent = %agent_plist.display(),
        "macOS LaunchDaemon + LaunchAgent installed"
    );
    Ok(())
}

/// Remove LaunchDaemon / LaunchAgent registration.
pub fn uninstall_daemon() -> Result<()> {
    ensure_root()?;
    let daemon_plist = PathBuf::from(SYSTEM_DAEMON_DIR).join(format!("{GUARDIAN_LABEL}.plist"));
    let agent_plist = PathBuf::from(SYSTEM_AGENT_DIR).join(format!("{AGENT_LABEL}.plist"));
    launchctl_bootout("system", &daemon_plist);
    bootout_agent_for_console_user(&agent_plist);
    let _ = fs::remove_file(&daemon_plist);
    let _ = fs::remove_file(&agent_plist);
    info!("macOS LaunchDaemon + LaunchAgent uninstalled");
    Ok(())
}

/// Lab helper: install Guardian + Agent as *user* LaunchAgents (no root).
/// Survives login, not true boot — document as lab-only.
pub fn install_user_agents(bin_dir: &Path) -> Result<()> {
    let home = dirs_home()?;
    let agents = home.join("Library/LaunchAgents");
    fs::create_dir_all(&agents)?;

    let guardian = resolve_bin(bin_dir, "meritscreen-guardian")?;
    let agent = resolve_bin(bin_dir, "meritscreen-agent")?;

    let g_plist = agents.join(format!("{GUARDIAN_LABEL}.plist"));
    let a_plist = agents.join(format!("{AGENT_LABEL}.plist"));
    fs::write(
        &g_plist,
        user_guardian_plist(&guardian, &home.join("Library/Logs/MeritScreen")),
    )?;
    fs::write(&a_plist, agent_launch_agent_plist(&agent))?;

    let uid = unsafe { libc::getuid() };
    let domain = format!("gui/{uid}");
    launchctl_bootout(&domain, &g_plist);
    launchctl_bootout(&domain, &a_plist);
    launchctl_bootstrap(&domain, &g_plist)?;
    launchctl_bootstrap(&domain, &a_plist)?;
    info!(
        domain,
        "user LaunchAgents installed (lab — not system boot)"
    );
    Ok(())
}

pub fn uninstall_user_agents() -> Result<()> {
    let home = dirs_home()?;
    let agents = home.join("Library/LaunchAgents");
    let g_plist = agents.join(format!("{GUARDIAN_LABEL}.plist"));
    let a_plist = agents.join(format!("{AGENT_LABEL}.plist"));
    let uid = unsafe { libc::getuid() };
    let domain = format!("gui/{uid}");
    launchctl_bootout(&domain, &g_plist);
    launchctl_bootout(&domain, &a_plist);
    let _ = fs::remove_file(&g_plist);
    let _ = fs::remove_file(&a_plist);
    Ok(())
}

/// Best-effort `SMAppService` daemon registration (must run from a signed `.app`).
/// Returns Ok when the Objective-C call reports success; otherwise a clear error.
pub fn register_smappservice_daemon() -> Result<()> {
    smappservice::register_daemon(GUARDIAN_LABEL)
}

pub fn unregister_smappservice_daemon() -> Result<()> {
    smappservice::unregister_daemon(GUARDIAN_LABEL)
}

fn ensure_root() -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("install/uninstall daemon requires root (use sudo or the .pkg)");
    }
    Ok(())
}

fn dirs_home() -> Result<PathBuf> {
    std::env::var_os("HOME")
        .map(PathBuf::from)
        .context("HOME not set")
}

fn resolve_bin(dir: &Path, name: &str) -> Result<PathBuf> {
    let p = dir.join(name);
    if p.exists() {
        return Ok(p.canonicalize().unwrap_or(p));
    }
    let exe = std::env::current_exe().context("current_exe")?;
    if let Some(parent) = exe.parent() {
        let cand = parent.join(name);
        if cand.exists() {
            return Ok(cand.canonicalize().unwrap_or(cand));
        }
    }
    bail!("binary not found: {name} under {}", dir.display());
}

fn launchctl_bootstrap(domain: &str, plist: &Path) -> Result<()> {
    let status = Command::new("launchctl")
        .args(["bootstrap", domain])
        .arg(plist)
        .status()
        .context("launchctl bootstrap")?;
    if !status.success() {
        // Already bootstrapped is ok for reinstall — try kickstart.
        let _ = Command::new("launchctl")
            .args(["kickstart", "-k", &format!("{domain}/{GUARDIAN_LABEL}")])
            .status();
    }
    Ok(())
}

fn launchctl_bootout(domain: &str, plist: &Path) {
    let _ = Command::new("launchctl")
        .args(["bootout", domain])
        .arg(plist)
        .status();
}

fn console_user_uid() -> Option<u32> {
    let out = Command::new("stat")
        .args(["-f", "%u", "/dev/console"])
        .output()
        .ok()?;
    if !out.status.success() {
        return None;
    }
    String::from_utf8_lossy(&out.stdout).trim().parse().ok()
}

fn bootstrap_agent_for_console_user(plist: &Path) -> Result<()> {
    if let Some(uid) = console_user_uid() {
        let domain = format!("gui/{uid}");
        launchctl_bootout(&domain, plist);
        let _ = launchctl_bootstrap(&domain, plist);
    }
    Ok(())
}

fn bootout_agent_for_console_user(plist: &Path) {
    if let Some(uid) = console_user_uid() {
        launchctl_bootout(&format!("gui/{uid}"), plist);
    }
}

fn guardian_daemon_plist(guardian: &Path) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{GUARDIAN_LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{}</string>
    <string>--daemon</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>ProcessType</key>
  <string>Interactive</string>
  <key>StandardOutPath</key>
  <string>/var/log/meritscreen-guardian.log</string>
  <key>StandardErrorPath</key>
  <string>/var/log/meritscreen-guardian.err</string>
</dict>
</plist>
"#,
        guardian.display()
    )
}

fn user_guardian_plist(guardian: &Path, log_dir: &Path) -> String {
    let _ = fs::create_dir_all(log_dir);
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{GUARDIAN_LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{}</string>
    <string>--daemon</string>
    <string>--no-agent</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>{}/guardian.log</string>
  <key>StandardErrorPath</key>
  <string>{}/guardian.err</string>
</dict>
</plist>
"#,
        guardian.display(),
        log_dir.display(),
        log_dir.display()
    )
}

fn agent_launch_agent_plist(agent: &Path) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{AGENT_LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{}</string>
    <string>--launch-agent</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>LimitLoadToSessionType</key>
  <string>Aqua</string>
</dict>
</plist>
"#,
        agent.display()
    )
}

mod smappservice {
    use std::path::PathBuf;

    use anyhow::{bail, Result};

    /// `SMAppService` must run from a signed `.app` that embeds
    /// `Contents/Library/LaunchDaemons/<label>.plist`. d4 install path is the
    /// notarized `.pkg` + `launchctl`; this helper validates the bundle layout
    /// and documents the registration call for the UI host (d6).
    pub fn register_daemon(label: &str) -> Result<()> {
        let plist = embedded_daemon_plist(label)?;
        bail!(
            "SMAppService.register requires a signed MeritScreen.app host \
             (found plist at {}). Use --install-daemon / .pkg for d4 lab installs.",
            plist.display()
        );
    }

    pub fn unregister_daemon(label: &str) -> Result<()> {
        let _ = embedded_daemon_plist(label)?;
        bail!(
            "SMAppService.unregister requires a signed MeritScreen.app host. \
             Use --uninstall-daemon / .pkg scripts instead."
        );
    }

    fn embedded_daemon_plist(label: &str) -> Result<PathBuf> {
        let exe = std::env::current_exe()?;
        // …/MeritScreen.app/Contents/MacOS/<bin>
        let contents = exe
            .parent()
            .and_then(|macos| macos.parent())
            .ok_or_else(|| anyhow::anyhow!("not running inside an .app bundle"))?;
        if contents.file_name().and_then(|s| s.to_str()) != Some("Contents") {
            bail!("not running inside an .app bundle (no Contents/)");
        }
        let plist = contents
            .join("Library/LaunchDaemons")
            .join(format!("{label}.plist"));
        if !plist.exists() {
            bail!("embedded LaunchDaemon plist missing: {}", plist.display());
        }
        Ok(plist)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plist_contains_label_and_daemon_flag() {
        let xml = guardian_daemon_plist(Path::new("/Library/MeritScreen/meritscreen-guardian"));
        assert!(xml.contains(GUARDIAN_LABEL));
        assert!(xml.contains("--daemon"));
        assert!(xml.contains("KeepAlive"));
    }

    #[test]
    fn agent_plist_aqua_session() {
        let xml = agent_launch_agent_plist(Path::new("/Library/MeritScreen/meritscreen-agent"));
        assert!(xml.contains(AGENT_LABEL));
        assert!(xml.contains("Aqua"));
        assert!(xml.contains("--launch-agent"));
    }
}
