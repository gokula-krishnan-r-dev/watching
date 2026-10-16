//! Linux systemd service & user agent installation for MeritScreen Guardian (d11 beta).
//!
//! Production path: `.deb` / `.rpm` packages install unit files to
//! `/lib/systemd/system/meritscreen-guardian.service` and
//! `/etc/xdg/autostart/meritscreen-agent.desktop`.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use tracing::info;

pub const GUARDIAN_SERVICE: &str = "meritscreen-guardian.service";
pub const AGENT_SERVICE: &str = "meritscreen-agent.service";
pub const AGENT_DESKTOP: &str = "meritscreen-agent.desktop";

pub const SYSTEMD_SYSTEM_DIR: &str = "/etc/systemd/system";
pub const SYSTEMD_USER_DIR: &str = "/etc/systemd/user";
pub const XDG_AUTOSTART_DIR: &str = "/etc/xdg/autostart";
pub const SYSTEM_INSTALL_BIN_DIR: &str = "/usr/bin";

/// Run Guardian as a systemd service (marks tamper flag if stopped).
pub fn run_as_systemd_service(disable_agent_spawn: bool) -> Result<()> {
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;

    use crate::runtime::{self, RuntimeOptions};

    let running = Arc::new(AtomicBool::new(true));
    let r = Arc::clone(&running);
    let _ = ctrlc::set_handler(move || {
        r.store(false, Ordering::SeqCst);
    });

    info!(
        service = GUARDIAN_SERVICE,
        "MeritScreen Guardian systemd service starting"
    );
    runtime::run_with_stop(
        RuntimeOptions {
            console_mode: disable_agent_spawn,
            disable_agent_spawn,
            mark_tamper_on_stop: true,
        },
        running,
    )
}

/// Generate content for `meritscreen-guardian.service`.
pub fn guardian_service_content(bin_path: &Path) -> String {
    format!(
        "[Unit]\n\
Description=MeritScreen Guardian Service\n\
Documentation=https://github.com/gokul/MeritScreen\n\
After=network.target local-fs.target\n\
Wants=network.target\n\
\n\
[Service]\n\
Type=simple\n\
ExecStart={} --systemd-service\n\
Restart=always\n\
RestartSec=3\n\
StandardOutput=journal\n\
StandardError=journal\n\
Environment=RUST_LOG=info\n\
\n\
[Install]\n\
WantedBy=multi-user.target\n",
        bin_path.display()
    )
}

/// Generate content for `meritscreen-agent.service` (systemd --user).
pub fn agent_service_content(bin_path: &Path) -> String {
    format!(
        "[Unit]\n\
Description=MeritScreen Session Agent\n\
PartOf=graphical-session.target\n\
After=graphical-session.target\n\
\n\
[Service]\n\
Type=simple\n\
ExecStart={}\n\
Restart=on-failure\n\
RestartSec=2\n\
StandardOutput=journal\n\
StandardError=journal\n\
\n\
[Install]\n\
WantedBy=graphical-session.target\n",
        bin_path.display()
    )
}

/// Generate content for `meritscreen-agent.desktop` (XDG autostart).
pub fn agent_desktop_content(bin_path: &Path) -> String {
    format!(
        "[Desktop Entry]\n\
Type=Application\n\
Name=MeritScreen Agent\n\
Comment=MeritScreen Session Agent\n\
Exec={}\n\
Terminal=false\n\
NoDisplay=true\n\
X-GNOME-Autostart-enabled=true\n\
X-KDE-autostart-after=panel\n",
        bin_path.display()
    )
}

/// Install systemd units and XDG autostart file from target binary directory.
pub fn install_systemd_units(bin_dir: &Path) -> Result<()> {
    if !cfg!(target_os = "linux") {
        bail!("systemd installation is Linux only");
    }

    let guardian_bin = bin_dir.join("meritscreen-guardian");
    let agent_bin = bin_dir.join("meritscreen-agent");

    if !guardian_bin.exists() {
        bail!("guardian binary missing at {}", guardian_bin.display());
    }

    let system_dir = PathBuf::from(SYSTEMD_SYSTEM_DIR);
    fs::create_dir_all(&system_dir).with_context(|| format!("create {}", system_dir.display()))?;

    let guardian_unit = system_dir.join(GUARDIAN_SERVICE);
    fs::write(&guardian_unit, guardian_service_content(&guardian_bin))
        .with_context(|| format!("write {}", guardian_unit.display()))?;
    info!(path = %guardian_unit.display(), "wrote guardian system unit");

    let autostart_dir = PathBuf::from(XDG_AUTOSTART_DIR);
    if let Ok(()) = fs::create_dir_all(&autostart_dir) {
        let desktop_file = autostart_dir.join(AGENT_DESKTOP);
        let _ = fs::write(&desktop_file, agent_desktop_content(&agent_bin));
        info!(path = %desktop_file.display(), "wrote agent autostart entry");
    }

    let reload = Command::new("systemctl").arg("daemon-reload").status();
    if let Ok(st) = reload {
        if st.success() {
            let _ = Command::new("systemctl")
                .args(["enable", "--now", GUARDIAN_SERVICE])
                .status();
            info!("enabled and started guardian service");
        }
    }

    Ok(())
}

/// Uninstall systemd units and disable service.
pub fn uninstall_systemd_units() -> Result<()> {
    let _ = Command::new("systemctl")
        .args(["stop", GUARDIAN_SERVICE])
        .status();
    let _ = Command::new("systemctl")
        .args(["disable", GUARDIAN_SERVICE])
        .status();

    let guardian_unit = PathBuf::from(SYSTEMD_SYSTEM_DIR).join(GUARDIAN_SERVICE);
    if guardian_unit.exists() {
        let _ = fs::remove_file(&guardian_unit);
    }

    let autostart_file = PathBuf::from(XDG_AUTOSTART_DIR).join(AGENT_DESKTOP);
    if autostart_file.exists() {
        let _ = fs::remove_file(&autostart_file);
    }

    let _ = Command::new("systemctl").arg("daemon-reload").status();

    info!("systemd units removed");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn systemd_unit_templates_are_valid() {
        let bin = Path::new("/usr/bin/meritscreen-guardian");
        let content = guardian_service_content(bin);
        assert!(content.contains("ExecStart=/usr/bin/meritscreen-guardian --systemd-service"));
        assert!(content.contains("WantedBy=multi-user.target"));
        assert!(content.contains("Restart=always"));

        let agent_bin = Path::new("/usr/bin/meritscreen-agent");
        let agent_content = agent_service_content(agent_bin);
        assert!(agent_content.contains("ExecStart=/usr/bin/meritscreen-agent"));
        assert!(agent_content.contains("WantedBy=graphical-session.target"));

        let desktop_content = agent_desktop_content(agent_bin);
        assert!(desktop_content.contains("Exec=/usr/bin/meritscreen-agent"));
        assert!(desktop_content.contains("X-GNOME-Autostart-enabled=true"));
    }
}
