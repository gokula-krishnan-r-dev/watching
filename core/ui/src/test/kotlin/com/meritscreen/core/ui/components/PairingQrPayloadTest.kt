package com.meritscreen.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrPayloadTest {

    @Test
    fun parsesDeepLinkWithCodeAndSecret() {
        val parsed = PairingQrPayload.parse("meritscreen://pair?c=542997&s=abc123def")
        assertEquals("542997", parsed?.code)
        assertEquals("abc123def", parsed?.secret)
    }

    @Test
    fun parsesDeepLinkCodeOnly() {
        val parsed = PairingQrPayload.parse("meritscreen://pair?c=842916")
        assertEquals("842916", parsed?.code)
        assertNull(parsed?.secret)
    }

    @Test
    fun parsesLooseSixDigitCode() {
        val parsed = PairingQrPayload.parse("542997")
        assertEquals("542997", parsed?.code)
        assertNull(parsed?.secret)
    }

    @Test
    fun rejectsBlank() {
        assertNull(PairingQrPayload.parse(null))
        assertNull(PairingQrPayload.parse("   "))
        assertNull(PairingQrPayload.parse("not-a-code"))
    }
}
