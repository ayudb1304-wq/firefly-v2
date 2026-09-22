package com.firefly.app.radio

/** Live view of the radio for the UI and field logs. Published by [FireflyService] through [RadioStatusHolder]. */
data class RadioStatus(
    val running: Boolean = false,
    val advertising: Boolean = false,
    val scanning: Boolean = false,
    /** Non-null when the service is up but cannot do its job (Bluetooth off, permission revoked). */
    val degradedReason: String? = null,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val packetsDeduped: Int = 0,
    val packetsForeign: Int = 0,
    val packetsRelayed: Int = 0,
    val relaysDropped: Int = 0,
    val lastPacketAtMillis: Long? = null,
    /** Received packets per second over the last minute (dups included: it measures air load). */
    val rxPerSecond: Double = 0.0,
    val startedAtMillis: Long? = null,
    val battery: BatteryStatus? = null,
)
