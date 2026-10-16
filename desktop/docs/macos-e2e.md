# macOS desktop E2E with Firebase

## Prerequisites

- macOS 13+ with Xcode CLT / Rust 1.80+ / Node 20+
- Firebase CLI (`npm i -g firebase-tools`)
- Access to project **`managing-screen-time`**

```bash
firebase login
firebase use managing-screen-time
```

## One-shot

| Make target | What it does |
| --- | --- |
| `make desktop-macos-doctor` | Verify CLI login + Identity Toolkit API key |
| `make desktop-macos` | UI build, a11y smoke, cargo build (guardian/agent/ui), conformance tests, Guardian smoke with Firebase env |
| `make desktop-macos-ui` | Same as above, then launch Tauri UI |

Scripts: `scripts/desktop-macos-e2e.sh`, `scripts/desktop-macos-env.sh`.

## What “Firebase wired” means

| Process | Behavior when API key set |
| --- | --- |
| **Guardian** | REST Auth + Firestore sync (not mock) |
| **Tauri parent** | `ParentSession::from_env()` → REST callables (`sendEmailOtp`, pairing, …) |
| **Child UI** | Still local / IPC only (product invariant) |

Lab data: `~/Library/Application Support/MeritScreen/lab` (+ `lab-secrets`).

## White screen?

`cargo run -p meritscreen-ui` must use the **`custom-protocol`** feature (enabled by default) so the WebView loads bundled `ui/dist`. If that feature is off, Tauri opens `http://localhost:1420` and shows a blank window when Vite is not running. Also rebuild the UI after Vite config changes (`npm run build` in `apps/meritscreen-ui/ui`) so asset URLs are relative (`base: "./"`).

## Parent OTP (demo vs live)

- Without `MERITSCREEN_FIREBASE_API_KEY`, or with `MERITSCREEN_PARENT_DEMO=1`, parent sign-in uses the local demo store — code **`424242`**.
- `make desktop-macos-ui` sources the Firebase API key and calls live `sendEmailOtp` / `verifyEmailOtp`. Use a tester-allowlisted email (static OTP) or a real mailbox with Resend configured.

## Manual UI checklist after `make desktop-macos-ui`

1. Role → Parent → email OTP (tester allowlist / Resend) or demo if callables fail closed to demo paths
2. Onboard child → create pairing code
3. Role → Child → enter code (demo accepts any 6-digit locally; live consume needs Guardian bind)
4. Launcher → Quiz now → fail lock → Parent PIN unpair

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Firebase CLI is not logged in` | `firebase login` |
| Identity Toolkit probe fails | Confirm API key / Google Cloud API restrictions for the Web key |
| Parent OTP fails | Deployed Functions + Resend secret; closed tester OTP in `TESTER_ACCOUNTS.md` |
| Guardian uses mock | Ensure `MERITSCREEN_FIREBASE_API_KEY` is set and `MERITSCREEN_SYNC_MOCK` unset |
