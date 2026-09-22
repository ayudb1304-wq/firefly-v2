package com.firefly.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences (ARCHITECTURE.md §4 DataStore). Nothing here is a location. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val displayName: Flow<String?> = dataStore.data.map { it[Keys.DISPLAY_NAME] }
    val groupCode: Flow<String?> = dataStore.data.map { it[Keys.GROUP_CODE] }
    val senderId: Flow<Int?> = dataStore.data.map { it[Keys.SENDER_ID] }
    val keepLog: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_LOG] ?: false }

    suspend fun setDisplayName(name: String) = dataStore.edit { it[Keys.DISPLAY_NAME] = name }
    suspend fun setKeepLog(keep: Boolean) = dataStore.edit { it[Keys.KEEP_LOG] = keep }

    /** Called on "Leave group": wipes group identity so a fresh senderId is generated. */
    suspend fun clearGroup() = dataStore.edit {
        it.remove(Keys.GROUP_CODE)
        it.remove(Keys.SENDER_ID)
    }

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val GROUP_CODE = stringPreferencesKey("group_code")
        val SENDER_ID = intPreferencesKey("sender_id")
        val KEEP_LOG = booleanPreferencesKey("keep_log")
    }
}
