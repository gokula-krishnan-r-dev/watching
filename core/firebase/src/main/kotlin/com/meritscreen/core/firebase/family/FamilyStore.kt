package com.meritscreen.core.firebase.family

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset

data class FamilyChildProfile(
    val childId: String,
    val displayName: String,
    val ageBand: AgeBand,
    val avatar: AvatarPreset,
    val language: String,
)

data class CreatedFamily(
    val familyId: String,
    val children: List<FamilyChildProfile>,
)

data class FamilyDraftChild(
    val localId: String,
    val name: String,
    val ageBand: AgeBand,
    val avatar: AvatarPreset,
    val language: String,
    /** Stable curriculum focus topic ids for [ageBand]; empty → catalog defaults. */
    val curriculumFocusIds: List<String> = emptyList(),
)

data class FamilyDraft(
    val familyName: String,
    val parentPinHash: String,
    val children: List<FamilyDraftChild>,
)

interface FamilyStore {
    suspend fun getUserFamilyId(uid: String): String?
    suspend fun listChildren(familyId: String): List<FamilyChildProfile>
    suspend fun createFamilyFromDraft(uid: String, email: String?, draft: FamilyDraft): CreatedFamily
    suspend fun updateParentPinHash(familyId: String, pinHash: String)
    fun observeDeviceCount(familyId: String, childId: String): kotlinx.coroutines.flow.Flow<Int>
}
