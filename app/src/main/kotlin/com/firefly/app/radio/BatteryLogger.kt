package com.firefly.app.radio

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.firefly.app.data.repo.FieldLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BatteryStatus(
    val level: Int,
    val charging: Boolean,
    val startLevel: Int,
    val startedAtMillis: Long,
    /** Percent per hour since the service started; null until 5 minutes have passed. */
    val drainPerHour: Double?,
)

/**
 * Battery level and drain rate while the service runs (BRD BR-04: ≤ 3 %/h).
 * Logged to Logcat and to the field log once a minute.
 *
 *   adb logcat -s Firefly/Battery
 */
class BatteryLogger(context: Context, private val fieldLog: FieldLogRepository) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private var job: Job? = null

    private val _status = MutableStateFlow<BatteryStatus?>(null)
    val status: StateFlow<BatteryStatus?> = _status.asStateFlow()

    fun start(scope: CoroutineScope, periodMillis: Long = 60_000L) {
        if (job?.isActive == true) return
        job = scope.launch {
            val startPct = level()
            val startAt = System.currentTimeMillis()
            Log.i(TAG, "start level=$startPct%")
            _status.value = BatteryStatus(startPct, bm.isCharging, startPct, startAt, null)
            fieldLog.battery(startPct, bm.isCharging, null)
            while (isActive) {
                delay(periodMillis)
                val now = System.currentTimeMillis()
                val pct = level()
                val hours = (now - startAt) / 3_600_000.0
                val drain = if (now - startAt >= MIN_WINDOW_MS) (startPct - pct) / hours else null
                _status.value = BatteryStatus(pct, bm.isCharging, startPct, startAt, drain)
                fieldLog.battery(pct, bm.isCharging, drain)
                Log.i(TAG, "level=$pct% elapsed=${"%.2f".format(hours)}h drain=${drain?.let { "%.2f".format(it) } ?: "-"}%/h charging=${bm.isCharging}")
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
    }

    fun level(): Int = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private companion object {
        const val TAG = "Firefly/Battery"
        const val MIN_WINDOW_MS = 5 * 60_000L
    }
}
