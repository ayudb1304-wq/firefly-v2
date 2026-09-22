package com.firefly.app.core.protocol

/**
 * The only place in the code base that turns [Packet]s into bytes and back.
 * Layout: PROTOCOL.md §2, big-endian, exactly [Protocol.PACKET_SIZE] bytes.
 */
object PacketCodec {

    fun encode(p: Packet): ByteArray {
        val b = ByteArray(Protocol.PACKET_SIZE)
        b[Protocol.Offset.VER_TYPE] = ((p.version shl 4) or p.type).toByte()
        putU32(b, Protocol.Offset.GROUP_ID, p.groupId)
        putU16(b, Protocol.Offset.SENDER_ID, p.senderId)
        putU16(b, Protocol.Offset.SEQ, p.seq)
        b[Protocol.Offset.TTL_HOPS] = ((p.ttl shl 4) or p.hops).toByte()
        if (p.type == Protocol.Type.NAME) {
            val name = p.name.orEmpty()
            for (i in 0 until Protocol.NAME_LENGTH) {
                b[Protocol.Offset.LAT + i] = if (i < name.length) name[i].code.toByte() else 0
            }
        } else {
            putI32(b, Protocol.Offset.LAT, p.latE6)
            putI32(b, Protocol.Offset.LON, p.lonE6)
        }
        var flags = p.accuracyBucket and Protocol.Flags.ACCURACY_MASK
        if (p.priority) flags = flags or Protocol.Flags.PRIORITY
        if (p.ackRequested) flags = flags or Protocol.Flags.ACK_REQUESTED
        b[Protocol.Offset.FLAGS] = flags.toByte()
        b[Protocol.Offset.CODE] = p.code.toByte()
        b[Protocol.Offset.ARG] = p.arg.toByte()
        putU16(b, Protocol.Offset.TARGET, p.target)
        b[Protocol.Offset.TS] = p.ts.toByte()
        return b
    }

    /**
     * Decode a manufacturer-data payload. Returns null when the bytes cannot
     * be a Firefly packet: wrong length, or unsupported protocol version.
     * Group filtering is the caller's job (PROTOCOL.md §5 step 1).
     */
    fun decode(b: ByteArray): Packet? {
        if (b.size != Protocol.PACKET_SIZE) return null
        val verType = b[Protocol.Offset.VER_TYPE].toInt() and 0xFF
        val version = verType ushr 4
        if (version != Protocol.VERSION) return null
        val type = verType and 0x0F
        val ttlHops = b[Protocol.Offset.TTL_HOPS].toInt() and 0xFF
        val flags = b[Protocol.Offset.FLAGS].toInt() and 0xFF
        val accuracy = flags and Protocol.Flags.ACCURACY_MASK
        if (accuracy > AccuracyBucket.MAX) return null

        val isName = type == Protocol.Type.NAME
        val name = if (isName) decodeName(b) ?: return null else null

        return Packet(
            version = version,
            type = type,
            groupId = getU32(b, Protocol.Offset.GROUP_ID),
            senderId = getU16(b, Protocol.Offset.SENDER_ID),
            seq = getU16(b, Protocol.Offset.SEQ),
            ttl = ttlHops ushr 4,
            hops = ttlHops and 0x0F,
            latE6 = if (isName) 0 else getI32(b, Protocol.Offset.LAT),
            lonE6 = if (isName) 0 else getI32(b, Protocol.Offset.LON),
            priority = flags and Protocol.Flags.PRIORITY != 0,
            ackRequested = flags and Protocol.Flags.ACK_REQUESTED != 0,
            accuracyBucket = accuracy,
            code = b[Protocol.Offset.CODE].toInt() and 0xFF,
            arg = b[Protocol.Offset.ARG].toInt() and 0xFF,
            target = getU16(b, Protocol.Offset.TARGET),
            ts = b[Protocol.Offset.TS].toInt() and 0xFF,
            name = name,
        )
    }

    private fun decodeName(b: ByteArray): String? {
        val sb = StringBuilder(Protocol.NAME_LENGTH)
        for (i in 0 until Protocol.NAME_LENGTH) {
            val c = b[Protocol.Offset.LAT + i].toInt() and 0xFF
            if (c == 0) break
            if (c !in 0x20..0x7E) return null
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 8).toByte()
        b[off + 1] = v.toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun putI32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun getU16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun getU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    private fun getI32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
}

/** Debug helper: `00 9F 8C ...` as in PROTOCOL.md §8. */
fun ByteArray.toHexSpaced(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
