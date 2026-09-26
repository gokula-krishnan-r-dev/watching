package com.meritscreen.core.common.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameFromEmailTest {

    @Test
    fun `strips digits from email local part and title-cases`() {
        assertEquals("Gokulakrishnanr", DisplayNameFromEmail.fromEmail("gokulakrishnanr812@gmail.com"))
        assertEquals("Sarah", DisplayNameFromEmail.fromEmail("sarah@example.com"))
        assertEquals("Johndoe", DisplayNameFromEmail.fromEmail("john.doe123@school.edu"))
    }

    @Test
    fun `falls back when local part has no letters`() {
        assertEquals("there", DisplayNameFromEmail.fromEmail("12345@example.com"))
        assertEquals("there", DisplayNameFromEmail.fromEmail("@example.com"))
        assertEquals("there", DisplayNameFromEmail.fromEmail(""))
        assertEquals("there", DisplayNameFromEmail.fromEmail(null))
        assertEquals("Parent", DisplayNameFromEmail.fromEmail("999@", fallback = "Parent"))
    }

    @Test
    fun `fromAuth prefers cleaned display name over email`() {
        assertEquals(
            "Ada Lovelace",
            DisplayNameFromEmail.fromAuth("Ada Lovelace", "ada99@example.com"),
        )
        assertEquals(
            "Ada",
            DisplayNameFromEmail.fromAuth("Ada123", "ignored@example.com"),
        )
        assertEquals(
            "Gokulakrishnanr",
            DisplayNameFromEmail.fromAuth(null, "gokulakrishnanr812@gmail.com"),
        )
        assertEquals(
            "Gokulakrishnanr",
            DisplayNameFromEmail.fromAuth("   ", "gokulakrishnanr812@gmail.com"),
        )
    }
}
