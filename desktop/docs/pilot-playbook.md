# Closed pilot & support playbook (d12)

**Scope:** Windows + macOS GA path. Linux pilots wait for d11.

## Pilot goals

1. Crash-free Guardian across reboot for ≥7 days per device.
2. Zero false “fully locked” claims on unsupported SKUs.
3. Mixed-family pairing works with production Firebase.
4. Support can resolve Strict / AV / PIN uninstall without eng escalation.

## Closed pilot intake

| Item | Target |
| --- | --- |
| Families | 5–15 closed (friends/family or design partners) |
| Devices | ≥3 Windows 11, ≥2 Windows 10, ≥3 macOS 13–15 |
| Roles | Mix of Android-parent and desktop-parent |
| Channel | Updater ring **`pilot`** only |
| Duration | ≥14 days or until exit criteria met |

### Onboarding script

1. Standard (non-admin) child account required — screenshot for support.
2. Install signed build from pilot CDN; verify Gatekeeper / SmartScreen messaging.
3. Pair with parent app; set `device_interval` 15 min for fast feedback.
4. Walk fail-lock once with parent present.
5. Enable tamper alerts on parent notifications.

## Exit criteria (must all pass)

- [ ] Conformance vectors + `resilience` tests green on CI (Win + Mac)
- [ ] No P0 crashes (Guardian/Agent) in last 7 pilot days
- [ ] ≥90% of pilot devices show Guardian alive after cold reboot (lab or telemetry)
- [ ] Mixed-family Android↔desktop pairing verified for ≥2 families
- [ ] Support playbook used for ≥1 AV false-positive and ≥1 PIN uninstall
- [ ] Privacy / store readiness reviewed ([store-readiness.md](store-readiness.md))
- [ ] Win/Mac [GA checklist](ga-checklist.md) signed by eng + product

**Linux** remains beta until d11 criteria; does not block Win/Mac GA.

## Crash-free SLO (pilot)

| Signal | Pilot threshold |
| --- | --- |
| Guardian process uptime | Survive reboot; restart within 30s of kill |
| Agent attach | Within 15s of user session |
| UI overlay | Respawn while `quiz_due` / `shielded`; never clear phase |
| Update health | Promote `pilot` → `%` stable only if heartbeat healthy |

## Support playbook

### AV / SmartScreen false positive (Windows)

1. Confirm binary signature / hash vs pilot release notes.
2. Ask parent to add MeritScreen install dir + `meritscreen-guardian.exe` exclusion **only if** they trust the signed build.
3. Log `tamperFlags` / `guardianState` from parent child detail (“device health”).
4. Do **not** ask the child to run as admin to “fix” AV.

### Gatekeeper / notarization (macOS)

1. Confirm notarized `.pkg` from pilot channel.
2. System Settings → Privacy for Accessibility / Automation only if overlay docs require (document honestly).
3. Lab LaunchAgents ≠ production LaunchDaemon — clarify for support.

### Strict mode unavailable

- Parent UI already shows degraded copy when L2 unavailable.
- Support script: “Strict shell replace is optional Windows SKU; L1+L3 still enforce quizzes and fail lock.”

### Child stopped Guardian / uninstalled

1. Check `tamperFlags.serviceStopped` / `uninstallAttempt`.
2. Confirm child is standard account; if admin, require remediating account type.
3. Reinstall via PIN-gated helper; do not claim silent MDM block.

### Pairing / OTP issues

- Never ask for pairing code or OTP over chat — parent regenerates.
- Logs must show lengths only (`code_len`), never codes.

### Escalation

| Severity | Example | Owner |
| --- | --- | --- |
| P0 | Guardian dead after reboot; fail lock cleared wrongly | Eng on-call |
| P1 | Pairing broken for desktop platform | Eng + Firebase |
| P2 | AV friction, UX copy | Support + product |

## Staged rollout after pilot

See [updater.md](updater.md) rings: `internal` → `pilot` → `stable` with **percent cohort**. Promote only after exit criteria + health gate.
