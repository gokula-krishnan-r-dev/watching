# MeritScreen Desktop

Native **Windows + macOS** child/parent client (Linux beta later). Stack: Rust Guardian + Session Agent + shared Tauri 2 UI. Same Firebase project and policy JSON as Android / iOS.

| Doc | Purpose |
| --- | --- |
| [docs/15-desktop-native-production.md](../docs/15-desktop-native-production.md) | Platform truth, process model, schema, limits |
| [docs/16-desktop-build-phases.md](../docs/16-desktop-build-phases.md) | Phases d0–d12 |
| [docs/17-desktop-d0-product-lock.md](../docs/17-desktop-d0-product-lock.md) | d0 product lock |
| [docs/sqlcipher.md](docs/sqlcipher.md) | SQLCipher Community licensing decision |
| [docs/backend-pr-draft.md](docs/backend-pr-draft.md) | Functions/rules additive PR (land in d5) |

## Layout

```
desktop/
  crates/          # core, db, firebase, security, ipc, parent, child, enforcement, guardian, agent
  apps/
    meritscreen-ui # Tauri 2 host + Vite UI (parent + child graphs)
  packaging/       # MSI/pkg/deb skeletons
  vectors/         # JSON golden files (d2)
  docs/            # desktop-only drafts + spike notes
```

## Build

```bash
cd desktop
cargo test --workspace

# UI typecheck / production assets
cd apps/meritscreen-ui/ui && npm ci && npm run build && cd -

# Launch role-gate shell (custom-protocol default loads ui/dist;
# without it Tauri hits dead localhost:1420 → white screen)
cargo run -p meritscreen-ui
```

Or from repo root: `make desktop-test` / `make desktop-ui`.

## macOS end-to-end (Firebase)

Requires Firebase CLI logged into project `managing-screen-time`.

```bash
firebase login
firebase use managing-screen-time

make desktop-macos-doctor   # CLI + Identity Toolkit probe
make desktop-macos          # full build, conformance, Guardian with live Firebase env
make desktop-macos-ui       # same, then launch Tauri (parent uses REST when API key set)
```

Env helper: `source scripts/desktop-macos-env.sh`  
Defaults: `MERITSCREEN_FIREBASE_PROJECT_ID=managing-screen-time` + Web API key (override with your own if needed). Guardian sync uses REST when the key is set; omit key / set `MERITSCREEN_SYNC_MOCK=1` for offline mock.

Requires Rust **1.80+**, Node **20+** (for the UI), and platform webview (WKWebView / WebView2).

## Engines (d2)

```bash
cargo test -p meritscreen-core vectors   # JSON golden vectors
cargo test -p meritscreen-db session     # phase/cooldown persistence
```

Vectors: [`vectors/`](vectors/) — share with Android/iOS eventually.

## Product invariants (locked)

- Fail lock is always **all non-emergency** apps — never per-app-only.
- Child launcher + quiz: **local DB / IPC only** — no Firestore or AI on that path.
- UI never depends on `meritscreen-firebase` or `meritscreen-db` crates.
- Guardian starts at **boot** (Windows Service d3; macOS LaunchDaemon d4); UI is ephemeral.

## Windows Guardian (d3)

```bash
cargo build -p meritscreen-guardian -p meritscreen-agent
# Lab VM (elevated PowerShell): see packaging/windows/README.md
cargo test -p meritscreen-guardian --test ipc_ping
```

## Parent system (d5)

```bash
cargo test -p meritscreen-parent
cargo run -p meritscreen-ui
# Demo OTP: any email + code 424242
# Google/Apple need MERITSCREEN_GOOGLE_CLIENT_ID / Apple Services ID (ops)
```

Backend landed with d5: desktop platforms on `consumePairingToken`, no pairing-code logs, desktop heartbeat fields in rules.

## Child UI (d6)

```bash
cargo test -p meritscreen-child
cargo test -p meritscreen-ui --lib architecture_lint
cargo run -p meritscreen-ui
# Role → Child → any 6-digit code → checklist → launcher
# Lab: “Quiz now”, “+5 min”, fail-lock cooldown tick; Parent PIN 1234 to unpair
```

