# MeritScreen Desktop — Security & Threat Model

This document specifies the security architecture, threat model, and signoff checklist for MeritScreen desktop binaries (Guardian, Session Agent, and Child/Parent Tauri UI) across Windows, macOS, and Linux.

See the canonical repository policy in [../../SECURITY.md](../../SECURITY.md).

---

## 1. Desktop Threat Matrix (Phase d9)

| Threat | Residual Risk | Honest Mitigation |
| :--- | :--- | :--- |
| **Admin / Root Child OS Account** | Child can terminate services, alter file permissions, or run uninstaller | Guardian detects admin account → sets `tamperFlags.adminAccount = true` and updates `degradedMessage` to parent. Setup checklist strictly requires a **standard (non-admin)** child OS account. We do not claim unbreakable lock against local admins. |
| **Guardian Service Stopped / Disabled** | Enforcement pauses until restarted | OS service restarts automatically on failure (`Restart=always` on systemd, `KeepAlive` on macOS LaunchDaemons, Windows Service Control Manager recovery). `tamperFlags.serviceStopped = true` persisted in SQLCipher. Last local policy remains cached and fail-closed. |
| **SQLCipher DB Key Theft** | Offline policy/session readable if OS secret store + disk are compromised | Master key stored in OS-protected vaults (DPAPI on Windows, macOS Keychain, strict 0600 file store on Linux). Key never logged (`sanitize_for_log`). Wrong key fails closed to protected lock screen. |
| **IPC Spoofing / Malicious Local Process** | Malicious process attempts to lift fail locks or unpair child | 1. **Peer Credentials**: Unix stream checks `LOCAL_PEERCRED` / `SO_PEERCRED` ensuring peer UID matches Guardian UID or is authorized child session UID. Windows verifies caller process ID and executable image path (`meritscreen-*`).<br>2. **HMAC-SHA256 Framing**: Every length-prefixed frame is signed with machine-local `IpcHmacKey`. Unsigned or corrupted MAC frames fail closed immediately. |
| **Unauthorized Uninstall** | Child attempts to uninstall via Windows ARP or macOS Drag-to-Trash | Dedicated uninstall helper `--uninstall-with-pin <pin>` requires Parent PIN verification. Incorrect attempts trigger `tamperFlags.uninstallAttempt = true` and alert parent via device heartbeat. |
| **Rollback / Broken Updater Attack** | Malicious or obsolete binary deployed | 1. **Ed25519 Signatures**: Manifest verified against pinned public keys.<br>2. **Rollback Floor**: Rejects candidates below `accepted_floor` and `manifest.min_version`.<br>3. **Staged Cohorts**: Deterministic `cohort_bucket(device_id, version)` prevents sudden fleet bricking. |
| **Active-Use Clock Tampering / Rollback** | Child sets system clock back to evade interval or cooldown | Monotonic clock (`clock_monotonic_ms`) drives elapsed session calculations. Wall-clock backward jumps exceeding 5 minutes trigger `tamperFlags.clockRollback = true` and fail closed into `SessionPhase::Shielded`. |
| **Desktop REST without App Check** | Token theft or direct API replay | Desktop REST requests use device-bound pairing credentials. Scoped callable Functions validate `familyId` / `childId` token claims. Backend rate limits and device revocation provide defense in depth. (See [app-check-attestation-spike.md](docs/app-check-attestation-spike.md)). |

---

## 2. Phase d9 Security Signoff Checklist

- [x] **IPC Spoof Fails Closed**: Verified by `spoofed_mac_fails_closed` and `unsigned_frame_rejected_when_key_required`.
- [x] **Peer Identity Enforcement**: Verified by `authorize_same_uid`, `authorize_unix_peer_credential_rules`, and Windows image path check.
- [x] **No Secrets in Logs**: Verified by `log_sanitizer` (redacts pairing codes, OTPs, JWTs, bearer tokens, PINs).
- [x] **Honest PIN-Gated Uninstall**: Verified by `uninstall_with_pin` and `wrong_pin_marks_attempt_and_fails`.
- [x] **Signed Updater with Rollback Floor**: Verified by `verify_and_accept_happy_path` and `rollback_below_floor_refused`.
- [x] **App Check Residual Risk Documented**: Documented in `app-check-attestation-spike.md`.

---

## 3. Phase d10 Performance, Accessibility & Localization Checklist

- [x] **Guardian RSS Budget**: Under 25 MB steady in production (CI `--perf-smoke` reports 7 MB).
- [x] **Guardian CPU Budget**: Event-driven 5s idle poll; 1 Hz watchdog active only while child session is running.
- [x] **Quiz UI Pre-Warming**: Pre-warms WebView ~45s before quiz due; caps idle residency to prevent memory creep.
- [x] **WebView Memory Discipline**: Quits UI process when not in active use (`quit_ui_if_idle`).
- [x] **Accessible Dialogs & Focus**: Screen reader ARIA dialogs (`role="dialog"`, `aria-modal="true"`), `:focus-visible` contrast rings.
- [x] **Keyboard Navigation**: Numeric hotkeys `1`–`9` for quiz options, `Enter` / keyboard navigation for all interactive controls.
- [x] **Multi-Language Catalogs**: English (`en.json`) and Spanish (`es.json`) catalogs in both TypeScript UI and Rust core.
