package com.meritscreen.core.security.pin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pbkdf2PinHasherTest {

    private val hasher = Pbkdf2PinHasher()

    @Test
    fun roundTripVerifiesMatchingPin() {
        val hash = hasher.hash("2468")
        assertTrue(hasher.verify("2468", hash))
    }

    @Test
    fun rejectsWrongPin() {
        val hash = hasher.hash("2468")
        assertFalse(hasher.verify("0000", hash))
    }

    @Test
    fun samePinProducesDifferentSalts() {
        val first = hasher.hash("1357")
        val second = hasher.hash("1357")
        assertNotEquals(first, second)
        assertTrue(hasher.verify("1357", first))
        assertTrue(hasher.verify("1357", second))
    }

    @Test
    fun rejectsMalformedStoredHash() {
        assertFalse(hasher.verify("2468", "not-a-hash"))
    }
}
