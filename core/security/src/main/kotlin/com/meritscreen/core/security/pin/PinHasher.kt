package com.meritscreen.core.security.pin

interface PinHasher {
    fun hash(pin: String): String
    fun verify(pin: String, storedHash: String): Boolean
}
