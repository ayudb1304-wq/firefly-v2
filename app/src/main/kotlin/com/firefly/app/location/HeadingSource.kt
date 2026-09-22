package com.firefly.app.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.firefly.app.core.geo.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which way the top of the phone points, degrees clockwise from true north. */
data class Heading(val degrees: Float, val accuracy: Int) {
    /** SensorManager.SENSOR_STATUS_ACCURACY_LOW or worse: ask the user to calibrate. */
    val unreliable: Boolean get() = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

/**
 * Compass from the rotation-vector sensor (fused gyro + magnetometer), corrected
 * to true north with the magnetic declination at the last GPS fix. No permission
 * needed. Only run while the map is visible: it is a battery cost.
 */
class HeadingSource(context: Context, private val fixes: StateFlow<Fix?>) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _heading = MutableStateFlow<Heading?>(null)
    val heading: StateFlow<Heading?> = _heading.asStateFlow()
    val available: Boolean get() = sensor != null

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothed: Float? = null
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }

    fun stop() { sm.unregisterListener(this); smoothed = null; _heading.value = null }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        fixes.value?.let { f ->
            azimuth += GeomagneticField(f.lat.toFloat(), f.lon.toFloat(), 0f, System.currentTimeMillis()).declination
        }
        azimuth = ((azimuth % 360f) + 360f) % 360f
        // Low-pass on the circle so the arrow does not jitter.
        val prev = smoothed
        val next = if (prev == null) azimuth else {
            val delta = GeoMath.angleDelta(azimuth.toDouble(), prev.toDouble()).toFloat()
            (((prev + delta * SMOOTHING) % 360f) + 360f) % 360f
        }
        smoothed = next
        _heading.value = Heading(next, accuracy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) this.accuracy = accuracy
    }

    private companion object {
        const val SMOOTHING = 0.25f
    }
}
