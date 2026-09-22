package com.firefly.app.core.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PacketCodecTest {

    private val group = 0x9F8C2107L

    private fun roundTrip(p: Packet): Packet {
        val bytes = PacketCodec.encode(p)
        assertEquals(Protocol.PACKET_SIZE, bytes.size)
        val back = PacketCodec.decode(bytes) ?: error("decode returned null for ${bytes.toHexSpaced()}")
        assertEquals(p, back)
        assertArrayEquals(bytes, PacketCodec.encode(back), "re-encode must be byte-identical")
        return back
    }

    @Test
    fun `BEACON round trip`() {
        roundTrip(
            Packet(
                type = Protocol.Type.BEACON, groupId = group, senderId = 0x1A2B, seq = 7, ttl = 6, hops = 0,
                latE6 = Packet.toE6(19.0760), lonE6 = Packet.toE6(72.8777),
                accuracyBucket = AccuracyBucket.UNDER_25M, ts = 0x7B,
            ),
        )
    }

    @Test
    fun `PING round trip with priority, ack and target`() {
        roundTrip(
            Packet(
                type = Protocol.Type.PING, groupId = group, senderId = 0x1A2B, seq = 42, ttl = 15, hops = 3,
                latE6 = Packet.toE6(-33.8688), lonE6 = Packet.toE6(151.2093),
                priority = true, ackRequested = true, accuracyBucket = AccuracyBucket.UNDER_10M,
                code = Codebook.MEET_AT, arg = 4, target = 0x3C4D, ts = 255,
            ),
        )
    }

    @Test
    fun `NAME round trip carries ASCII in the position bytes`() {
        val p = roundTrip(
            Packet(type = Protocol.Type.NAME, groupId = group, senderId = 0x0001, seq = 1, ttl = 4, hops = 0, name = "Priya"),
        )
        assertEquals("Priya", p.name)
        assertEquals(0, p.latE6)
        val bytes = PacketCodec.encode(p)
        assertEquals('P'.code.toByte(), bytes[Protocol.Offset.LAT])
        assertEquals(0.toByte(), bytes[Protocol.Offset.LAT + 5], "unused name bytes are zero padded")
    }

    @Test
    fun `NAME round trip with the maximum 8 characters`() {
        roundTrip(Packet(type = Protocol.Type.NAME, groupId = group, senderId = 5, seq = 9, ttl = 4, hops = 1, name = "ABCDEFGH"))
    }

    @Test
    fun `ACK round trip`() {
        roundTrip(
            Packet(
                type = Protocol.Type.ACK, groupId = group, senderId = 0x3C4D, seq = 3, ttl = 6, hops = 0,
                latE6 = Packet.toE6(19.0), lonE6 = Packet.toE6(72.8), code = Codebook.ACK, arg = 0x2A, target = 0x1A2B,
            ),
        )
    }

    @Test
    fun `TOTEM round trip`() {
        roundTrip(Packet(type = Protocol.Type.TOTEM, groupId = group, senderId = 0xFFFE, seq = 0xFFFF, ttl = 15, hops = 0, latE6 = 1, lonE6 = -1))
    }

    @Test
    fun `extreme field values survive`() {
        roundTrip(
            Packet(
                type = 0xE, groupId = 0xFFFFFFFFL, senderId = 0xFFFF, seq = 0xFFFF, ttl = 15, hops = 15,
                latE6 = Int.MIN_VALUE, lonE6 = Int.MAX_VALUE, priority = true, ackRequested = true,
                accuracyBucket = 5, code = 0xFF, arg = 0xFF, target = 0, ts = 0xFF,
            ),
        )
        roundTrip(Packet(type = 0, groupId = 0, senderId = 0, seq = 0, ttl = 0, hops = 0))
    }

    @Test
    fun `worked example from PROTOCOL md section 8 encodes to the documented bytes`() {
        val p = Packet(
            type = Protocol.Type.PING, groupId = 0x9F8C2107L, senderId = 0x1A2B, seq = 42, ttl = 6, hops = 0,
            latE6 = 19_076_000, lonE6 = 72_877_700, ackRequested = true, accuracyBucket = 2,
            code = Codebook.MEET_AT, arg = 4, target = 0x3C4D, ts = 0x7B,
        )
        // Byte-for-byte the example in PROTOCOL.md §8 (19076000 = 0x012313A0, 72877700 = 0x04580684).
        val expected = "01 9F 8C 21 07 1A 2B 00 2A 60 01 23 13 A0 04 58 06 84 42 02 04 3C 4D 7B"
        assertEquals(expected, PacketCodec.encode(p).toHexSpaced())
    }

    @Test
    fun `decode rejects wrong length`() {
        assertNull(PacketCodec.decode(ByteArray(23)))
        assertNull(PacketCodec.decode(ByteArray(25)))
        assertNull(PacketCodec.decode(ByteArray(0)))
    }

    @Test
    fun `decode rejects other protocol versions`() {
        val bytes = PacketCodec.encode(Packet(type = 0, groupId = group, senderId = 1, seq = 1, ttl = 6, hops = 0))
        bytes[0] = (0x10 or (bytes[0].toInt() and 0x0F)).toByte()
        assertNull(PacketCodec.decode(bytes))
    }

    @Test
    fun `decode rejects invalid accuracy bucket and non-ASCII names`() {
        val ok = PacketCodec.encode(Packet(type = 0, groupId = group, senderId = 1, seq = 1, ttl = 6, hops = 0))
        ok[Protocol.Offset.FLAGS] = 0x0F
        assertNull(PacketCodec.decode(ok))

        val name = PacketCodec.encode(Packet(type = Protocol.Type.NAME, groupId = group, senderId = 1, seq = 1, ttl = 4, hops = 0, name = "A"))
        name[Protocol.Offset.LAT] = 0xC3.toByte()
        assertNull(PacketCodec.decode(name))
    }

    @Test
    fun `Packet validates ranges`() {
        assertThrows<IllegalArgumentException> { Packet(type = 16, groupId = 0, senderId = 0, seq = 0, ttl = 0, hops = 0) }
        assertThrows<IllegalArgumentException> { Packet(type = 0, groupId = 0, senderId = 0x10000, seq = 0, ttl = 0, hops = 0) }
        assertThrows<IllegalArgumentException> { Packet(type = 0, groupId = 0, senderId = 0, seq = 0, ttl = 16, hops = 0) }
        assertThrows<IllegalArgumentException> { Packet(type = Protocol.Type.NAME, groupId = 0, senderId = 0, seq = 0, ttl = 0, hops = 0, name = "TOO_LONG_NAME") }
        assertThrows<IllegalArgumentException> { Packet(type = Protocol.Type.NAME, groupId = 0, senderId = 0, seq = 0, ttl = 0, hops = 0, name = null) }
    }

    @Test
    fun `relayed consumes one TTL and adds one hop`() {
        val p = Packet(type = 1, groupId = group, senderId = 1, seq = 1, ttl = 6, hops = 2)
        val r = p.relayed()
        assertEquals(5, r.ttl)
        assertEquals(3, r.hops)
        assertTrue(r.copy(ttl = 6, hops = 2) == p)
    }

    @Test
    fun `helpers convert degrees and timestamps`() {
        assertEquals(19_076_000, Packet.toE6(19.0760))
        assertEquals(-33_868_800, Packet.toE6(-33.8688))
        assertEquals(19.076, Packet(type = 0, groupId = 0, senderId = 0, seq = 0, ttl = 0, hops = 0, latE6 = 19_076_000).latitude, 1e-9)
        assertEquals(0x7B, Packet.tsByte(123_000L))
        assertEquals(0x7B, Packet.tsByte((256 + 123) * 1000L))
    }
}
