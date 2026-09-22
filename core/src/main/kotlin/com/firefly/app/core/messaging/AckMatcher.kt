package com.firefly.app.core.messaging

import com.firefly.app.core.protocol.Protocol

/**
 * An ACK only carries the low byte of the acknowledged seq (PROTOCOL.md §3),
 * so we match it against our recent outgoing pings by seq-low-byte and recipient.
 */
object AckMatcher {
    /** What we remember about a ping we sent. */
    data class Outgoing(val id: Long, val seq: Int, val target: Int, val sentAtMillis: Long)

    const val WINDOW_MS = 5 * 60_000L

    /**
     * @param ackFrom senderId of the phone that ACKed
     * @param ackArg the `arg` byte of the ACK packet
     * @return the id of the most recent ping this ACK can belong to, or null
     */
    fun match(outgoing: List<Outgoing>, ackFrom: Int, ackArg: Int, nowMillis: Long): Long? =
        outgoing
            .asSequence()
            .filter { nowMillis - it.sentAtMillis in 0..WINDOW_MS }
            .filter { (it.seq and 0xFF) == (ackArg and 0xFF) }
            .filter { it.target == Protocol.TARGET_BROADCAST || it.target == ackFrom }
            .maxByOrNull { it.sentAtMillis }
            ?.id
}
