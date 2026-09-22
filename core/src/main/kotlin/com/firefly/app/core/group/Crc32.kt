package com.firefly.app.core.group

/** Standard CRC-32 (IEEE 802.3, reflected, poly 0xEDB88320) — same as zlib and java.util.zip. Portable to C. */
object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    /** Returns the CRC as an unsigned 32-bit value in a Long. */
    fun of(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFF.toInt()
        for (b in bytes) crc = table[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
    }
}
