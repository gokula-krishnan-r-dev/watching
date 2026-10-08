package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.child.ChildPolicyDao
import com.meritscreen.core.database.child.PolicySyncStateDao
import com.meritscreen.core.database.child.QuizAttemptDao
import com.meritscreen.core.database.child.QuizDao
import com.meritscreen.core.database.child.SessionStateDao
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.database.device.DeviceRuntimeStateDao
import com.meritscreen.core.database.device.InstalledAppDao
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes child-specific Room state on unpair / family delete.
 * Keeps the built-in quiz bank (`quiz_item`) so a later pair does not re-seed from assets.
 */
@Singleton
class ChildLocalDataWiper @Inject constructor(
    private val policyDao: ChildPolicyDao,
    private val sessionStateDao: SessionStateDao,
    private val quizDao: QuizDao,
    private val usageDao: UsageDao,
    private val quizAttemptDao: QuizAttemptDao,
    private val policySyncStateDao: PolicySyncStateDao,
    private val installedAppDao: InstalledAppDao,
    private val deviceRuntimeStateDao: DeviceRuntimeStateDao,
    private val dispatchers: AppDispatchers,
) {
    suspend fun wipeForChild(childId: String) = withContext(dispatchers.io) {
        if (childId.isBlank()) return@withContext
        policyDao.clearPolicy(childId)
        policyDao.clearAppRules(childId)
        policyDao.clearProfile(childId)
        sessionStateDao.clearForChild(childId)
        quizDao.clearSkills(childId)
        quizDao.clearRecent(childId)
        usageDao.clearForChild(childId)
        quizAttemptDao.clearForChild(childId)
        policySyncStateDao.clear(childId)
        installedAppDao.clear()
        deviceRuntimeStateDao.clear()
    }
}
