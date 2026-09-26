package com.meritscreen.core.common.domain

/** Runtime phase of the child session engine (docs/07). Fail is always device-wide. */
enum class SessionPhase {
    Idle,
    InBlock,
    QuizDue,
    Shielded,
}
