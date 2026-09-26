package com.meritscreen.feature.parent.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentInstallationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("watching_parent_device", Context.MODE_PRIVATE)

    fun getInstallationId(): String {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id.isNullOrBlank()) {
            id = "parent_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    private companion object {
        const val KEY_INSTALLATION_ID = "parent_installation_id"
    }
}
