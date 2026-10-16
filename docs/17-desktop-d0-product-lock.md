# 17 — Desktop Phase d0 product lock (signed off)

**Phase:** d0 — Product lock & bootstrap  
**Status:** Signed off for implementation start (d1+)  
**Date:** 2026-10-05  
**Companions:** [15](15-desktop-native-production.md), [16](16-desktop-build-phases.md), [07](07-app-blocks-and-fail-lock.md)

This checklist locks product invariants, OS matrix, signing, Firebase gaps, additive schema, background/uninstall behavior, and spike conclusions before feature code.

---

## 1. Invariants (confirmed)

| Invariant | Locked decision |
| --- | --- |
| Fail lock scope | Always **`all_non_emergency`** (device-wide). Never per-app-only fail. |
| Unlock paths | Cooldown ends **or** child **passes Retry** quiz. Failed retry **restarts** cooldown. |
| Quiz overlay | **No Close / Skip / Minimize.** Kill UI → Agent respawns; Guardian phase unchanged. |
| Retry during cooldown | **On** (same as Android / iOS). |
| Child hot path | Launcher + quiz read **SQLCipher / IPC snapshots only** — zero Firestore / AI / FCM. |
| Tunables | Only in `desktop/crates/meritscreen-core` `app_config` (mirror `:core:common`). |
| Privacy | No GPS, contacts, SMS, photos, child email, keylogging, or other-app screen capture. |
| Admin child account | Detect + `tamperFlags.adminAccount` + parent alert. Do **not** claim unbreakable lockdown. |
| Distribution (child) | **Direct signed download** is primary (full L2/L3). Store SKUs may be reduced — document if shipped. |

---

## 2. OS support matrix

| OS | Versions | Enforcement v1 | Notes |
| --- | --- | --- | --- |
| **Windows** | 10 (22H2+), 11 | Full (L1+L3; L2 Strict opt-in) | Service + session agent; MSI/MSIX |
| **macOS** | 13 Ventura → current | Full (L1+L3 only) | No Finder/Dock replace; notarized `.pkg` |
| **Linux** | Ubuntu LTS, Fedora (current) | **Beta in d11** | X11 preferred; Wayland best-effort |

| Display session | Status |
| --- | --- |
| Windows desktop session | Supported |
| macOS Aqua | Supported |
| Linux X11 | Beta path |
| Linux Wayland | Best-effort; parent UI shows degraded `enforcementTier` when needed |

**Rollout lock:** Windows + macOS GA first. Linux does not block Win/Mac v1.

---

## 3. Always-on after reboot (product requirement)

| Process | Boot behavior | Phase |
| --- | --- | --- |
| **Guardian** | Starts as Windows Service / macOS `SMAppService` LaunchDaemon / (later) systemd | d3 / d4 / d11 |
| **Session Agent** | Starts when child user logs in; Guardian respawns if killed | d3 / d4 |
| **UI (Tauri)** | Ephemeral — spawned when launcher / quiz / lock needed; not required for timers | d1 / d6 |

**Done meaning for child PC:** Power off → power on → log into standard child account → Guardian already running → Agent attaches → last SQLCipher phase (`quiz_due` / `shielded` / cooldown) still enforced without parent action.

UI quit, crash, or logout of the MeritScreen window must **not** clear fail lock or skip a due quiz.

---

## 4. Uninstall / disable protection (honest model)

Consumer OS accounts cannot get silent MDM-grade uninstall blocks without enterprise enrollment. Locked approach:

| Control | Windows | macOS |
| --- | --- | --- |
| PIN gate before uninstall UX | Custom uninstall wizard / ARP entry that requires Parent PIN when we own the uninstaller | Custom uninstall script / pkg forget path behind PIN when we own it |
| Service disable / delete | Detect → `tamperFlags.serviceStopped` + parent alert on next sync; last policy stays fail-closed | Same via LaunchDaemon absence |
| Standard (non-admin) child | Cannot remove system service without admin password — **required setup** | Same — child must not be admin |
| Hard block without MDM | **Not claimed** | **Not claimed** |

Implementation phase: **d9** (security hardening). Setup checklist (C02) must require standard child account and document admin risk.

---

## 5. Signing accounts (ops checklist)

| Platform | Artifact | Account / key | Owner action |
| --- | --- | --- | --- |
| Windows | MSI/MSIX + binaries | Code signing cert (**EV preferred** for SmartScreen reputation) | Procure + store in CI secrets; do not commit |
| macOS | `.pkg` + binaries | Apple Developer ID Application + Installer; notarization | Team ID in CI; hardened runtime entitlements |
| Linux (d11) | `.deb` / `.rpm` | Package signing GPG key | Beta repo key |

Until certs exist, CI builds **unsigned** artifacts for compile verification only.

---

## 6. Firebase (desktop)

| Item | Decision |
| --- | --- |
| Project | Same as mobile: `managing-screen-time` |
| Auth | Parent: Email OTP callables + Google **loopback+PKCE**; Sign in with Apple on macOS. Child: custom token after `consumePairingToken` |
| OAuth clients | Register **desktop** OAuth clients in Google Cloud / Firebase when d5 starts (placeholder tracked below) |
| Firestore / Functions | REST + HTTPS callables from Guardian only |
| App Check | **Gap:** no official desktop attestation provider. Stay in monitor/rate-limit mode for desktop; document in SECURITY.md. Custom attestation later (non-blocking for d0–d8) |
| Push | Poll first (policy ~6h, usage ~2h, heartbeat ~30m). APNs on macOS later |

