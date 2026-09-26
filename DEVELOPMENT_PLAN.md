# Development plan

Android-only roadmap derived from the product spec (`docs/01`–`docs/07`) and the foundation milestone. Complete each phase before starting the next. A feature is done only when UI, logic, backend, authz, loading/error/empty, cache/offline, tests, and docs are in place.

## Phase 0 — Product & technical planning

Status: **done** (product docs in `docs/`, architecture in this repo).

## Phase 1 — Project foundation (Milestone 1)

Status: **done**.

- Gradle multi-module project, version catalog, convention plugins
- Design system (light/dark), base Compose states
- Hilt, Navigation, role router
- Room / DataStore / Keystore / Timber
- Firebase project `managing-screen-time` wired
- Firestore rules + indexes
- Cloud Functions pairing stubs
- Cursor rules and project documentation

**Verification (production/perf hardening pass):**

- `./gradlew testDebugUnitTest` — all module unit tests pass.
- `./gradlew :app:assembleDebug` and `./gradlew :app:assembleRelease` both succeed; the
  release build runs R8 (full mode), resource shrinking, and ART profile compilation
  cleanly with Hilt + Room + Compose + Firebase, so no proguard keep-rule gaps exist yet.
- Debug builds enable `StrictMode` (main-thread disk/network + leaked closable/SQLite
  detection) so violations surface during development instead of at review time.
- Gradle build/config cache, parallel execution, and VFS watching are enabled; incremental
  Kotlin/KSP compilation confirmed via repeated builds returning `UP-TO-DATE`.
- Child Home path still has zero Firestore/FCM/AI calls (Room + DataStore only), per the
  fail-lock/child-safety invariants.
- Baseline profile *generation* (macrobenchmark-driven, real user journeys) is intentionally
  deferred to Phase 11 — Phase 10 ships a starter `app/src/main/baseline-prof.txt` plus
  ProfileInstaller so ART can AOT the critical Home path on first install; AGP already compiles
  the profile into the release APK.

## Phase 2 — Welcome & onboarding

Status: **mostly done** — pre-auth onboarding + parent first-run wizard shipped; pairing and
the policy wizard are intentionally deferred (see below).

- S00 splash (already using Android 12 splash API)
- S01 role select — moved into `:features:onboarding` (was a placeholder in `:app`)
- S02 legal / kids privacy / parental consent — explicit checkbox, COPPA-style copy
- P01 welcome copy
- Parent first-run wizard (P05-P07): create family, add child(ren) with age band + avatar
  preset, set Parent PIN (PBKDF2-hashed via `core:security`, never stored/logged in plain text)

**Design decision — draft-before-auth:** the product spec explicitly allows collecting
first-run info before account creation (`docs/02`). So P05-P07 write to a local, typed
DataStore (`OnboardingDraftRepository`, JSON via a custom `Serializer`, `core:common`'s
`AgeBand`/`AvatarPreset` reused for future child-profile editing) instead of Firestore. The
whole wizard runs while `DeviceRole` is still `Unassigned`, so `RoleGate`'s `NavHost` is never
re-keyed mid-flow. Once Phase 3 provides a signed-in `uid`, that sign-up flow commits this
draft to Firestore (family + child docs) and clears it — the UI/domain layer here does not
need to be rewritten, only wired to a real backend call.

**Deferred from Phase 2 that landed here:**
- P08 pairing (6-digit code + wait-for-device) and C01 child pairing code entry — Cloud Functions
  mint/consume + custom token are implemented; QR camera scan / on-screen QR rendering can be
  polished later (`qrPayload` is already returned by `createPairingToken`).
- P10 first policy wizard remains deferred to Phase 6 (needs installed-app inventory).

**Verification:** unit tests for the draft repository (real DataStore, temp file, no Android
`Context` needed) and each wizard ViewModel's validation (blank name, child cap, PIN
length/mismatch); a Compose test for the new reusable `PinInputField` in `core:ui`; full
release build (R8 + resource shrink) still succeeds with the new code.

## Phase 3 — Authentication

Status: **done** (Android client + Cloud Functions + rules wired; console enable + deploy still manual).

- Email/password sign-up and sign-in (P02/P03) with `CredentialsValidator` + Firebase error mapping
- Google Sign-In via Credential Manager + Google ID token → Firebase credential (gated on
  `default_web_client_id` from `google-services.json`)
