package com.firefly.app.ui.map

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firefly.app.core.geo.FreeMap
import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.geo.MapProjection
import com.firefly.app.core.group.SenderId
import com.firefly.app.data.db.MemberEntity
import com.firefly.app.data.db.PingEntity
import com.firefly.app.di.AppContainer
import com.firefly.app.location.Fix
import com.firefly.app.location.Heading
import com.firefly.app.core.messaging.LighthousePattern
import com.firefly.app.core.messaging.Rendezvous
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.radio.RadioStatus
import com.firefly.app.venue.VenuePack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the map draws: either a venue image or a free grid, and the calibration for it. */
data class MapFrame(
    /** Venue calibration when the venue image is shown; null in free-map mode. */
    val projection: MapProjection?,
    /** Non-null when the venue image should be drawn under the dots. */
    val venue: VenuePack?,
    /** Free-map centre and width in metres (null when a venue is shown). */
    val centre: LatLon?,
    val widthMetres: Double?,
    /** Set when a venue pack is loaded but I am outside it. */
    val venueDistanceMetres: Double?,
    /** True until we have any position to centre on. */
    val waitingForFix: Boolean,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {

    val members: StateFlow<List<MemberEntity>> =
        container.memberRepository.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myFix: StateFlow<Fix?> = container.locationSource.fixes

    val radio: StateFlow<RadioStatus> = container.radioStatus.status

    val venue: StateFlow<VenuePack?> = container.venueRepository.venue

    val myName: StateFlow<String?> = container.settings.displayName
        .map { it?.takeIf { n -> n.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pings: StateFlow<List<PingEntity>> =
        container.pingRepository.timeline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** senderId → display name (hex ID when unknown). */
    val names: StateFlow<Map<Int, String>> = members.map { list ->
        list.associate { it.senderId to (it.name ?: SenderId.hex(it.senderId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val incoming: SharedFlow<PingEntity> = container.incomingPings

    fun send(code: Int, arg: Int, target: Int) = viewModelScope.launch {
        if (code == com.firefly.app.core.protocol.Codebook.HELP) { container.pingRepository.startSos(); return@launch }
        if (container.pingRepository.send(code, arg, target) == null) _message.value = "Could not send"
    }

    val sosActive: StateFlow<Boolean> = container.pingRepository.sosActive
    fun cancelSos() = container.pingRepository.stopSos()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Free-map state, kept between emissions so the map does not jump on every fix.
    private var freeCentre: LatLon? = null
    private var freeWidth: Double? = null

    val frame: StateFlow<MapFrame> = combine(myFix, members, venue) { fix, members, venue ->
        val me = fix?.let { LatLon(it.lat, it.lon) }
        if (venue != null && me != null && venue.projection.contains(me.lat, me.lon)) {
            freeCentre = null; freeWidth = null
            return@combine MapFrame(venue.projection, venue, null, null, null, waitingForFix = false)
        }
        val positions = members.mapNotNull { m -> if (m.lat != null && m.lon != null) LatLon(m.lat, m.lon) else null }
        val anchor = me ?: freeCentre ?: positions.firstOrNull()
            ?: return@combine MapFrame(null, venue, null, FreeMap.MIN_WIDTH_M, null, waitingForFix = true)

        val farthest = positions.maxOfOrNull { GeoMath.distanceMetres(anchor.lat, anchor.lon, it.lat, it.lon) }
        val width = FreeMap.nextWidth(freeWidth, farthest).also { freeWidth = it }
        if (me != null && FreeMap.shouldRecentre(freeCentre, me, width)) freeCentre = me
        val centre = freeCentre ?: anchor.also { freeCentre = it }
        val venueDistance = venue?.let { v -> v.projection.centre().let { c -> GeoMath.distanceMetres(anchor.lat, anchor.lon, c.lat, c.lon) } }
        MapFrame(null, null, centre, width, venueDistance, waitingForFix = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapFrame(null, null, null, FreeMap.MIN_WIDTH_M, null, waitingForFix = true))

    fun importVenue(uri: Uri) = viewModelScope.launch {
        container.venueRepository.importZip(uri)
            .onSuccess { _message.value = "Loaded venue: ${it.name}" }
            .onFailure { _message.value = "Could not load venue pack: ${it.message ?: "invalid file"}" }
    }

    fun removeVenue() {
        container.venueRepository.remove()
        _message.value = "Venue pack removed"
    }

    fun consumeMessage() { _message.value = null }

    fun leave(onDone: () -> Unit) = viewModelScope.launch {
        container.groupRepository.leave()
        onDone()
    }

    // ---- Phase 4: compass, meeting points, lighthouse ----

    val heading: StateFlow<Heading?> = container.headingSource.heading
    fun startCompass() = container.headingSource.start()
    fun stopCompass() = container.headingSource.stop()

    /** Meeting points from MEET_AT pings in the last 30 minutes, newest per counterpart. */
    val meetPins: StateFlow<List<MapPin>> = combine(pings, venue, names) { list, venue, names ->
        val cutoff = System.currentTimeMillis() - MEET_PIN_TTL_MS
        list.asSequence()
            .filter { it.code == Codebook.MEET_AT && it.ts >= cutoff }
            .distinctBy { if (it.direction == PingEntity.IN) it.senderId else it.target }
            .mapNotNull { p ->
                val point = if (p.arg == Codebook.POI_HERE) {
                    if (p.lat != null && p.lon != null) LatLon(p.lat, p.lon) else null
                } else venue?.pois?.firstOrNull { it.index == p.arg }?.let { LatLon(it.lat, it.lon) }
                point?.let {
                    val other = if (p.direction == PingEntity.IN) p.senderId else p.target
                    val who = if (other == Protocol.TARGET_BROADCAST) null else names[other] ?: SenderId.hex(other)
                    val place = venue?.pois?.firstOrNull { poi -> poi.index == p.arg && p.arg != Codebook.POI_HERE }?.name
                    MapPin(it.lat, it.lon, listOfNotNull(place ?: "Meet", who).joinToString(" · "))
                }
            }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** senderId → pattern label for members whose LIGHTHOUSE_ON is less than 2 minutes old. */
    val lighthouses: StateFlow<Map<Int, String>> = pings.map { list ->
        val cutoff = System.currentTimeMillis() - LighthousePattern.AUTO_OFF_MS
        list.filter { it.direction == PingEntity.IN && it.code == Codebook.LIGHTHOUSE_ON && it.ts >= cutoff }
            .associate { it.senderId to LighthousePattern.label(LighthousePattern.forSender(it.senderId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    sealed interface MeetPlan {
        data class Ready(val member: MemberEntity, val suggestion: Rendezvous.Suggestion) : MeetPlan
        data class NoTheirPosition(val member: MemberEntity) : MeetPlan
        data object NoMyPosition : MeetPlan
    }

    fun planMeeting(memberId: Int): MeetPlan? {
        val m = members.value.firstOrNull { it.senderId == memberId } ?: return null
        val me = myFix.value ?: return MeetPlan.NoMyPosition
        val lat = m.lat ?: return MeetPlan.NoTheirPosition(m)
        val lon = m.lon ?: return MeetPlan.NoTheirPosition(m)
        val places = venue.value?.pois.orEmpty().map { Rendezvous.Place(it.index, it.name, LatLon(it.lat, it.lon)) }
        return MeetPlan.Ready(m, Rendezvous.suggest(LatLon(me.lat, me.lon), LatLon(lat, lon), m.lastSeen, places, System.currentTimeMillis()))
    }

    fun propose(plan: MeetPlan.Ready) = viewModelScope.launch {
        val s = plan.suggestion
        container.pingRepository.send(Codebook.MEET_AT, s.poi?.index ?: Codebook.POI_HERE, plan.member.senderId, if (s.poi == null) s.point else null)
    }

    /** Accept a MEET_AT: the automatic ACK already went out; add the human answer. */
    fun accept(ping: PingEntity) = send(Codebook.ON_MY_WAY, 0, ping.senderId)

    fun startLighthouse() = send(Codebook.LIGHTHOUSE_ON, 0, Protocol.TARGET_BROADCAST)

    /** Unread = incoming pings newer than the last time the timeline was opened. */
    private val _timelineOpenedAt = MutableStateFlow(0L)
    val unread: StateFlow<Int> = combine(pings, _timelineOpenedAt) { list, since ->
        list.count { it.direction == PingEntity.IN && it.ts > since }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markTimelineSeen() { _timelineOpenedAt.value = System.currentTimeMillis() }

    private companion object {
        const val MEET_PIN_TTL_MS = 30 * 60_000L
    }
}
