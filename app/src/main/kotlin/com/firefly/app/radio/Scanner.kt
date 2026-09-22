package com.firefly.app.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import com.firefly.app.core.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Raw advert as seen by the radio, before decoding. */
class RawAdvert(val bytes: ByteArray, val rssi: Int, val timestampMillis: Long)

/**
 * Duty-cycled BLE scanner filtered on our manufacturer ID (PROTOCOL.md §6).
 *
 * Android silently throttles an app that starts more than 5 scans in 30 s, so
 * the on/off period must stay ≥ 6 s. Defaults are 8 s on / 2 s off (80 % duty,
 * 3 starts per 30 s). The off gap must be shorter than a ping's repeat span
 * (3 × 1.5 s) so no message can fall entirely into a gap — see DECISIONS.md.
 */
@SuppressLint("MissingPermission")
class Scanner(
    private val adapter: BluetoothAdapter,
    private val onAdvert: (RawAdvert) -> Unit,
    private val onStateChanged: (scanning: Boolean, error: String?) -> Unit,
) {
    var onMillis: Long = DEFAULT_ON_MS
    var offMillis: Long = DEFAULT_OFF_MS

    private var cycleJob: Job? = null
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Log.w(TAG, "scan failed: $errorCode")
            onStateChanged(false, "scan failed ($errorCode)")
        }
    }

    fun startCycling(scope: CoroutineScope) {
        if (cycleJob?.isActive == true) return
        cycleJob = scope.launch {
            while (isActive) {
                startScan()
                delay(onMillis)
                stopScan()
                delay(offMillis)
            }
        }
    }

    fun stop() {
        cycleJob?.cancel()
        cycleJob = null
        stopScan()
    }

    private fun handle(result: ScanResult) {
        val payload = result.scanRecord?.getManufacturerSpecificData(Protocol.COMPANY_ID) ?: return
        val wall = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtimeNanos() - result.timestampNanos) / 1_000_000
        onAdvert(RawAdvert(payload, result.rssi, wall))
    }

    private fun startScan() {
        if (scanning) return
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onStateChanged(false, "Bluetooth off"); return
        }
        val filter = ScanFilter.Builder()
            .setManufacturerData(Protocol.COMPANY_ID, ByteArray(0))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            scanning = true
            onStateChanged(true, null)
        } catch (e: IllegalStateException) {
            onStateChanged(false, "Bluetooth off")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        runCatching { adapter.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
        onStateChanged(false, null)
    }

    companion object {
        private const val TAG = "Firefly/Scan"
        const val DEFAULT_ON_MS = 8_000L
        const val DEFAULT_OFF_MS = 2_000L
    }
}
