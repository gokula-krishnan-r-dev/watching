# Security

Authorization is enforced in **Firebase Security Rules** and Cloud Functions. Android UI is not a security boundary.

## Roles

| Actor | Auth | May access |
| --- | --- | --- |
| Parent | Email / Google | Own `users/{uid}`, own family, own children |
| Child device | Custom token with `role=child_device`, `familyId`, `childId`, `deviceId` | Own child policy/packs (read), own usage/attempts/skill (write) while **not revoked** |
| Parent-on-child-device | Local PIN only | Launcher settings on that device. No cloud parent data |

A child must never read another child by guessing IDs. Rules compare token claims to path `familyId` / `childId`.

Family membership is **owner-granted only** — a signed-in parent cannot self-join an arbitrary `families/{id}/members/{uid}`.

## Secrets and PIN

- Pairing secrets live in Android Keystore via `SecureStorage` (AES-GCM).
- Parent PIN is hashed with PBKDF2-HMAC-SHA256 (120k iterations, random salt). Never store or log the PIN.
- After `AppConfig.PARENT_PIN_MAX_ATTEMPTS` failures, lock for `PARENT_PIN_LOCKOUT_MINUTES`. Lockout is persisted in Keystore-backed storage so process death does not reset the counter.
- Forgot PIN: parent resets from **their** phone; `onParentPinChanged` FCM wakes child devices; policy sync refreshes `parentPinHash` in the local pairing credential.

## Device revocation

- Parent sets `devices/{deviceId}.revoked = true`.
- Rules block revoked devices from usage/quiz/skill writes and from mutating their own `revoked` flag.
- FCM `device_revoked` + heartbeat poll unpair the device locally.

## Logging

`LogSanitizer` / `ReleaseTree` redact emails, JWTs, pairing deep links, and long tokens. Do not log auth tokens, PINs, or pairing codes.

## Local data

- `allowBackup` is false. Backup/extraction rules exclude prefs, DBs, and files so a PIN hash and pairing secret are not copied off-device casually.
- Room holds policy and timers so restrictions still apply offline. Cached policy must not be weaker than the last server policy; if a decrypt/integrity check fails, fail closed to the lock screen, not open to the stock drawer.
- Unpair / family delete wipes child Room tables (policy, usage, session, skills, inventory) but keeps the builtin quiz bank.

## App Check

Debug builds use the debug provider (via reflection so release does not depend on it). Release uses Play Integrity.

**Enforcement is gated** until a release keystore SHA-256 is registered:

1. Register the release SHA-256 in Firebase Console → App Check.
2. Turn on enforce for Firestore, Cloud Functions, and Storage in the console.
3. Set Functions env `APP_CHECK_ENFORCE=true` and redeploy Functions.

Until then, providers are installed in **monitor** mode (`enforceAppCheck` defaults to false).

### Cloud debug demos

Production Auth/Firestore from a debug APK still needs a **registered debug token** once the App Check API is enabled (otherwise the SDK logs exchange failures). Firebase AI Logic also enforces App Check — an unregistered / random debug secret causes `QuizPackGenerationWorker` to fail closed to the builtin bank:

1. `make dev` / `make up-dev` (or `./scripts/run-demo.sh` / `./scripts/run-dual-demo.sh`) write `appCheckDebugToken` to gitignored `local.properties`, register it via the App Check API when the Firebase CLI is logged in (pruning older MeritScreen debug tokens if the 20-token cap is hit), and pin it with `adb shell setprop debug.firebase.appcheck.app_check_token …`.
2. `:app` copies that value into `BuildConfig.APP_CHECK_DEBUG_TOKEN`; on startup the debug build seeds Firebase's App Check SharedPreferences store and registers `InternalDebugSecretProvider` so emulators (which block `SystemProperties.set`) still exchange the same UUID.
3. Manual registration: Firebase Console → App Check → your Android app → Manage debug tokens. Keep `firebase login` current so demos can auto-register.

Do not commit debug tokens. Do not enable App Check enforcement until Play Integrity works for release builds.

### Closed-tester (`internal`) APKs

Sideloaded builds are not covered reliably by Play Integrity. The `internal` build type is
minified + release-signed against production Firebase, but uses the **debug App Check
provider** with a registered `appCheckDebugToken` so Firebase AI Logic and callables work
for a closed tester group. Build with:

```bash
./gradlew :app:assembleInternal
```

Do not publish `internal` APKs to Play Store; use `assembleRelease` (Play Integrity) for store builds.

## Permissions

Keep the child permission list short: default Home, internet, optional usage stats, optional camera for QR, optional notifications. Do not request location, contacts, SMS, or microphone.

## Unsupported mechanisms (do not implement)

