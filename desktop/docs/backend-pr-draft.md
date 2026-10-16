# Draft PR — Desktop additive Functions & Firestore rules

**Status:** ✅ **Landed with d5** (2026-10-05) in `functions/src/index.ts` + `firestore.rules`.  
Originally drafted in d0 for landing in d5.

## Summary

- Accept desktop platforms on `consumePairingToken` (`windows` \| `macos` \| `linux`).
- Stop logging raw pairing codes (product invariant).
- Allow child heartbeats to write new device health fields without clearing `revoked`.
- Policy already tolerates unknown `quizMode` on older mobile clients; desktop will write `device_interval` + `quizIntervalMinutes`.

## Functions (`functions/src/index.ts`)

### `consumePairingToken`

**Today:**

```ts
const platform = platformRaw === "ios" ? "ios" : "android";
logger.info("[consumePairingToken] Attempt", { code, deviceId, platform, ip });
```

**Change to:**

```ts
const ALLOWED = new Set(["android", "ios", "windows", "macos", "linux"]);
const platform = ALLOWED.has(platformRaw) ? platformRaw : "android";
// Never log pairing code / secret
logger.info("[consumePairingToken] Attempt", {
  deviceId,
  platform,
  ip,
  codeLength: code.length,
});
```

Also redact `code` from the “Code not found” warn path.

### Policy / clients

No Function change required for `quizMode=device_interval` — storage is a string field parents write via existing policy commit paths. Desktop/Android mappers coerce unknown values safely.

## Firestore rules (`firestore.rules`)

Extend `childDeviceUpdateKeysOnly()` `hasOnly([...])` with:

- `agentVersion`
- `osBuild`
- `guardianState`
- `tamperFlags`
- `enforcementTier`

Do **not** allow children to mutate `revoked`, `platform` after bind (platform set at pairing by Function), or arbitrary new keys.

## Test plan

- [ ] Unit / emulator: `consumePairingToken` with `platform=windows` persists `windows` on device doc
- [ ] Same for `macos` and `linux`
- [ ] Unknown platform still defaults safely (document: default `android` only for legacy clients omitting field — prefer requiring explicit platform from desktop builds)
- [ ] Function logs never contain the 6-digit code
- [ ] Rules: child can patch `guardianState` + `tamperFlags`; cannot clear `revoked`
- [ ] Android / iOS regression: existing pairing still works with `android` / `ios`

## Out of scope for this PR

- Desktop OAuth client console setup (ops)
- App Check desktop attestation
- Updater CDN
