# 12 — Sticker rewards & staged explorer levels

MeritScreen rewards children for learning with **collectible stickers** and an **Explorer level**, not public leaderboards. Parents configure whether stickers and weekend cheer are on. The child Home path stays **Room-only**.

---

## Goals

| Actor | Experience |
| --- | --- |
| Child | Earn stickers on quiz pass; open **Sticker Book** from Home dock Rewards; see Explorer level on Home |
| Parent | Toggle stickers / weekend bonus / extra minutes (P16); see real earned counts on Child Detail |

Non-goals: public leaderboards, paid sticker packs, network calls on Home/quiz compose paths.

---

## Staging model

Explorer levels **1–10**. Stickers are grouped into **five stages**:

| Stage | Levels | Theme |
| --- | --- | --- |
| 1 Sprout | 1–2 | First wins |
| 2 Explorer | 3–4 | Curiosity |
| 3 Trailblazer | 5–6 | Persistence |
| 4 Champion | 7–8 | Mastery |
| 5 Legend | 9–10 | Rare cheer |

XP thresholds live in `AppConfig.EXPLORER_LEVEL_XP_THRESHOLDS`. Quiz pass grants XP; sticker unlocks are **idempotent** per `(childId, stickerId)`.

---

## Data

### Bundled catalog (code)

`StickerCatalog` in `:core:common` — stable `stickerId`, stage, title, emoji glyph, `xpReward`, unlock hint. No remote asset dependency for v1.

### Room (child device)

| Table | Purpose |
| --- | --- |
| `child_sticker_unlock` | Earned stickers; `dirty` for upload |
| `child_explorer_progress` | `xp`, `explorerLevel`, `passStreakDays`, `lastPassDayKey` |

### Firestore

```
families/{familyId}/children/{childId}/stickerUnlocks/{unlockId}
  stickerId, stage, title, emoji, source, attemptId?, unlockedAt, xpAtUnlock

families/{familyId}/children/{childId}/explorerProgress/current
  xp, explorerLevel, passStreakDays, updatedAt
```

**Rules:** child device may **create** unlocks and **merge** progress while not revoked; parents **read**; no client delete.

### Policy (existing)

- `rewardsEnabled` — gates sticker unlocks **and** extra minutes  
- `weekendBonusEnabled` — +XP and +minutes when local day is Sat/Sun  
- `extraMinutesOnPass` — next block grant (unchanged)

---

## Award flow (quiz pass)

1. Child completes quiz → Room attempt row (existing).  
2. If `rewardsEnabled`: add XP (base + optional weekend bonus) → recompute level.  
3. Unlock next eligible locked sticker for the new level (or stage-appropriate).  
4. Celebration UI shows **real** sticker + minutes.  
5. `UsageSyncWorker` uploads dirty unlocks + explorer progress (never from Home ViewModel).

Fail path: no sticker, no XP.

---

## Screens

| ID | Screen | Data source |
| --- | --- | --- |
| C05 | Home header Explorer level | Room progress |
| C05 dock | Rewards → **C16 Sticker Book** | Room unlocks + catalog |
| C10 | Pass celebration | Just-awarded sticker from Room |
| P12 | Child Detail rewards row | Firestore unlocks / progress (parent) |
| P16 | Rewards settings | Policy toggles + short stage explainer |

---

## Invariants

1. Home / quiz UI read **Room only**.  
2. Fail lock stays device-wide — stickers never bypass it.  
3. No public leaderboard.  
4. Tunables only in `AppConfig`.  
5. Offline: awards persist in Room; sync when online.

---

## Rollout

1. Ship catalog + Room + child Sticker Book + award on pass.  
2. Parent Detail shows live counts.  
3. Later: more sources (streak-day sticker, nursery teach complete) without schema break.
