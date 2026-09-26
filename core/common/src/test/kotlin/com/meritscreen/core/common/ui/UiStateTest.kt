package com.meritscreen.core.common.ui

import com.meritscreen.core.common.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    @Test
    fun loadingIsLoading() {
        assertTrue(UiState.Loading.isLoading())
    }

    @Test
    fun successHoldsData() {
        val state = UiState.Success(listOf("a", "b"))
        assertEquals(listOf("a", "b"), state.data)
    }

    @Test
    fun errorHoldsUserFacingError() {
        val state = UiState.Error(AppError.Network())
        assertTrue(state.error is AppError.Network)
    }
}
