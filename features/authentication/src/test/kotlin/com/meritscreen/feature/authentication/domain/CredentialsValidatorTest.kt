package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.common.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CredentialsValidatorTest {

    @Test
    fun rejectsBlankEmail() {
        assertEquals("Enter your email.", CredentialsValidator.emailError("  "))
    }

    @Test
    fun rejectsMalformedEmail() {
        assertNotNull(CredentialsValidator.emailError("not-an-email"))
    }

    @Test
    fun acceptsValidEmail() {
        assertNull(CredentialsValidator.emailError("parent@example.com"))
    }

    @Test
    fun isValidEmailReturnsExpectedBoolean() {
        org.junit.Assert.assertTrue(CredentialsValidator.isValidEmail("parent@example.com"))
        org.junit.Assert.assertFalse(CredentialsValidator.isValidEmail("invalid"))
    }

    @Test
    fun rejectsEmptyOrShortOtp() {
        assertEquals("Enter the 6-digit verification code.", CredentialsValidator.otpError(""))
        assertEquals("Enter all 6 digits of the verification code.", CredentialsValidator.otpError("123"))
    }

    @Test
    fun rejectsNonNumericOtp() {
        assertEquals("Enter all 6 digits of the verification code.", CredentialsValidator.otpError("12345a"))
    }

    @Test
    fun acceptsValid6DigitOtp() {
        assertNull(CredentialsValidator.otpError("123456"))
    }
}

