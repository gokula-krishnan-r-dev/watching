package com.meritscreen.feature.child.data

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ChildPolicyMapper
import com.meritscreen.core.database.child.ChildAppRuleEntity
import com.meritscreen.core.database.child.ChildPolicyDao
import com.meritscreen.core.database.child.ChildPolicyEntity
import com.meritscreen.core.database.child.ChildProfileCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ChildLocalProfile(
    val childId: String,
    val familyId: String,
    val displayName: String,
    val ageBand: String,
    val avatarId: String,
    val language: String,
)

@Singleton
class ChildPolicyRepository @Inject constructor(
    private val dao: ChildPolicyDao,
) {
    fun observePolicy(childId: String): Flow<ChildPolicy> =
        dao.observePolicy(childId).map { it?.toDomain() ?: ChildPolicy() }

    suspend fun getPolicy(childId: String): ChildPolicy =
        dao.getPolicy(childId)?.toDomain() ?: ChildPolicy()

    fun observeAppRules(childId: String): Flow<List<AppRule>> =
        dao.observeAppRules(childId).map { list -> list.map { it.toDomain() } }

    suspend fun listAppRules(childId: String): List<AppRule> =
        dao.listAppRules(childId).map { it.toDomain() }

    fun observeProfile(childId: String): Flow<ChildLocalProfile?> =
        dao.observeProfile(childId).map { it?.toDomain() }

    suspend fun getProfile(childId: String): ChildLocalProfile? =
        dao.getProfile(childId)?.toDomain()

    suspend fun savePolicy(familyId: String, childId: String, policy: ChildPolicy) {
        dao.upsertPolicy(policy.toEntity(familyId, childId))
    }

    suspend fun saveAppRules(childId: String, rules: List<AppRule>) {
        dao.replaceAppRules(childId, rules.map { it.toEntity(childId) })
    }

    suspend fun saveProfile(profile: ChildLocalProfile) {
        dao.upsertProfile(
            ChildProfileCacheEntity(
                childId = profile.childId,
                familyId = profile.familyId,
                displayName = profile.displayName,
                ageBand = profile.ageBand,
                avatarId = profile.avatarId,
                language = profile.language,
            ),
        )
    }
}

private fun ChildPolicyEntity.toDomain(): ChildPolicy = ChildPolicyMapper.fromMap(
    mapOf(
        "quizMode" to quizMode,
        "allowRetryDuringCooldown" to allowRetryDuringCooldown,
        "dailyCeilingMinutes" to dailyCeilingMinutes,
        "questionsPerQuiz" to questionsPerQuiz,
        "passScorePercent" to passScorePercent,
        "rewardsEnabled" to rewardsEnabled,
        "weekendBonusEnabled" to weekendBonusEnabled,
        "extraMinutesOnPass" to extraMinutesOnPass,
        "aiQuizzesEnabled" to aiQuizzesEnabled,
        "adaptiveDifficultyEnabled" to adaptiveDifficultyEnabled,
        "showExplanations" to showExplanations,
        "defaultBlockMinutes" to defaultBlockMinutes,
        "defaultCooldownMinutes" to defaultCooldownMinutes,
        "emergencyApps" to emergencyAppsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        "paused" to paused,
        "bonusMinutesToday" to bonusMinutesToday,
        "gradeStandard" to gradeStandard,
        "region" to region,
        "customPromptGuidelines" to customPromptGuidelines,
        "curriculumFocusIds" to curriculumFocusIdsCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
    ),
)

private fun ChildPolicy.toEntity(familyId: String, childId: String): ChildPolicyEntity =
    ChildPolicyEntity(
        childId = childId,
        familyId = familyId,
        quizMode = quizMode.storageKey,
        allowRetryDuringCooldown = allowRetryDuringCooldown,
        dailyCeilingMinutes = dailyCeilingMinutes,
        questionsPerQuiz = questionsPerQuiz,
        passScorePercent = passScorePercent,
        rewardsEnabled = rewardsEnabled,
        weekendBonusEnabled = weekendBonusEnabled,
        extraMinutesOnPass = extraMinutesOnPass,
        aiQuizzesEnabled = aiQuizzesEnabled,
        adaptiveDifficultyEnabled = adaptiveDifficultyEnabled,
        showExplanations = showExplanations,
        defaultBlockMinutes = defaultBlockMinutes,
        defaultCooldownMinutes = defaultCooldownMinutes,
        emergencyAppsCsv = emergencyApps.joinToString(","),
        updatedAtEpochMs = System.currentTimeMillis(),
        gradeStandard = gradeStandard,
        region = region.storageKey,
        customPromptGuidelines = customPromptGuidelines,
        curriculumFocusIdsCsv = curriculumFocusIds.joinToString(","),
        paused = paused,
        bonusMinutesToday = bonusMinutesToday,
    )

private fun ChildAppRuleEntity.toDomain(): AppRule = AppRule(
    appId = appId,
    packageOrBundleId = packageOrBundleId,
    displayName = displayName,
    allowed = allowed,
    blockMinutes = blockMinutes,
    grantOnPassMinutes = grantOnPassMinutes,
    cooldownMinutes = cooldownMinutes,
    isEmergency = isEmergency,
)

private fun AppRule.toEntity(childId: String): ChildAppRuleEntity = ChildAppRuleEntity(
    appId = appId,
    childId = childId,
    packageOrBundleId = packageOrBundleId,
    displayName = displayName,
    allowed = allowed,
    blockMinutes = blockMinutes,
    grantOnPassMinutes = grantOnPassMinutes,
    cooldownMinutes = cooldownMinutes,
    isEmergency = isEmergency,
)

private fun ChildProfileCacheEntity.toDomain(): ChildLocalProfile = ChildLocalProfile(
    childId = childId,
    familyId = familyId,
    displayName = displayName,
    ageBand = ageBand,
    avatarId = avatarId,
    language = language,
)
