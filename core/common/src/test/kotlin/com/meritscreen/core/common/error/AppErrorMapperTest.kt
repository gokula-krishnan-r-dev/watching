package com.meritscreen.core.common.error

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class AppErrorMapperTest {

    @Test
    fun mapsUnknownHostToNetwork() {
        val error = AppErrorMapper.from(UnknownHostException("offline"))
        assertTrue(error is AppError.Network)
    }

    @Test
    fun mapsIoExceptionToNetwork() {
        val error = AppErrorMapper.from(IOException("reset"))
        assertTrue(error is AppError.Network)
    }

    @Test
    fun mapsAppErrorExceptionDirectly() {
        val original = AppError.Validation("PIN must be 4 to 6 digits")
        val error = AppErrorMapper.from(AppErrorException(original))
        assertTrue(error is AppError.Validation)
        assertTrue(error.userMessage.contains("PIN"))
    }

    @Test
    fun neverExposesRawUnknownMessages() {
        val error = AppErrorMapper.from(IllegalStateException("FirebaseFirestoreException: PERMISSION_DENIED"))
        assertTrue(error is AppError.Unknown)
        assertTrue(!error.userMessage.contains("PERMISSION_DENIED"))
        assertTrue(!error.userMessage.contains("Firebase"))
    }
}
