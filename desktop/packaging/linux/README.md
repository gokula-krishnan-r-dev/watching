# Linux Packaging & Beta Deployment (Phase d11)

MeritScreen Linux Beta supports **Ubuntu LTS (22.04 / 24.04)** and **Fedora (39 / 40)**.

## Architecture

| Component | Linux Host Deployment |
| :--- | :--- |
| **`meritscreen-guardian`** | systemd **system** unit (`/lib/systemd/system/meritscreen-guardian.service`), `Restart=always`, boot-persistent |
| **`meritscreen-agent`** | XDG Autostart entry (`/etc/xdg/autostart/meritscreen-agent.desktop`) + optional `systemd --user` session unit |
| **`meritscreen-ui`** | Ephemeral Tauri overlay UI |

## X11 vs Wayland Support (Honest Degradation)

- **X11 Session (Recommended for Beta):**
  - Full L3 window/process gating.
  - Active window queried via `_NET_ACTIVE_WINDOW` / `WM_CLASS`.
  - Override-redirect fullscreen overlay window elevation.
- **Wayland Session (Best-Effort):**
  - Compositors restrict cross-client window grabs and global positioning.
  - L3 process termination is active; visual overlay cannot guarantee bypass-proof trapping without compositor plugins.
  - Honest parent UI copy: `StrictAvailability::Unavailable` explains: *"Wayland session cannot enforce global grab — best-effort L3 only (log into X11 for full overlay)."*

## Building Packages

### Debian / Ubuntu `.deb`

```bash
BUILD_CONFIG=debug ./packaging/linux/build-deb.sh
# Output: target/linux-deb/meritscreen_0.1.0_amd64.deb
sudo dpkg -i target/linux-deb/meritscreen_0.1.0_amd64.deb
```

### Fedora / RHEL `.rpm`

```bash
BUILD_CONFIG=debug ./packaging/linux/build-rpm.sh
# Staged spec and builds under target/linux-rpm/
```

## Service Control

```bash
# Check status
systemctl status meritscreen-guardian.service

# Logs
journalctl -u meritscreen-guardian.service -f

# Uninstall with parent PIN
sudo ./packaging/linux/uninstall-with-pin.sh 1234
```