- Forgot password (P04) via Firebase password reset
- Session: Firebase Auth + local `ParentSession` DataStore; cold-start reconciliation in
  `AppViewModel` aligns `DeviceRole` with Auth / parent session / child pairing credential
- Pairing token mint/consume Cloud Functions + custom token for child devices (P08/C01)
- Parent pairing UI shows 6-digit code and watches device count; child enters code
- Logout clears Auth + `ParentSession` + onboarding draft + `DeviceRole` — does **not** unpair children
- Parent PIN hash sync: committed on family create from onboarding draft; re-sign-in can push a
  newer local hash; pairing consume stores hash on the child for offline PIN (ongoing parent→child
  PIN push remains Phase 7 FCM/policy sync)

**Verification:** auth use-case unit tests (sign-up/commit draft, existing family + PIN sync,
needs-onboarding, offline, pairing consume, sign-out); full `testDebugUnitTest` +
`:app:assembleDebug` green.

**Still manual in Firebase Console / CLI:**
1. Enable Email/password and Google under Authentication → Sign-in method.
2. Ensure `google-services.json` includes a Web client ID (`default_web_client_id`) for Google.
3. `firebase deploy --only functions,firestore:rules` so pairing callables and rules are live.

## Phase 4 — Parent system

Status: **done** (parent UI + Firestore control store + deleteFamily callable; usage/app
inventory remain empty until Phases 6–8).

- Nested parent graph (`ParentRoute` → dashboard + child detail + policy screens)
- Dashboard P11–P12 with live children observer; child card shows today minutes / paired
  devices / allowed-app count (honest zeros until child sync)
- Child detail hub → allowlist P13, time limits P14, quiz settings P15, rewards P16, reports P17
- Allowlist supports manual package add until Phase 6 inventory
- Policy writes to `families/.../children/.../policy/current` via `ParentControlStore`;
  fail lock remains fixed `all_non_emergency`
- Reports P17: 7/30-day usage fetch (empty until Phase 7 sync)
- Notifications P18: local DataStore prefs (push delivery Phase 7/8)
- Account P19: email, child cap (`AppConfig.MAX_CHILDREN_PER_PARENT`), add child, reset PIN,
  sign-out (does not unpair children)
- Delete family P20: `deleteFamily` callable recursive wipe + local session clear + sign-out
- Pairing from child detail reuses auth `ParentPairingRoute` without re-keying the NavHost

**Verification:** unit tests for `ChildPolicyMapper` / quiz mode keys and add-child ViewModel
validation; `:features:parent:compileDebugKotlin` + `:app:compileDebugKotlin`.

**Deferred (honest empty states):** installed-app picker (Phase 6), usage charts / quiz
accuracy (Phases 7–8), FCM notification delivery (Phase 7).

## Phase 5 — Child system

Status: **done** (local session engine + Room-backed Home/quiz; launcher Home role and
usage upload remain Phase 6/7).

- Child Home C05: approved-app grid from Room `appRules`; PackageManager launch; empty/error/retry
- Block timer + remaining minutes C07 (in-process; checked on Home tick / resume)
- Quiz interrupt flow C07b–C11 when block ends or every-session mode opens an app
- Built-in offline quiz bank (`assets/quiz_bank_builtin.json`) + adaptive local engine (L1–L5)
- Fail lock C12: device-wide shield for all non-emergency apps; cooldown countdown + retry
- Daily ceiling awareness C13 (quiz due / remaining minutes when configured)
- Parent PIN C14 + on-device parent menu C15 (refresh rules, unpair)
- Policy sync off the Home path: `ChildPolicySyncCoordinator` one-shot pull + Firestore
  snapshot → Room; `HomeViewModel` / quiz read **Room only**
- Session engine + quiz engine unit tests

**Honest platform limits (documented in UI):**
- True “quiz over YouTube” interrupt and Recents trapping need default Home (Phase 6).
- Usage/quiz upload to Firestore + FCM silent policy ping are Phase 7; child already
  applies live Firestore policy listeners into Room when online.

**Verification:** `:features:child:testDebugUnitTest` + `:app:compileDebugKotlin`.

## Phase 6 — Launcher / device management

Status: **done** (Home-role setup UX, installed-app inventory + picker, device
heartbeat/revocation, boot/lifecycle handling; Device Owner/Lock Task remain documented,
not-shipped, advanced paths — see ARCHITECTURE.md "Launcher & device management").

