package com.meritscreen.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun redactsEmailLikeValues() {
        val sanitized = LogSanitizer.sanitize("parent@example.com")
        assertEquals("<redacted>", sanitized)
    }

    @Test
    fun redactsJwtLikeTokens() {
        val sanitized = LogSanitizer.sanitize("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaaa")
        assertEquals("<redacted>", sanitized)
    }

    @Test
    fun keepsShortSafeMessages() {
        val sanitized = LogSanitizer.sanitize("policy_sync_completed")
        assertEquals("policy_sync_completed", sanitized)
        assertFalse(sanitized.contains("@"))
        assertTrue(sanitized.isNotBlank())
    }

    @Test
    fun redactsSensitiveKeys() {
        assertEquals("<redacted>", LogSanitizer.sanitizeKey("idToken", "abc"))
        assertEquals("<redacted>", LogSanitizer.sanitizeKey("pin", "1234"))
        assertEquals("<redacted>", LogSanitizer.sanitizeKey("customToken", "xyz"))
        assertEquals("<redacted>", LogSanitizer.sanitizeKey("pairingCode", "123456"))
    }

    @Test
    fun redactsPairingDeepLinks() {
        assertEquals("<redacted>", LogSanitizer.sanitize("meritscreen://pair?c=123456&s=abcdef"))
    }
}
