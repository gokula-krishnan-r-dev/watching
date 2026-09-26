package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerRewardsTest {

    @Test
    fun levelForXp_matchesThresholds() {
        assertEquals(1, ExplorerLevelCalculator.levelForXp(0))
        assertEquals(2, ExplorerLevelCalculator.levelForXp(20))
        assertEquals(3, ExplorerLevelCalculator.levelForXp(50))
        assertEquals(10, ExplorerLevelCalculator.levelForXp(AppConfig.EXPLORER_LEVEL_XP_THRESHOLDS[10]))
        assertEquals(10, ExplorerLevelCalculator.levelForXp(10_000))
    }

    @Test
    fun xpToNextLevel_nullAtMax() {
        val maxXp = AppConfig.EXPLORER_LEVEL_XP_THRESHOLDS[AppConfig.EXPLORER_LEVEL_MAX]
        assertNull(ExplorerLevelCalculator.xpToNextLevel(maxXp))
        assertEquals(20, ExplorerLevelCalculator.xpToNextLevel(0))
    }

    @Test
    fun nextUnlockable_skipsOwnedAndRespectsLevel() {
        val first = StickerCatalog.nextUnlockable(1, emptySet())
        assertEquals("sprout_star", first?.stickerId)

        val second = StickerCatalog.nextUnlockable(1, setOf("sprout_star"))
        assertEquals("sprout_fox", second?.stickerId)

        val explorer = StickerCatalog.nextUnlockable(
            3,
            StickerCatalog.forStage(StickerStage.SPROUT).map { it.stickerId }.toSet(),
        )
        assertEquals("explorer_map", explorer?.stickerId)
        assertTrue(explorer!!.stage.minExplorerLevel <= 3)
    }

    @Test
    fun stageForLevel_bands() {
        assertEquals(StickerStage.SPROUT, StickerStage.forExplorerLevel(1))
        assertEquals(StickerStage.EXPLORER, StickerStage.forExplorerLevel(3))
        assertEquals(StickerStage.LEGEND, StickerStage.forExplorerLevel(10))
    }
}
