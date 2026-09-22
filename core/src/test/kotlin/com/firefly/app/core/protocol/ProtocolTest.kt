package com.firefly.app.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtocolTest {

    @Test
    fun `field sizes sum to the 24-byte packet`() {
        assertEquals(Protocol.PACKET_SIZE, Protocol.Size.ALL_IN_ORDER.sum())
    }

    @Test
    fun `offsets are contiguous and match sizes`() {
        val offsets = listOf(
            Protocol.Offset.VER_TYPE, Protocol.Offset.GROUP_ID, Protocol.Offset.SENDER_ID,
            Protocol.Offset.SEQ, Protocol.Offset.TTL_HOPS, Protocol.Offset.LAT, Protocol.Offset.LON,
            Protocol.Offset.FLAGS, Protocol.Offset.CODE, Protocol.Offset.ARG, Protocol.Offset.TARGET,
            Protocol.Offset.TS,
        )
        var expected = 0
        offsets.zip(Protocol.Size.ALL_IN_ORDER).forEach { (offset, size) ->
            assertEquals(expected, offset, "offset mismatch at $offset")
            expected += size
        }
        assertEquals(Protocol.PACKET_SIZE, expected)
    }

    @Test
    fun `packet fits in a legacy advert with company id`() {
        assertTrue(Protocol.PACKET_SIZE <= Protocol.MAX_MANUFACTURER_PAYLOAD)
        assertEquals(31 - 4, Protocol.MAX_MANUFACTURER_PAYLOAD)
    }

    @Test
    fun `NAME payload occupies exactly the lat and lon bytes`() {
        assertEquals(Protocol.Size.LAT + Protocol.Size.LON, Protocol.NAME_LENGTH)
        assertEquals(Protocol.Offset.LAT, 10)
        assertEquals(Protocol.Offset.LAT + Protocol.NAME_LENGTH, Protocol.Offset.FLAGS)
    }
}
