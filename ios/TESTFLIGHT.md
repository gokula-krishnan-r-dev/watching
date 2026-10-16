# iOS TestFlight (Sandeep Suntha · team `DUYBZ23SQ4`)

## Why bundle IDs changed

`com.watching.app` (and its extensions / App Group) are **already registered to another Apple Developer team**, so Xcode cannot create profiles for them under Sandeep Suntha.

This project now uses team-owned IDs:

| Target | Bundle ID | Display name |
| --- | --- | --- |
| Main app (scheme MeritScreen → product **Watching**) | `com.watching.sandeep` | **Watching** |
| Shield Config | `com.watching.sandeep.shieldconfig` |
| Shield Action | `com.watching.sandeep.shieldaction` |
| Device Activity | `com.watching.sandeep.deviceactivity` |
| App Group | `group.com.watching.sandeep` |

Version: **0.2.1 (5)** — aligned with Android.

**App name:** on-device and App Store listing must be **Watching** (not MeritScreen).  
If App Store Connect still shows MeritScreen: **App Information → Name → Watching** (save). Bundle ID stays `com.watching.sandeep`.

## One-time App Store Connect setup

1. Open [App Store Connect](https://appstoreconnect.apple.com) → **My Apps** → **+** → New App.
2. Bundle ID: **`com.watching.sandeep`** (must appear after Xcode registered it — already done via archive).
3. SKU: e.g. `watching-sandeep-001`. Platforms: iOS.

### Firebase (required for production backend)

1. Firebase Console → project → **Add app** → iOS → bundle `com.watching.sandeep`.
2. Download the new `GoogleService-Info.plist` and replace  
   `ios/MeritScreen/Resources/GoogleService-Info.plist`.
3. Enable the same Auth / Firestore / Functions / Messaging as Android.

### Family Controls (Screen Time)

Apple separates **development** vs **distribution** Family Controls. Store provisioning profiles reject the development-only entitlement, so Release is set up to ship cleanly **without** Screen Time until Apple approves distribution.

| Build | Entitlement | Extensions | Screen Time shields |
| --- | --- | --- | --- |
| Debug (device/simulator) | Included | Embedded | Works after system auth |
| Release / TestFlight | Omitted | **Not embedded** (`scripts/embed-screentime-extensions.sh`) | Pairing + quiz work; shields deferred |

Request distribution approval (required before shields on TestFlight):

https://developer.apple.com/contact/request/family-controls-distribution

After Apple approves:

1. Add this key back to:
   - `MeritScreen/MeritScreenRelease.entitlements`
   - `MeritScreenDeviceActivity/MeritScreenDeviceActivityRelease.entitlements`

```xml
<key>com.apple.developer.family-controls</key>
<true/>
```

2. Set `FAMILY_CONTROLS_DISTRIBUTION=YES` on the MeritScreen **Release** build settings (or pass it to `xcodebuild`) so the embed script includes the extensions.
3. Archive and upload again.

## Upload to TestFlight

Archive is at:

```text
ios/build/archives/MeritScreen.xcarchive
```

### Option A — Xcode Organizer (recommended)

1. Xcode → **Window → Organizer** (archive should be open).
2. Select **MeritScreen** → **Distribute App**.
3. **App Store Connect** → **Upload** → Automatic signing → team **Sandeep Suntha**.
4. Wait for processing in App Store Connect → **TestFlight** → add internal testers.

### Option B — CLI (after the ASC app record exists)

```bash
./ios/scripts/upload-testflight.sh
# or archive only:
./ios/scripts/upload-testflight.sh --archive-only
```

## Signing checklist in Xcode

All targets → **Signing & Capabilities**:

- Team: **Sandeep Suntha** (`DUYBZ23SQ4`)
- Bundle IDs as in the table above
- App Groups: `group.com.watching.sandeep`
- Automatically manage signing: **ON**

If you still see “group.com.watching.app is not available”, Product → Clean Build Folder, then re-open Signing & Capabilities so Xcode picks up the new group.
