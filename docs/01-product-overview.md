# 01 — Product overview

## What MeritScreen is

MeritScreen is a **parental-control launcher** for children aged **3–12**. The same rules run on Android and iOS. The shell is different because the platforms are different.

- **Android:** MeritScreen **is the default Home app**. Home or unlock opens our launcher, not Samsung One UI Home, Pixel Launcher, or Oppo Launcher. The child does not get the stock app drawer or a widget wall. They see approved apps, remaining time, and quizzes.
- **iOS:** Apple does not allow a custom Home. The app is a **controlled environment** built in **SwiftUI**, using Screen Time / Family Controls (`FamilyControls`, `ManagedSettings`, `DeviceActivity`). Blocked apps are shielded. The quiz and parent dashboard are SwiftUI.

The parent manages everything from **their own phone**. The child never creates an account.

## Alignment with the product brief

| Brief requirement | Spec |
| --- | --- |
| Ages 3–12, Android + iOS | Yes. Age bands 3–6, 7–9, 10–12 |
| Android replaces stock launcher | Yes. `HOME` / `ROLE_HOME`. No stock drawer |
| iOS uses Screen Time / Family Controls | Yes. SwiftUI + Family Controls. Not a fake launcher |
| Parent: allow apps, limits, quiz mode, rewards, reports | Yes. Remote dashboard |
| Child: approved apps, time left, quiz | Yes |
| Pass → more time on that app + rewards | Yes. Another **block** of that app (example: +30 min YouTube) |
| Fail → cooldown set by parent; apps shielded until cooldown or retry | Yes. **All non-emergency apps** shield immediately, including the one in use. Only Phone stays open. Unlock = cooldown ends or a retry is passed. See [07](07-app-blocks-and-fail-lock.md) |
| Adaptive quiz + concept explanation on a wrong answer | Yes. [06](06-adaptive-quiz-and-explanations.md) |
| Kotlin launcher, SwiftUI, Firebase | Yes. [05](05-architecture-performance.md) |
| COPPA / Play Families / App Store kids | Yes |
| Out of v1: GPS, web filter, child social | Yes |

## The promise

| Goal | How the app delivers it |
| --- | --- |
| Safe | Only parent-approved apps. Stock drawer is not available on Android |
| Limited | Each app has a time **block**. Optional daily ceiling |
| Fair fail | Failed quiz stops all further usage. Only emergency apps stay open until cooldown or a passed retry |
| Educational | Adaptive quiz. Wrong tap explains the choice and the concept |
| Remote | Parent changes rules without touching the child’s phone |
| Light | Child UI is native, local-first, offline capable |

## Who uses it

| Person | Device | How they enter |
| --- | --- | --- |
| **Parent** | Parent’s phone (Android or iOS) | Email / Google / Apple (Firebase Auth) |
| **Child** | Child’s phone | No password. Parent pairs with QR or 6-digit code |

## Age bands (quiz difficulty)

| Age band | Quiz style |
| --- | --- |
| 3–6 | Colors, shapes, matching, simple counting |
| 7–9 | Basic math, spelling, reading |
| 10–12 | General knowledge, reasoning, slightly harder math |

The parent picks the band. Difficulty then **moves inside that band** from the child’s answers. A wrong answer teaches the concept. The engine does not promote the child into another age band by itself.

## What the parent can control

1. **Approve apps** the child may open.
2. Set a **time block per app** (example: YouTube 30 minutes, then a quiz).
3. Set **cooldown on fail** (example: 15 minutes). A fail locks **all** non-emergency apps. A passed **retry** ends the lock early.
4. Optional **daily ceiling** across all apps.
5. **Quiz trigger mode:** app block (recommended), every session, or daily ceiling.
6. **Rewards:** extra minutes (the next block), stickers, weekend bonus.
7. **Reports:** usage by app, quiz results, weak topics, which apps are in cooldown.

## What the child sees

- Approved apps only (Android Home grid; iOS allowed set + shields).
- Time left on the app they are using.
- A quiz when the block (or other rule) says so.
- **Pass** → that app continues for another block, plus reward if configured.
- **Fail** → the launcher shields **all apps**, including the one they were using. Only emergency apps such as Phone stay open until the cooldown ends or they pass a retry quiz.
- Wrong answers: why that choice is wrong, then the concept, in short age-fit language.

Worked example and shield rules: [07 — App blocks and fail lock](07-app-blocks-and-fail-lock.md).

## Why the stacks differ

**Android** is Kotlin + Jetpack Compose because the process that answers Home must start fast, stay small, and work offline. AI does not run on that path.

**iOS** is **SwiftUI** (Swift) for every screen: parent dashboard, child quiz, shields, and pairing. Family Controls does the shielding the launcher does on Android. Same Firebase project and the same policy JSON so a family can mix an Android child phone and an iOS parent phone.

## Out of scope for v1

- Live GPS tracking
- Full web filtering / DNS VPN
- Child social features or chat
- Behavioral ads
- Storing the child’s photos or contacts