- AccessibilityService used to trap or block navigation
- Device Admin used to prevent uninstall in the consumer app
- Private / hidden APIs
- Silently blocking uninstall or hiding Settings without Device Owner
- Desktop: kernel drivers, rootkits, keyloggers, other-app screen capture, fake MDM uninstall locks on consumer SKUs

If a requirement cannot be enforced on a given API level or OEM, document the gap in the parent “device health” UI instead of faking it.

## Desktop (Windows / macOS)

Desktop shares this threat model and the same Firebase project. Process split: **Guardian** (boot service) owns SQLCipher + session/sync; **Session Agent** tracks foreground/idle; **Tauri UI** is ephemeral and never talks to Firebase directly. See [docs/15](docs/15-desktop-native-production.md) and [docs/17](docs/17-desktop-d0-product-lock.md).

| Topic | Rule |
| --- | --- |
| App Check | No official desktop attestation provider yet. Mitigate with rate limits, device-bound pairing tokens, revoke, and monitor mode. Custom attestation spike: [desktop/docs/app-check-attestation-spike.md](desktop/docs/app-check-attestation-spike.md) — do not block Win/Mac v1 on it. |
| Secrets | DPAPI (Windows) / Keychain (macOS) for DB key, refresh material, pairing credential, IPC HMAC key. Never log PIN, tokens, emails, or pairing codes (`sanitize_for_log` on tracing). |
| Uninstall | PIN-gated uninstall helper where we own the UX; detect service removal → `tamperFlags.uninstallAttempt` + parent alert. Require standard (non-admin) child account. Do not claim silent uninstall block without MDM. |
| Reboot | Guardian must start at boot; fail-lock / quiz_due persist in SQLCipher across power cycles. |
| IPC | Peer credentials **and** HMAC-SHA256 on every frame (d9); spoof without MAC fails closed. UI crash must not clear `quiz_due` / `shielded`. |
| Updater | Ed25519-signed manifest; binary SHA-256; rollback floor (`minVersion`); staged rings `internal` → `pilot` → `stable` with `rolloutPercent`. Health-gated rollout — never leave child without a Guardian. |

### Desktop threat model (d9)

| Threat | Residual risk | Mitigation (honest) |
| --- | --- | --- |
| **Admin / root child account** | Child can stop services, change shell, uninstall | Detect → `tamperFlags.adminAccount` + parent copy; enforce **standard** child account in setup checklist — do not claim unbreakable lockdown |
| **Guardian service stopped / disabled** | Enforcement pauses until restart | Boot service + respawn; `tamperFlags.serviceStopped`; last local policy stays fail-closed (does not loosen) |
| **SQLCipher DB key theft** | Offline policy/session readable if OS secret store + disk are both compromised | Key in DPAPI/Keychain (or lab file store); no key in logs; wrong key fails closed; physical+admin access is out of consumer threat model |
| **IPC spoofing** (local malware talking to Guardian) | Without auth, any local process could raise lock / bind pairing | Unix peer UID (+ root→user Agent); Windows PID + MeritScreen image path check; **HMAC** on frames with machine-local key; unauthorized / bad MAC → drop |
| **Uninstall without parent** | Consumer OS cannot silently block ARP/pkg remove without MDM | PIN-gated helper we ship; else detect → `uninstallAttempt` + alert; standard account raises the bar |
| **Broken / rolled-back updater** | Bad binary bricks Guardian | Ed25519 verify; refuse versions below accepted floor; staged channel + manual recovery (reinstall pkg/MSI) |
| **Clock rollback** | Skip cooldowns / quiz due | Monotonic active-use clock; large wall rollback → shield + `clockRollback` |
| **No App Check on desktop REST** | Stolen child token abuse | Rate limits on Functions; device-bound pairing; revoke; monitor — see spike doc |

**Security checklist (d9 sign-off):** IPC spoof test fails closed · pairing/OTP not logged · uninstall PIN path documented · updater dry-run verifies signature + rollback · App Check residual risk accepted for v1.

## Pairing tokens

`createPairingToken` / `consumePairingToken` are Cloud Functions. The Functions runtime
service account must be able to mint custom tokens (`roles/iam.serviceAccountTokenCreator`
on the compute / App Engine default SA). `consumePairingToken` creates the custom token
**before** marking the code used or writing `devices/{deviceId}`, and can remint for the
same device if a prior attempt left a half-paired device doc.

## Delete family

Parent-initiated delete (`deleteFamily` callable, owner-only, rate-limited):

1. FCM `family_deleted` to paired devices (wake → local Room wipe + unpair).
2. Delete pairing codes, child Auth users (`dev_*`), and the full Firestore family tree.
3. Clear `users/{uid}.familyId` for members.
