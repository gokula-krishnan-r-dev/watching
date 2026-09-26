# MeritScreen — Product & Data Flow Documents

MeritScreen is a **parental-control launcher** for children aged **3–12**.  
It keeps the child’s phone **safe** (only approved apps) and **educational** (a short quiz before extra screen time).

These documents turn the original product brief into a build-ready specification.

| Document | What it explains |
| --- | --- |
| [01 — Product overview](01-product-overview.md) | What we are building, who it is for, Android vs iOS |
| [02 — Onboarding & authentication](02-onboarding-authentication.md) | How a parent and child get set up, login, pairing, PIN |
| [03 — Data model & data flow](03-data-model-and-flow.md) | What we collect, how data moves, privacy |
| [04 — Screens & features](04-screens-and-features.md) | Every screen, who sees it, what it does |
| [05 — Architecture & performance](05-architecture-performance.md) | Kotlin Android launcher, Firebase, AI quizzes, speed rules |
| [06 — Adaptive quiz & explanations](06-adaptive-quiz-and-explanations.md) | Difficulty follows past answers; wrong answers teach the concept; visual/audio-first mode for ages 3–6 |
| [07 — App blocks & fail lock](07-app-blocks-and-fail-lock.md) | Quiz interrupt; fail shields all apps until cooldown or a passed retry |
| [08 — AI memory, progressive difficulty & the 30s teaching lock](08-ai-personalized-learning-and-lockout.md) | Parent grade/region/prompt config, per-child AI memory, cache-first Gemini Flash pack generation, TTS narration, and the per-question 30-second locked teaching screen |
| [09 — Nursery early-learner curriculum (ages 3–6)](09-nursery-early-learner-curriculum.md) | Teach-first for nursery: auto video + images + audio; picture questions only later (~5–6) |
| [10 — Super Admin Panel](10-super-admin-panel.md) | React + shadcn ops console: users/families/children analytics, disable/delete, exports (spec only) |
| [11 — Firebase push notifications](11-firebase-push-notifications.md) | FCM design: control-plane (child) vs parent alerts, triggers, receivers, tokens, phases |
| [12 — Sticker rewards & staged explorer levels](12-sticker-rewards.md) | Collectible stickers, Explorer XP/levels, Room-first awards, parent P16 / child Sticker Book |

**Read this set in order** if you are new to the product.  
If you already know the idea, start at **02** (onboarding) and **03** (data).  
Operators building the internal console: start at **10** (after **03** and [SECURITY.md](../SECURITY.md)).  
Engineers wiring FCM end-to-end: start at **11** (after **03**, **04** § Notifications, and [FIREBASE_ARCHITECTURE.md](../FIREBASE_ARCHITECTURE.md)).

## PDF export

Full printable pack (cover, table of contents, product docs, tables, and rendered flow diagrams):

- [MeritScreen-Product-Specification.pdf](MeritScreen-Product-Specification.pdf)

Regenerate anytime (includes docs 01–09 when the PDF script is next updated; **10** may be added to the PDF pack in a later script update):

```bash
.venv-pdf/bin/python scripts/build_docs_pdf.py
```

---

## One-sentence product

A parent sets rules on their own phone. On Android, MeritScreen is the Home screen. On iOS, SwiftUI plus Family Controls applies the same rules. Each approved app has a time block (example: 30 minutes of YouTube, then a quiz). A pass grants another block of that app. A fail immediately shields every app except emergency apps such as Phone, until the cooldown ends or a retry quiz is passed. Quizzes adapt, and a wrong answer explains the concept — from text (or pictures and speech for ages 3–6) already on the device.
