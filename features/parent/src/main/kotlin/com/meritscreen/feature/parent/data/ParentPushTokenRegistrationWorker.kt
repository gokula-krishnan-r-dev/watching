package com.meritscreen.feature.parent.data

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.firebase.messaging.PushTokenProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ParentPushTokenRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val parentSessionRepository: ParentSessionRepository,
    private val pushTokenProvider: PushTokenProvider,
    private val parentControlStore: ParentControlStore,
    private val installationManager: ParentInstallationManager,
    private val notificationPrefsRepository: ParentNotificationPrefsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = parentSessionRepository.current() ?: return Result.success()
        return try {
            val token = pushTokenProvider.currentToken()
                ?: return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

            val installationId = installationManager.getInstallationId()
            val prefs = runCatching { notificationPrefsRepository.prefs.first().toMap() }
                .getOrDefault(emptyMap())

            val modelName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val appVersion = runCatching {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
            }.getOrDefault("1.0.0") ?: "1.0.0"

            parentControlStore.registerParentPushToken(
                familyId = session.familyId,
                installationId = installationId,
                uid = session.uid,
                fcmToken = token,
                platform = "android",
                model = modelName,
                appVersion = appVersion,
                notificationPrefs = prefs,
            )
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
