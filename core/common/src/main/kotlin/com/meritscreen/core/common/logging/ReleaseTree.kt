package com.meritscreen.core.common.logging

import android.util.Log
import timber.log.Timber

class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val safeMessage = LogSanitizer.sanitize(message)
        if (t == null) {
            Log.println(priority, tag ?: "MeritScreen", safeMessage)
        } else {
            Log.println(priority, tag ?: "MeritScreen", "$safeMessage\n${t.javaClass.simpleName}")
        }
    }
}
