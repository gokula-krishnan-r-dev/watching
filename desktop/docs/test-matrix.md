# Desktop test matrix (d12) — Windows + macOS GA

Linux (Ubuntu/Fedora, X11/Wayland) is **deferred** to phase d11 / post-GA beta. This matrix is the GA gate for Win/Mac.

## OS / SKU grid

| Platform | Versions | Install path | Enforcement | Automation |
| --- | --- | --- | --- | --- |
| Windows | 10 22H2+, 11 | MSI lab / Service | L1+L3; Strict SKU-gated | CI `windows-latest` + packing README VM checklist |
| macOS | 13–15 | LaunchAgent lab / LaunchDaemon pkg | L1+L3 only | CI `macos-latest` + packaging README |
| Linux | — | — | — | **Deferred** (d11) |

## Mixed-family (same Firebase project)

| Parent | Child | Must verify |
| --- | --- | --- |
| Android | Windows / macOS | Pair → policy → interval quiz → fail lock → parent sees usage/tamper |
| iOS | Windows / macOS | Same |
| Desktop parent | Android / iOS child | Policy edit + pairing mint (desktop parent UI) |
| Desktop parent | Desktop child | Lab path (demo or REST backends) |

Pilot families should cover at least **one Android-parent ↔ desktop-child** and **one reverse** before GA sign-off.

## Automated conformance (every PR)

```bash
cd desktop
cargo test -p meritscreen-core vectors          # golden session JSON
cargo test -p meritscreen-db session            # phase persist
cargo test -p meritscreen-enforcement clock     # clock rollback
cargo test -p meritscreen-guardian --test resilience
cargo test -p meritscreen-guardian --test ipc_ping
cargo test -p meritscreen-updater               # signed rings + % cohort
cargo test --workspace
```

CI: `.github/workflows/desktop-ci.yml` (macOS + Windows).

## Resilience cases (automated where possible)

| Case | How |
| --- | --- |
| Kill Session Agent | `resilience::agent_stale_after_silence` — stale → respawn intent; phase unchanged |
| Kill / quit UI during `quiz_due` / `shielded` | Phase owned by Guardian SQLCipher; `ui_required` stays true |
| Reboot mid-cooldown | `resilience::reboot_mid_cooldown_restores_shielded` (DB reopen) |
| Clock rollback | `resilience::clock_rollback_fails_closed` + enforcement unit tests |
| Offline cached policy | Sync tests + product invariant (no network on launcher/quiz) |

Manual lab (before pilot exit): cold reboot with real Service/LaunchDaemon — see packaging READMEs.

## Manual lab checklist (per OS version)

Copy into pilot run notes:

1. [ ] Fresh install → pair with 6-digit code
2. [ ] Interval quiz fires; no Close/Skip
3. [ ] Fail lock device-wide; cooldown ends or Retry pass
4. [ ] Kill `meritscreen-ui` during quiz → Agent respawns overlay; phase unchanged
5. [ ] Kill `meritscreen-agent` → Guardian respawns within ~15s
6. [ ] Reboot → Guardian up before login completes; last phase enforced
7. [ ] Parent sees tamper / enforcement tier on child detail
8. [ ] Uninstall with wrong PIN → `uninstallAttempt`; correct PIN removes service
9. [ ] AV / SmartScreen / Gatekeeper path documented for support

## Residual limitations (document in-product)

- Admin / root child account → `tamperFlags.adminAccount` (no unbreakable claim)
- macOS: no Finder/Dock replace
- App Check: desktop residual risk (monitor mode)
- Linux: deferred beta
- Windows Strict (L2): SKU-gated; Home gets honest “not available”
