package com.firefly.app.core.messaging

import com.firefly.app.core.protocol.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AckMatcherTest {
    private val out = listOf(
        AckMatcher.Outgoing(id = 1, seq = 0x012A, target = 0x3C4D, sentAtMillis = 1_000),
        AckMatcher.Outgoing(id = 2, seq = 0x022A, target = Protocol.TARGET_BROADCAST, sentAtMillis = 5_000),
        AckMatcher.Outgoing(id = 3, seq = 0x0300, target = 0x3C4D, sentAtMillis = 9_000),
    )

    @Test
    fun `matches by seq low byte and recipient, newest first`() {
        assertEquals(2L, AckMatcher.match(out, ackFrom = 0x1111, ackArg = 0x2A, nowMillis = 10_000), "broadcast accepts any acker")
        assertEquals(2L, AckMatcher.match(out, ackFrom = 0x3C4D, ackArg = 0x2A, nowMillis = 10_000), "newest of two candidates")
        assertEquals(3L, AckMatcher.match(out, ackFrom = 0x3C4D, ackArg = 0x00, nowMillis = 10_000))
        assertNull(AckMatcher.match(out, ackFrom = 0x9999, ackArg = 0x00, nowMillis = 10_000), "targeted ping, wrong acker")
        assertNull(AckMatcher.match(out, ackFrom = 0x3C4D, ackArg = 0x2B, nowMillis = 10_000))
    }

    @Test
    fun `ignores pings older than the window`() {
        assertNull(AckMatcher.match(out, ackFrom = 0x3C4D, ackArg = 0x00, nowMillis = 9_000 + AckMatcher.WINDOW_MS + 1))
        assertEquals(3L, AckMatcher.match(out, ackFrom = 0x3C4D, ackArg = 0x00, nowMillis = 9_000 + AckMatcher.WINDOW_MS))
    }
}
