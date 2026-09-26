package com.meritscreen.feature.parent

import com.meritscreen.feature.parent.ui.formatRelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRelativeTimeTest {

    @Test
    fun `null timestamp reads as never`() {
        assertEquals("never", formatRelativeTime(null))
    }

    @Test
    fun `recent timestamp reads as just now`() {
        assertEquals("just now", formatRelativeTime(System.currentTimeMillis() - 5_000))
    }

    @Test
    fun `minutes ago`() {
        assertEquals("5 min ago", formatRelativeTime(System.currentTimeMillis() - 5 * 60_000))
    }

    @Test
    fun `hours ago`() {
        assertEquals("2 hr ago", formatRelativeTime(System.currentTimeMillis() - 2 * 60 * 60_000))
    }

    @Test
    fun `days ago`() {
        assertEquals("3 d ago", formatRelativeTime(System.currentTimeMillis() - 3L * 24 * 60 * 60_000))
    }
}
