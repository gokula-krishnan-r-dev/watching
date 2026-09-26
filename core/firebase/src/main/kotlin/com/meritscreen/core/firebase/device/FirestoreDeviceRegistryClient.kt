package com.meritscreen.core.firebase.device

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.DeviceRemoteStatus
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDeviceRegistryClient @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppDispatchers,
) : DeviceRegistryClient {

    override suspend fun heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        launcherDefault: Boolean,
        model: String,
        batteryPercent: Int?,
        osVersion: String?,
        appVersion: String?,
    ) {
        runRegistry {
            val payload = mutableMapOf<String, Any>(
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "launcherDefault" to launcherDefault,
                "model" to model.take(MAX_MODEL_LENGTH),
            )
            batteryPercent?.takeIf { it in 0..100 }?.let { payload["batteryPercent"] = it }
            osVersion?.takeIf { it.isNotBlank() }?.let {
                payload["osVersion"] = it.take(MAX_OS_VERSION_LENGTH)
            }
            appVersion?.takeIf { it.isNotBlank() }?.let {
                payload["appVersion"] = it.take(MAX_APP_VERSION_LENGTH)
            }
            deviceRef(familyId, childId, deviceId).set(
                payload,
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }
    }

    override suspend fun uploadInstalledApps(
        familyId: String,
        childId: String,
        deviceId: String,
        apps: List<InstalledAppSummary>,
    ) {
        runRegistry {
            val trimmed = apps.take(AppConfig.INSTALLED_APPS_MAX_COUNT).map { app ->
                buildMap {
                    put("packageName", app.packageName)
                    put("label", app.label.take(AppConfig.INSTALLED_APP_LABEL_MAX_CHARS))
                    val icon = app.iconBase64
                    val hash = app.iconHash
                    if (!icon.isNullOrBlank() && !hash.isNullOrBlank() &&
                        icon.length <= AppConfig.APP_ICON_MAX_BYTES * 2
                    ) {
                        put("iconBase64", icon)
                        put("iconHash", hash.take(32))
                    }
                }
            }
            deviceRef(familyId, childId, deviceId).set(
                mapOf(
                    "installedApps" to trimmed,
                    "installedAppsUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }
    }

    override suspend fun fetchStatus(
        familyId: String,
        childId: String,
        deviceId: String,
    ): DeviceRemoteStatus = runRegistry {
        val snap = deviceRef(familyId, childId, deviceId).get().await()
        parseStatus(snap.exists(), snap.getBoolean("revoked"), snap.getBoolean("launcherDefault"))
    }

    override fun observeStatus(
        familyId: String,
        childId: String,
        deviceId: String,
    ): Flow<DeviceRemoteStatus> = callbackFlow {
        var registration: ListenerRegistration? = null
        registration = deviceRef(familyId, childId, deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Soft-fail: keep listening; never treat listener errors as revoke.
                    // Permission / network blips are common right after pairing.
                    return@addSnapshotListener
                }
                val status = parseStatus(
                    exists = snapshot?.exists() == true,
                    revoked = snapshot?.getBoolean("revoked"),
                    launcherDefault = snapshot?.getBoolean("launcherDefault"),
                )
                trySend(status)
            }
        awaitClose { registration?.remove() }
    }

    override suspend fun registerPushToken(
        familyId: String,
        childId: String,
        deviceId: String,
        token: String,
    ) {
        val cleanToken = token.take(MAX_TOKEN_LENGTH)
        runRegistry {
            deviceRef(familyId, childId, deviceId).set(
                mapOf(
                    "fcmToken" to cleanToken,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()

            // Mirror to global fcmTokens collection for admin campaigns
            firestore.collection("fcmTokens").document(cleanToken).set(
                mapOf(
                    "token" to cleanToken,
                    "role" to "child",
                    "familyId" to familyId,
                    "childId" to childId,
                    "deviceId" to deviceId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }
    }

    private fun parseStatus(
        exists: Boolean,
        revoked: Boolean?,
        launcherDefault: Boolean?,
    ): DeviceRemoteStatus {
        if (!exists) {
            // Pairing / offline race: doc may not be visible yet. Do not fail-closed to
            // revoked — that falsely unpairs a freshly paired child home.
            return DeviceRemoteStatus(
                revoked = false,
                launcherDefault = launcherDefault,
                registered = false,
            )
        }
        return DeviceRemoteStatus(
            revoked = revoked ?: false,
            launcherDefault = launcherDefault,
            registered = true,
        )
    }

    private fun deviceRef(familyId: String, childId: String, deviceId: String) =
        firestore.collection(FAMILIES).document(familyId)
            .collection(CHILDREN).document(childId)
            .collection(DEVICES).document(deviceId)

    private suspend fun <T> runRegistry(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private companion object {
        const val FAMILIES = "families"
        const val CHILDREN = "children"
        const val DEVICES = "devices"
        const val MAX_MODEL_LENGTH = 80
        const val MAX_OS_VERSION_LENGTH = 32
        const val MAX_APP_VERSION_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 512
    }
}
