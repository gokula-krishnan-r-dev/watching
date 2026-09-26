# Firebase architecture

Project ID: **`managing-screen-time`**

Android application ID: **`com.meritscreen.app`**

## Services

| Service | v1 use |
| --- | --- |
| Authentication | Parents: email/password + Google. Child devices: custom token minted at pairing |
| Cloud Firestore | Families, children, policy, usage, quiz attempts, skill state, quiz packs |
| Cloud Functions | Pairing token mint/consume (rate-limited), AI quiz pack generation, delete-family (rate-limited, Auth+pairing cleanup), FCM push on policy/device/PIN/family-delete |
| Cloud Messaging | Data-only wake-ups: `policy_sync`, `device_revoked`, `pin_sync`, `family_deleted`. Parent **display** alerts + **Super Admin campaign / release announcements** (audience-targeted): see [docs/11-firebase-push-notifications.md](docs/11-firebase-push-notifications.md) |
| Storage | Optional quiz media (audio/images for ages 3–6) |
| Crashlytics | Crashes, no PII |
| Remote Config | Later: `MAX_CHILDREN_PER_PARENT` and similar tunables |
| App Check | Play Integrity in release; debug provider in debug. Enforce after signing keys exist |
| Analytics | Optional, child-safe allowlist only |

## Auth model

- **Parent:** Firebase Auth user. `users/{uid}` holds `familyId`. Membership is `families/{familyId}/members/{uid}`.
- **Child device:** No email. Cloud Function verifies a one-time pairing code and mints a custom token with claims `{ role: "child_device", familyId, childId, deviceId }`. The child SDK signs in with that token.
- Parent password is **never** stored on the child phone.
- Parent PIN is a **hash** (PBKDF2) on the family/child docs and verified locally via Keystore-backed storage.

## Firestore layout

```
users/{uid}
families/{familyId}
  ownerUid
  members/{uid}
  children/{childId}          # profile: name, ageBand, language, avatarId
    policy/current            # quizMode, ceilings, fail lock, rewards
    appRules/{appId}          # package, blockMinutes, grantOnPass, cooldown, emergency
    devices/{deviceId}        # platform, launcherDefault, lastSeenAt, revoked, fcmToken
    usageDays/{yyyy-mm-dd}    # minutesUsed, minutesByApp
    quizAttempts/{attemptId}  # append-only
    skillState/current        # topics.{topic}: level, streak, weak, weakConcepts[], weakConceptTitles{}
    quizPacks/{packId}        # written only by Functions
```

## Data direction

- **Policy:** parent → Firestore → `onPolicyChanged`/`onAppRuleChanged` Cloud Function → data-only
  `policy_sync` FCM → child `PolicySyncScheduler` (expedited `WorkManager` job) → Firestore pull
  → Room. A 6-hour periodic `WorkManager` job and a pull-on-reconnect are the fallback if the
  push never arrives (FCM is best-effort). Last parent write wins. Offline child keeps the last
  cached policy (restrictions stay on) — the child **never** has a live Firestore listener.
- **Usage / attempts / skill:** child Room (dirty-flagged rows) → `UsageSyncCoordinator` /
  `UsageSyncWorker` (2 h periodic + opportunistic run after a quiz) → Firestore. Usage minutes
  are absolute per-day overwrites (idempotent on retry) and are not lowered by the parent.
  Attempts are append-only with a deterministic id so a retried upload is a no-op, not a
  duplicate.
- **Pairing revoke:** parent write → `onDeviceRevoked` Cloud Function sends a `device_revoked`
  FCM as a fast path; the existing 30-minute `DeviceHeartbeatWorker` poll is the guaranteed
  fallback (rules also block the revoked device from any further write once it does check in).
  Offline PIN still works on device.
- **Push tokens:** each device registers/refreshes its own FCM token via
  `PushTokenRegistrationWorker` → `devices/{deviceId}.fcmToken`; Cloud Functions prune any token
  Firebase reports as unregistered/invalid so dead tokens do not accumulate.
  Parent phones will register under `families/{familyId}/parentDevices/{installationId}` (see
  [docs/11](docs/11-firebase-push-notifications.md)). Super Admin campaigns never read or expose
  raw tokens in the SPA — Functions resolve audiences server-side only.

## Super Admin broadcast / campaign push

The ops console ([docs/10 — Super Admin Panel](docs/10-super-admin-panel.md)) must include a
**Notifications / Campaigns** capability so operators can send professional, audited pushes
without touching Firebase Console ad-hoc. This is separate from the family **control plane**
(`policy_sync`, revoke) and from day-to-day **parent supervision** alerts.

| Capability | Requirement |
| --- | --- |
| Audiences | **Parents only**, **children only**, or **both** (resolved to FCM tokens via Admin SDK) |
| Intent levels | **`product`** (new feature / release notes — non-promotional) vs **`promo`** (optional offers, seasonal — clearly labeled, lower urgency channel) |
| Creative | Title, body, optional **image URL** (Firebase Storage or HTTPS CDN; WebP/JPEG; size-capped), optional deep link / in-app route |
| Targeting filters | Optional: age band, region, app version min, family active in last N days — evaluated in Cloud Functions, not by dumping user lists to the browser |
| Delivery | Callable / Admin API → Cloud Function → `sendEachForMulticast` (or topic fan-out once topics exist) with rate limits, dry-run preview, and scheduled send |
| Child safety | Child-targeted campaigns must stay **calm and educational** (feature tips, curriculum updates). No marketing spam, no mid-fail-lock “open app” pressure, no YouTube/promo CTAs on the child tray. Prefer parents for commercial `promo` |
| Privacy | SPA never displays `fcmToken`. Campaign payloads carry `campaignId` + `type` only; Functions attach display copy server-side. Full audit log: who sent what, when, audience counts, success/fail |
| Opt-out | Respect parent `notificationPrefs` (e.g. future `productUpdates` / `promotions` toggles). Child devices ignore `promo` unless an explicit product flag allows educational tips |

