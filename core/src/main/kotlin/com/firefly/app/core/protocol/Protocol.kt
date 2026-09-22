package com.firefly.app.core.protocol

/**
 * Constants from docs/PROTOCOL.md (Firefly Protocol v0).
 *
 * This file is the single source of truth for the wire layout inside the
 * code base. Anything that touches bytes goes through [PacketCodec] (Phase 1)
 * and uses these offsets. Do not change a value here without updating
 * PROTOCOL.md and bumping [VERSION].
 */
object Protocol {
    /** Protocol version carried in the high nibble of byte 0. */
    const val VERSION: Int = 0

    /** Total payload size in bytes inside the Manufacturer Specific Data AD. */
    const val PACKET_SIZE: Int = 24

    /** Bluetooth SIG company identifier used during development (test ID). */
    const val COMPANY_ID: Int = 0xFFFF

    /** Legacy advert: 31 bytes minus 4 bytes of AD overhead (len, type, company). */
    const val MAX_MANUFACTURER_PAYLOAD: Int = 27

    /** `target` value meaning "whole group". */
    const val TARGET_BROADCAST: Int = 0xFFFF

    /** `senderId` 0x0000 is reserved and never assigned. */
    const val SENDER_ID_RESERVED: Int = 0x0000

    /** Byte offsets of every field in the 24-byte packet (big-endian). */
    object Offset {
        const val VER_TYPE = 0
        const val GROUP_ID = 1
        const val SENDER_ID = 5
        const val SEQ = 7
        const val TTL_HOPS = 9
        const val LAT = 10
        const val LON = 14
        const val FLAGS = 18
        const val CODE = 19
        const val ARG = 20
        const val TARGET = 21
        const val TS = 23
    }

    /** Field sizes in bytes, in packet order. Must sum to [PACKET_SIZE]. */
    object Size {
        const val VER_TYPE = 1
        const val GROUP_ID = 4
        const val SENDER_ID = 2
        const val SEQ = 2
        const val TTL_HOPS = 1
        const val LAT = 4
        const val LON = 4
        const val FLAGS = 1
        const val CODE = 1
        const val ARG = 1
        const val TARGET = 2
        const val TS = 1

        val ALL_IN_ORDER: List<Int> = listOf(
            VER_TYPE, GROUP_ID, SENDER_ID, SEQ, TTL_HOPS, LAT, LON, FLAGS, CODE, ARG, TARGET, TS,
        )
    }

    /** Packet types: low nibble of byte 0. */
    object Type {
        const val BEACON = 0x0
        const val PING = 0x1
        const val NAME = 0x2
        const val ACK = 0x3
        const val TOTEM = 0xF
    }

    /** Bits inside the `flags` byte. */
    object Flags {
        const val PRIORITY = 0x80          // bit 7: SOS
        const val ACK_REQUESTED = 0x40     // bit 6
        const val ACCURACY_MASK = 0x0F     // bits 3-0: accuracy bucket
    }

    /** Default TTL per packet type (PROTOCOL.md §4). */
    object Ttl {
        const val MAX = 15
        const val DEFAULT = 6
        const val NAME = 4
        const val ACK = 6
        const val SOS = 15
    }

    /** Fixed-point scale for lat/lon: degrees × 1e6 stored as i32. */
    const val COORD_SCALE: Int = 1_000_000

    /** Display name carried by a NAME packet: bytes 10–17 as ASCII. */
    const val NAME_LENGTH: Int = 8
}