- `:features:launcher` — `HomeRoleManager` requests `RoleManager.ROLE_HOME` (API 29+) or
  fires the standard `ACTION_MAIN`/`CATEGORY_HOME` intent so the system's own chooser lets
  the parent pick MeritScreen as Home on API 26-28. `LauncherSetupScreen` (reachable from a
  dismissible Home banner) shows live status and re-checks on resume — never polls.
  `LockTaskGuard` gives the device-wide fail-lock screen best-effort screen pinning via the
  public `startLockTask()`/`stopLockTask()` APIs (no Device Owner required, and honestly
  documented as user-exitable without one).
- `:features:applications` — `InstalledAppsRepository` queries `PackageManager`
  (`ACTION_MAIN`/`CATEGORY_LAUNCHER`) once at startup and caches the result in Room;
  `PackageChangeMonitor` is a dynamically registered (not manifest, not polling) receiver for
  `PACKAGE_ADDED`/`REMOVED`/`REPLACED` that triggers one debounced re-scan. `AppIconLoader` is a
  small in-memory LRU so the child Home grid renders real app icons without repeated
  `Drawable` decoding. `InstalledAppsSyncCoordinator` uploads only package name + label (no
  icons, no usage) to this device's own Firestore doc, and only when the hash of the set
  actually changed.
- `:features:devices` — `DeviceHeartbeatUseCase`/`DeviceHeartbeatWorker` write
  `lastSeenAt` + Home-role status and read back `revoked` on a WorkManager periodic job
  (30 min, network-constrained, `KEEP` policy so re-enqueuing is idempotent across boot/
  process restart) plus one lightweight check on Home resume — never a Firestore listener.
  `BootCompletedReceiver` re-arms the schedule after reboot. `DeviceRevocationHandler` is an
  interface `:features:child` binds to the existing `UnpairChildDeviceUseCase`, so a
  parent-revoked device signs itself out and clears local credentials automatically (the
  "kill switch" for a lost/handed-down phone) — enforced server-side by `firestore.rules`
  (a child device can only update its **own** `devices/{deviceId}` doc).
- Parent side (`:features:parent`): Allowlist now shows a "pick from the child's device"
  section sourced from the synced inventory (manual package entry stays as a fallback for
  apps not yet synced); a new Devices screen lists paired devices with last-seen time,
  Home-role badge, and a Revoke/Restore action.
- Room schema bumped to v3 (`installed_app`, `device_runtime_state`); `DeviceSummary` gained
  `model`.

**Verification:** `InstalledAppInfoTest` (hash stability/diffing), `InstalledAppsSyncCoordinatorTest`
(skip-when-unchanged, offline, unpaired), `DeviceHeartbeatUseCaseTest` (skipped/ok/revoked/failed),
`AllowlistUiTest` + `FormatRelativeTimeTest`; `:app:compileDebugKotlin` and full
`testDebugUnitTest` across all modules green.

**Explicitly not shipped, and why:** Device Owner / Dedicated Device provisioning requires a
factory-reset-time enrollment flow (NFC bump, QR at setup wizard, or `adb shell dpm
set-device-owner` before any account exists) that is fundamentally incompatible with
installing MeritScreen from Play onto an already-set-up consumer phone — building a fake
version of this would be exactly the "fragile workaround" the product brief says not to
build. `DevicePolicyManager.isDeviceOwnerApp()` is cheap to add later if a managed-device
deployment model is ever needed, but no provisioning UI exists in v1.

## Phase 7 — Real-time synchronization

