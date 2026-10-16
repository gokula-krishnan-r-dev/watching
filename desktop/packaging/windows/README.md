# Windows Packaging & Deployment (Phase d3)

## Process Model & Binaries

| Binary | Role | Context & Permissions |
| --- | --- | --- |
| `meritscreen-guardian.exe` | Windows Service (`MeritScreenGuardian`), AutoStart at boot | Session 0, `LocalSystem` / Service account. Manages SQLCipher, enforcement state machine, sync engine, and watchdog. |
| `meritscreen-agent.exe` | Interactive User Session Agent | User Session (Session 1+), launched via `WTSQueryUserToken` + `CreateProcessAsUserW` into `winsta0\default`. Foreground/idle sampling, topmost overlay proof, and UI watchdog. |
| `meritscreen-ui.exe` | Tauri 2 UI Host | User Session, spawned on demand by Agent when quiz due or locked. Never resident 24/7. |

---

## Lab Testing & PowerShell Installation

### 1. Direct Service Installation (Lab / VM)
From an elevated PowerShell session after compiling:
```powershell
# Compile binaries
cargo build --release -p meritscreen-guardian -p meritscreen-agent -p meritscreen-ui

# Install service and copy files to %ProgramFiles%\MeritScreen
cd desktop\packaging\windows
.\install-service.ps1 -PayloadDir ..\..\target\release

# Check service status (starts automatically upon installation)
Get-Service MeritScreenGuardian
```

### 2. Uninstalling with Parent PIN
```powershell
.\uninstall-service.ps1 -ParentPin "1234"
```
*(If uninstalled without PIN or killed by an administrator, `tamperFlags.uninstallAttempt` or `tamperFlags.serviceStopped` is recorded in SQLCipher and synced on next connection).*

---

## WiX MSI Packaging (`build-msi.ps1`)

The installer uses WiX v4/v5 (defined in `MeritScreen.wxs`) to create a standard, per-machine MSI installer.

### Build MSI:
```powershell
# Default release package:
.\build-msi.ps1 -Configuration release

# Output is staged in target\installer\MeritScreen.msi
```

### Code Signing:
Production builds sign both the individual binaries and the MSI using an EV / OV certificate:
```powershell
.\build-msi.ps1 -Configuration release -Sign -CertThumbprint "0123456789ABCDEF0123456789ABCDEF01234567"
```

---

## Phase d3 Done Criteria Verification

1. **Windows Service Binary (`meritscreen-guardian`)**:
   - Registered as `MeritScreenGuardian` (`AutoStart`, `OwnProcess`).
   - SCM service control handler receives `Stop` / `Shutdown` / `Interrogate`.
   - Clean shutdown marks `tamperFlags.serviceStopped` in local SQLCipher before terminating.
2. **Session Agent (`meritscreen-agent`)**:
   - Launched across Session 0 boundary via `WTSGetActiveConsoleSessionId` / `WTSEnumerateSessionsW` + `WTSQueryUserToken` + `CreateProcessAsUserW` into `winsta0\default`.
   - IPC connection over local socket / named pipe (`meritscreen.guardian.v1`) authenticated with HMAC-SHA256 and process image path checks.
3. **Watchdog Resilience**:
   - Guardian detects when the agent is stale (>15s without heartbeat) or killed, and respawns it.
   - Agent polls Guardian every ~2s; when `QuizDue` or `Shielded`, Agent immediately ensures UI overlay is up and respawns it if closed.
4. **Topmost Fullscreen Proof (`overlay::show_overlay_proof`)**:
   - Native Win32 topmost fullscreen window (`WS_EX_TOPMOST | WS_EX_TOOLWINDOW`) covering the entire screen.
   - Non-blocking message pump using `PeekMessageW(PM_REMOVE)` avoids hanging the agent main thread.
   - Trivial dismissal via `dismiss_overlay_proof()` upon session unlock.
5. **MSI Package**:
   - Per-machine installation under `ProgramFiles64Folder\MeritScreen`.
   - Executes `--install-service` custom action during install and `--uninstall-service` on removal.

---

## Production Security Notes

- **Standard Child OS Account Required**: The child user must be a standard (non-administrator) account. The agent detects administrative token elevation (`is_elevated()`) and marks `tamperFlags.adminAccount` if elevated privileges are found.
- **Session 0 Isolation**: Guardian never opens UI or touches desktop window handles directly; all user interaction and screen coverage is driven through the interactive session Agent and Tauri UI.
- **SmartScreen**: EV code-signing certificate establishes instant Microsoft SmartScreen reputation.
