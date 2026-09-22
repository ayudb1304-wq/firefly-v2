package com.firefly.app.di

import android.content.Context
import androidx.room.Room
import com.firefly.app.data.db.FireflyDatabase
import com.firefly.app.data.prefs.SettingsStore
import com.firefly.app.data.prefs.settingsDataStore

/**
 * Manual dependency container. One instance per process, owned by [com.firefly.app.FireflyApp].
 *
 * Everything is lazy so cold start stays under the 3 s budget (PRD §4).
 * Later phases add: LocationSource, FireflyService bindings, repositories, VenuePack.
 */
class AppContainer(private val appContext: Context) {

    val database: FireflyDatabase by lazy {
        Room.databaseBuilder(appContext, FireflyDatabase::class.java, FireflyDatabase.NAME)
            // Session data is disposable (privacy rule); a schema bump wipes it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }
}
