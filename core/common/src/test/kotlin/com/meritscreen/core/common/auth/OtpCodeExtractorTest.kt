package com.meritscreen.core.common.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpCodeExtractorTest {

    @Test
    fun extractsFromWatchingEmailBody() {
        val body = """
            Watching verification code

            Your code is: 169483

            This code expires in 10 minutes.
        """.trimIndent()
        assertEquals("169483", OtpCodeExtractor.extract(body))
    }

    @Test
    fun extractsFromMeritScreenEmailBody() {
        val body = """
            MeritScreen verification code

            Your code is: 169483

            This code expires in 10 minutes.
        """.trimIndent()
        assertEquals("169483", OtpCodeExtractor.extract(body))
    }

    @Test
    fun extractsFromSubjectStyleLine() {
        assertEquals(
            "482910",
            OtpCodeExtractor.extract("482910 is your MeritScreen verification code"),
        )
    }

    @Test
    fun extractsUnambiguousStandaloneCode() {
        assertEquals("654321", OtpCodeExtractor.extract("654321"))
    }

    @Test
    fun rejectsAmbiguousMultipleCodes() {
        assertNull(OtpCodeExtractor.extract("try 111111 or 222222"))
    }

    @Test
    fun rejectsPartialOrNonDigitNoise() {
        assertNull(OtpCodeExtractor.extract("12345"))
        assertNull(OtpCodeExtractor.extract(""))
        assertNull(OtpCodeExtractor.extract(null))
        assertNull(OtpCodeExtractor.extract("order #1234567 shipped"))
    }

    @Test
    fun prefersKeywordMatchOverOtherDigits() {
        assertEquals(
            "169483",
            OtpCodeExtractor.extract("Call 18005550199 — verification code: 169483"),
        )
    }
}
