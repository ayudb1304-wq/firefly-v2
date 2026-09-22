package com.firefly.app.core.protocol

/**
 * In-memory form of one 24-byte Firefly packet (PROTOCOL.md §2).
 *
 * All integer fields are unsigned on the wire and held here as non-negative
 * [Int]s (groupId as an unsigned 32-bit value in a [Long]). Latitude and
 * longitude are fixed-point degrees × 1e6. For [Protocol.Type.NAME] packets the
 * lat/lon bytes carry [name] instead and [latE6]/[lonE6] are zero.
 */
data class Packet(
    val type: Int,
    val groupId: Long,
    val senderId: Int,
    val seq: Int,
    val ttl: Int,
    val hops: Int,
    val latE6: Int = 0,
    val lonE6: Int = 0,
    val priority: Boolean = false,
    val ackRequested: Boolean = false,
    val accuracyBucket: Int = AccuracyBucket.UNKNOWN,
    val code: Int = Codebook.BEACON,
    val arg: Int = 0,
    val target: Int = Protocol.TARGET_BROADCAST,
    val ts: Int = 0,
    val name: String? = null,
    val version: Int = Protocol.VERSION,
) {
    init {
        require(type in 0..0xF) { "type out of range: $type" }
        require(version in 0..0xF) { "version out of range: $version" }
        require(groupId in 0..0xFFFF_FFFFL) { "groupId out of range: $groupId" }
        require(senderId in 0..0xFFFF) { "senderId out of range: $senderId" }
        require(seq in 0..0xFFFF) { "seq out of range: $seq" }
        require(ttl in 0..Protocol.Ttl.MAX) { "ttl out of range: $ttl" }
        require(hops in 0..0xF) { "hops out of range: $hops" }
        require(accuracyBucket in 0..AccuracyBucket.MAX) { "accuracyBucket out of range: $accuracyBucket" }
        require(code in 0..0xFF) { "code out of range: $code" }
        require(arg in 0..0xFF) { "arg out of range: $arg" }
        require(target in 0..0xFFFF) { "target out of range: $target" }
        require(ts in 0..0xFF) { "ts out of range: $ts" }
        if (type == Protocol.Type.NAME) {
            requireNotNull(name) { "NAME packet needs a name" }
            require(name.length <= Protocol.NAME_LENGTH) { "name longer than ${Protocol.NAME_LENGTH}" }
            require(name.all { it.code in 0x20..0x7E }) { "name must be printable ASCII" }
        }
    }

    val isBroadcast: Boolean get() = target == Protocol.TARGET_BROADCAST
    val latitude: Double get() = latE6.toDouble() / Protocol.COORD_SCALE
    val longitude: Double get() = lonE6.toDouble() / Protocol.COORD_SCALE
    val hasPosition: Boolean get() = type != Protocol.Type.NAME && (latE6 != 0 || lonE6 != 0)

    /** The packet as it should be re-advertised by a relay: one hop consumed. */
    fun relayed(): Packet = copy(ttl = ttl - 1, hops = minOf(hops + 1, 0xF))

    companion object {
        /** Convert degrees to the wire fixed-point representation. */
        fun toE6(degrees: Double): Int = Math.round(degrees * Protocol.COORD_SCALE).toInt()

        /** `ts` byte: seconds since epoch mod 256 (freshness hint only). */
        fun tsByte(epochMillis: Long): Int = ((epochMillis / 1000) and 0xFF).toInt()
    }
}
