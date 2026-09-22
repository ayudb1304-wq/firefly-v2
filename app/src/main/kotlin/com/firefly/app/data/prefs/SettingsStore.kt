package com.firefly.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences (ARCHITECTURE.md §4 DataStore). Nothing here is a location. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        const val DEFAULT_SCAN_ON_MS = 8_000L
        const val DEFAULT_SCAN_OFF_MS = 2_000L
        const val DEFAULT_ADV_INTERVAL = 1
        const val ADV_LOW_LATENCY = 0
        const val ADV_BALANCED = 1
        const val ADV_LOW_POWER = 2
    }

    val displayName: Flow<String?> = dataStore.data.map { it[Keys.DISPLAY_NAME] }
    val groupCode: Flow<String?> = dataStore.data.map { it[Keys.GROUP_CODE] }
    val senderId: Flow<Int?> = dataStore.data.map { it[Keys.SENDER_ID] }
    val keepLog: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_LOG] ?: false }
    val joinedAt: Flow<Long?> = dataStore.data.map { it[Keys.JOINED_AT] }

    /** Field-test radio knobs (ARCHITECTURE.md §4 DataStore: scanDuty, advIntervals). */
    val scanOnMs: Flow<Long> = dataStore.data.map { it[Keys.SCAN_ON_MS] ?: DEFAULT_SCAN_ON_MS }
    val scanOffMs: Flow<Long> = dataStore.data.map { it[Keys.SCAN_OFF_MS] ?: DEFAULT_SCAN_OFF_MS }
    /** 0 = low latency (100 ms), 1 = balanced (250 ms), 2 = low power (1 s). */
    val advInterval: Flow<Int> = dataStore.data.map { it[Keys.ADV_INTERVAL] ?: DEFAULT_ADV_INTERVAL }
    val batteryCardDismissed: Flow<Boolean> = dataStore.data.map { it[Keys.BATTERY_CARD_DISMISSED] ?: false }

    suspend fun setScanWindow(onMs: Long, offMs: Long) = dataStore.edit { it[Keys.SCAN_ON_MS] = onMs; it[Keys.SCAN_OFF_MS] = offMs }
    suspend fun setAdvInterval(mode: Int) = dataStore.edit { it[Keys.ADV_INTERVAL] = mode }
    suspend fun setBatteryCardDismissed(v: Boolean) = dataStore.edit { it[Keys.BATTERY_CARD_DISMISSED] = v }

    suspend fun setDisplayName(name: String) = dataStore.edit { it[Keys.DISPLAY_NAME] = name }
    suspend fun setKeepLog(keep: Boolean) = dataStore.edit { it[Keys.KEEP_LOG] = keep }

    suspend fun setGroup(code: String, senderId: Int) = dataStore.edit {
        it[Keys.GROUP_CODE] = code
        it[Keys.SENDER_ID] = senderId
        it[Keys.JOINED_AT] = System.currentTimeMillis()
    }

    /** Called on "Leave group": wipes group identity so a fresh senderId is generated. */
    suspend fun clearGroup() = dataStore.edit {
        it.remove(Keys.GROUP_CODE)
        it.remove(Keys.SENDER_ID)
        it.remove(Keys.JOINED_AT)
    }

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val GROUP_CODE = stringPreferencesKey("group_code")
        val SENDER_ID = intPreferencesKey("sender_id")
        val KEEP_LOG = booleanPreferencesKey("keep_log")
        val JOINED_AT = longPreferencesKey("joined_at")
        val SCAN_ON_MS = longPreferencesKey("scan_on_ms")
        val SCAN_OFF_MS = longPreferencesKey("scan_off_ms")
        val ADV_INTERVAL = intPreferencesKey("adv_interval")
        val BATTERY_CARD_DISMISSED = booleanPreferencesKey("battery_card_dismissed")
    }
}
