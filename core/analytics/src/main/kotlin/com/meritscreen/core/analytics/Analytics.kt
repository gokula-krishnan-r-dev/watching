package com.meritscreen.core.analytics

interface CrashReporter {
    fun record(throwable: Throwable)
    fun log(message: String)
    fun setDeviceRole(role: String)
}

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

enum class AnalyticsEvent(val key: String, val allowedOnChild: Boolean) {
    AppOpen("app_open", true),
    RoleSelected("role_selected", false),
    PolicySyncCompleted("policy_sync_completed", true),
    QuizCompleted("quiz_completed", true),
    ParentSignedIn("parent_signed_in", false),
    ParentSignedOut("parent_signed_out", false),
    ChildDevicePaired("child_device_paired", true),
    ChildDeviceUnpaired("child_device_unpaired", true),
    /** Parent opened P17 reports — never fired on a child device. */
    ReportsViewed("reports_viewed", false),
}
