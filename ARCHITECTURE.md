# Architecture

MeritScreen Android uses **Clean Architecture** inside a **feature-based Gradle modularization**. The `app` module is a thin shell. Business rules live in `core` and `features`. UI does not talk to Firebase.

## Layers

```
Compose UI  →  ViewModel (StateFlow<UiState<T>>)  →  Use cases  →  Repositories
                                                              ↳ Room / DataStore / Keystore
                                                              ↳ Firebase wrappers (off the Home path)
```

**Home and quiz rendering must read only Room + PackageManager.** Firestore, FCM, and AI pack generation are background work (WorkManager / Cloud Functions). Do not add Firestore listeners to `HomeViewModel`.

## Modules

| Module | Responsibility |
| --- | --- |
| `:app` | `Application`, `MainActivity` (LAUNCHER + HOME), splash, role router |
| `:core:common` | `AppConfig`, `UiState`, `AppError`, `Outcome`, dispatchers, session role |
| `:core:ui` | Material 3 theme (light/dark), spacing, loading/error/empty |
| `:core:network` | `NetworkMonitor` |
| `:core:database` | Room (`MeritScreenDatabase`, v5: policy/apps/usage/quiz-attempt/skill+weak-concepts/sync-state) |
| `:core:firebase` | Firebase SDK behind interfaces, incl. `MeritScreenMessagingService` (FCM dispatcher) and `PushTokenProvider` |
| `:core:security` | PBKDF2 PIN hash, Keystore AES-GCM storage |
| `:core:analytics` | Crashlytics + child-safe event allowlist |
| `:core:testing` | Fakes |
| `:features:launcher` | Home-role request/detection (`HomeRoleManager`), setup screen, best-effort screen pinning (`LockTaskGuard`) |
| `:features:applications` | Installed-app inventory (`PackageManager` + Room cache), icon cache, package-change monitor, inventory upload |
| `:features:devices` | Device heartbeat/revocation + expedited revoke check, push-token registration (all WorkManager), boot receiver — no UI |
| `:features:*` | One feature per module; navigation graph extension per feature |

## State

Every screen uses `UiState` (`Loading`, `Empty`, `Success`, `Error`). `Error` carries `AppError` with a **user-facing** message. Map Firebase exceptions in `core/firebase` before they reach Compose.

## Navigation

A single Activity hosts Compose Navigation. `RoleGate` keys the `NavHost` on `DeviceRole`:

- `Unassigned` → `:features:onboarding`'s role select (S01), then Welcome/legal/parent
  first-run (P01, S02, P05–P07), then `:features:authentication` (sign-in/up, forgot
  password, parent pairing P08, child pairing C01) — all still `Unassigned`, so the host is
  not re-keyed mid-flow
- `Parent` → `:features:parent` nested graph (`ParentRoute` → dashboard P11–P12, child
  detail, allowlist/time/quiz/rewards/reports, account/notifications/delete). Pairing from
  parent pops back into the same host (does not re-key `DeviceRole`).
- `Child` → `:features:child` nested graph (`ChildRoute` → Home C05, quiz C07b–C11,
  fail lock C12, PIN C14, parent menu C15). After pairing, role becomes Child. Home can push
  into `:features:launcher`'s `LauncherSetupRoute` (dismissible banner, not forced) without
  re-keying `DeviceRole`.

Cold start: `AppViewModel` reconciles Firebase Auth, `ParentSession`, and
`ChildPairingStore` before emitting a role so splash stays up until the session is coherent.

**Shared-device multi-child (Android):** One handset may hold up to
`AppConfig.MAX_CHILDREN_PER_PARENT` paired profiles in a Keystore
`ChildPairingRegistry`. Parent PIN unlocks Parent menu → **Switch child profile**;
`SwitchActiveChildUseCase` flushes the outgoing session, remints Auth via callable
`activateChildOnDevice`, reloads per-`childId` Room `session_state`, and restarts
sync/heartbeat. Adding another child uses the same pairing code flow (PIN-gated)
without clearing siblings. iOS/tablet parity can reuse the same registry + remint contract.

Feature modules expose `NavGraphBuilder` extensions and `@Serializable` routes.

## Parent control data