Implementation detail and message types (`admin_product`, `admin_promo`) live in
[docs/11 — Firebase push notifications](docs/11-firebase-push-notifications.md). Do not send
campaigns by pasting tokens into the Firebase Console in production.

## Security rules

Create the default Firestore database in the Firebase console if it does not exist (`firebase firestore:databases:create` requires Owner/Editor on the Cloud project). Then `firebase deploy --only firestore:rules,firestore:indexes`.

Enable Email/password and Google sign-in in Authentication → Sign-in method.

See `firestore.rules`. Summary:

- Parents only access families where they are a member.
- Child tokens may read only their `childId` policy/packs and write usage, attempts, and skill state for that child **while the device is not revoked**.
- Cloud Functions that mint Auth custom tokens require the runtime service account to have
  `roles/iam.serviceAccountTokenCreator` on itself (`iam.serviceAccounts.signBlob`). Without
  it, `consumePairingToken` can write a device doc then fail — parent shows paired, child does not.
- Custom claims (`role`, `familyId`, `childId`, `deviceId`) are read only after an `in` guard — bare access to a missing claim fails the whole rule (deny) for normal parent tokens.
- Child device doc updates are field-allowlisted; children cannot clear `revoked`.
- Family membership is owner-only (no self-join of arbitrary families). Bootstrap uses `getAfter` so family + first member + children can commit in one batch.
- Quiz packs are Functions-only writes.
- Quiz attempts cannot be updated or deleted by clients.
- `pairingCodes/{code}`, `consumeRate/{ip}`, `pairingMintRate/{uid}`, `deleteRate/{uid}` are Functions/Admin-only.

## Indexes

`firestore.indexes.json` covers quiz attempt `createdAt` and device `lastSeenAt` field overrides.
Parent usage summaries read `usageDays` with a bounded collection get and sort by ISO day id
client-side (avoids a production `__name__` index requirement). Add composite indexes as real
filtered queries land; do not scan unbounded collections.

## Functions

`functions/src/index.ts` implements:

- **`createPairingToken`** (parent-authenticated) — mints a 10-minute, one-time 6-digit code +
  high-entropy secret; stores a hashed secret in `pairingCodes/{code}`; returns `code`,
  `secret`, `expiresAtEpochMs`, and `qrPayload` (`meritscreen://pair?…`).
- **`consumePairingToken`** (unauthenticated, IP rate-limited) — verifies code (+ optional
  secret), creates/updates `devices/{deviceId}`, mints a custom token
  `dev_{deviceId}` with claims `{ role: "child_device", familyId, childId, deviceId }`, and
  returns `customToken` plus `parentPinHash` for offline PIN.
- **`onPolicyChanged`** / **`onAppRuleChanged`** (Firestore-triggered, `onDocumentWritten` on
  `policy/{policyId}` / `appRules/{appId}`) — push a data-only `policy_sync` FCM to the child's
  paired, non-revoked devices so they pull the change instead of polling for it.
- **`onDeviceRevoked`** (Firestore-triggered, `onDocumentUpdated` on `devices/{deviceId}`, fires
  only on a `revoked` false→true transition) — pushes a `device_revoked` FCM to that one device
  as a fast path on top of its own heartbeat poll.
- **`onParentPinChanged`** (family root `parentPinHash` change) — pushes `pin_sync` so every
  paired child refreshes the offline PIN hash.
- **`deleteFamily`** (owner-authenticated, rate-limited) — FCM `family_deleted`, then recursive
  wipe including pairing codes and `dev_*` Auth users.
- All push helpers share `pushToChildDevices`: `messaging.sendEachForMulticast` with
  `data`-only payload + `android.priority: "high"`, and it deletes any `fcmToken` Firebase
  reports back as unregistered/invalid so dead tokens do not accumulate on `devices/{deviceId}`.
- **Planned (Super Admin):** `adminSendCampaign` (Admin Auth only) — audience
  `parents` \| `children` \| `all`, intent `product` \| `promo`, optional image + deep link,
  dry-run + audit doc under `adminCampaigns/{id}`; fans out via a shared
  `pushToAudience` helper that never returns tokens to the SPA. See docs/11.
- Callables honor Functions env `APP_CHECK_ENFORCE=true` (default off until release SHA is
  registered — see SECURITY.md).

Deploy with `firebase deploy --only functions` after Auth providers are enabled.

AI pack generation stays off the Home/quiz path: trigger when packs run low, schema-check, write `quizPacks`.

## Client read/write budget

- No Firestore listeners on child Home, and no continuous Firestore listener anywhere on the
  child device (Phase 7 removed the Phase 5/6 policy listener in favor of FCM-triggered pulls).
  A child device only opens Firestore for the duration of a bounded `WorkManager` job.
- Parent dashboard: listen to one child (selected) plus a shallow children list; paginate reports.
- Prefer `usageDays/{date}` documents over raw session ticks after 7–14 days.
- Usage/quiz/skill uploads are batched (2 h periodic + opportunistic after a quiz), not a write
  per event, and only touch rows Room has flagged dirty.
- Parent reports (P17): five parallel one-shot reads (usage / attempts / skill / rules /
  installed apps), no listeners; aggregate in-process via `ReportsAggregator`.
