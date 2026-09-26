package com.meritscreen.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.analytics.CrashReporter
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.session.SessionRoleRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.authentication.domain.SignOutParentUseCase
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionRoleRepository: SessionRoleRepository,
    private val parentSessionRepository: ParentSessionRepository,
    private val childPairingStore: ChildPairingStore,
    private val authClient: AuthClient,
    private val signOutParentUseCase: SignOutParentUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val crashReporter: CrashReporter,
    val googleIdTokenClient: GoogleIdTokenClient,
) : ViewModel() {

    private val sessionReady = MutableStateFlow(false)

    /**
     * null while cold-start reconciliation runs so the splash/loading shell stays up until
     * Firebase Auth, parent session, and child pairing credentials agree with [DeviceRole].
     */
    val role: StateFlow<DeviceRole?> = combine(
        sessionRoleRepository.role,
        sessionReady,
    ) { role, ready ->
        if (ready) role else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val keepSplash: StateFlow<Boolean> = role
        .combine(sessionReady) { current, ready -> current == null || !ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(800L) {
                reconcileSession()
            }
            sessionReady.value = true
            val role = sessionRoleRepository.role.first()
            crashReporter.setDeviceRole(roleAnalyticsLabel(role))
            analyticsTracker.track(AnalyticsEvent.AppOpen)
        }
    }

    fun selectRole(role: DeviceRole) {
        viewModelScope.launch {
            // Track while still Unassigned so the child-safe gate does not drop RoleSelected.
            analyticsTracker.track(AnalyticsEvent.RoleSelected)
            sessionRoleRepository.setRole(role)
            crashReporter.setDeviceRole(roleAnalyticsLabel(role))
        }
    }

    fun resetRole() {
        viewModelScope.launch { sessionRoleRepository.clear() }
    }

    fun signOutParent() {
        viewModelScope.launch { signOutParentUseCase() }
    }

    /**
     * Aligns persisted [DeviceRole] with Firebase Auth + local session stores.
     * Parent sign-out clears role; child pairing credentials keep Child offline-capable.
     */
    private suspend fun reconcileSession() {
        val storedRole = sessionRoleRepository.role.first()
        val parentSession = parentSessionRepository.current()
        val childCredential = childPairingStore.get()
        // Fast path: use cached local auth user first to prevent blocking cold start on network
        val authUser = authClient.currentUser ?: runCatching {
            kotlinx.coroutines.withTimeoutOrNull(1000L) {
                authClient.refreshCurrentUser()
            }
        }.getOrNull()

        // Background non-blocking token refresh if cached user was used
        if (authClient.currentUser != null) {
            viewModelScope.launch {
                runCatching { authClient.refreshCurrentUser() }
            }
        }

        when {
            childCredential != null && (authUser == null || authUser.isChildDevice) -> {
                if (storedRole != DeviceRole.Child) {
                    sessionRoleRepository.setRole(DeviceRole.Child)
                }
            }
            parentSession != null && authUser != null && !authUser.isChildDevice -> {
                if (storedRole != DeviceRole.Parent) {
                    sessionRoleRepository.setRole(DeviceRole.Parent)
                }
            }
            storedRole == DeviceRole.Parent && (authUser == null || authUser.isChildDevice) -> {
                parentSessionRepository.clear()
                sessionRoleRepository.clear()
            }
            storedRole == DeviceRole.Child && childCredential == null -> {
                sessionRoleRepository.clear()
            }
        }
    }

    private fun roleAnalyticsLabel(role: DeviceRole): String = when (role) {
        DeviceRole.Unassigned -> "unassigned"
        DeviceRole.Parent -> "parent"
        DeviceRole.Child -> "child"
    }
}
