# Desktop performance (d10 / docs/15 §15)

| Metric | Target | Gate |
| --- | --- | --- |
| Guardian RSS | ≤ 25 MB release / ≤ 120 MB debug | `meritscreen-guardian --perf-smoke` |
| Idle CPU | Event-driven; 5s poll when not child-active; 1 Hz when child session | Watchdog in `runtime.rs` |
| Usage flush | ~45s when dirty; immediate on phase change | `USAGE_FLUSH_INTERVAL_SECONDS` |
| Session persist | 45s | `SESSION_PERSIST_INTERVAL_SECONDS` |
| Quiz first paint | Pre-warm UI ~45s before due | `QUIZ_PREWARM_SECONDS` + Agent `--prewarm` |
| WebView residency | Quit when idle; pre-warm capped | Agent `quit_ui_if_idle` |

## CI

Desktop workflow runs `--perf-smoke` after the Guardian build on macOS and Windows.

## Manual

```bash
cd desktop
cargo build -p meritscreen-guardian
./target/debug/meritscreen-guardian --perf-smoke
```