Status: **done** (push-triggered pull for policy, WorkManager-batched upload for usage/quiz/skill
data, FCM plumbing, offline-safe idempotent sync layer; see ARCHITECTURE.md "Real-time
synchronization (Phase 7)" for the full design).

- **FCM plumbing** — `MeritScreenMessagingService` (`:core:firebase`) is the single
  `FirebaseMessagingService`; it fans data messages out to Hilt-multibound `FcmMessageHandler`s
  and token refreshes to `FcmTokenRegistrar`s (`:core:common` contracts), so `:core:firebase`
  never depends on feature modules. `DeviceRegistryClient.registerPushToken` stores each
  device's current token on its own `devices/{deviceId}` doc.
- **Policy sync (`:features:child`)** — `ChildPolicySyncCoordinator` was rewritten to drop the
  continuous Firestore listener from Phase 5/6: it now pulls once on start, on network
  reconnect, and whenever `PolicySyncScheduler` runs (6 h periodic `WorkManager` fallback, or an
  expedited one-shot triggered by a `policy_sync` FCM). Every pull result — success or failure —
  is recorded in the new `PolicySyncStateEntity` (Room v4) so the parent-menu UI can show
  "synced Xm ago" honestly.
- **Usage / quiz / skill upload (`:features:child`)** — `UsageRecorder` accumulates per-app
  per-day minutes into the new `UsageDayEntity` (dirty-flagged). `ChildQuizViewModel` persists
  each attempt into the new append-only `QuizAttemptEntity` and marks `SkillStateEntity` rows
  dirty. `UsageSyncCoordinator` (run by `UsageSyncWorker`, 2 h periodic + opportunistic
  `runSoon()` after a quiz) uploads only dirty rows and idempotently marks them synced: usage
  days are absolute overwrites, quiz attempts check `exists()` before writing (rules forbid
  update/delete on that collection anyway), skill state is a full-map replace.
- **Device revocation fast path (`:features:devices`)** — `onDeviceRevoked` Cloud Function sends
  a `device_revoked` FCM; `DeviceRevokedFcmHandler` schedules an expedited
  `DeviceHeartbeatWorker` run instead of waiting for the next periodic window, while the
  existing 30-minute heartbeat poll remains the guaranteed fallback (FCM delivery is
  best-effort, not guaranteed).
- **Cloud Functions (`functions/src/index.ts`)** — `onPolicyChanged` / `onAppRuleChanged` /
  `onDeviceRevoked` Firestore triggers call a shared `pushToChildDevices` helper: a data-only,
  high-priority multicast that prunes any token Firebase reports as unregistered/invalid.
- **Offline / recovery** — Home and quiz screens still read Room only; the last synced policy
  stays in force offline; every dirty local row is retried by the next successful sync
  (push-triggered, periodic, or reconnect-triggered), never silently dropped.
- **Rules / schema** — reviewed `firestore.rules` against the new `fcmToken` field on
  `devices/{deviceId}` (already covered by the existing "device may update its own doc" rule)
  and the existing usage/quiz-attempt/skill-state read/write rules (already correctly scoped to
  the owning child device) — no rule changes were needed for Phase 7.

**Verification:** new unit tests — `UsageRecorderTest`, `ChildPolicySyncCoordinatorTest`,
`UsageSyncCoordinatorTest`, `ChildFcmHandlersTest`, `DevicesFcmHandlersTest` — plus the full
existing suite; `./gradlew testDebugUnitTest` (all modules) and `./gradlew :app:assembleDebug`
both green.

## Phase 8 — Usage & analytics

Status: **done** (parent-friendly P17 reports with charts / per-app / quiz / learning,
dashboard teasers, weak-concept upload enrichment, child-safe analytics wiring).

- **Parent reports (P17)** — `ReportsViewModel` loads usage, quiz attempts, skill state,
  allowlist labels, and installed-app labels in parallel (one-shot Firestore reads only),
  then `ReportsAggregator` (`:core:common`) builds a parent-ready `ChildReportsSnapshot`:
  continuous daily timeline (zeros filled), per-app rollup with friendly names, quiz pass
  rate + recent attempts, topic levels (L1–L5 plain-language labels), and “Practice next”
  weak-concept hints. Compose Canvas bar chart + horizontal app bars — no third-party chart
  library. Empty / loading / error + retry; range chips keep selection on retry.
- **Skill upload enrichment** — Room v5 adds `weakConceptsCsv` + `weakConceptTitlesJson` on
  `SkillStateEntity`; `AdaptiveQuizEngine` tracks concept titles; Firestore
  `skillState/current` now carries `weakConcepts` + `weakConceptTitles` so parents see
  “Adding within 20”, not raw ids.
- **Dashboard / child detail** — cards surface last quiz pass/fail and a practice hint when
  available, so reports are discoverable without opening P17 first.
- **Child-safe analytics** — `AppOpen` on cold start, `RoleSelected` before role is persisted
  (so the child gate does not drop it), `CrashReporter.setDeviceRole`, and parent-only
  `ReportsViewed`. Allowlist still blocks non-child-safe events on child devices.
- **Tunables** — `AppConfig.REPORTS_*` owns 7/30/90-day windows and attempt ceilings.

**Verification:** `ReportsAggregatorTest`, AdaptiveQuizEngine weak-concept title test,
`AppConfigTest` reports windows; full `./gradlew testDebugUnitTest` + `:app:assembleDebug`
green.

## Phase 9 — Security hardening

Status: **done** (rules hardened, delete-family E2E wipe, PIN lockout persistence + hash
sync, App Check ready-to-enforce, claims/logging hygiene). Console App Check **enforce**
remains a manual flip after release SHA-256 is registered.

- **Firestore rules** — closed member self-enrollment (owner-only); revoked child devices
  cannot write usage/attempts/skill; child device updates are field-allowlisted and cannot
  clear `revoked`; Admin-only rate collections for pairing mint / family delete.
- **Storage rules** — quiz media readable only by `child_device` tokens (writes still denied).
- **Delete family** — pushes `family_deleted` FCM before wipe; deletes pairing codes +
  `dev_*` Auth users; rate-limited (1/hour/owner); child wipes Room via
  `ChildLocalDataWiper` (keeps builtin quiz bank) then clears pairing/PIN gate.
- **PIN** — lockout persisted in Keystore-backed `PinGateStore`; parent PIN reset triggers
  `onParentPinChanged` → `pin_sync` FCM → policy pull refreshes `parentPinHash` on device.
- **App Check** — client installs Play Integrity (release) / debug provider (debug);
  callables read `APP_CHECK_ENFORCE` env (default off). Enable console enforce + set env
  after release keystore SHA-256 is registered (Phase 12).
- **Claims / logging** — `AuthUser` surfaces `familyId`/`childId`/`deviceId` from custom
  claims; `LogSanitizer` covers pairing deep links and more secret keys; pairing mint rate
  limited (10/10 min/parent).

**Verification:** `PinGateStoreTest`, LogSanitizer + FCM handler tests; Functions `tsc`
build; full `./gradlew testDebugUnitTest` + `:app:assembleDebug` green.

**Still manual (ops):** register release SHA-256 → Firebase App Check enforce (Firestore,
Functions, Storage) → set Functions `APP_CHECK_ENFORCE=true` →
`firebase deploy --only functions,firestore:rules,storage`.

## Phase 10 — Performance

Status: **done** (hot-path hardening; measured macrobenchmark journeys deferred to Phase 11).

- **Home Compose:** `@Immutable` tiles / UI models, `LazyVerticalGrid` + stable keys, Room-backed
  installed-package set (no per-tick `PackageManager` probes), icon LRU at fixed 128px decode size.
- **Session / Room:** `SessionPersistPolicy` throttles in-block flushes to
  `AppConfig.SESSION_PERSIST_INTERVAL_SECONDS` (45s); phase / app / day / minute changes still flush
  immediately. Usage minute deltas remain ~1/min.
- **Listeners:** removed unused Firestore snapshot APIs on `ChildRemotePolicyClient` (policy sync is
  FCM + WorkManager pull only). Parent dashboard debounces children-listener bursts
  (`DASHBOARD_CHILDREN_DEBOUNCE_MS`) before N parallel one-shot card reads.
- **Startup:** `baseline-prof.txt` + `profileinstaller`; R8 full mode already on. Defer sync /
  inventory / heartbeat until after the first Home frame (`yield()` then `deviceLifecycle.start()`).
- **Battery:** no foreground / always-on services; WorkManager + FCM data-only remain the only
  background sync paths.
- **Verify:** unit guards for persist policy + AppConfig intervals; `testDebugUnitTest` +
  `assembleDebug` / `assembleRelease`.

## Phase 11 — Testing

- Session engine + fail lock (highest coverage)
- Auth, pairing, rules emulator tests
- UI tests for parent dashboard and child Home
- Multi-API (26, 29, 34, 36) and OEM notes

## Phase 12 — Production release

- Release signing, Play App Signing
- Crashlytics + release mapping
- Play Families / Designed for Families
- Privacy policy matching `docs/03`
- Store listing, final QA checklist

## Explicitly out of v1

Live GPS, web filter / DNS VPN, child social, ads, storing child photos/contacts, on-device LLM.
