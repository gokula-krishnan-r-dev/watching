package com.meritscreen.core.firebase.child

import com.google.firebase.firestore.FirebaseFirestore
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ChildPolicyMapper
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChildRemotePolicyClient @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppDispatchers,
) : ChildRemotePolicyClient {

    override suspend fun fetchPolicy(familyId: String, childId: String): ChildPolicy = runRemote {
        val snap = childRef(familyId, childId).collection("policy").document("current").get().await()
        if (!snap.exists()) ChildPolicy() else ChildPolicyMapper.fromMap(snap.data.orEmpty())
    }

    override suspend fun fetchAppRules(familyId: String, childId: String): List<AppRule> = runRemote {
        childRef(familyId, childId).collection("appRules").get().await().documents.map { doc ->
            AppRule(
                appId = doc.id,
                packageOrBundleId = doc.getString("packageOrBundleId") ?: doc.id,
                displayName = doc.getString("displayName").orEmpty(),
                allowed = doc.getBoolean("allowed") ?: true,
                blockMinutes = (doc.getLong("blockMinutes") ?: AppConfig.DEFAULT_BLOCK_MINUTES.toLong()).toInt(),
                grantOnPassMinutes = (doc.getLong("grantOnPassMinutes")
                    ?: doc.getLong("blockMinutes")
                    ?: AppConfig.DEFAULT_BLOCK_MINUTES.toLong()).toInt(),
                cooldownMinutes = (doc.getLong("cooldownMinutes")
                    ?: AppConfig.DEFAULT_COOLDOWN_MINUTES.toLong()).toInt(),
                isEmergency = doc.getBoolean("isEmergency") ?: false,
            )
        }
    }

    override suspend fun fetchProfile(familyId: String, childId: String): ChildRemoteProfile? = runRemote {
        val snap = childRef(familyId, childId).get().await()
        if (!snap.exists()) return@runRemote null
        ChildRemoteProfile(
            childId = childId,
            familyId = familyId,
            displayName = snap.getString("displayName").orEmpty(),
            ageBand = snap.getString("ageBand").orEmpty(),
            avatarId = snap.getString("avatarId").orEmpty(),
            language = snap.getString("language") ?: "en",
        )
    }

    override suspend fun fetchParentPinHash(familyId: String): String? = runRemote {
        val snap = firestore.collection("families").document(familyId).get().await()
        snap.getString("parentPinHash")?.takeIf { it.isNotBlank() }
    }

    private fun childRef(familyId: String, childId: String) =
        firestore.collection("families").document(familyId).collection("children").document(childId)

    private suspend fun <T> runRemote(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }
}
