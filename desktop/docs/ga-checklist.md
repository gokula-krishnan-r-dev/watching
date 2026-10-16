# Windows + macOS GA checklist (d12)

Sign when pilot exit criteria in [pilot-playbook.md](pilot-playbook.md) are met. **Linux is not required for GA.**

## Product

- [x] Fail lock always `all_non_emergency`
- [x] Child launcher + quiz: local/IPC only (architecture lint green)
- [x] Mixed-family Android/iOS parent ↔ desktop child verified
- [x] In-product limitations: admin account, macOS no shell replace, App Check gap, Linux beta status

## Reliability

- [x] Guardian survives reboot (Service / LaunchDaemon / systemd)
- [x] Kill Agent / UI does not clear `quiz_due` / `shielded`
- [x] Clock rollback fails closed
- [x] Perf smoke under RSS budget (`--perf-smoke`)
- [x] a11y smoke green

## Security / updates

- [x] IPC HMAC + peer checks (d9)
- [x] PIN-gated uninstall + `uninstallAttempt`
- [x] Updater Ed25519 + rollback + staged rings with % cohort
- [x] Pairing/OTP never logged

## Store / policy

- [x] Privacy policy covers desktop Guardian/Agent (see [store-readiness.md](store-readiness.md))
- [x] Direct-download primary messaging ready
- [x] Nutrition / data-safety labels drafted for store SKUs (if any)

## Sign-off

| Role | Name | Date |
| --- | --- | --- |
| Eng | | |
| Product | | |
| Support | | |

**GA status:** ☐ Not signed · ☐ Signed for Windows + macOS (Linux beta later)
