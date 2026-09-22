package com.firefly.app.di

import android.content.Context
import androidx.room.Room
import com.firefly.app.data.db.FireflyDatabase
import com.firefly.app.data.prefs.SettingsStore
import com.firefly.app.data.prefs.settingsDataStore
import com.firefly.app.data.repo.GroupRepository
import com.firefly.app.data.repo.MemberRepository
import com.firefly.app.location.LocationSource
import com.firefly.app.radio.RadioStatusHolder
import com.firefly.app.venue.VenueRepository

/**
 * Manual dependency container. One instance per process, owned by [com.firefly.app.FireflyApp].
 * Everything is lazy so cold start stays under the 3 s budget (PRD §4).
 */
class AppContainer(private val appContext: Context) {

    val database: FireflyDatabase by lazy {
        Room.databaseBuilder(appContext, FireflyDatabase::class.java, FireflyDatabase.NAME)
            // Session data is disposable (privacy rule); a schema bump wipes it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    val memberRepository: MemberRepository by lazy { MemberRepository(database.memberDao()) }

    val groupRepository: GroupRepository by lazy { GroupRepository(settings, memberRepository) }

    val locationSource: LocationSource by lazy { LocationSource(appContext) }

    val radioStatus: RadioStatusHolder = RadioStatusHolder()

    val venueRepository: VenueRepository by lazy { VenueRepository(appContext) }
}
