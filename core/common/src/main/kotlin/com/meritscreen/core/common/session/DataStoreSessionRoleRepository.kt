package com.meritscreen.core.common.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "meritscreen_session")

@Singleton
class DataStoreSessionRoleRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SessionRoleRepository {

    private val dataStore = context.sessionDataStore

    override val role: Flow<DeviceRole> = dataStore.data.map { preferences ->
        when (preferences[ROLE_KEY]) {
            ROLE_PARENT -> DeviceRole.Parent
            ROLE_CHILD -> DeviceRole.Child
            else -> DeviceRole.Unassigned
        }
    }

    override suspend fun setRole(role: DeviceRole) {
        dataStore.edit { preferences ->
            preferences[ROLE_KEY] = when (role) {
                DeviceRole.Unassigned -> ROLE_UNASSIGNED
                DeviceRole.Parent -> ROLE_PARENT
                DeviceRole.Child -> ROLE_CHILD
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(ROLE_KEY) }
    }

    private companion object {
        val ROLE_KEY = stringPreferencesKey("device_role")
        const val ROLE_UNASSIGNED = "unassigned"
        const val ROLE_PARENT = "parent"
        const val ROLE_CHILD = "child"
    }
}
