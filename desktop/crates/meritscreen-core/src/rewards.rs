//! Explorer XP, levels and sticker rewards — a port of Android `StickerRewards.kt`
//! (`core/common/.../domain/StickerRewards.kt`) and `StickerRepository.awardQuizPass`.
//!
//! Firestore formats match Android exactly:
//! - `explorerProgress/current`: `{xp, explorerLevel, passStreakDays, lastPassDayKey, updatedAt}` (epoch ms)
//! - `stickerUnlocks/{unlockId}`: `{stickerId, stage, title, emoji, source, attemptId, unlockedAt, xpAtUnlock}`
//!   where `unlockId = "stk_{childId}_{stickerId}"` and `unlockedAt` is epoch ms on Android;
//!   the parent reader also accepts a Firestore timestamp.

use serde::{Deserialize, Serialize};

/// XP granted per passed quiz (Android `AppConfig.STICKER_QUIZ_PASS_XP`).
pub const QUIZ_PASS_XP: u32 = 10;
/// Extra XP on Sat/Sun with the weekend bonus enabled (Android `STICKER_WEEKEND_BONUS_XP`).
pub const WEEKEND_BONUS_XP: u32 = 5;
/// Cumulative XP required to reach levels 1..=10 (index 0 unused), Android `EXPLORER_LEVEL_XP_THRESHOLDS`.
pub const LEVEL_XP_THRESHOLDS: [u32; 11] = [0, 0, 20, 50, 90, 140, 200, 270, 350, 450, 580];
pub const LEVEL_MAX: u32 = 10;

pub fn level_for_xp(xp: u32) -> u32 {
    let mut level = 1;
    for i in 1..=LEVEL_MAX {
        if xp >= LEVEL_XP_THRESHOLDS[i as usize] {
            level = i;
        } else {
            break;
        }
    }
    level.clamp(1, LEVEL_MAX)
}

