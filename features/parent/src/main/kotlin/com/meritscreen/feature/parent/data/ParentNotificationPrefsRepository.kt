package com.meritscreen.feature.parent.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.parentNotificationPrefs by preferencesDataStore("parent_notification_prefs")

data class ParentNotificationPrefs(
    val dailySummary: Boolean = true,
    val timeUp: Boolean = true,
    val quizFailedRepeatedly: Boolean = true,
    val pairingEvents: Boolean = true,
    val quizPassedOptional: Boolean = true,
    val promotions: Boolean = true,
    val productUpdates: Boolean = true,
) {
    fun toMap(): Map<String, Boolean> = mapOf(
        "dailySummary" to dailySummary,
        "timeUp" to timeUp,
        "quizFailedRepeatedly" to quizFailedRepeatedly,
        "pairingEvents" to pairingEvents,
        "quizPassedOptional" to quizPassedOptional,
        "promotions" to promotions,
        "productUpdates" to productUpdates,
    )
}

@Singleton
class ParentNotificationPrefsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.parentNotificationPrefs

    val prefs: Flow<ParentNotificationPrefs> = store.data.map { preferences ->
        ParentNotificationPrefs(
            dailySummary = preferences[DAILY] ?: true,
            timeUp = preferences[TIME_UP] ?: true,
            quizFailedRepeatedly = preferences[QUIZ_FAILED] ?: true,
            pairingEvents = preferences[PAIRING_EVENTS] ?: true,
            quizPassedOptional = preferences[QUIZ_PASSED] ?: true,
            promotions = preferences[PROMOTIONS] ?: true,
            productUpdates = preferences[UPDATES] ?: true,
        )
    }

    suspend fun setDailySummary(enabled: Boolean) {
        store.edit { it[DAILY] = enabled }
    }

    suspend fun setTimeUp(enabled: Boolean) {
        store.edit { it[TIME_UP] = enabled }
    }

    suspend fun setQuizFailedRepeatedly(enabled: Boolean) {
        store.edit { it[QUIZ_FAILED] = enabled }
    }

    suspend fun setPairingEvents(enabled: Boolean) {
        store.edit { it[PAIRING_EVENTS] = enabled }
    }

    suspend fun setQuizPassedOptional(enabled: Boolean) {
        store.edit { it[QUIZ_PASSED] = enabled }
    }

    suspend fun setPromotions(enabled: Boolean) {
        store.edit { it[PROMOTIONS] = enabled }
    }

    suspend fun setProductUpdates(enabled: Boolean) {
        store.edit { it[UPDATES] = enabled }
    }

    private companion object {
        val DAILY = booleanPreferencesKey("daily_summary")
        val TIME_UP = booleanPreferencesKey("time_up")
        val QUIZ_FAILED = booleanPreferencesKey("quiz_failed_3")
        val PAIRING_EVENTS = booleanPreferencesKey("pairing_events")
        val QUIZ_PASSED = booleanPreferencesKey("quiz_passed_optional")
        val PROMOTIONS = booleanPreferencesKey("promotions")
        val UPDATES = booleanPreferencesKey("product_updates")
    }
}
