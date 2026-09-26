package com.meritscreen.core.common.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.parentSessionStore: DataStore<Preferences> by preferencesDataStore(
    name = "meritscreen_parent_session",
)

@Singleton
class DataStoreParentSessionRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ParentSessionRepository {

    private val dataStore = context.parentSessionStore

    override val session: Flow<ParentSession?> = dataStore.data.map { preferences ->
        preferences[SESSION_KEY]?.let { decode(it) }
    }

    override suspend fun current(): ParentSession? = session.first()

    override suspend fun set(session: ParentSession) {
        dataStore.edit { it[SESSION_KEY] = Json.encodeToString(ParentSession.serializer(), session) }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    private fun decode(raw: String): ParentSession? = try {
        Json.decodeFromString(ParentSession.serializer(), raw)
    } catch (_: Exception) {
        null
    }

    private companion object {
        val SESSION_KEY = stringPreferencesKey("parent_session")
    }
}
