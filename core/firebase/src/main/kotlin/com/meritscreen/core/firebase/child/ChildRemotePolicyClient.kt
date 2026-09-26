package com.meritscreen.core.firebase.child

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy

data class ChildRemoteProfile(
    val childId: String,
    val familyId: String,
    val displayName: String,
    val ageBand: String,
    val avatarId: String,
    val language: String,
)

/**
 * Child-device Firestore reads for policy/appRules/profile.
 * Used only off the Home path (sync coordinator). Home reads Room.
 *
 * One-shot fetches only — never snapshot listeners. Continuous policy push uses FCM +
 * WorkManager pull (ARCHITECTURE.md). Dead listener APIs were removed in Phase 10.
 */
interface ChildRemotePolicyClient {
    suspend fun fetchPolicy(familyId: String, childId: String): ChildPolicy
    suspend fun fetchAppRules(familyId: String, childId: String): List<AppRule>
    suspend fun fetchProfile(familyId: String, childId: String): ChildRemoteProfile?
    /** Family-root Parent PIN hash — used to refresh offline PIN after a parent reset. */
    suspend fun fetchParentPinHash(familyId: String): String?
}
