package com.meritscreen.core.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.common.session.SessionRoleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildSafeAnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
    sessionRoleRepository: SessionRoleRepository,
    dispatchers: AppDispatchers,
) : AnalyticsTracker {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    @Volatile
    private var role: DeviceRole = DeviceRole.Unassigned

    init {
        scope.launch {
            sessionRoleRepository.role.collect { role = it }
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (role is DeviceRole.Child && !event.allowedOnChild) return
        firebaseAnalytics.logEvent(event.key, null)
    }
}
