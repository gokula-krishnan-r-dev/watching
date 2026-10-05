# Desktop golden vectors

Contractual session / quiz transition fixtures for MeritScreen desktop.

- Run from Rust: `cargo test -p meritscreen-core vectors`
- Android / iOS should eventually load the **same JSON files** so engines cannot drift.

## Session vectors (`session/`)

| File | Covers |
| --- | --- |
| `pass_grant.json` | Block → quiz pass → new grant |
| `fail_shield.json` | Quiz fail → device-wide shield |
| `retry_restarts_cooldown.json` | Failed retry restarts cooldown |
| `ceiling_win.json` | Daily ceiling tighter rule → quiz_due |
| `interval_tick.json` | `device_interval` active-use → quiz_due |
| `cooldown_expiry.json` | Cooldown end → idle |

## Quiz

Builtin bank lives in `crates/meritscreen-core/assets/quiz_bank_builtin.json` (ported from Android). Engine unit tests cover pick/grade/finalize.
