package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import kotlinx.serialization.Serializable

/** Sticker collection stage — maps to Explorer level bands (docs/12). */
@Serializable
enum class StickerStage(
    val displayLabel: String,
    val minExplorerLevel: Int,
    val maxExplorerLevel: Int,
) {
    SPROUT("Sprout", 1, 2),
    EXPLORER("Explorer", 3, 4),
    TRAILBLAZER("Trailblazer", 5, 6),
    CHAMPION("Champion", 7, 8),
    LEGEND("Legend", 9, 10),
    ;

    companion object {
        fun forExplorerLevel(level: Int): StickerStage {
            val clamped = level.coerceIn(1, AppConfig.EXPLORER_LEVEL_MAX)
            return entries.first { clamped in it.minExplorerLevel..it.maxExplorerLevel }
        }
    }
}

@Serializable
enum class StickerUnlockSource {
    QUIZ_PASS,
    WEEKEND_BONUS,
    STREAK,
    NURSERY_TEACH,
}

@Serializable
data class StickerDefinition(
    val stickerId: String,
    val stage: StickerStage,
    val title: String,
    val emoji: String,
    val orderIndex: Int,
    val hint: String = "",
)

@Serializable
data class StickerUnlock(
    val unlockId: String,
    val stickerId: String,
    val stage: StickerStage,
    val title: String,
    val emoji: String,
    val source: StickerUnlockSource,
    val attemptId: String? = null,
    val unlockedAtEpochMs: Long,
    val xpAtUnlock: Int,
)

@Serializable
data class ExplorerProgress(
    val xp: Int = 0,
    val explorerLevel: Int = 1,
    val passStreakDays: Int = 0,
    val lastPassDayKey: String? = null,
)

/** Child → Firestore upload payload for one sticker unlock (idempotent by unlockId). */
data class StickerUnlockUpload(
    val unlockId: String,
    val stickerId: String,
    val stage: String,
    val title: String,
    val emoji: String,
    val source: String,
    val attemptId: String?,
    val unlockedAtEpochMs: Long,
    val xpAtUnlock: Int,
)

/** Child → Firestore upload payload for explorer progress (`explorerProgress/current`). */
data class ExplorerProgressUpload(
    val xp: Int,
    val explorerLevel: Int,
    val passStreakDays: Int,
    val lastPassDayKey: String?,
    val updatedAtEpochMs: Long,
)

/** Parent one-shot read of a sticker unlock. */
data class StickerUnlockSummary(
    val unlockId: String,
    val stickerId: String,
    val stage: String,
    val title: String,
    val emoji: String,
    val source: String,
    val unlockedAtEpochMs: Long,
)

/** Parent one-shot read of explorer progress. */
data class ExplorerProgressSummary(
    val xp: Int,
    val explorerLevel: Int,
    val passStreakDays: Int,
    val lastPassDayKey: String?,
    val updatedAtEpochMs: Long,
)

object ExplorerLevelCalculator {
    fun levelForXp(xp: Int): Int {
        val thresholds = AppConfig.EXPLORER_LEVEL_XP_THRESHOLDS
        var level = 1
        for (i in 1..AppConfig.EXPLORER_LEVEL_MAX) {
            val need = thresholds.getOrElse(i) { Int.MAX_VALUE }
            if (xp >= need) level = i else break
        }
        return level.coerceIn(1, AppConfig.EXPLORER_LEVEL_MAX)
    }

    fun xpToNextLevel(xp: Int): Int? {
        val level = levelForXp(xp)
        if (level >= AppConfig.EXPLORER_LEVEL_MAX) return null
        val next = AppConfig.EXPLORER_LEVEL_XP_THRESHOLDS.getOrElse(level + 1) { return null }
        return (next - xp).coerceAtLeast(0)
    }
}

/**
 * Bundled sticker catalog — emoji glyphs only (no remote assets).
 * Order within a stage is unlock priority after a quiz pass.
 */
object StickerCatalog {
    val all: List<StickerDefinition> = listOf(
        // Stage 1 — Sprout
        StickerDefinition("sprout_star", StickerStage.SPROUT, "Bright Star", "⭐", 1, "First quiz win"),
        StickerDefinition("sprout_fox", StickerStage.SPROUT, "Curious Fox", "🦊", 2, "Keep exploring"),
        StickerDefinition("sprout_seed", StickerStage.SPROUT, "Tiny Seed", "🌱", 3, "Growth begins"),
        StickerDefinition("sprout_smile", StickerStage.SPROUT, "Happy Smile", "😊", 4, "Cheer unlocked"),
        // Stage 2 — Explorer
        StickerDefinition("explorer_map", StickerStage.EXPLORER, "Trail Map", "🗺️", 1, "Level 3 explorer"),
        StickerDefinition("explorer_owl", StickerStage.EXPLORER, "Wise Owl", "🦉", 2, "Think it through"),
        StickerDefinition("explorer_rocket", StickerStage.EXPLORER, "Mini Rocket", "🚀", 3, "Launch mode"),
        StickerDefinition("explorer_book", StickerStage.EXPLORER, "Story Book", "📖", 4, "Reading win"),
        // Stage 3 — Trailblazer
        StickerDefinition("trail_compass", StickerStage.TRAILBLAZER, "Compass", "🧭", 1, "Find your way"),
        StickerDefinition("trail_lion", StickerStage.TRAILBLAZER, "Brave Lion", "🦁", 2, "Bold answers"),
        StickerDefinition("trail_gem", StickerStage.TRAILBLAZER, "Blue Gem", "💎", 3, "Rare focus"),
        StickerDefinition("trail_camp", StickerStage.TRAILBLAZER, "Campfire", "🏕️", 4, "Keep the streak"),
        // Stage 4 — Champion
        StickerDefinition("champ_medal", StickerStage.CHAMPION, "Gold Medal", "🏅", 1, "Champion tier"),
        StickerDefinition("champ_trophy", StickerStage.CHAMPION, "Trophy", "🏆", 2, "Big win"),
        StickerDefinition("champ_dragon", StickerStage.CHAMPION, "Kind Dragon", "🐉", 3, "Power learner"),
        StickerDefinition("champ_spark", StickerStage.CHAMPION, "Spark Burst", "✨", 4, "Shine on"),
        // Stage 5 — Legend
        StickerDefinition("legend_crown", StickerStage.LEGEND, "Crown", "👑", 1, "Legend stage"),
        StickerDefinition("legend_phoenix", StickerStage.LEGEND, "Phoenix", "🔥", 2, "Rise again"),
        StickerDefinition("legend_galaxy", StickerStage.LEGEND, "Galaxy", "🌌", 3, "Sky high"),
        StickerDefinition("legend_heart", StickerStage.LEGEND, "Hero Heart", "💖", 4, "Kind & smart"),
    )

    fun byId(stickerId: String): StickerDefinition? = all.firstOrNull { it.stickerId == stickerId }

    fun forStage(stage: StickerStage): List<StickerDefinition> =
        all.filter { it.stage == stage }.sortedBy { it.orderIndex }

    /** Next sticker the child can unlock at [explorerLevel], skipping already owned ids. */
    fun nextUnlockable(explorerLevel: Int, ownedIds: Set<String>): StickerDefinition? {
        val stage = StickerStage.forExplorerLevel(explorerLevel)
        val eligibleStages = StickerStage.entries.filter { it.minExplorerLevel <= explorerLevel }
        return eligibleStages
            .flatMap { forStage(it) }
            .firstOrNull { it.stickerId !in ownedIds }
            ?: forStage(stage).firstOrNull { it.stickerId !in ownedIds }
    }
}
