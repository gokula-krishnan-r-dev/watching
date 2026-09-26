# Testing

## Priority

Highest coverage goes to **parental-control correctness**:

1. Session engine (app block, pass grant, fail lock, cooldown, retry)
2. Fail lock is device-wide (never per-app-only)
3. Policy application from Room (offline)
4. Authz: parent isolation, child token scoped to `childId`
5. PIN verify + lockout
6. Role router / navigation graphs

UI chrome and placeholders get smoke tests only.

## Layers

| Layer | Where | Tools |
| --- | --- | --- |
| Domain / mappers | `src/test` in `core/*` | JUnit, MockK, Turbine |
| ViewModels | feature `src/test` | Turbine, fakes from `:core:testing` |
| Repositories | `src/test` with fakes; emulator for Firestore later | MockK, Firebase emulator |
| Compose states | `core/ui` androidTest | Compose UI Test |
| Navigation | `app` androidTest (later) | Compose Navigation testing |
| Device / launcher | Instrumented on API 26, 29, 34, 36 | Espresso / Compose |

## Milestone 1 harness

Already in the tree:

- `AppErrorMapperTest` — raw Firebase strings never become UI copy
- `UiStateTest` / `AppConfigTest` / `LogSanitizerTest`
- `Pbkdf2PinHasherTest`
- `StateComponentsTest` (loading / error / empty)

Run JVM unit tests:

```bash
./gradlew testDebugUnitTest
```

Compose androidTests need a device or emulator:

```bash
./gradlew :core:ui:connectedDebugAndroidTest
```

## Fakes

Use `:core:testing` (`FakeSessionRoleRepository`, `FakeNetworkMonitor`, `testAppDispatchers`). Do not hit live Firebase from unit tests.

## Later phases

- Firebase emulator suite for security rules (`firebase emulators:exec`)
- Session-engine table tests matching the YouTube 30/15 timeline in `docs/07`
- Pairing token one-time-use tests in Functions
- Launcher: “not default Home” detection
