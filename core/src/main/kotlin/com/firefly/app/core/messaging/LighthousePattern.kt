package com.firefly.app.core.messaging

/**
 * PRD E1: a 3-colour flash sequence unique-ish to a sender, derived from senderId,
 * so a friend can pick your screen out of a crowd. Six colours → 216 patterns;
 * the first two colours are never identical so the sequence reads as a rhythm.
 */
object LighthousePattern {
    enum class Colour(val argb: Long, val label: String) {
        RED(0xFFFF1744, "red"),
        GREEN(0xFF00E676, "green"),
        BLUE(0xFF2979FF, "blue"),
        YELLOW(0xFFFFEA00, "yellow"),
        WHITE(0xFFFFFFFF, "white"),
        MAGENTA(0xFFD500F9, "magenta"),
    }

    const val LENGTH = 3
    const val FLASH_MS = 450L
    const val GAP_MS = 150L
    /** One full cycle: three flashes, then a longer dark pause so the start is obvious. */
    const val CYCLE_PAUSE_MS = 900L
    const val AUTO_OFF_MS = 2 * 60_000L

    fun forSender(senderId: Int): List<Colour> {
        val n = Colour.entries.size
        var x = mix(senderId)
        val first = Colour.entries[x % n]; x /= n
        var second = Colour.entries[x % n]; x /= n
        if (second == first) second = Colour.entries[(second.ordinal + 1) % n]
        val third = Colour.entries[x % n]
        return listOf(first, second, third)
    }

    fun label(pattern: List<Colour>): String = pattern.joinToString("-") { it.label }

    /** Spread the 16 bits so neighbouring IDs do not get near-identical patterns. */
    private fun mix(id: Int): Int {
        var h = id * 0x9E3779B1.toInt()
        h = h xor (h ushr 15)
        h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        return h and 0x7FFFFFFF
    }
}