### Ops TODO (console — before d5 pairing)

- [ ] Create Firebase / Google OAuth client for desktop (Windows + macOS redirect / loopback)
- [ ] Enable Sign in with Apple for macOS parent builds
- [ ] Do **not** enable App Check enforcement for desktop until attestation exists

---

## 7. Additive schema proposal (backward compatible)

Land Functions/rules in **d5** (or earlier if parallel). Draft PR: [desktop/docs/backend-pr-draft.md](../desktop/docs/backend-pr-draft.md).

### `policy/current`

| Field | Type | Notes |
| --- | --- | --- |
| `quizMode` | string | Add `device_interval`. Mobile `fromStorage` already falls back unknown → `app_block` |
| `quizIntervalMinutes` | number | Default 15; coerce with desktop `AppConfig` min/max |

### `devices/{deviceId}`

| Field | Values |
| --- | --- |
| `platform` | `android` \| `ios` \| **`windows`** \| **`macos`** \| **`linux`** |
| `agentVersion` | Guardian semver |
| `osBuild` | OS build string |
| `guardianState` | `running` \| `stopped` \| `degraded` |
| `tamperFlags` | map: `serviceStopped`, `clockRollback`, `adminAccount`, `binaryMismatch`, `uninstallAttempt`, … |
| `enforcementTier` | `L1` \| `L1_L3` \| `L1_L2_L3` |

### Functions

- `consumePairingToken`: accept `windows` \| `macos` \| `linux` (stop collapsing to android).
- **Redact** pairing `code` from Function logs (product invariant).

### Rules

Extend `childDeviceUpdateKeysOnly()` with the new device heartbeat fields above.

### Installed app identity

| Platform | `appId` scheme |
| --- | --- |
| Windows | `win:` + AppUserModelID or normalized path |
| macOS | `mac:` + bundle id |
| Linux | `linux:` + desktop-file id |

---

## 8. Spikes (verify-in-d0)

Full notes: [desktop/docs/spikes/](../desktop/docs/spikes/) and updated “Verify in d0” section in [15](15-desktop-native-production.md).

| Spike | Result | Implication |
| --- | --- | --- |
| Windows custom shell / Assigned Access | **PASS (API path)** — `Shell=` / Assigned Access viable on Pro+ for standard user; Home limited → Strict mode **opt-in**, document Home gaps | Ship L1+L3 always; Strict (L2) behind parent setting after d3 lab confirm |
| macOS shielding window + kiosk presentation | **PASS (API path)** — `NSWindow` shielding level + `NSApplication.PresentationOptions` under notarized hardened runtime; **no** consumer Family Controls desktop API | L1+L3 only; never claim Home replace |
| Linux X11 vs Wayland | **CONDITIONAL** — X11 grab viable for beta; Wayland compositor-dependent | Linux stays **d11 beta** |
| Idle + foreground without Accessibility | **PASS** — Win: `GetLastInputInfo` + process APIs; macOS: `CGEventSourceSecondsSinceLastEventType` + NSWorkspace; Linux: X11 idle / Wayland portal best-effort | Prefer these APIs; Accessibility only if disclosed and required later |

---

## 9. UX / performance lock (from Android + docs/15)

- Mirror Android screen jobs (S00–S02, P02–P20, C01–C15) with calm child copy and large targets.
- Design tokens in d1 from `:core:ui` intent — no one-off hex in feature screens.
- Appearance: System / Light / Dark.
- Motion: short; **none heavy** on fail-lock path.
- Perf budgets (docs/15 §15): Guardian RSS &lt; ~25 MB; idle CPU &lt; ~0.5%; quiz paint &lt; ~250 ms pre-warmed; zero network on launcher/quiz paint.

---

## 10. Bootstrap deliverables

| Deliverable | Location |
| --- | --- |
| Empty Rust workspace compiles | `desktop/` (`cargo test --workspace`) |
| Win + Mac CI | `.github/workflows/desktop-ci.yml` |
| Packaging placeholders | `desktop/packaging/{windows,macos,linux}/` |
| Backend PR draft | `desktop/docs/backend-pr-draft.md` |
| Spike notes | `desktop/docs/spikes/*.md` |

---

## 11. Sign-off

| Item | Status |
| --- | --- |
| Invariants §1 | **Locked** |
| OS matrix §2 | **Locked** |
| Reboot / background §3 | **Locked** (implement d3/d4) |
| Uninstall honesty §4 | **Locked** (implement d9) |
| Signing ops §5 | **Tracked** (certs outside repo) |
| Firebase / App Check §6 | **Locked** + console TODOs |
| Schema §7 | **Proposed** — land d5 |
| Spikes §8 | **Recorded** |
| Workspace + CI | **Shipped in d0** |

**d0–d5 done** (through Parent system — [16](16-desktop-build-phases.md)). Next: **d6** Child UI (pairing, launcher, quiz, fail lock, PIN).
