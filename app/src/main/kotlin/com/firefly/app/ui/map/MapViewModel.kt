package com.firefly.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firefly.app.data.db.MemberEntity
import com.firefly.app.di.AppContainer
import com.firefly.app.location.Fix
import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.geo.MapProjection
import com.firefly.app.radio.RadioStatus
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(private val container: AppContainer) : ViewModel() {
    val venue get() = container.venue

    val members: StateFlow<List<MemberEntity>> =
        container.memberRepository.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myFix: StateFlow<Fix?> = container.locationSource.fixes

    val radio: StateFlow<RadioStatus> = container.radioStatus.status

    /** The map calibration in use plus, when off-venue, how far away the real venue is. */
    data class MapFrame(val projection: MapProjection, val testMap: Boolean, val venueDistanceMetres: Double?)

    private var testProjection: MapProjection? = null

    /**
     * Off-venue fallback for walk tests: if my fix is outside the venue image, draw a
     * synthetic 400 m map centred on where I first was, and re-centre only if I leave it.
     */
    val frame: StateFlow<MapFrame> = container.locationSource.fixes.map { fix ->
        val real = venue.projection
        when {
            fix == null -> testProjection?.let { MapFrame(it, true, null) } ?: MapFrame(real, false, null)
            real.contains(fix.lat, fix.lon) -> { testProjection = null; MapFrame(real, false, null) }
            else -> {
                val current = testProjection?.takeIf { it.contains(fix.lat, fix.lon) }
                    ?: MapProjection.centredOn(fix.lat, fix.lon, TEST_MAP_WIDTH_M, venue.image.width, venue.image.height).also { testProjection = it }
                val vc = real.centre()
                MapFrame(current, true, GeoMath.distanceMetres(fix.lat, fix.lon, vc.lat, vc.lon))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapFrame(venue.projection, false, null))

    private companion object {
        const val TEST_MAP_WIDTH_M = 400.0
    }

    fun leave(onDone: () -> Unit) = viewModelScope.launch {
        container.groupRepository.leave()
        onDone()
    }
}
