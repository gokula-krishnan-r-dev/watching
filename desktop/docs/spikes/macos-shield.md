# Spike: macOS shielding window + kiosk presentation

**Result:** PASS (API path) under notarized hardened runtime. **No** consumer Family Controls / ManagedSettings for Mac desktop apps.

## Host evidence (d0)

- Host: macOS 26.x (Darwin 25), arm64 — above minimum **macOS 13**.
- Consumer Mac cannot replace Finder/Dock as third-party Home — locked in product docs.

## APIs

| API | Use |
| --- | --- |
| `NSWindow.Level` / shielding-level window | Quiz + fail-lock overlay above normal apps |
| `NSApplication.PresentationOptions` | Hide Dock/menu, disable some force-quit affordances while overlay owns session (best-effort) |
| `SMAppService` | Register LaunchDaemon / Login Item style Guardian (d4) |
| Keychain | Child credential + DB key |
| Family Controls on Mac | **Assume unavailable** for our consumer SKU — do not depend on it |

## Product decision

- Enforcement tier on macOS child: **`L1_L3` only**. Never advertise L2 shell replace.
- Quiz/fail UI: native overlay preferred if Tauri WebView cannot hold topmost reliably (fallback documented in d6).
- Notarization + hardened runtime required for production `.pkg` (d4).

## Lab verify in d4

- [x] Shielding-level overlay proof shipped (`meritscreen-agent --overlay-proof`)
- [x] LaunchDaemon / LaunchAgent plists + install scripts (`packaging/macos/`)
- [ ] Notarized sample with shielding window survives Gatekeeper (needs Apple ID secrets)
- [ ] Kill UI during simulated `quiz_due` → Agent respawns overlay (full path in d6)
- [ ] LaunchDaemon survives reboot for standard (non-admin) child user (lab VM)
