package com.meritscreen.feature.onboarding.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeScheduleTest {

    @Test
    fun overnightWindowCalculatesRestAcrossMidnight() {
        val window = BedtimeWindow("8:30 PM", "7:00 AM")
        assertEquals(10 * 60 + 30, window.restMinutes)
        assertEquals("10h 30m", window.restLabel())
    }

    @Test
    fun sameStartAndEndIsInvalid() {
        val error = BedtimeSchedule.validate(BedtimeWindow("8:00 PM", "8:00 PM"))
        assertEquals("Start and end times must be different.", error)
    }

    @Test
    fun tooShortRestIsInvalid() {
        val error = BedtimeSchedule.validate(BedtimeWindow("11:00 PM", "2:00 AM"))
        assertEquals("Rest window must be at least 6 hours.", error)
    }

    @Test
    fun applyToWeekdaysLeavesWeekendUntouched() {
        val base = BedtimeSchedule.defaultSchedule()
        val updated = BedtimeSchedule.applyToWeekdays(base, BedtimeWindow("9:00 PM", "6:30 AM"))
        assertEquals("9:00 PM", updated.getValue(WeekDay.MON).startLabel)
        assertEquals("8:30 PM", updated.getValue(WeekDay.SAT).startLabel)
    }

    @Test
    fun detectModeRecognizesWeekdayWeekendSplit() {
        var schedule = BedtimeSchedule.defaultSchedule()
        schedule = BedtimeSchedule.applyToWeekdays(schedule, BedtimeWindow("8:30 PM", "7:00 AM"))
        schedule = BedtimeSchedule.applyToWeekend(schedule, BedtimeWindow("9:30 PM", "8:00 AM"))
        assertEquals(BedtimeScheduleMode.WEEKDAYS_WEEKEND, BedtimeSchedule.detectMode(schedule))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        val minutes = parseTimeLabelToMinutes("8:30 PM")
        assertEquals(20 * 60 + 30, minutes)
        assertEquals("8:30 PM", formatMinutesToTimeLabel(minutes))
        assertNull(BedtimeSchedule.validateSchedule(BedtimeSchedule.defaultSchedule()))
        assertTrue(BedtimeSchedule.detectMode(BedtimeSchedule.defaultSchedule()) == BedtimeScheduleMode.EVERY_DAY)
    }
}
