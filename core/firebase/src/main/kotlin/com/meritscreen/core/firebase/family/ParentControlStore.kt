package com.meritscreen.core.firebase.family

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.ExplorerProgressSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.QuizAttemptSummary
import com.meritscreen.core.common.domain.StickerUnlockSummary
import com.meritscreen.core.common.domain.TopicSkillSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.domain.WeakConceptHint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class FamilyMeta(
    val familyId: String,
    val name: String,
    val ownerUid: String,
)

/**
 * Parent-facing Firestore reads/writes for policy, allowlist, usage summaries, and devices.
 * One-shot usage reads only — never attach listeners on the child Home path.
 */
interface ParentControlStore {
    suspend fun getFamilyMeta(familyId: String): FamilyMeta?
    suspend fun listChildren(familyId: String): List<FamilyChildProfile>
    suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile
    suspend fun updateChild(familyId: String, childId: String, child: FamilyDraftChild): FamilyChildProfile
    suspend fun deleteChild(familyId: String, childId: String)

    suspend fun getPolicy(familyId: String, childId: String): ChildPolicy
    suspend fun updatePolicy(familyId: String, childId: String, policy: ChildPolicy)
    suspend fun setChildPaused(familyId: String, childId: String, paused: Boolean)
    suspend fun addBonusTime(familyId: String, childId: String, bonusMinutes: Int)

    suspend fun listAppRules(familyId: String, childId: String): List<AppRule>
    suspend fun upsertAppRule(familyId: String, childId: String, rule: AppRule)
    suspend fun deleteAppRule(familyId: String, childId: String, appId: String)

    suspend fun listUsageDays(familyId: String, childId: String, limit: Int): List<UsageDaySummary>
    suspend fun listQuizAttempts(familyId: String, childId: String, limit: Int): List<QuizAttemptSummary>
    suspend fun getSkillState(familyId: String, childId: String): List<TopicSkillSummary>
    /** One-shot sticker unlocks for parent Child Detail (never on child Home). */
    suspend fun listStickerUnlocks(familyId: String, childId: String, limit: Int): List<StickerUnlockSummary> =
        emptyList()
    /** One-shot explorer progress for parent Child Detail. */
    suspend fun getExplorerProgress(familyId: String, childId: String): ExplorerProgressSummary? = null
    suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary>
    suspend fun setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Boolean)

    /** Merged, deduped installed-app inventory across this child's non-revoked devices. */
    suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary>

    /**
     * Live inventory for the parent Allowlist screen only (screen-scoped listener).
     * Never attach this on the child Home path.
     */
    fun observeInstalledApps(familyId: String, childId: String): Flow<List<InstalledAppSummary>>

    /**
     * Live device docs for parent Child Detail / Devices (screen-scoped listener).
     * Powers connection status from `lastSeenAt` heartbeats. Never on the child Home path.
     */
    fun observeDevices(familyId: String, childId: String): Flow<List<DeviceSummary>>

    fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>>
    fun observeUsageDays(familyId: String, childId: String, limit: Int): Flow<List<UsageDaySummary>> = flowOf(emptyList())
    fun observeQuizAttempts(familyId: String, childId: String, limit: Int): Flow<List<QuizAttemptSummary>> = flowOf(emptyList())
    fun observeSkillState(familyId: String, childId: String): Flow<List<TopicSkillSummary>> = flowOf(emptyList())
    fun observePolicy(familyId: String, childId: String): Flow<ChildPolicy?> = flowOf(null)
    fun observeAppRules(familyId: String, childId: String): Flow<List<AppRule>> = flowOf(emptyList())

    suspend fun deleteFamily(familyId: String)

    suspend fun registerParentPushToken(
        familyId: String,
        installationId: String,
        uid: String,
        fcmToken: String,
        platform: String = "android",
        model: String = "",
        appVersion: String = "",
        notificationPrefs: Map<String, Boolean> = emptyMap(),
    ) {}
}
