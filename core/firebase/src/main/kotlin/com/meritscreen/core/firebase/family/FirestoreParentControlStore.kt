package com.meritscreen.core.firebase.family

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ChildPolicyMapper
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.ExplorerProgressSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.QuizAttemptSummary
import com.meritscreen.core.common.domain.ReportsAggregator
import com.meritscreen.core.common.domain.StickerUnlockSummary
import com.meritscreen.core.common.domain.TopicSkillSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.domain.WeakConceptHint
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreParentControlStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val dispatchers: AppDispatchers,
) : ParentControlStore {

    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? = runStore {
        val snap = firestore.collection(FAMILIES).document(familyId).get().await()
        if (!snap.exists()) return@runStore null
        FamilyMeta(
            familyId = familyId,
            name = snap.getString("name").orEmpty(),
            ownerUid = snap.getString("ownerUid").orEmpty(),
        )
    }

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = runStore {
        firestore.collection(FAMILIES).document(familyId)
            .collection(CHILDREN)
            .get()
            .await()
            .documents
            .mapNotNull { doc -> parseChild(doc.id, doc.data) }
    }

    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile =
        runStore {
            val childId = child.localId.ifBlank { UUID.randomUUID().toString() }
            val existing = firestore.collection(FAMILIES).document(familyId)
                .collection(CHILDREN).get().await().size()
            if (existing >= AppConfig.MAX_CHILDREN_PER_PARENT) {
                throw AppErrorException(
                    com.meritscreen.core.common.error.AppError.Validation(
                        "You can add up to ${AppConfig.MAX_CHILDREN_PER_PARENT} children for now.",
                    ),
                )
            }
            val profile = FamilyChildProfile(
                childId = childId,
                displayName = child.name.trim(),
                ageBand = child.ageBand,
                avatar = child.avatar,
                language = child.language.ifBlank { "en" },
            )
            val childRef = firestore.collection(FAMILIES).document(familyId)
                .collection(CHILDREN).document(childId)
            val batch = firestore.batch()
            batch.set(
                childRef,
                mapOf(
                    "displayName" to profile.displayName,
                    "ageBand" to profile.ageBand.name,
                    "avatarId" to profile.avatar.name,
                    "language" to profile.language,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                childRef.collection("policy").document("current"),
                ChildPolicyMapper.toMap(
                    ChildPolicy(
                        curriculumFocusIds = CurriculumFocusCatalog.reconcile(
                            child.ageBand,
                            child.curriculumFocusIds,
                        ),
                    ),
                ) + ("updatedAt" to FieldValue.serverTimestamp()),
            )
            batch.commit().await()
            profile
        }

    override suspend fun updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild,
    ): FamilyChildProfile = runStore {
        val profile = FamilyChildProfile(
            childId = childId,
            displayName = child.name.trim(),
            ageBand = child.ageBand,
            avatar = child.avatar,
            language = child.language.ifBlank { "en" },
        )
        childRef(familyId, childId)
            .set(
                mapOf(
                    "displayName" to profile.displayName,
                    "ageBand" to profile.ageBand.name,
                    "avatarId" to profile.avatar.name,
                    "language" to profile.language,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        profile
    }

    override suspend fun deleteChild(familyId: String, childId: String) {
        runStore {
            functions.getHttpsCallable("deleteChild")
                .call(mapOf("familyId" to familyId, "childId" to childId))
                .await()
        }
    }

    override suspend fun getPolicy(familyId: String, childId: String): ChildPolicy = runStore {
        val snap = childRef(familyId, childId).collection("policy").document("current").get().await()
        if (!snap.exists()) return@runStore ChildPolicy()
        ChildPolicyMapper.fromMap(snap.data.orEmpty())
    }

    override suspend fun updatePolicy(familyId: String, childId: String, policy: ChildPolicy) {
        runStore {
            childRef(familyId, childId).collection("policy").document("current")
                .set(ChildPolicyMapper.toMap(policy) + ("updatedAt" to FieldValue.serverTimestamp()))
                .await()
        }
    }

    override suspend fun setChildPaused(familyId: String, childId: String, paused: Boolean) {
        runStore {
            // Merge-only write so FCM fires immediately without a get→set round trip.
            childRef(familyId, childId).collection("policy").document("current")
                .set(
                    mapOf(
                        "paused" to paused,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        }
    }

    override suspend fun addBonusTime(familyId: String, childId: String, bonusMinutes: Int) {
        runStore {
            val current = getPolicy(familyId, childId)
            val currentCeiling = current.dailyCeilingMinutes ?: AppConfig.DEFAULT_DAILY_CEILING_MINUTES
            val newCeiling = currentCeiling + bonusMinutes
            updatePolicy(
                familyId,
                childId,
                current.copy(
                    dailyCeilingMinutes = newCeiling,
                    bonusMinutesToday = current.bonusMinutesToday + bonusMinutes,
                ),
            )
        }
    }

    override suspend fun listAppRules(familyId: String, childId: String): List<AppRule> = runStore {
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
        }.sortedBy { it.displayName.ifBlank { it.packageOrBundleId } }
    }

    override suspend fun upsertAppRule(familyId: String, childId: String, rule: AppRule) {
        runStore {
            val id = rule.appId.ifBlank { rule.packageOrBundleId.replace('.', '_') }
            childRef(familyId, childId).collection("appRules").document(id)
                .set(
                    mapOf(
                        "packageOrBundleId" to rule.packageOrBundleId.trim(),
                        "displayName" to rule.displayName.trim(),
                        "allowed" to rule.allowed,
                        "blockMinutes" to rule.blockMinutes.coerceIn(5, 240),
                        "grantOnPassMinutes" to rule.grantOnPassMinutes.coerceIn(5, 240),
                        "cooldownMinutes" to rule.cooldownMinutes.coerceIn(1, 180),
                        "isEmergency" to rule.isEmergency,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        }
    }

    override suspend fun deleteAppRule(familyId: String, childId: String, appId: String) {
        runStore {
            childRef(familyId, childId).collection("appRules").document(appId).delete().await()
        }
    }

    override suspend fun listUsageDays(
        familyId: String,
        childId: String,
        limit: Int,
    ): List<UsageDaySummary> = runStore {
        val docs = childRef(familyId, childId).collection("usageDays")
            .get()
            .await()
            .documents
        parseUsageDays(docs, limit)
    }

    override suspend fun listQuizAttempts(
        familyId: String,
        childId: String,
        limit: Int,
    ): List<QuizAttemptSummary> = runStore {
        val docs = childRef(familyId, childId).collection("quizAttempts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1, AppConfig.REPORTS_MAX_ATTEMPTS).toLong())
            .get()
            .await()
            .documents
        parseQuizAttempts(docs)
    }

    override suspend fun getSkillState(
        familyId: String,
        childId: String,
    ): List<TopicSkillSummary> = runStore {
        val snap = childRef(familyId, childId).collection("skillState").document("current").get().await()
        parseSkillState(snap)
    }

    override suspend fun listStickerUnlocks(
        familyId: String,
        childId: String,
        limit: Int,
    ): List<StickerUnlockSummary> = runStore {
        val docs = childRef(familyId, childId).collection("stickerUnlocks")
            .orderBy("unlockedAt", Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1, 100).toLong())
            .get()
            .await()
            .documents
        docs.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val unlockedAt = (data["unlockedAt"] as? com.google.firebase.Timestamp)
                ?.toDate()?.time
                ?: (data["unlockedAtEpochMs"] as? Number)?.toLong()
                ?: 0L
            StickerUnlockSummary(
                unlockId = doc.id,
                stickerId = data["stickerId"] as? String ?: return@mapNotNull null,
                stage = data["stage"] as? String ?: "",
                title = data["title"] as? String ?: "",
                emoji = data["emoji"] as? String ?: "",
                source = data["source"] as? String ?: "",
                unlockedAtEpochMs = unlockedAt,
            )
        }
    }

    override suspend fun getExplorerProgress(
        familyId: String,
        childId: String,
    ): ExplorerProgressSummary? = runStore {
        val snap = childRef(familyId, childId).collection("explorerProgress").document("current").get().await()
        val data = snap.data ?: return@runStore null
        ExplorerProgressSummary(
            xp = (data["xp"] as? Number)?.toInt() ?: 0,
            explorerLevel = (data["explorerLevel"] as? Number)?.toInt() ?: 1,
            passStreakDays = (data["passStreakDays"] as? Number)?.toInt() ?: 0,
            lastPassDayKey = data["lastPassDayKey"] as? String,
            updatedAtEpochMs = (data["updatedAtEpochMs"] as? Number)?.toLong()
                ?: (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: 0L,
        )
    }

    override suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary> =
        runStore {
            childRef(familyId, childId).collection("devices").get().await().documents.mapNotNull { doc ->
                parseDevice(doc)
            }
        }

    override suspend fun setDeviceRevoked(
        familyId: String,
        childId: String,
        deviceId: String,
        revoked: Boolean,
    ) {
        runStore {
            val payload = mutableMapOf<String, Any>(
                "revoked" to revoked,
                "revokedUpdatedAt" to FieldValue.serverTimestamp(),
            )
            if (revoked) {
                // Drop push token so a lost phone stops receiving family pushes.
                payload["fcmToken"] = FieldValue.delete()
            }
            childRef(familyId, childId).collection("devices").document(deviceId)
                .set(payload, SetOptions.merge())
                .await()
        }
    }

    override suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary> =
        runStore {
            parseInstalledApps(
                childRef(familyId, childId).collection("devices").get().await().documents,
            )
        }

    override fun observeInstalledApps(
        familyId: String,
        childId: String,
    ): Flow<List<InstalledAppSummary>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("devices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parseInstalledApps(snapshot?.documents.orEmpty()))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeDevices(
        familyId: String,
        childId: String,
    ): Flow<List<DeviceSummary>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("devices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { parseDevice(it) })
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> =
        callbackFlow {
            val registration = firestore.collection(FAMILIES).document(familyId)
                .collection(CHILDREN)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Prefer cached snapshot when present; otherwise surface once for VM recovery.
                        if (snapshot != null) {
                            val children = snapshot.documents.mapNotNull { doc ->
                                parseChild(doc.id, doc.data)
                            }
                            trySend(children)
                        } else {
                            close(error)
                        }
                        return@addSnapshotListener
                    }
                    val children = snapshot?.documents?.mapNotNull { doc ->
                        parseChild(doc.id, doc.data)
                    }.orEmpty()
                    trySend(children)
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeUsageDays(
        familyId: String,
        childId: String,
        limit: Int,
    ): Flow<List<UsageDaySummary>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("usageDays")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parseUsageDays(snapshot?.documents.orEmpty(), limit))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeQuizAttempts(
        familyId: String,
        childId: String,
        limit: Int,
    ): Flow<List<QuizAttemptSummary>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("quizAttempts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.coerceIn(1, AppConfig.REPORTS_MAX_ATTEMPTS).toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parseQuizAttempts(snapshot?.documents.orEmpty()))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeSkillState(
        familyId: String,
        childId: String,
    ): Flow<List<TopicSkillSummary>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("skillState").document("current")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parseSkillState(snapshot))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observePolicy(
        familyId: String,
        childId: String,
    ): Flow<ChildPolicy?> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("policy").document("current")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parsePolicy(snapshot))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override fun observeAppRules(
        familyId: String,
        childId: String,
    ): Flow<List<AppRule>> =
        callbackFlow {
            val registration = childRef(familyId, childId).collection("appRules")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(parseAppRules(snapshot?.documents.orEmpty()))
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    override suspend fun deleteFamily(familyId: String) {
        runStore {
            functions.getHttpsCallable("deleteFamily")
                .call(mapOf("familyId" to familyId))
                .await()
        }
    }

    private fun childRef(familyId: String, childId: String) =
        firestore.collection(FAMILIES).document(familyId).collection(CHILDREN).document(childId)

    private fun parseChild(childId: String, data: Map<String, Any>?): FamilyChildProfile? {
        val name = data?.get("displayName") as? String ?: return null
        return FamilyChildProfile(
            childId = childId,
            displayName = name,
            ageBand = AgeBand.entries.firstOrNull { it.name == data["ageBand"] }
                ?: AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.entries.firstOrNull { it.name == data["avatarId"] }
                ?: AvatarPreset.Default,
            language = (data["language"] as? String) ?: "en",
        )
    }

    private fun parseDevice(doc: DocumentSnapshot): DeviceSummary? {
        if (!doc.exists()) return null
        val battery = (doc.getLong("batteryPercent") ?: (doc.get("batteryPercent") as? Number)?.toLong())
            ?.toInt()
            ?.takeIf { it in 0..100 }
        return DeviceSummary(
            deviceId = doc.id,
            platform = doc.getString("platform") ?: "android",
            revoked = doc.getBoolean("revoked") ?: false,
            lastSeenAtEpochMs = doc.getTimestamp("lastSeenAt")?.toDate()?.time,
            launcherDefault = doc.getBoolean("launcherDefault"),
            model = doc.getString("model"),
            batteryPercent = battery,
            osVersion = doc.getString("osVersion"),
            appVersion = doc.getString("appVersion")?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseUsageDays(documents: List<DocumentSnapshot>, limit: Int): List<UsageDaySummary> {
        val capped = limit.coerceIn(1, 90)
        return documents
            .sortedByDescending { it.id }
            .take(capped)
            .map { doc ->
                @Suppress("UNCHECKED_CAST")
                val byApp = (doc.get("minutesByApp") as? Map<String, Any?>)?.mapValues { (_, v) ->
                    when (v) {
                        is Number -> v.toInt()
                        else -> 0
                    }
                }.orEmpty()
                UsageDaySummary(
                    day = doc.id,
                    minutesUsed = (doc.getLong("minutesUsed") ?: 0L).toInt(),
                    minutesByApp = byApp,
                )
            }
    }

    private fun parseQuizAttempts(documents: List<DocumentSnapshot>): List<QuizAttemptSummary> {
        return documents.map { doc ->
            @Suppress("UNCHECKED_CAST")
            val topics = (doc.get("topics") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            QuizAttemptSummary(
                attemptId = doc.id,
                createdAtEpochMs = doc.getTimestamp("createdAt")?.toDate()?.time
                    ?: doc.getLong("createdAtEpochMs")
                    ?: 0L,
                topics = topics,
                score = (doc.getLong("score") ?: 0L).toInt(),
                total = (doc.getLong("total") ?: 0L).toInt(),
                passed = doc.getBoolean("passed") ?: false,
                extraMinutesGranted = (doc.getLong("extraMinutesGranted") ?: 0L).toInt(),
            )
        }
    }

    private fun parseSkillState(doc: DocumentSnapshot?): List<TopicSkillSummary> {
        if (doc == null || !doc.exists()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val topics = (doc.get("topics") as? Map<String, Any?>).orEmpty()
        return topics.mapNotNull { (topic, raw) ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val level = (map["level"] as? Number)?.toInt() ?: 2
            val streak = (map["streakCorrect"] as? Number)?.toInt() ?: 0
            val weak = map["weak"] as? Boolean ?: false
            @Suppress("UNCHECKED_CAST")
            val weakIds = (map["weakConcepts"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            @Suppress("UNCHECKED_CAST")
            val titles = (map["weakConceptTitles"] as? Map<String, Any?>)
                ?.mapNotNull { (id, title) -> (title as? String)?.let { id to it } }
                ?.toMap()
                .orEmpty()
            @Suppress("UNCHECKED_CAST")
            val masteredIds = (map["masteredConcepts"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val tier = (map["tierLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "basic"
            TopicSkillSummary(
                topic = topic,
                level = level,
                streakCorrect = streak,
                weak = weak || weakIds.isNotEmpty(),
                weakConcepts = weakIds.map { id ->
                    WeakConceptHint(
                        conceptId = id,
                        title = titles[id]?.takeIf { it.isNotBlank() }
                            ?: ReportsAggregator.humanizeToken(id),
                    )
                },
                masteredConcepts = masteredIds,
                tierLabel = tier,
            )
        }
    }

    private fun parsePolicy(doc: DocumentSnapshot?): ChildPolicy? {
        if (doc == null || !doc.exists()) return null
        return ChildPolicyMapper.fromMap(doc.data.orEmpty())
    }

    private fun parseAppRules(documents: List<DocumentSnapshot>): List<AppRule> {
        return documents.map { doc ->
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
        }.sortedBy { it.displayName.ifBlank { it.packageOrBundleId } }
    }

    private fun parseInstalledApps(documents: List<DocumentSnapshot>): List<InstalledAppSummary> =
        documents
            .filter { it.getBoolean("revoked") != true }
            .flatMap { doc ->
                @Suppress("UNCHECKED_CAST")
                (doc.get("installedApps") as? List<Map<String, Any?>>).orEmpty()
            }
            .mapNotNull { app ->
                val pkg = app["packageName"] as? String ?: return@mapNotNull null
                val iconBase64 = (app["iconBase64"] as? String)?.takeIf { it.isNotBlank() }
                val iconHash = (app["iconHash"] as? String)?.takeIf { it.isNotBlank() }
                InstalledAppSummary(
                    packageName = pkg,
                    label = (app["label"] as? String) ?: pkg,
                    iconBase64 = iconBase64,
                    iconHash = iconHash,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

    override suspend fun registerParentPushToken(
        familyId: String,
        installationId: String,
        uid: String,
        fcmToken: String,
        platform: String,
        model: String,
        appVersion: String,
        notificationPrefs: Map<String, Boolean>,
    ) {
        runStore {
            val parentDeviceRef = firestore.collection(FAMILIES)
                .document(familyId)
                .collection("parentDevices")
                .document(installationId)

            val docData = mapOf(
                "uid" to uid,
                "fcmToken" to fcmToken,
                "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                "platform" to platform,
                "model" to model,
                "appVersion" to appVersion,
                "notificationPrefs" to notificationPrefs,
            )
            parentDeviceRef.set(docData, com.google.firebase.firestore.SetOptions.merge()).await()

            // Also mirror to global fcmTokens collection
            val fcmTokenRef = firestore.collection("fcmTokens").document(fcmToken)
            val globalTokenData = mapOf(
                "token" to fcmToken,
                "role" to "parent",
                "uid" to uid,
                "familyId" to familyId,
                "installationId" to installationId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            fcmTokenRef.set(globalTokenData, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    private suspend fun <T> runStore(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private companion object {
        const val FAMILIES = "families"
        const val CHILDREN = "children"
    }
}
