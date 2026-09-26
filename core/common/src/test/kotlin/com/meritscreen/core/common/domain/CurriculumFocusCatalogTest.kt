package com.meritscreen.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumFocusCatalogTest {

    @Test
    fun optionsFor_earlyBand_excludesFormalMathAndFinance() {
        val labels = CurriculumFocusCatalog.optionsFor(AgeBand.AGE_3_TO_6).map { it.displayLabel }
        assertTrue(labels.contains("Colors & Shapes"))
        assertTrue(labels.contains("Letters & Sounds"))
        assertFalse(labels.contains("Math & Numbers"))
        assertFalse(labels.contains("Financial Literacy"))
    }

    @Test
    fun optionsFor_middleBand_includesFinanceAndCoding() {
        val labels = CurriculumFocusCatalog.optionsFor(AgeBand.AGE_10_TO_12).map { it.displayLabel }
        assertTrue(labels.contains("Financial Literacy"))
        assertTrue(labels.contains("Coding & Logic"))
        assertFalse(labels.contains("Colors & Shapes"))
        assertFalse(labels.contains("Reading & Phonics"))
    }

    @Test
    fun reconcile_replacesInvalidIdsWithDefaults() {
        val result = CurriculumFocusCatalog.reconcile(
            AgeBand.AGE_3_TO_6,
            listOf("math_numbers", "financial_literacy"),
        )
        assertEquals(CurriculumFocusCatalog.defaultIds(AgeBand.AGE_3_TO_6), result)
    }

    @Test
    fun reconcile_keepsValidOverlapWhenSwitchingBands() {
        val fromElementary = listOf("math_numbers", "science_nature", "reading_phonics")
        val result = CurriculumFocusCatalog.reconcile(AgeBand.AGE_10_TO_12, fromElementary)
        assertEquals(listOf("math_numbers", "science_nature"), result)
    }

    @Test
    fun toggle_enforcesMinAndMaxSelection() {
        val band = AgeBand.AGE_7_TO_9
        val one = CurriculumFocusCatalog.defaultIds(band).take(1)
        // Cannot deselect last remaining topic
        assertEquals(one, CurriculumFocusCatalog.toggle(band, one, one.first()))

        var selected = CurriculumFocusCatalog.defaultIds(band)
        CurriculumFocusCatalog.optionsFor(band).map { it.id }.forEach { id ->
            selected = CurriculumFocusCatalog.toggle(band, selected, id)
        }
        assertTrue(selected.size <= CurriculumFocusCatalog.MAX_SELECTION)
        assertTrue(selected.size >= CurriculumFocusCatalog.MIN_SELECTION)
    }
}
