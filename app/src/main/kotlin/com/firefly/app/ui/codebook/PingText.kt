package com.firefly.app.ui.codebook

import android.content.Context
import com.firefly.app.R
import com.firefly.app.core.protocol.Codebook

/** Human text for codebook codes. Used by Compose and by the service's notifications. */
object PingText {
    fun labelRes(code: Int): Int = when (code) {
        Codebook.WHERE_ARE_YOU -> R.string.code_where_are_you
        Codebook.MEET_AT -> R.string.code_meet_at
        Codebook.ON_MY_WAY -> R.string.code_on_my_way
        Codebook.STAY_THERE -> R.string.code_stay_there
        Codebook.GOING_TO -> R.string.code_going_to
        Codebook.BACK_IN_MINUTES -> R.string.code_back_in
        Codebook.HELP -> R.string.code_help
        Codebook.LIGHTHOUSE_ON -> R.string.code_lighthouse
        Codebook.ACK -> R.string.code_ack
        Codebook.LOW_BATTERY -> R.string.code_low_battery
        Codebook.CALL_ME -> R.string.code_call_me
        Codebook.LEAVING_VENUE -> R.string.code_leaving
        else -> R.string.code_unknown
    }

    /** A visual anchor per message so the grid can be scanned without reading. */
    fun glyph(code: Int): String = when (code) {
        Codebook.WHERE_ARE_YOU -> "❓"
        Codebook.MEET_AT -> "🤝"
        Codebook.ON_MY_WAY -> "🏃"
        Codebook.STAY_THERE -> "✋"
        Codebook.GOING_TO -> "➡️"
        Codebook.BACK_IN_MINUTES -> "⏱"
        Codebook.HELP -> "🆘"
        Codebook.LIGHTHOUSE_ON -> "🔦"
        Codebook.ACK -> "👍"
        Codebook.LOW_BATTERY -> "🔋"
        Codebook.CALL_ME -> "📞"
        Codebook.LEAVING_VENUE -> "🚪"
        else -> "💬"
    }

    /** Full sentence including the argument, e.g. "Meet at Bar 2", "Back in 10 min". */
    fun describe(context: Context, code: Int, arg: Int, poiName: String?): String {
        val label = context.getString(labelRes(code))
        return when (Codebook.entry(code)?.arg) {
            Codebook.ArgKind.POI -> if (arg == Codebook.POI_HERE) context.getString(R.string.arg_here_fmt, label)
            else context.getString(R.string.arg_poi_fmt, label, poiName ?: context.getString(R.string.arg_poi_number, arg))
            Codebook.ArgKind.MINUTES -> context.getString(R.string.arg_minutes_fmt, label, arg)
            else -> label
        }
    }
}
