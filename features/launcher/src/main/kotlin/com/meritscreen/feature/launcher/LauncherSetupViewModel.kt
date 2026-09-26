package com.meritscreen.feature.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LauncherSetupUi(
    val isDefaultHome: Boolean,
    val roleRequestSupported: Boolean,
    val userMessage: String? = null,
)

@HiltViewModel
class LauncherSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(currentStatus())
    val uiState: StateFlow<LauncherSetupUi> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = currentStatus(userMessage = _uiState.value.userMessage?.takeIf {
            // Clear success/error noise once Home is granted.
            !HomeRoleManager.isDefaultHome(context)
        })
        if (HomeRoleManager.isDefaultHome(context)) {
            _uiState.update { it.copy(isDefaultHome = true, userMessage = null) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Intent for the Activity Result launcher (RoleManager requires for-result). */
    fun setupIntent(): Intent = HomeRoleManager.createSetupIntent(context)

    fun onSetupResult(resultCode: Int) {
        refresh()
        if (HomeRoleManager.isDefaultHome(context)) {
            _uiState.update { it.copy(userMessage = null) }
            return
        }
        // RESULT_CANCELED is normal if the user backed out; only nudge when still not Home.
        if (resultCode == android.app.Activity.RESULT_OK) {
            _uiState.update {
                it.copy(
                    userMessage = "Android still has another Home app selected. " +
                        "Tap Set as Home app again and choose Watching.",
                )
            }
        }
    }

    fun onSetupFailed(detail: String? = null) {
        _uiState.update {
            it.copy(
                userMessage = detail
                    ?: "Couldn't open Home-app settings. Open Settings → Apps → Default apps → Home app and pick Watching.",
            )
        }
    }

    private fun currentStatus(userMessage: String? = null) = LauncherSetupUi(
        isDefaultHome = HomeRoleManager.isDefaultHome(context),
        roleRequestSupported = HomeRoleManager.isRoleRequestSupported(context),
        userMessage = userMessage,
    )
}
