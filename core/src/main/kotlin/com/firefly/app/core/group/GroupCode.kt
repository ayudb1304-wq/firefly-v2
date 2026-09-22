package com.firefly.app.core.group

import kotlin.random.Random

/**
 * Six-character join code (PRD A1). Uppercase, unambiguous alphabet — no 0/O/1/I.
 * The on-air group ID is the CRC-32 of the code's ASCII bytes (PROTOCOL.md §2).
 */
object GroupCode {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /**
     * Normalise user input: trim, uppercase, and map the ambiguous characters
     * people type by mistake (0→O is not in the alphabet, so 0→O→... no: we map
     * 0→Q? No.) We keep it honest: O/0 → nothing valid, so we only uppercase and
     * strip spaces/dashes. Returns null if the result is not a valid code.
     */
    fun normalise(input: String): String? {
        val cleaned = input.trim().uppercase().filter { it != ' ' && it != '-' }
        return if (isValid(cleaned)) cleaned else null
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /** Unsigned 32-bit group ID for a valid code. */
    fun groupId(code: String): Long {
        require(isValid(code)) { "invalid group code: $code" }
        return Crc32.of(code.toByteArray(Charsets.US_ASCII))
    }
}
