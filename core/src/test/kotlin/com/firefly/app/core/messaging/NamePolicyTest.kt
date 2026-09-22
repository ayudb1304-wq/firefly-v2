package com.firefly.app.core.messaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NamePolicyTest {
    @Test
    fun `30 s for five minutes then five minutes`() {
        assertEquals(30_000L, NamePolicy.intervalMillis(0))
        assertEquals(30_000L, NamePolicy.intervalMillis(5 * 60_000L - 1))
        assertEquals(300_000L, NamePolicy.intervalMillis(5 * 60_000L))
    }

    @Test
    fun `sanitise keeps 8 printable ASCII characters`() {
        assertEquals("Priya", NamePolicy.sanitise("  Priya "))
        assertEquals("ABCDEFGH", NamePolicy.sanitise("ABCDEFGHIJ"))
        assertEquals("Ra", NamePolicy.sanitise("Raéन"))
        assertEquals("", NamePolicy.sanitise("नम"))
    }
}
