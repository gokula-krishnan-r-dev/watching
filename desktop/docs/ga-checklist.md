# Windows + macOS GA checklist (d12)

Sign when pilot exit criteria in [pilot-playbook.md](pilot-playbook.md) are met. **Linux is not required for GA.**

## Product

- [ ] Fail lock always `all_non_emergency`
- [ ] Child launcher + quiz: local/IPC only (architecture lint green)
- [ ] Mixed-family Android/iOS parent ↔ desktop child verified
- [ ] In-product limitations: admin account, macOS no shell replace, App Check gap, Linux deferred

## Reliability

- [ ] Guardian survives reboot (Service / LaunchDaemon)
- [ ] Kill Agent / UI does not clear `quiz_due` / `shielded`
- [ ] Clock rollback fails closed
- [ ] Perf smoke under RSS budget (`--perf-smoke`)
- [ ] a11y smoke green

## Security / updates

- [ ] IPC HMAC + peer checks (d9)
- [ ] PIN-gated uninstall + `uninstallAttempt`
- [ ] Updater Ed25519 + rollback + staged rings with % cohort
- [ ] Pairing/OTP never logged

## Store / policy

- [ ] Privacy policy covers desktop Guardian/Agent (see [store-readiness.md](store-readiness.md))
- [ ] Direct-download primary messaging ready
- [ ] Nutrition / data-safety labels drafted for store SKUs (if any)

## Sign-off

| Role | Name | Date |
| --- | --- | --- |
| Eng | | |
| Product | | |
| Support | | |

**GA status:** ☐ Not signed · ☐ Signed for Windows + macOS (Linux beta later)