`ParentControlStore` (`:core:firebase`) is the parent-only Firestore surface for family meta,
children, `policy/current`, `appRules`, one-shot `usageDays` / `devices`, and `deleteFamily`.
`ChildPolicyMapper` in `:core:common` owns the policy field map; fail lock scope is always
written as `all_non_emergency`. Dashboard uses a single children snapshot listener (debounced)
plus parallel one-shot reads for card fields (never on the child Home path).

## Child runtime (Phase 5)

- **Room** holds cached `ChildPolicy`, `AppRule`, profile, session state, quiz bank, and skills.
- **`ChildPolicySyncCoordinator`** (background) pulls Firestore on a schedule and writes Room —
  see "Real-time synchronization (Phase 7)" below for why this is pull-based, not a live
  listener. Home and quiz ViewModels never call Firestore.
- **`SessionEngine`** is pure Kotlin: `idle → in_block → quiz_due → granted | shielded`.
  Fail is always device-wide; emergency packages stay launchable. Parent **remote freeze**
  (`policy.paused`) also blocks all non-emergency launches. Block usage accrues from
  `elapsedRealtime` since `blockStarted` (whole minutes via floor of elapsed, not summed 1s
  floats). Home pauses the 1 Hz ticker while covered by another app and catches up on resume;
  tile badges and “Used Xm / remaining today” read live `SessionSnapshot` + clock via StateFlow.
  Policy sync uses FCM `policy_sync` → in-process `refreshNow` while alive + expedited WorkManager.
- **`AdaptiveQuizEngine`** picks/grades from the on-device bank; explainers are pre-written.

## Launcher & device management (Phase 6)

**Deployment model decision:** v1 targets **standard consumer devices** installed from Play,
not factory-provisioned Device Owner/Dedicated Device. Device Owner requires enrollment
*before* any user account exists on the phone (NFC bump, QR at setup wizard, or `adb shell dpm
set-device-owner`), which is incompatible with "parent installs MeritScreen from Play on their
kid's already-set-up phone." Building a Device-Owner-only feature and faking it otherwise would
be exactly the fragile workaround the product brief prohibits. The strongest mechanism that
*is* compatible with a Play-store consumer install is: **default Home (`ROLE_HOME`) + best-effort
Lock Task (screen pinning) + server-enforced device revocation.** This is documented, not
assumed, and is re-evaluated if a future managed/BYOD SKU needs Device Owner.

- **Home role.** `HomeRoleManager` (`:features:launcher`) uses `RoleManager.ROLE_HOME` on API
  29+ via **Activity Result** (`startActivityForResult` / `StartActivityForResult` — plain
  `startActivity` is a silent no-op for role-request intents on many API 29+ images). Fallbacks:
  `Settings.ACTION_HOME_SETTINGS`, then a Home-intent chooser. `MainActivity` declares both
  `LAUNCHER` and `HOME` intent filters. `DefaultHomeChecker` (`:core:common`) is a cheap,
  synchronous check (`RoleManager.isRoleHeld` / `PackageManager` resolve) called on Home
  `onResume` and by the heartbeat worker — never a background poll.
- **Lock Task (screen pinning).** `LockTaskGuard` calls the public `Activity.startLockTask()` /
  `stopLockTask()` APIs only while Child Home is in fail-lock (`Shielded` or parent `paused`).
  Pinning is owned by `ChildHomeScreen` (not `FailLockPane`): engage when fail-lock applies,
  `ensureReleased` on playground / daily-cap, on every Home resume outside fail-lock, and via
  `prepareExternalLaunch` immediately before any `startActivity` to another package (approved
  apps, Phone, Camera, emergency). Residual pin after cooldown — common on Samsung/One UI —
  otherwise blocks launches with the system “unpin this app” toast. Without Device Owner, this
  is standard *screen pinning*: the user can always exit via long-press Back+Overview. It is a
  UX deterrent on top of the real control (device-wide app block in `SessionEngine`), not a hard
  security boundary — documented as such in the fail-lock UI copy.
