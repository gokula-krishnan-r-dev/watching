package com.meritscreen.core.common.session

import kotlinx.serialization.Serializable

/** Cached parent cloud session. Sign-out clears this; it does not unpair child devices. */
@Serializable
data class ParentSession(
    val uid: String,
    val familyId: String,
    val childIds: List<String> = emptyList(),
)

interface ParentSessionRepository {
    val session: kotlinx.coroutines.flow.Flow<ParentSession?>
    suspend fun current(): ParentSession?
    suspend fun set(session: ParentSession)
    suspend fun clear()
}
