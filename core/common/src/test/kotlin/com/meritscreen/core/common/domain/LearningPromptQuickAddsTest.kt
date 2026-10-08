package com.meritscreen.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPromptQuickAddsTest {

    @Test
    fun earlyBand_excludesSchoolExamAndFractionsHints() {
        val labels = LearningPromptQuickAdds.forAgeBand(AgeBand.AGE_3_TO_6).map { it.label }
        assertTrue(labels.contains("Letter sounds"))
        assertTrue(labels.contains("Counting play"))
        assertTrue(labels.contains("Colors & shapes"))
        assertFalse(labels.contains("Weak in Math"))
        assertFalse(labels.contains("Board / exam focus"))
        assertFalse(labels.contains("Fast reader"))
    }

    @Test
    fun elementaryBand_includesMathAndReadingFoundations() {
        val labels = LearningPromptQuickAdds.forAgeBand(AgeBand.AGE_7_TO_9).map { it.label }
        assertTrue(labels.contains("Math practice"))
        assertTrue(labels.contains("Reading & phonics"))
        assertTrue(labels.contains("School curriculum"))
        assertFalse(labels.contains("Letter sounds"))
        assertFalse(labels.contains("Coding & logic"))
    }

    @Test
    fun middleBand_includesExamAndLogicHints() {
        val labels = LearningPromptQuickAdds.forAgeBand(AgeBand.AGE_10_TO_12).map { it.label }
        assertTrue(labels.contains("Weak in Math"))
        assertTrue(labels.contains("Board / exam focus"))
        assertTrue(labels.contains("Coding & logic"))
        assertTrue(labels.contains("Fast reader"))
        assertFalse(labels.contains("Colors & shapes"))
        assertFalse(labels.contains("Letter sounds"))
    }

    @Test
    fun forAgeBand_hidesChipsAlreadyPresentInText() {
        val band = AgeBand.AGE_7_TO_9
        val all = LearningPromptQuickAdds.forAgeBand(band)
        val first = all.first()
        val remaining = LearningPromptQuickAdds.forAgeBand(band, first.snippet)
        assertFalse(remaining.any { it.id == first.id })
        assertEquals(all.size - 1, remaining.size)
    }

    @Test
    fun snippets_passSanitizer() {
        AgeBand.entries.forEach { band ->
            LearningPromptQuickAdds.forAgeBand(band).forEach { chip ->
                val sanitized = CustomPromptSanitizer.sanitize(chip.snippet)
                assertEquals(
                    "Quick-add snippet must survive sanitizer: ${chip.id}",
                    chip.snippet,
                    sanitized,
                )
            }
        }
    }
}
