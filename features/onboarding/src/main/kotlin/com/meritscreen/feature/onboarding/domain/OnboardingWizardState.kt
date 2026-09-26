package com.meritscreen.feature.onboarding.domain

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.firebase.family.FamilyDraft
import com.meritscreen.core.firebase.family.FamilyDraftChild
import java.security.MessageDigest

/**
 * Shared in-memory state tracking the parent's progressive configuration across the
 * 12-step onboarding wizard.
 */
data class OnboardingWizardState(
    val familyName: String = "The Family Space",
    val childName: String = "Leo",
    val ageBand: AgeBand = AgeBand.AGE_7_TO_9,
    val grade: String = "Grade 2",
    val avatar: AvatarPreset = AvatarPreset.FOX,
    val curriculumFocusIds: List<String> = CurriculumFocusCatalog.defaultIds(AgeBand.AGE_7_TO_9),
    val dailyCeilingMinutes: Int = 90,
    val quizFrequencyMinutes: Int = 30,
    val bedtimeEnabled: Boolean = true,
    val bedtimeStart: String = "8:30 PM",
    val bedtimeEnd: String = "7:00 AM",
    val defaultCooldownMinutes: Int = 10,
    val pairingCode: String = "842 901",
    val isPaired: Boolean = false,
    val pairedDeviceModel: String = "Leo's Tablet (Galaxy Tab)",
    val appRules: List<AppRule> = defaultOnboardingAppRules(),
    val globalScreenTimeLimitEnabled: Boolean = true,
    val parentPin: String = "",
    val aiPromptContext: String = "",
) {
    fun toFamilyDraft(): FamilyDraft {
        val pinHash = if (parentPin.isNotBlank()) {
            MessageDigest.getInstance("SHA-256")
                .digest(parentPin.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } else {
            ""
        }
        return FamilyDraft(
            familyName = familyName,
            parentPinHash = pinHash,
            children = listOf(
                FamilyDraftChild(
                    localId = "c_onboarding_${System.currentTimeMillis()}",
                    name = childName,
                    ageBand = ageBand,
                    avatar = avatar,
                    language = "en",
                    curriculumFocusIds = CurriculumFocusCatalog.reconcile(ageBand, curriculumFocusIds),
                ),
            ),
        )
    }

    fun toChildPolicy(): ChildPolicy = ChildPolicy(
        dailyCeilingMinutes = dailyCeilingMinutes,
        paused = false,
        questionsPerQuiz = 2,
        passScorePercent = 80,
        defaultCooldownMinutes = defaultCooldownMinutes,
        curriculumFocusIds = CurriculumFocusCatalog.reconcile(ageBand, curriculumFocusIds),
    )
}

fun defaultOnboardingAppRules(): List<AppRule> = listOf(
    AppRule(
        appId = "duo_abc",
        packageOrBundleId = "com.duolingo.kids",
        displayName = "Duo ABC",
        allowed = true,
        blockMinutes = 0,
    ),
    AppRule(
        appId = "khan_kids",
        packageOrBundleId = "org.khankids.android",
        displayName = "Khan Academy Kids",
        allowed = true,
        blockMinutes = 0,
    ),
    AppRule(
        appId = "yt_kids",
        packageOrBundleId = "com.google.android.apps.youtube.kids",
        displayName = "YouTube Kids",
        allowed = true,
        blockMinutes = 30,
    ),
    AppRule(
        appId = "scratch_jr",
        packageOrBundleId = "org.scratchjr.android",
        displayName = "ScratchJr",
        allowed = true,
        blockMinutes = 0,
    ),
    AppRule(
        appId = "roblox",
        packageOrBundleId = "com.roblox.client",
        displayName = "Roblox",
        allowed = false,
        blockMinutes = 15,
    ),
)
