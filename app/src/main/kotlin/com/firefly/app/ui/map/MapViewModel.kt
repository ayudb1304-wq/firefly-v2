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
    val projection: MapProjection,
    /** Non-null when the venue image should be drawn under the dots. */
    val venue: VenuePack?,
    /** Free-map width in metres (null when a venue is shown). */
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
            return@combine MapFrame(venue.projection, venue, null, null, waitingForFix = false)
        }
        val positions = members.mapNotNull { m -> if (m.lat != null && m.lon != null) LatLon(m.lat, m.lon) else null }
        val anchor = me ?: freeCentre ?: positions.firstOrNull()
            ?: return@combine MapFrame(FALLBACK, venue, FreeMap.MIN_WIDTH_M, null, waitingForFix = true)

        val farthest = positions.maxOfOrNull { GeoMath.distanceMetres(anchor.lat, anchor.lon, it.lat, it.lon) }
        val width = FreeMap.nextWidth(freeWidth, farthest).also { freeWidth = it }
        if (me != null && FreeMap.shouldRecentre(freeCentre, me, width)) freeCentre = me
        val centre = freeCentre ?: anchor.also { freeCentre = it }
        val projection = MapProjection.centredOn(centre.lat, centre.lon, width, VIRTUAL_PX, VIRTUAL_PX)
        val venueDistance = venue?.let { v -> v.projection.centre().let { c -> GeoMath.distanceMetres(anchor.lat, anchor.lon, c.lat, c.lon) } }
        MapFrame(projection, null, width, venueDistance, waitingForFix = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapFrame(FALLBACK, null, FreeMap.MIN_WIDTH_M, null, waitingForFix = true))

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

    private companion object {
        const val VIRTUAL_PX = 1000
        val FALLBACK: MapProjection = MapProjection.centredOn(0.0, 0.0, FreeMap.MIN_WIDTH_M, VIRTUAL_PX, VIRTUAL_PX)
    }
}