- **Installed-app inventory.** `InstalledAppsRepository` (`:features:applications`) queries
  `PackageManager` for `ACTION_MAIN`/`CATEGORY_LAUNCHER` activities once (app start / explicit
  refresh) and caches to Room (`installed_app`). Query flags stay at `0` /
  `ResolveInfoFlags.of(0)` — not `MATCH_ALL`, which can return an empty set under API 30+
  package visibility even with the LAUNCHER `<queries>` declaration. A dynamically-registered
  `PackageChangeMonitor` listens for `PACKAGE_ADDED`/`REMOVED`/`REPLACED` and triggers one
  debounced re-scan — no polling, no manifest-registered receiver, no `QUERY_ALL_PACKAGES`.
  `AppIconLoader` is an in-memory LRU (`LruCache`) so Compose lists never re-decode a
  `Drawable` per frame. `ChildDeviceLifecycleCoordinator` retries the post-pair upload with
  short backoff on cold start / Home resume until Firestore accepts it.
- **Inventory upload.** `InstalledAppsSyncCoordinator` uploads `packageName` + `label` plus an
  optional tiny WEBP thumbnail (`iconBase64`/`iconHash`, size-budgeted in `AppConfig`) to this
  device's Firestore doc, gated on: paired, online, and `stableInventoryHash()` (package+label+
  versionCode) changed since the last upload (Room `device_runtime_state`). Parent Allowlist and
  onboarding App Rules use a **screen-scoped** `observeInstalledApps` listener (never on child
  Home) and render icons with Coil (`SyncedAppIcon`, memory+disk cache). Install/uninstall on
  the child invalidates local icon LRU and re-syncs so the parent list updates live.
- **Device heartbeat & revocation.** `DeviceHeartbeatWorker` (`:features:devices`, WorkManager,
  ~30 min periodic, network-constrained, `ExistingPeriodicWorkPolicy.KEEP`) writes `lastSeenAt`
  + Home-role status and reads back `revoked` from Firestore in the same round trip. If revoked,
  it calls `DeviceRevocationHandler` — an interface that `:features:child` implements against
  `UnpairChildDeviceUseCase`, so `:features:devices` never depends on `:features:child` — which
  clears the local pairing credential and forces the child back to the pairing screen. This is
  the "parent hits Revoke, device locks itself out" flow, and it is enforced by
  `firestore.rules` (a device can only heartbeat/update its **own** `devices/{deviceId}`), not
  just client trust. `BootCompletedReceiver` re-arms the WorkManager schedule after reboot.
- **Screen-time integration.** The launcher and Home screen never duplicate `SessionEngine`
  logic; they only read its `SessionPhase` (`Idle`/`InBlock`/`QuizDue`/`Shielded`) plus the
  cached policy from Room. No launcher-side timer, no second source of truth.
- **Quiz interrupt vs Picture-in-Picture.** `ChildTimeLimitOverlayActivity` hosts quiz Compose
  in `InterruptSurfaceController` (`TYPE_APPLICATION_OVERLAY`) when Display-over-apps is
  granted, so pinned PiP tasks (YouTube, etc.) cannot float above the quiz. Audio focus pauses
  underlying media. There is no public API for a Play-installed app to *dismiss* another
  package’s PiP; covering + pausing is the honest consumer approach (no Accessibility).

## Real-time synchronization (Phase 7)

**Firebase is the remote source of truth; nothing in the UI layer talks to it directly.** Every
screen reads Room/DataStore through a repository. A synchronization layer (schedulers +
coordinators + workers, all in the relevant feature module) is the only thing that touches
Firestore/FCM on the child device, and it always writes its result into Room before any
ViewModel sees it.

```
Firestore (source of truth)
   ↕ (push wakes, pull fetches — never a Home-path listener)
Sync layer: FCM handler → WorkManager scheduler → Worker → Coordinator
   ↕
Room (fast, resilient local state; dirty-flagged rows queue outbound work)
   ↕
Domain (SessionEngine, AdaptiveQuizEngine, repositories) — pure Kotlin, no I/O awareness
   ↕
ViewModel → Compose UI (`UiState`, loading/error/empty)
```

### Why push-triggered pull, not continuous listeners

Phase 5/6 used a live Firestore snapshot listener for policy. That does not scale: every child
device holds an open socket indefinitely, costs a read on every write regardless of whether the
device is awake, and drains battery keeping the connection alive. Phase 7 replaces it with:

