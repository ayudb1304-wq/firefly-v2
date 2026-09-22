package com.firefly.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.firefly.app.core.geo.GeoMath
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One GPS fix in the shape the rest of the app needs. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyMetres: Float?,
    /** Ground speed, m/s. From the provider when available, else derived from the previous fix. */
    val speedMps: Float?,
    val timeMillis: Long,
) {
    val isMoving: Boolean get() = (speedMps ?: 0f) > com.firefly.app.core.protocol.BeaconPolicy.MOVING_SPEED_MPS
}

/**
 * Thin wrapper over [FusedLocationProviderClient] (ARCHITECTURE.md §4).
 * Started and stopped by the service; observed by the map. Requires ACCESS_FINE_LOCATION.
 */
@SuppressLint("MissingPermission")
class LocationSource(context: Context) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val _fixes = MutableStateFlow<Fix?>(null)
    val fixes: StateFlow<Fix?> = _fixes.asStateFlow()

    private var active = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _fixes.value = toFix(loc, _fixes.value)
        }
    }

    fun start() {
        if (active) return
        active = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        client.lastLocation.addOnSuccessListener { loc -> if (loc != null && _fixes.value == null) _fixes.value = toFix(loc, null) }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Log.i(TAG, "location updates started")
    }

    fun stop() {
        if (!active) return
        active = false
        client.removeLocationUpdates(callback)
        Log.i(TAG, "location updates stopped")
    }

    private fun toFix(loc: Location, previous: Fix?): Fix {
        val speed = when {
            loc.hasSpeed() && loc.speed > 0f -> loc.speed
            previous != null && loc.time > previous.timeMillis -> {
                val d = GeoMath.distanceMetres(previous.lat, previous.lon, loc.latitude, loc.longitude)
                (d / ((loc.time - previous.timeMillis) / 1000.0)).toFloat()
            }
            else -> null
        }
        return Fix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyMetres = if (loc.hasAccuracy()) loc.accuracy else null,
            speedMps = speed,
            timeMillis = loc.time,
        )
    }

    private companion object {
        const val TAG = "Firefly/Loc"
        const val UPDATE_INTERVAL_MS = 2_000L
        const val MIN_INTERVAL_MS = 1_000L
    }
}
