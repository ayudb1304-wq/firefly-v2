package com.firefly.app.core.protocol

/**
 * The 12 pre-defined messages (PRD §3 C3). UI labels live in string resources
 * keyed by [Entry.code]; this file only knows structure.
 */
object Codebook {
    const val BEACON = 0x00
    const val WHERE_ARE_YOU = 0x01
    const val MEET_AT = 0x02
    const val ON_MY_WAY = 0x03
    const val STAY_THERE = 0x04
    const val GOING_TO = 0x05
    const val BACK_IN_MINUTES = 0x06
    const val HELP = 0x07
    const val LIGHTHOUSE_ON = 0x08
    const val ACK = 0x09
    const val LOW_BATTERY = 0x0A
    const val CALL_ME = 0x0B
    const val LEAVING_VENUE = 0x0C

    /** What the `arg` byte means for a code. */
    enum class ArgKind {
        NONE,
        /** POI index 1–255 from the loaded venue pack; 0 = "here", the sender's own position. */
        POI,
        /** Minutes 1–60. */
        MINUTES,
        /** Low byte of the acknowledged seq (ACK only). */
        ACK_SEQ,
    }

    /** `arg` value for POI-kind messages meaning "at my current position". */
    const val POI_HERE = 0

    data class Entry(
        val code: Int,
        val arg: ArgKind = ArgKind.NONE,
        /** Sender asks receivers to ACK (all user-facing pings do; ACKs themselves do not). */
        val wantsAck: Boolean = true,
        /** SOS flag: relays flood it with max TTL (PROTOCOL.md §4/§5). */
        val priority: Boolean = false,
        /** Shown in the codebook grid. ACK is sent automatically, never by hand. */
        val userSendable: Boolean = true,
    )

    val entries: List<Entry> = listOf(
        Entry(WHERE_ARE_YOU),
        Entry(MEET_AT, ArgKind.POI),
        Entry(ON_MY_WAY),
        Entry(STAY_THERE),
        Entry(GOING_TO, ArgKind.POI),
        Entry(BACK_IN_MINUTES, ArgKind.MINUTES),
        Entry(HELP, priority = true),
        Entry(LIGHTHOUSE_ON),
        Entry(ACK, ArgKind.ACK_SEQ, wantsAck = false, userSendable = false),
        Entry(LOW_BATTERY),
        Entry(CALL_ME),
        Entry(LEAVING_VENUE),
    )

    private val byCode = entries.associateBy { it.code }

    fun entry(code: Int): Entry? = byCode[code]

    val userSendable: List<Entry> get() = entries.filter { it.userSendable }

    /** Send schedule (PROTOCOL.md §4): PING ×3 at 1 s; SOS ×5 over 10 s; ACK ×2 (DECISIONS.md). */
    fun repeats(code: Int): Int = when {
        code == ACK -> 2
        entry(code)?.priority == true -> 5
        else -> 3
    }

    fun spacingMillis(code: Int): Long = if (entry(code)?.priority == true) 2_500L else 1_000L

    fun ttl(code: Int): Int = if (entry(code)?.priority == true) Protocol.Ttl.SOS else Protocol.Ttl.DEFAULT

    fun isValidArg(code: Int, arg: Int): Boolean = when (entry(code)?.arg) {
        null -> false
        ArgKind.NONE -> arg == 0
        ArgKind.POI -> arg in 0..255
        ArgKind.MINUTES -> arg in 1..60
        ArgKind.ACK_SEQ -> arg in 0..255
    }
}
