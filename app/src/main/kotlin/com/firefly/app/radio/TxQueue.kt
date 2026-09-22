package com.firefly.app.radio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Something to put on air: [repeats] bursts of the same bytes, [spacingMillis] apart. */
class TxRequest(
    val bytes: ByteArray,
    val repeats: Int,
    val spacingMillis: Long,
    val priority: Boolean = false,
    val label: String = "",
)

/**
 * Hand-off from repositories to [FireflyService]. Two lanes so SOS never waits
 * behind chatter. Bounded: if the radio is down, old requests are dropped, not hoarded.
 */
class TxQueue {
    val priority = Channel<TxRequest>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val normal = Channel<TxRequest>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun enqueue(request: TxRequest) {
        (if (request.priority) priority else normal).trySend(request)
    }
}

/**
 * Per-install packet sequence, shared by beacons, pings, names and ACKs
 * (PROTOCOL.md §4: increments per origination). Starts random — see DECISIONS.md.
 */
class SeqCounter(start: Int = Random.nextInt(0, 0x10000)) {
    private val value = AtomicInteger(start and 0xFFFF)
    fun next(): Int = value.updateAndGet { (it + 1) and 0xFFFF }
}