1. **FCM data-only message** (`type: policy_sync` or `device_revoked`) wakes the device only when
   something actually changed. No `notification` payload — the app decides what to do, never the
   system tray.
2. **A short-lived `WorkManager` job** (`PolicySyncWorker` / `UsageSyncWorker` /
   `PushTokenRegistrationWorker`) does the actual network round trip, off the main thread, with
   OS-managed retry/backoff. `Result.retry()` up to a bounded attempt count, then gives up
   quietly rather than looping forever.
3. **Periodic fallback jobs** (`PolicySyncScheduler` 6 h, `UsageSyncScheduler` 2 h,
   `DeviceHeartbeatWorker` 30 min — all `ExistingPeriodicWorkPolicy.KEEP`, network-constrained)
   cover the case where FCM delivery is dropped, delayed, or the device was fully offline —
   FCM/Cloud Messaging is explicitly best-effort, not guaranteed delivery.
4. **Network-reconnect trigger**: `ChildPolicySyncCoordinator` observes `NetworkMonitor` and
   fires one expedited pull the moment the device comes back online, instead of waiting for the
   next periodic window.

Net effect: zero idle Firestore connections on the child device, bursty instead of continuous
reads, and the same end-to-end latency as a listener in the common case (push arrives, worker
runs in seconds) while degrading gracefully (periodic/foreground fallback) when push fails.

### Data ownership and direction

| Data | Owner / writer | Direction | Notes |
| --- | --- | --- | --- |
| `policy/current`, `appRules/*` | Parent (`ParentControlStore`) | Parent → Firestore → FCM `policy_sync` → child pull → Room | Last parent write wins; child never writes policy |
| `devices/{deviceId}` (`revoked`, `lastSeenAt`, `fcmToken`, `model`, `batteryPercent`, `osVersion`, `appVersion`, `launcherDefault`) | Parent revokes / restores; child heartbeats & registers token | Bidirectional, scoped by `firestore.rules` to the device's own doc | `onDeviceRevoked` Function fires `device_revoked` FCM as a fast path on top of the heartbeat poll. Parent Devices screen observes live docs (screen-scoped) |
| `usageDays/{day}` | Child only | Child Room (`UsageDao`, dirty-flagged) → `UsageSyncCoordinator` → Firestore | Absolute per-day-per-app minute totals overwrite on each upload — idempotent, no double-counting on WorkManager retry |
| `quizAttempts/{attemptId}` | Child only, append-only | Child Room (`QuizAttemptDao`) → Firestore | Deterministic `attemptId`; upload checks `exists()` first so a retried write is a no-op, never a duplicate or an update (rules forbid update/delete) |
| `skillState/current` | Child only | Child Room (`SkillStateEntity`, dirty-flagged) → Firestore | Full-map replace (`SetOptions.merge()` on the parent doc, replace on the topics map) — simpler than field-level diffing at this data size |

### Sync layer building blocks

- **`core/common/messaging/FcmContracts.kt`** — `FcmMessageHandler` / `FcmTokenRegistrar`
  interfaces, Hilt-multibound (`@IntoSet`). `:core:firebase` depends on neither `:features:child`
  nor `:features:devices`; feature modules bind their own handlers into the set instead, so the
  dispatcher stays a thin, feature-agnostic router.
- **`core/firebase/messaging/MeritScreenMessagingService`** — the only `FirebaseMessagingService`
  in the app. Fans a data message out to every bound `FcmMessageHandler` (first one that returns
  `true` "owns" that message type) and every token refresh to every `FcmTokenRegistrar`. No
  business logic lives here.
- **Per-feature schedulers** (`PolicySyncScheduler`, `UsageSyncScheduler`,
  `DeviceHeartbeatScheduler`, `PushTokenRegistrationScheduler`) own their own `WorkManager` unique
  work names, constraints (`NetworkType.CONNECTED`), and backoff — a coordinator never calls
  `WorkManager` directly, and a ViewModel never calls a scheduler for anything except "user asked
  me to refresh now."
- **Coordinators** (`ChildPolicySyncCoordinator`, `UsageSyncCoordinator`) contain the actual
  fetch-and-merge-into-Room or read-dirty-and-upload logic and are unit-testable in isolation from
  WorkManager (fakes for `ChildPairingStore`, `NetworkMonitor`, the Firestore client interfaces).
