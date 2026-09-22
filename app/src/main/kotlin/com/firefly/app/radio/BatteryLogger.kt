package com.firefly.app.radio

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Logs battery level and drain rate to Logcat while the service runs.
 * Phase 1 exit criterion: "battery drain logged". Phase 5 moves this into the CSV.
 *
 *   adb logcat -s Firefly/Battery
 */
class BatteryLogger(context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private var job: Job? = null

    fun start(scope: CoroutineScope, periodMillis: Long = 60_000L) {
        if (job?.isActive == true) return
        job = scope.launch {
            val startPct = level()
            val startAt = System.currentTimeMillis()
            Log.i(TAG, "start level=$startPct%")
            while (isActive) {
                delay(periodMillis)
                val now = System.currentTimeMillis()
                val pct = level()
                val hours = (now - startAt) / 3_600_000.0
                val drainPerHour = if (hours > 0) (startPct - pct) / hours else 0.0
                Log.i(TAG, "level=$pct% elapsed=${"%.2f".format(hours)}h drain=${"%.2f".format(drainPerHour)}%/h charging=${charging()}")
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
    }

    fun level(): Int = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun charging(): Boolean = bm.isCharging

    private companion object {
        const val TAG = "Firefly/Battery"
    }
}
