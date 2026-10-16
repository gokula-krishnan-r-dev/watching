# macOS packaging (phase d4)

## Binaries

| Binary | Role |
| --- | --- |
| `meritscreen-guardian` | LaunchDaemon (`com.meritscreen.guardian`), KeepAlive at boot |
| `meritscreen-agent` | LaunchAgent (`com.meritscreen.agent`) in child Aqua session |
| `meritscreen-ui` | Tauri UI (ephemeral; Agent respawns when phase needs overlay) |

Enforcement tier: **L1+L3 only** (no Finder/Dock replace).

## Install paths

### Lab (no root / no notarization)

```bash
./install-lab.sh
# or: cargo run -p meritscreen-guardian -- --install-user --bin-dir target/debug
```

User LaunchAgents survive **login**, not true cold boot. Fine for IPC + overlay proofs.

### System (root / production shape)

```bash
sudo mkdir -p /Library/MeritScreen
sudo cp target/release/meritscreen-{guardian,agent} /Library/MeritScreen/
sudo /Library/MeritScreen/meritscreen-guardian --install-daemon
# Reboot, then:
launchctl print system/com.meritscreen.guardian
```

### Notarized `.pkg`

```bash
./build-pkg.sh
# With Developer ID secrets:
./notarize.sh
sudo installer -pkg target/macos-pkg/MeritScreen-0.1.0.pkg -target /
```

`entitlements.plist` + hardened runtime (`codesign --options runtime`) are required for Gatekeeper-clean launch.

### SMAppService

Plists under `packaging/macos/` match the layout for
`MeritScreen.app/Contents/Library/LaunchDaemons/`. When the UI `.app` host lands (d6),
call `SMAppService.daemon(plistName:)` from the app; until then **pkg / launchctl** is the install path.
`meritscreen-guardian --register-smappservice` validates the bundle layout.

## Done criteria (d4)

1. Fresh macOS 13+ device: install → reboot → Guardian + Agent alive → IPC ping
2. Gatekeeper-clean launch of notarized `.pkg` (requires Apple signing secrets)
3. Shielding-level overlay proof: `meritscreen-agent --overlay-proof`
4. Peer credential checks on Unix IPC (`LOCAL_PEERCRED` / `LOCAL_PEERPID`)

## Notes

- Child account must be **standard (non-admin)** for enforceable uninstall resistance.
- PIN-gated uninstall: `./uninstall-with-pin.sh <parent-pin>` (or `meritscreen-guardian --uninstall-with-pin <pin>`).
- Unguarded `--uninstall-daemon` / missing PIN records `tamperFlags.uninstallAttempt` for parent alert.
- No claim of silent uninstall block without MDM.