- **`PolicySyncStateEntity`** (Room) records `lastAttemptAtEpochMs` / `lastSuccessAtEpochMs` /
  `consecutiveFailures` per child so the parent-menu UI can show an honest "synced 12m ago" /
  "sync failing" hint instead of guessing.

### Offline & recovery

- Home and quiz screens read only what is already in Room; there is no code path where they block
  on, or fall back to, a network call. Losing connectivity never blackscreens the child.
- The last successfully-synced policy stays in force while offline — restrictions do not silently
  loosen because the network dropped.
- Usage/quiz/skill rows are dirty-flagged in Room the moment they are produced, independent of
  connectivity, and are picked up by the next successful sync (push-triggered, periodic, or
  reconnect-triggered) — nothing is lost, only delayed.
- All outbound writes are safe to retry: usage-day totals are absolute overwrites, quiz attempts
  check existence before writing, skill-state is a full replace. A `WorkManager` retry after a
  process death or a flaky network never double-applies anything.

### Firebase Cloud Functions (push triggers)

Three Firestore-triggered functions (`functions/src/index.ts`) turn parent writes into pushes,
so the mobile client never has to poll to discover a change quickly:

- `onPolicyChanged` (`policy/{policyId}` write) → `policy_sync` FCM to the child's devices.
- `onAppRuleChanged` (`appRules/{appId}` write) → `policy_sync` FCM to the child's devices.
- `onDeviceRevoked` (`devices/{deviceId}` update, `revoked` false→true) → `device_revoked` FCM to
  that one device.

All three call a shared `pushToChildDevices` helper that sends a **data-only, high-priority**
multicast (`sendEachForMulticast`) and prunes any token Firebase reports as
unregistered/invalid, so `devices/{deviceId}.fcmToken` never accumulates dead tokens. Push is
always a wake-up hint, not a payload — the client re-fetches from Firestore itself, so a missed
or duplicated push is harmless.

## Usage & analytics (Phase 8)

Parent reports (P17) never open a Firestore listener. `ReportsViewModel` fans out one-shot
reads (usage days, quiz attempts, skill state, app rules, installed-app labels, policy), then
`ReportsAggregator` (`:core:common`) produces a parent-ready snapshot for **Today / 7 / 30 days**:

- Calendar daily average (`total ÷ window days`) and half-window trend (null when insufficient data)
- Continuous daily bar chart (missing days filled with zero); tap a day to filter app allocation
- Per-app rollup with allowlist / inventory labels; unattributed minutes surface as “Other”
- Educational share via `AppInventoryCategorizer` (no mock app lists when usage is empty)
- Focus sessions from `quizAttempts` (pass rate, extra minutes earned, recent attempts)
- AI learning summary from `skillState` (topic levels, tier, mastered / weak concepts)
- Cooldown count = failed quizzes in window; rest minutes from `policy.defaultCooldownMinutes`

Dashboard / child-detail cards surface last-quiz + one practice hint so parents see signal
without opening reports. Analytics stays on the allowlist: `AppOpen`, `RoleSelected`,
`ReportsViewed` (parent-only), plus existing pairing / quiz / sync events. Child Home still
fires no analytics beyond the allowlisted child-safe set.

## Dependency injection

Hilt. `@HiltAndroidApp` is only on `MeritScreenApplication`. Feature and core modules contribute `@Module` / `@HiltViewModel`.

## Design system

Tokens live in `:core:ui` (`MeritScreenTheme`, `MeritSpacing`, `MeritTypography`, `MeritShapes`). Do not hard-code colors in screens. Light and dark schemes are designed separately (warm paper vs night teal), not inverted copies.

Typography is **Inter** (primary UI) and **Roboto Mono** (PIN / OTP / pairing codes), bundled as latin-subset TTFs under `core/ui/src/main/res/font` — no network font fetch. Every screen under `MeritScreenTheme` inherits Inter via Material 3 roles; use `MeritPinDigitStyle` / `MeritCodeLabelStyle` for security digits.

## Configuration

