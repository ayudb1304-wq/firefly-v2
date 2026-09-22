package com.firefly.app.core.group

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.zip.CRC32
import kotlin.random.Random

class GroupCodeTest {
    @Test
    fun `alphabet has no ambiguous characters`() {
        for (c in "0O1I") assertFalse(c in GroupCode.ALPHABET, "$c must not be in alphabet")
        assertEquals(32, GroupCode.ALPHABET.length)
        assertEquals(GroupCode.ALPHABET.toSet().size, GroupCode.ALPHABET.length)
    }

    @Test
    fun `generated codes are valid and vary`() {
        val rnd = Random(7)
        val codes = (1..200).map { GroupCode.generate(rnd) }
        codes.forEach { assertTrue(GroupCode.isValid(it), it) }
        assertTrue(codes.toSet().size > 190)
    }

    @Test
    fun `normalise accepts lowercase and separators, rejects junk`() {
        assertEquals("ABCDEF", GroupCode.normalise(" abc-def "))
        assertEquals("XY2345", GroupCode.normalise("xy2 345"))
        assertNull(GroupCode.normalise("ABCDE"))
        assertNull(GroupCode.normalise("ABCDEFG"))
        assertNull(GroupCode.normalise("ABC0EF"))
        assertNull(GroupCode.normalise("ABCIEF"))
        assertNull(GroupCode.normalise(""))
    }

    @Test
    fun `group id is CRC-32 of the ASCII code`() {
        val code = "PRYA27"
        val ref = CRC32().apply { update(code.toByteArray(Charsets.US_ASCII)) }.value
        assertEquals(ref, GroupCode.groupId(code))
        assertTrue(GroupCode.groupId(code) in 0..0xFFFFFFFFL)
    }

    @Test
    fun `hand rolled CRC-32 matches the JDK on random input`() {
        val rnd = Random(1)
        repeat(100) {
            val bytes = rnd.nextBytes(rnd.nextInt(0, 64))
            assertEquals(CRC32().apply { update(bytes) }.value, Crc32.of(bytes))
        }
        assertEquals(0xCBF43926L, Crc32.of("123456789".toByteArray()), "CRC-32 check value")
    }

    @Test
    fun `sender ids avoid reserved values`() {
        val rnd = Random(3)
        repeat(5000) {
            val id = SenderId.generate(rnd)
            assertTrue(SenderId.isValid(id), "$id")
        }
        assertFalse(SenderId.isValid(0))
        assertFalse(SenderId.isValid(0xFFFF))
        assertEquals("1A2B", SenderId.hex(0x1A2B))
    }
}
