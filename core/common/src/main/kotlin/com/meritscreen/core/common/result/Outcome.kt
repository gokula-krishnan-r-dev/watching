package com.meritscreen.core.common.result

import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T> runOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(AppErrorMapper.from(t))
}

suspend inline fun <T> runSuspendOutcome(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(AppErrorMapper.from(t))
}