/// XP still needed for the next level; `None` at max level.
pub fn xp_to_next_level(xp: u32) -> Option<u32> {
    let level = level_for_xp(xp);
    if level >= LEVEL_MAX {
        return None;
    }
    Some(LEVEL_XP_THRESHOLDS[(level + 1) as usize].saturating_sub(xp))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StickerStage {
    Sprout,
    Explorer,
    Trailblazer,
    Champion,
    Legend,
}

impl StickerStage {
    pub fn label(self) -> &'static str {
        match self {
            Self::Sprout => "Sprout",
            Self::Explorer => "Explorer",
            Self::Trailblazer => "Trailblazer",
            Self::Champion => "Champion",
            Self::Legend => "Legend",
        }
    }
    pub fn level_range(self) -> (u32, u32) {
        match self {
            Self::Sprout => (1, 2),
            Self::Explorer => (3, 4),
            Self::Trailblazer => (5, 6),
            Self::Champion => (7, 8),
            Self::Legend => (9, 10),
        }
    }
    pub fn for_level(level: u32) -> Self {
        let l = level.clamp(1, LEVEL_MAX);
        [Self::Sprout, Self::Explorer, Self::Trailblazer, Self::Champion, Self::Legend]
            .into_iter()
            .find(|s| {
                let (lo, hi) = s.level_range();
                (lo..=hi).contains(&l)
            })
            .unwrap_or(Self::Legend)
    }
    pub const ALL: [Self; 5] = [Self::Sprout, Self::Explorer, Self::Trailblazer, Self::Champion, Self::Legend];
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StickerDefinition {
    pub sticker_id: String,
    pub stage: StickerStage,
    pub title: String,
    pub emoji: String,
    pub order_index: u32,
    pub hint: String,
}

fn def(id: &str, stage: StickerStage, title: &str, emoji: &str, order: u32, hint: &str) -> StickerDefinition {
    StickerDefinition { sticker_id: id.into(), stage, title: title.into(), emoji: emoji.into(), order_index: order, hint: hint.into() }
}

/// The Android bundled catalog (20 stickers, emoji only), in unlock order.
pub fn catalog() -> Vec<StickerDefinition> {
    use StickerStage::*;
    vec![
        def("sprout_star", Sprout, "Bright Star", "⭐", 1, "First quiz win"),
        def("sprout_fox", Sprout, "Curious Fox", "🦊", 2, "Keep exploring"),
        def("sprout_seed", Sprout, "Tiny Seed", "🌱", 3, "Growth begins"),
        def("sprout_smile", Sprout, "Happy Smile", "😊", 4, "Cheer unlocked"),
        def("explorer_map", Explorer, "Trail Map", "🗺️", 1, "Level 3 explorer"),
        def("explorer_owl", Explorer, "Wise Owl", "🦉", 2, "Think it through"),
        def("explorer_rocket", Explorer, "Mini Rocket", "🚀", 3, "Launch mode"),
        def("explorer_book", Explorer, "Story Book", "📖", 4, "Reading win"),
        def("trail_compass", Trailblazer, "Compass", "🧭", 1, "Find your way"),
        def("trail_lion", Trailblazer, "Brave Lion", "🦁", 2, "Bold answers"),
        def("trail_gem", Trailblazer, "Blue Gem", "💎", 3, "Rare focus"),
        def("trail_camp", Trailblazer, "Campfire", "🏕️", 4, "Keep the streak"),
        def("champ_medal", Champion, "Gold Medal", "🏅", 1, "Champion tier"),
        def("champ_trophy", Champion, "Trophy", "🏆", 2, "Big win"),
        def("champ_dragon", Champion, "Kind Dragon", "🐉", 3, "Power learner"),
        def("champ_spark", Champion, "Spark Burst", "✨", 4, "Shine on"),
        def("legend_crown", Legend, "Crown", "👑", 1, "Legend stage"),
        def("legend_phoenix", Legend, "Phoenix", "🔥", 2, "Rise again"),
        def("legend_galaxy", Legend, "Galaxy", "🌌", 3, "Sky high"),
        def("legend_heart", Legend, "Hero Heart", "💖", 4, "Kind & smart"),
    ]
}

pub fn for_stage(stage: StickerStage) -> Vec<StickerDefinition> {
    let mut v: Vec<_> = catalog().into_iter().filter(|d| d.stage == stage).collect();
    v.sort_by_key(|d| d.order_index);
    v
}

/// Next sticker unlockable at `level`, skipping owned ids (Android `StickerCatalog.nextUnlockable`).
pub fn next_unlockable(level: u32, owned: &std::collections::HashSet<String>) -> Option<StickerDefinition> {
    let eligible: Vec<StickerDefinition> = StickerStage::ALL
        .into_iter()
        .filter(|s| s.level_range().0 <= level)
        .flat_map(for_stage)
        .collect();
    eligible
        .iter()
        .find(|d| !owned.contains(&d.sticker_id))
        .cloned()
        .or_else(|| for_stage(StickerStage::for_level(level)).into_iter().find(|d| !owned.contains(&d.sticker_id)))
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExplorerProgress {
    pub xp: u32,
    #[serde(default = "one")]
    pub explorer_level: u32,
    pub pass_streak_days: u32,
    pub last_pass_day_key: Option<String>,
}

fn one() -> u32 {
    1
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StickerUnlock {
    pub unlock_id: String,
    pub sticker_id: String,
    /// Android stage name, e.g. `SPROUT`.
    pub stage: String,
    pub title: String,
    pub emoji: String,
    /// `QUIZ_PASS` | `WEEKEND_BONUS` | `STREAK` | `NURSERY_TEACH`
    pub source: String,
    pub attempt_id: Option<String>,
    pub unlocked_at_epoch_ms: i64,
    pub xp_at_unlock: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AwardResult {
    pub progress: ExplorerProgress,
    pub unlocked: Option<StickerUnlock>,
    pub xp_gained: u32,
    pub leveled_up: bool,
    pub weekend_bonus_applied: bool,
}

/// Award XP + at most one sticker for a passed quiz (Android `awardQuizPass`).
/// Dates are child-local day keys (`YYYY-MM-DD`); yesterday's key decides the streak.
#[allow(clippy::too_many_arguments)]
pub fn award_quiz_pass(
    previous: &ExplorerProgress,
    owned: &std::collections::HashSet<String>,
    child_id: &str,
    attempt_id: Option<&str>,
    source_override: Option<&str>,
    rewards_enabled: bool,
    weekend_bonus_enabled: bool,
    is_weekend: bool,
    today_key: &str,
    yesterday_key: &str,
    now_ms: i64,
) -> AwardResult {
    if !rewards_enabled {
        return AwardResult { progress: previous.clone(), unlocked: None, xp_gained: 0, leveled_up: false, weekend_bonus_applied: false };
    }
    let weekend_bonus = weekend_bonus_enabled && is_weekend;
    let xp_gain = QUIZ_PASS_XP + if weekend_bonus { WEEKEND_BONUS_XP } else { 0 };
    let streak = match previous.last_pass_day_key.as_deref() {
        Some(d) if d == today_key => previous.pass_streak_days,
        Some(d) if d == yesterday_key => previous.pass_streak_days + 1,
        _ => 1,
    };
    let new_xp = previous.xp + xp_gain;
    let new_level = level_for_xp(new_xp);
    let progress = ExplorerProgress {
        xp: new_xp,
        explorer_level: new_level,
        pass_streak_days: streak,
        last_pass_day_key: Some(today_key.to_string()),
    };
    let unlocked = next_unlockable(new_level, owned).map(|next| StickerUnlock {
        unlock_id: format!("stk_{child_id}_{}", next.sticker_id),
        sticker_id: next.sticker_id.clone(),
        stage: format!("{:?}", next.stage).to_uppercase(),
        title: next.title.clone(),
        emoji: next.emoji.clone(),
        source: source_override
            .map(str::to_string)
            .unwrap_or_else(|| if weekend_bonus { "WEEKEND_BONUS".into() } else { "QUIZ_PASS".into() }),
        attempt_id: attempt_id.map(str::to_string),
        unlocked_at_epoch_ms: now_ms,
        xp_at_unlock: new_xp,
    });
    AwardResult { progress, unlocked, xp_gained: xp_gain, leveled_up: new_level > previous.explorer_level, weekend_bonus_applied: weekend_bonus }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    #[test]
    fn levels_match_android_thresholds() {
        assert_eq!(level_for_xp(0), 1);
        assert_eq!(level_for_xp(19), 1);
        assert_eq!(level_for_xp(20), 2);
        assert_eq!(level_for_xp(90), 4);
        assert_eq!(level_for_xp(580), 10);
        assert_eq!(level_for_xp(99_999), 10);
        assert_eq!(xp_to_next_level(0), Some(20));
        assert_eq!(xp_to_next_level(580), None);
    }

    #[test]
    fn catalog_and_stage_bands() {
        assert_eq!(catalog().len(), 20);
        assert_eq!(StickerStage::for_level(2), StickerStage::Sprout);
        assert_eq!(StickerStage::for_level(3), StickerStage::Explorer);
        let first = next_unlockable(1, &HashSet::new()).unwrap();
        assert_eq!(first.sticker_id, "sprout_star");
        let owned: HashSet<String> = catalog().iter().take(4).map(|d| d.sticker_id.clone()).collect();
        assert_eq!(next_unlockable(3, &owned).unwrap().sticker_id, "explorer_map");
    }

    #[test]
    fn award_xp_streak_and_weekend() {
        let prev = ExplorerProgress { xp: 10, explorer_level: 1, pass_streak_days: 2, last_pass_day_key: Some("2026-10-16".into()) };
        let r = award_quiz_pass(&prev, &HashSet::new(), "c1", Some("a1"), None, true, true, true, "2026-10-17", "2026-10-16", 123);
        assert_eq!(r.xp_gained, 15);
        assert!(r.weekend_bonus_applied);
        assert_eq!(r.progress.xp, 25);
        assert_eq!(r.progress.explorer_level, 2);
        assert!(r.leveled_up);
        assert_eq!(r.progress.pass_streak_days, 3);
        let u = r.unlocked.unwrap();
        assert_eq!(u.unlock_id, "stk_c1_sprout_star");
        assert_eq!(u.source, "WEEKEND_BONUS");
        // Same-day second pass: streak unchanged.
        let r2 = award_quiz_pass(&r.progress, &["sprout_star".to_string()].into_iter().collect(), "c1", None, None, true, false, false, "2026-10-17", "2026-10-16", 124);
        assert_eq!(r2.progress.pass_streak_days, 3);
        assert_eq!(r2.xp_gained, 10);
        // Rewards off: nothing changes.
        let r3 = award_quiz_pass(&prev, &HashSet::new(), "c1", None, None, false, false, false, "2026-10-17", "2026-10-16", 1);
        assert_eq!(r3.progress, prev);
        assert!(r3.unlocked.is_none());
    }
}