Child path uses `meritscreen-child` only (SessionEngine + AdaptiveQuizEngine + builtin pack). No Firebase on UI/child crates. Overlay focus fallback: [docs/spikes/webview-overlay-focus.md](docs/spikes/webview-overlay-focus.md).

## Enforcement (d7)

```bash
cargo test -p meritscreen-enforcement
cargo test -p meritscreen-db inventory
cargo test -p meritscreen-guardian --test ipc_ping
# Guardian L3: Agent samples foreground/idle → ReportForeground → GateDecision
```

- Inventory: `win:` / `mac:` / `linux:` appIds → SQLCipher `installed_apps` + `inventory_hash`
- Protected process list never terminated; emergency apps always allowed
- Clock rollback → shield + `tamperFlags.clockRollback`
- Parent child detail shows enforcement tier, degraded copy, demo allowlist inventory
- Windows Strict (L2): SKU probe only; Home/macOS get honest “not available” copy

## Sync (d8)

```bash
cargo test -p meritscreen-guardian sync
cargo test -p meritscreen-firebase --lib
# Lab mock sync (default without Firebase env):
# MERITSCREEN_SYNC_MOCK=1  or omit MERITSCREEN_FIREBASE_API_KEY
# Cloud:
# MERITSCREEN_FIREBASE_API_KEY=… MERITSCREEN_FIREBASE_PROJECT_ID=…
# Disable: MERITSCREEN_SYNC_DISABLE=1
```

Guardian schedulers (poll-first): policy ~6h + reconnect + `PolicyRefresh` IPC; usage ~2h + `QuizCompleted`; heartbeat ~30m (revoke check); inventory on hash change; pack-low log. Offline keeps last SQLCipher policy. UI never calls Firebase.

## Test matrix, pilot, staged rollout (d12)

Win/Mac GA path — **Linux deferred** (d11 later).

```bash
cargo test -p meritscreen-core vectors
cargo test -p meritscreen-guardian --test resilience
cargo test -p meritscreen-updater
```

Docs: [test-matrix.md](docs/test-matrix.md) · [pilot-playbook.md](docs/pilot-playbook.md) · [ga-checklist.md](docs/ga-checklist.md) · [store-readiness.md](docs/store-readiness.md) · [updater.md](docs/updater.md) (`internal` → `pilot` → `stable@N%`)

## Performance, a11y, localization (d10)

```bash
cargo test -p meritscreen-guardian perf
cargo run -p meritscreen-guardian -- --perf-smoke
cd apps/meritscreen-ui/ui && npm run a11y-smoke && npm run build
```

- Budgets: [docs/perf.md](docs/perf.md) (docs/15 §15)
- Pre-warm quiz UI ~45s before due; quit WebView when idle
- Event-driven Guardian: 1 Hz when child-active, 5s idle poll
- Usage flush ~45s when dirty; immediate on quiz/fail phase change
- i18n: [docs/localization.md](docs/localization.md)

## Security hardening (d9)

```bash
cargo test -p meritscreen-ipc          # HMAC spoof fails closed
cargo test -p meritscreen-updater      # Ed25519 + rollback dry-run
cargo test -p meritscreen-guardian uninstall db_boot
# PIN uninstall (device with stored parent PIN hash):
# meritscreen-guardian --uninstall-with-pin <pin>
# Unguarded uninstall → --mark-uninstall-attempt → tamperFlags.uninstallAttempt
```

- Threat model: root [`SECURITY.md`](../SECURITY.md); App Check spike: [docs/app-check-attestation-spike.md](docs/app-check-attestation-spike.md)
- Updater: [docs/updater.md](docs/updater.md)
- IPC: peer credentials **and** HMAC-SHA256 (`MERITSCREEN_IPC_HMAC_DISABLE=1` lab only)

## macOS Guardian (d4)

```bash
cargo build -p meritscreen-guardian -p meritscreen-agent
./packaging/macos/install-lab.sh          # user LaunchAgents (lab)
# System boot path: sudo copy to /Library/MeritScreen && --install-daemon
# Notarized pkg: ./packaging/macos/build-pkg.sh && ./packaging/macos/notarize.sh
cargo test -p meritscreen-guardian --test ipc_ping
./target/debug/meritscreen-agent --overlay-proof
```
- Tunables only in `meritscreen-core::app_config`.
- Appearance: System / Light / Dark, persisted locally.
