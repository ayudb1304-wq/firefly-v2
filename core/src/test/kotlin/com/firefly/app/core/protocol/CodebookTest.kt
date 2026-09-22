package com.firefly.app.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodebookTest {
    @Test
    fun `twelve codes 0x01 to 0x0C, eleven sendable by hand`() {
        assertEquals((1..12).toList(), Codebook.entries.map { it.code })
        assertEquals(11, Codebook.userSendable.size)
        assertFalse(Codebook.userSendable.any { it.code == Codebook.ACK })
        Codebook.entries.forEach { assertNotNull(Codebook.entry(it.code)) }
    }

    @Test
    fun `schedules follow the protocol`() {
        assertEquals(3, Codebook.repeats(Codebook.WHERE_ARE_YOU))
        assertEquals(1_500L, Codebook.spacingMillis(Codebook.WHERE_ARE_YOU))
        assertTrue((Codebook.repeats(Codebook.WHERE_ARE_YOU) - 1) * Codebook.spacingMillis(Codebook.WHERE_ARE_YOU) > 2_000L, "a ping must outlast the 2 s scan-off gap")
        assertEquals(5, Codebook.repeats(Codebook.HELP))
        assertEquals(10_000L, (Codebook.repeats(Codebook.HELP) - 1) * Codebook.spacingMillis(Codebook.HELP), "5 sends over 10 s")
        assertEquals(15, Codebook.ttl(Codebook.HELP))
        assertEquals(6, Codebook.ttl(Codebook.MEET_AT))
        assertEquals(2, Codebook.repeats(Codebook.ACK))
    }

    @Test
    fun `argument validation`() {
        assertTrue(Codebook.isValidArg(Codebook.MEET_AT, Codebook.POI_HERE))
        assertTrue(Codebook.isValidArg(Codebook.MEET_AT, 4))
        assertTrue(Codebook.isValidArg(Codebook.BACK_IN_MINUTES, 60))
        assertFalse(Codebook.isValidArg(Codebook.BACK_IN_MINUTES, 0))
        assertFalse(Codebook.isValidArg(Codebook.BACK_IN_MINUTES, 61))
        assertTrue(Codebook.isValidArg(Codebook.ON_MY_WAY, 0))
        assertFalse(Codebook.isValidArg(Codebook.ON_MY_WAY, 1))
        assertFalse(Codebook.isValidArg(0x7F, 0))
    }
}
