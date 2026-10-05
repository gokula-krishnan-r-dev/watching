# Windows packaging (phase d3)

## Binaries

| Binary | Role |
| --- | --- |
| `meritscreen-guardian.exe` | Windows Service (`MeritScreenGuardian`), AutoStart at boot |
| `meritscreen-agent.exe` | Per-user session agent (spawned via `CreateProcessAsUser`) |
| `meritscreen-ui.exe` | Tauri UI (ephemeral; Agent respawns when phase needs overlay) |

## Lab install (VM)

```powershell
# From an elevated PowerShell, after cargo build --release:
.\install-service.ps1 -PayloadDir ..\..\target\release
# Reboot, then:
Get-Service MeritScreenGuardian
# Agent should appear in the user session; IPC ping is automatic on connect.
```

Uninstall (skeleton — PIN gate in d9):

```powershell
.\uninstall-service.ps1
```

## MSI (WiX)

`MeritScreen.wxs` is a WiX v4 skeleton. Production ships a **signed** MSI/MSIX.

Code signing (CI secrets, not in repo):

- Prefer **EV** certificate for SmartScreen reputation.
- Sign `meritscreen-*.exe` and the MSI after build.

## Done criteria (d3)

1. Fresh Win 10/11 VM: install → reboot → service **Running**
2. Agent process present in interactive session
3. Agent ↔ Guardian IPC `Ping` / `Pong`
4. Stopping the service sets local `tamperFlags.serviceStopped` in SQLCipher

## Notes

- Child account must be **standard (non-admin)** for enforceable uninstall resistance.
- Antivirus false positives: plan SmartScreen reputation + publisher attestation.
- Session 0 isolation: Guardian never shows UI; Agent owns overlays.
