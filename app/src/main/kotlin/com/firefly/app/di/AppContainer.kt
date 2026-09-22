package com.firefly.app.di

import android.content.Context
import androidx.room.Room
import com.firefly.app.data.db.FireflyDatabase
import com.firefly.app.data.db.PingEntity
import com.firefly.app.data.prefs.SettingsStore
import com.firefly.app.data.prefs.settingsDataStore
import com.firefly.app.data.repo.GroupRepository
import com.firefly.app.data.repo.MemberRepository
import com.firefly.app.data.repo.PingRepository
import com.firefly.app.location.LocationSource
import com.firefly.app.radio.RadioStatusHolder
import com.firefly.app.radio.SeqCounter
import com.firefly.app.radio.TxQueue
import com.firefly.app.venue.VenueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manual dependency container. One instance per process, owned by [com.firefly.app.FireflyApp].
 * Everything is lazy so cold start stays under the 3 s budget (PRD §4).
 */
class AppContainer(private val appContext: Context) {

    /** Process-lifetime scope for repositories' hot flows. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FireflyDatabase by lazy {
        Room.databaseBuilder(appContext, FireflyDatabase::class.java, FireflyDatabase.NAME)
            // Session data is disposable (privacy rule); a schema bump wipes it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    val memberRepository: MemberRepository by lazy { MemberRepository(database.memberDao()) }

    val groupRepository: GroupRepository by lazy { GroupRepository(settings, memberRepository, { pingRepository }, appScope) }

    val locationSource: LocationSource by lazy { LocationSource(appContext) }

    val radioStatus: RadioStatusHolder = RadioStatusHolder()

    val txQueue: TxQueue = TxQueue()

    val seqCounter: SeqCounter = SeqCounter()

    val pingRepository: PingRepository by lazy {
        PingRepository(database.pingDao(), txQueue, seqCounter, { groupRepository.current.value }, { locationSource.fixes.value }, appScope)
    }

    /** Fresh incoming pings, for the in-app banner. No replay: late subscribers use the timeline. */
    private val _incomingPings = MutableSharedFlow<PingEntity>(extraBufferCapacity = 16)
    val incomingPings: SharedFlow<PingEntity> = _incomingPings.asSharedFlow()
    fun publishIncoming(ping: PingEntity) { _incomingPings.tryEmit(ping) }

    val venueRepository: VenueRepository by lazy { VenueRepository(appContext) }
}
