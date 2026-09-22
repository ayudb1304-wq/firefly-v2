package com.firefly.app.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.util.Log
import com.firefly.app.core.protocol.Protocol

/**
 * One legacy, non-connectable, non-scannable advertising set carrying a
 * Firefly packet as Manufacturer Specific Data (PROTOCOL.md §1).
 *
 * Preferred path is [AdvertisingSet] so the payload can be swapped in place
 * (own beacon ↔ relay bursts, Phase 3). If the chipset refuses the set API we
 * fall back to the old start/stop API. Callbacks arrive on the main thread.
 *
 * Callers must hold BLUETOOTH_ADVERTISE (API 31+); the service checks before use.
 */
@SuppressLint("MissingPermission")
class Advertiser(
    private val adapter: BluetoothAdapter,
    /** AdvertisingSetParameters.INTERVAL_* (units of 0.625 ms). */
    private val intervalUnits: Int = AdvertisingSetParameters.INTERVAL_MEDIUM,
    private val onStateChanged: (advertising: Boolean, error: String?) -> Unit,
) {
    private var advertisingSet: AdvertisingSet? = null
    private var legacyActive = false
    private var pendingPayload: ByteArray? = null
    private var starting = false

    private val setCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
            starting = false
            if (status == ADVERTISE_SUCCESS && set != null) {
                advertisingSet = set
                Log.i(TAG, "advertising set started, txPower=$txPower dBm")
                onStateChanged(true, null)
                pendingPayload?.let { p -> pendingPayload = null; update(p) }
            } else {
                Log.w(TAG, "advertising set failed status=$status; falling back to legacy API")
                pendingPayload?.let { startLegacy(it) } ?: onStateChanged(false, "advertise failed ($status)")
            }
        }

        override fun onAdvertisingSetStopped(set: AdvertisingSet?) {
            advertisingSet = null
            onStateChanged(false, null)
        }
    }

    private val legacyCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            legacyActive = true
            onStateChanged(true, null)
        }

        override fun onStartFailure(errorCode: Int) {
            legacyActive = false
            onStateChanged(false, "legacy advertise failed ($errorCode)")
        }
    }

    /** Start (or restart) advertising [payload]. Safe to call repeatedly; later calls become [update]s. */
    fun start(payload: ByteArray) {
        require(payload.size == Protocol.PACKET_SIZE)
        if (advertisingSet != null || legacyActive) {
            update(payload); return
        }
        if (starting) {
            pendingPayload = payload; return
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onStateChanged(false, "BLE advertising unsupported or Bluetooth off"); return
        }
        pendingPayload = payload
        starting = true
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(false)
            .setScannable(false)
            .setInterval(intervalUnits)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .build()
        try {
            advertiser.startAdvertisingSet(params, data(payload), null, null, null, setCallback)
        } catch (e: IllegalArgumentException) {
            starting = false
            Log.w(TAG, "startAdvertisingSet rejected: ${e.message}; using legacy API")
            startLegacy(payload)
        }
    }

    /** Replace the advertised bytes without restarting the set. */
    fun update(payload: ByteArray) {
        require(payload.size == Protocol.PACKET_SIZE)
        val set = advertisingSet
        when {
            set != null -> set.setAdvertisingData(data(payload))
            legacyActive -> {
                adapter.bluetoothLeAdvertiser?.stopAdvertising(legacyCallback)
                legacyActive = false
                startLegacy(payload)
            }
            else -> start(payload)
        }
    }

    fun stop() {
        pendingPayload = null
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertisingSet != null) {
            runCatching { advertiser?.stopAdvertisingSet(setCallback) }
            advertisingSet = null
        }
        if (legacyActive) {
            runCatching { advertiser?.stopAdvertising(legacyCallback) }
            legacyActive = false
        }
        starting = false
        onStateChanged(false, null)
    }

    private fun startLegacy(payload: ByteArray) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: run {
            onStateChanged(false, "Bluetooth off"); return
        }
        pendingPayload = null
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                when {
                    intervalUnits <= AdvertisingSetParameters.INTERVAL_LOW -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                    intervalUnits >= AdvertisingSetParameters.INTERVAL_HIGH -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                    else -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
                },
            )
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        advertiser.startAdvertising(settings, data(payload), legacyCallback)
    }

    private fun data(payload: ByteArray): AdvertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(Protocol.COMPANY_ID, payload)
        .build()

    private companion object {
        const val TAG = "Firefly/Adv"
    }
}
