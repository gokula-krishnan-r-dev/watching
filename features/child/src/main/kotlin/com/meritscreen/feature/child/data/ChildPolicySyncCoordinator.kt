package com.meritscreen.feature.child.data

import android.content.Context
import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.child.PolicySyncStateDao
import com.meritscreen.core.database.child.PolicySyncStateEntity
import com.meritscreen.core.firebase.child.ChildRemotePolicyClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Room policy/appRules in sync with Firestore **off the Home path**. `HomeViewModel`
 * only reads Room Flows written here.
 *
 * Phase 7 design: no continuous Firestore snapshot listener. A parent write triggers a
 * Cloud Function → FCM data message → [PolicySyncWorker] pull (works even if this process is
 * dead); while the app is alive, network recovery is caught in-process below; a 6-hour
 * WorkManager periodic job is the last-resort fallback if both miss. This trades a few
 * minutes of worst-case staleness for far fewer always-open Firestore connections — see
 * ARCHITECTURE.md "Real-time synchronization (Phase 7)".
 */
@Singleton
class ChildPolicySyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingStore: ChildPairingStore,
    private val remote: ChildRemotePolicyClient,
    private val repository: ChildPolicyRepository,
    private val quizBankSeeder: BuiltinQuizBankSeeder,
    private val networkMonitor: NetworkMonitor,
    private val analyticsTracker: AnalyticsTracker,
    private val policySyncStateDao: PolicySyncStateDao,
    private val policySyncScheduler: PolicySyncScheduler,
    private val quizPackGenerationScheduler: QuizPackGenerationScheduler,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            quizBankSeeder.ensureSeeded(context)
            policySyncScheduler.ensureScheduled()
            quizPackGenerationScheduler.ensureScheduled()
            refreshNow()
            // First AI pack fill after pairing / cold start (no Home-path call).
            quizPackGenerationScheduler.runSoon()
            observeNetworkRecovery()
            startPeriodicSync()
        }
    }

    private suspend fun startPeriodicSync() {
        while (scope.isActive) {
            kotlinx.coroutines.delay(30_000L)
            if (networkMonitor.isCurrentlyOnline()) {
                refreshNow()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        policySyncScheduler.cancel()
    }

    /** Manual pull (pull-to-refresh, parent menu, app resume, WorkManager worker). */
    suspend fun refreshNow(): Boolean = withContext(dispatchers.io) {
        val credential = pairingStore.get() ?: return@withContext false
        if (!networkMonitor.isCurrentlyOnline()) return@withContext false
        pullOnce(credential.familyId, credential.childId)
    }

    suspend fun lastSyncState(): PolicySyncStateEntity? {
        val childId = pairingStore.get()?.childId ?: return null
        return policySyncStateDao.get(childId)
    }

    /** Cheap, local-only: reacts to connectivity coming back while the app is alive. */
    private suspend fun observeNetworkRecovery() {
        networkMonitor.isOnline
            .distinctUntilChanged()
            .drop(1)
            .filter { online -> online }
            .collectLatest {
                refreshNow()
                quizPackGenerationScheduler.runSoon()
            }
    }

    private suspend fun pullOnce(familyId: String, childId: String): Boolean {
        val attemptAt = System.currentTimeMillis()
        val previousPolicy = runCatching { repository.getPolicy(childId) }.getOrNull()
        val succeeded = runCatching {
            val policy = remote.fetchPolicy(familyId, childId)
            val rules = remote.fetchAppRules(familyId, childId)
            val profile = remote.fetchProfile(familyId, childId)
            val pinHash = remote.fetchParentPinHash(familyId)
            repository.savePolicy(familyId, childId, policy)
            repository.saveAppRules(childId, rules)
            if (profile != null) {
                repository.saveProfile(
                    ChildLocalProfile(
                        childId = profile.childId,
                        familyId = profile.familyId,
                        displayName = profile.displayName,
                        ageBand = profile.ageBand,
                        avatarId = profile.avatarId,
                        language = profile.language,
                    ),
                )
            }
            if (!pinHash.isNullOrBlank()) {
                val credential = pairingStore.get()
                if (credential != null && credential.parentPinHash != pinHash) {
                    pairingStore.updateParentPinHash(pinHash)
                }
            }
            analyticsTracker.track(AnalyticsEvent.PolicySyncCompleted)
            val learningChanged = previousPolicy == null ||
                previousPolicy.gradeStandard != policy.gradeStandard ||
                previousPolicy.region != policy.region ||
                previousPolicy.customPromptGuidelines != policy.customPromptGuidelines ||
                previousPolicy.curriculumFocusIds != policy.curriculumFocusIds ||
                previousPolicy.aiQuizzesEnabled != policy.aiQuizzesEnabled
            if (learningChanged && policy.aiQuizzesEnabled) {
                quizPackGenerationScheduler.runSoon()
            }
        }.isSuccess
        recordSyncState(childId, attemptAt, succeeded)
        return succeeded
    }

    private suspend fun recordSyncState(childId: String, attemptAtEpochMs: Long, success: Boolean) {
        val previous = policySyncStateDao.get(childId)
        policySyncStateDao.upsert(
            PolicySyncStateEntity(
                childId = childId,
                lastAttemptAtEpochMs = attemptAtEpochMs,
                lastSuccessAtEpochMs = if (success) attemptAtEpochMs else previous?.lastSuccessAtEpochMs,
                consecutiveFailures = if (success) 0 else (previous?.consecutiveFailures ?: 0) + 1,
            ),
        )
    }
}
