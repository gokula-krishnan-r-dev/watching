package com.meritscreen.core.common.ui

import com.meritscreen.core.common.error.AppError

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
}

fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading
