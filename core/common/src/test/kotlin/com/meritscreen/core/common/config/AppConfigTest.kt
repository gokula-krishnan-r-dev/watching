package com.meritscreen.core.common.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun childrenLimitIsConfigurableAndPositive() {
        assertEquals(5, AppConfig.MAX_CHILDREN_PER_PARENT)
        assertTrue(AppConfig.MAX_CHILDREN_PER_PARENT in 1..20)
    }

    @Test
    fun pinLengthMatchesProductSpec() {
        assertEquals(4, AppConfig.PARENT_PIN_MIN_LENGTH)
        assertEquals(4, AppConfig.PARENT_PIN_MAX_LENGTH)
    }

    @Test
    fun pairingAndOtpTunablesMatchProductSpec() {
        assertEquals(10, AppConfig.PAIRING_TOKEN_TTL_MINUTES)
        assertEquals(6, AppConfig.PAIRING_CODE_LENGTH)
        assertEquals(6, AppConfig.EMAIL_OTP_LENGTH)
        assertEquals(10, AppConfig.EMAIL_OTP_TTL_MINUTES)
        assertEquals(42, AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS)
        assertEquals(5, AppConfig.EMAIL_OTP_MAX_ATTEMPTS)
    }


    @Test
    fun reportsWindowsMatchProductSpec() {
        assertEquals(1, AppConfig.REPORTS_TODAY_DAYS)
        assertEquals(7, AppConfig.REPORTS_DEFAULT_DAYS)
        assertEquals(30, AppConfig.REPORTS_EXTENDED_DAYS)
        assertEquals(90, AppConfig.REPORTS_MAX_DAYS)
        assertTrue(AppConfig.REPORTS_MAX_ATTEMPTS >= AppConfig.REPORTS_RECENT_ATTEMPTS)
    }

    @Test
    fun performanceFlushIntervalsMatchDocsBudget() {
        assertTrue(AppConfig.USAGE_FLUSH_INTERVAL_SECONDS in 30..60)
        assertTrue(AppConfig.SESSION_PERSIST_INTERVAL_SECONDS in 30..60)
        assertTrue(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS in 100L..1_000L)
        assertTrue(AppConfig.DEVICE_ONLINE_THRESHOLD_MINUTES in 5..45)
        assertTrue(AppConfig.CHILD_DETAIL_DEVICES_DEBOUNCE_MS in 100L..1_000L)
    }

    @Test
    fun adaptiveQuizTunablesMatchDocs() {
        assertEquals(30, AppConfig.QUIZ_LOCKOUT_SECONDS)
        assertEquals(12, AppConfig.QUIZ_PACK_LOW_THRESHOLD)
        assertEquals(12, AppConfig.QUIZ_PACK_GENERATE_COUNT)
        assertEquals("gemini-3.7-flash", AppConfig.GEMINI_FLASH_MODEL)
        assertEquals("gemini-2.5-flash", AppConfig.GEMINI_FLASH_MODEL_FALLBACKS.first())
        assertEquals("us-central1", AppConfig.GEMINI_VERTEX_LOCATION)
    }

    @Test
    fun sessionChunkPresetsCoverCushionAndCustom() {
        assertEquals(2, AppConfig.SESSION_CHUNK_MIN_MINUTES)
        assertTrue(AppConfig.SESSION_CHUNK_CUSHION_MINUTES.containsAll(listOf(2, 3, 5)))
        assertTrue(AppConfig.SESSION_CHUNK_STANDARD_MINUTES.containsAll(listOf(20, 30, 45)))
        assertTrue(AppConfig.SESSION_CHUNK_EXTENDED_MINUTES.contains(50))
    }
}
