package com.meritscreen.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.meritscreen.core.common.logging.LogSanitizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    override fun record(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(LogSanitizer.sanitize(message))
    }

    override fun setDeviceRole(role: String) {
        crashlytics.setCustomKey("device_role", role)
    }
}
