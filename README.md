# MeritScreen

MeritScreen is a parental-control launcher for children aged 3–12. Parents set rules on their own phone. On Android, MeritScreen becomes the child’s Home screen: only approved apps, per-app time blocks, a short quiz for extra time, and a device-wide fail lock.

This repository is the **Android** client (parent + child in one app), **iOS** app, **desktop** Rust workspace (Windows/macOS first), and Firebase configuration. All clients share the same Firebase project and policy JSON.

## Current milestone

**Milestone 1 — Android foundation** is in progress: Gradle modules, design system, role router, Firebase wiring, and base architecture. Feature screens (auth, onboarding, launcher enforcement, quiz) are stubbed as placeholders.

## Requirements

- JDK 17+ (21 is fine)
- Android SDK with `compileSdk` 36
- Android Studio / AGP 9.4
- Firebase CLI authenticated against project `managing-screen-time`

## Project layout

```
app/                 # Application shell, splash, role router
core/
  common/            # AppConfig, UiState, AppError, session role
  ui/                # Material 3 design system + loading/error/empty
  network/           # Connectivity monitor
  database/          # Room
  firebase/          # Auth/Firestore/Functions/Messaging wrappers
  security/          # PIN hashing, Keystore storage
  analytics/         # Crashlytics + child-safe analytics allowlist
  testing/           # Fakes for unit tests
features/            # Feature modules (placeholders until later phases)
ios/                 # Native iOS client
desktop/             # Rust Guardian + Agent + Tauri UI (phases d0+)
docs/                # Product specification
functions/           # Cloud Functions scaffold
```

Desktop phase d0 product lock: `docs/17-desktop-d0-product-lock.md`. Build: `make desktop-test`.

Configurable limits such as **maximum children per parent** live in `core/common` (`AppConfig`). Do not hard-code them in feature code.

## Setup

1. Copy Firebase config:

   ```bash
   cp app/google-services.json.example app/google-services.json
   ```

   Prefer the real file from Firebase (`firebase apps:sdkconfig ANDROID`). `app/google-services.json` is gitignored.

2. SDK path is `local.properties` (`sdk.dir`). That file is gitignored.

3. Build:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Or use Make (boots the local AVD, installs, launches — production Firebase by default):

   ```bash
   make dev          # one emulator
   make up-dev       # parent + child emulators
   make down         # stop emulators
   ```

   Scripts still work: `./scripts/run-demo.sh`, `--emulators`, `--reuse-apk`.

4. Unit tests:

   ```bash
   ./gradlew testDebugUnitTest
   ```

5. Create the default Firestore database in the [Firebase console](https://console.firebase.google.com/project/managing-screen-time/firestore) if it does not exist, then deploy rules:

   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```

   Enable **Google** (and any other providers you use) under Authentication → Sign-in method. Parent email sign-in uses **email OTP** via Cloud Functions + Resend (not Firebase Email/password). Leave App Check in monitor mode until a release signing certificate is registered.

6. Deploy Cloud Functions and configure Resend for OTP mail:

   ```bash
   cd functions && npm ci && npm run build && cd ..
   # One-time / rotate: store the API key in Secret Manager (do not commit it)
   printf '%s' 're_xxxxxxxx' | firebase functions:secrets:set RESEND_API_KEY --data-file=-
   firebase deploy --only functions:sendEmailOtp,functions:verifyEmailOtp
   ```

   For the Functions emulator, copy `functions/.secret.local.example` → `functions/.secret.local` and set `RESEND_API_KEY`.

   **Production sender:** `sendEmailOtp` defaults to `MeritScreen <noreply@anajyo.com>`
   (`RESEND_FROM_EMAIL` param). That domain must stay **Verified** in
   [Resend Domains](https://resend.com/domains). Until verification completes, Resend
   only delivers to the account-owner inbox when using the free test sender.

7. `compileSdk` is 37. Install `platforms;android-37.0` or `platforms;android-37.2` if the build asks for API 37. Set `ANDROID_HOME` to your SDK (this machine uses `/opt/homebrew/share/android-commandlinetools`).

## Roles in one APK

The same binary serves parents and children. `SessionRoleRepository` stores the local role. Unassigned devices see a role-select placeholder; later phases replace that with the real onboarding and pairing flows.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)
- [FIREBASE_ARCHITECTURE.md](FIREBASE_ARCHITECTURE.md)
- [SECURITY.md](SECURITY.md)
- [TESTING.md](TESTING.md)
- Product spec: [docs/README.md](docs/README.md)
# watching