`AppConfig.MAX_CHILDREN_PER_PARENT` (currently 5) is the single source of truth for the child cap. Future Remote Config can feed this object; call sites stay unchanged.

## Session engine

Kotlin owns a local copy of the policy state machine from [docs/07-app-blocks-and-fail-lock.md](docs/07-app-blocks-and-fail-lock.md):

`idle → in_block → quiz_due → granted | shielded`

Implemented in `:features:child` (`SessionEngine` + `ChildSessionController`) with Room persistence.
A failed quiz is **always device-wide** (all non-emergency apps). Never implement a per-app-only fail.

## Android platform limitations

Documented so we do not claim enforcement Android does not allow:

| Topic | What we do | What we do not do |
| --- | --- | --- |
| Default Home | `HOME` / `DEFAULT` intent; `ROLE_HOME` on API 29+ | Accessibility or Device Admin to trap the user |
| API 26–28 | Legacy Home chooser | `RoleManager.ROLE_HOME` (API 29+) |
| Uninstall | Detect if we are no longer default Home; notify parent | Secretly block uninstall without Device Owner / MDM |
| Recents / other launchers | Best-effort with default Home + disabled icons | Guarantee if the user changes the default Home |
| OEM differences | Test on Pixel + major OEMs; document gaps | Claim universal lock-down |
| Screen pinning | `startLockTask()`/`stopLockTask()` during `Shielded` as a UX deterrent | Claim it is unexitable without Device Owner |
| Device Owner / Dedicated Device | Documented as a future managed-device path if ever needed | Ship v1 assuming factory-time enrollment on a consumer Play install |
| App hiding | Rely on the child Home grid simply not rendering unapproved apps | `PackageManager.setApplicationEnabledSetting` on apps we do not own, or hidden APIs |
| Device revocation | Server-enforced via `firestore.rules`; `onDeviceRevoked` pushes FCM using the **pre-revoke** `fcmToken` (parent often deletes the token in the same write). Child also watches its own `devices/{deviceId}` snapshot from `ChildDeviceLifecycleCoordinator` (not Home ViewModel) and unpairs immediately; heartbeat WorkManager remains the offline fallback | Treating FCM delivery as guaranteed without a token-before-delete path |
| Background | WorkManager (periodic + expedited one-time) woken by FCM data messages | Persistent foreground services for timers/sync, or continuous Firestore listeners on the child device |
| AI | Cloud Functions generate packs asynchronously | On-device LLM or live model calls on Home / quiz |

Consumer v1 is **PIN + default Home + parent alert**, matching the product spec. Device Owner / lock-task is an optional managed-device path, not the Play-store consumer default.

## Desktop clients (Windows, macOS, Linux)

Desktop is a **separate native client family** that shares this Firebase project and the same policy JSON / session transitions (not Android code). Stack: Rust Guardian + Session Agent + Tauri 2 UI under `desktop/`. Child hot path stays local-DB-only; fail lock remains device-wide. Guardian starts at boot; UI is ephemeral. Full production guide and phases: [docs/15](docs/15-desktop-native-production.md), [docs/16](docs/16-desktop-build-phases.md). Phase d0 product lock: [docs/17](docs/17-desktop-d0-product-lock.md).

## Security hardening (Phase 9)

Authz lives in **Firestore rules + Cloud Functions**, not the UI. Highlights:

- Owner-only family membership; revoked child devices cannot write telemetry or clear `revoked`.
- `deleteFamily` is rate-limited, pushes `family_deleted`, then wipes Firestore + Auth users; the
  child clears Room via `ChildLocalDataWiper`.
- Parent PIN lockout is Keystore-persisted; PIN resets sync via `pin_sync` FCM + policy pull.
- App Check providers are installed; **enforce** flips with release SHA registration +
  `APP_CHECK_ENFORCE=true` (see SECURITY.md).

## Performance budget (child)

From the product spec — treat as requirements:

- Home first paint: ~300 ms warm / ~800 ms cold on mid-range
- No network on the Home path
- Session / usage Room flushes throttled (45 s steady-state); phase changes flush immediately
- Starter baseline profile + ProfileInstaller; measured journeys in Phase 11
- No always-on / foreground sync services — WorkManager + FCM only
- R8 full mode for release