package com.meritscreen.core.common.logging

interface AppLogger {
    fun d(message: String, vararg extras: Pair<String, Any?>)
    fun i(message: String, vararg extras: Pair<String, Any?>)
    fun w(message: String, throwable: Throwable? = null, vararg extras: Pair<String, Any?>)
    fun e(message: String, throwable: Throwable? = null, vararg extras: Pair<String, Any?>)
}
