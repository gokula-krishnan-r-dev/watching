package com.meritscreen.core.common.logging

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimberAppLogger @Inject constructor() : AppLogger {
    override fun d(message: String, vararg extras: Pair<String, Any?>) {
        Timber.d(format(message, extras))
    }

    override fun i(message: String, vararg extras: Pair<String, Any?>) {
        Timber.i(format(message, extras))
    }

    override fun w(message: String, throwable: Throwable?, vararg extras: Pair<String, Any?>) {
        if (throwable == null) Timber.w(format(message, extras)) else Timber.w(throwable, format(message, extras))
    }

    override fun e(message: String, throwable: Throwable?, vararg extras: Pair<String, Any?>) {
        if (throwable == null) Timber.e(format(message, extras)) else Timber.e(throwable, format(message, extras))
    }

    private fun format(message: String, extras: Array<out Pair<String, Any?>>): String {
        if (extras.isEmpty()) return message
        val sanitized = extras.joinToString(prefix = " ", separator = " ") { (key, value) ->
            "$key=${LogSanitizer.sanitize(value)}"
        }
        return message + sanitized
    }
}
