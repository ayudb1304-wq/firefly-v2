package com.firefly.app.core.protocol

/**
 * Codebook codes (PRD §3 C3). Only the numeric constants live here for Phase 1;
 * the full model with labels and arguments arrives in Phase 2.
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
}
