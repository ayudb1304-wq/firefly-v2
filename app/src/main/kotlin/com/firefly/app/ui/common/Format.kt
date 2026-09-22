package com.firefly.app.ui.common

import kotlin.math.roundToInt

object Format {
    fun age(nowMillis: Long, thenMillis: Long): String {
        val s = ((nowMillis - thenMillis) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            else -> "${s / 3600}h ago"
        }
    }

    fun distance(metres: Double?): String = when {
        metres == null -> "— m"
        metres < 1000 -> "${metres.roundToInt()} m"
        else -> "%.1f km".format(metres / 1000)
    }

    fun bearingArrow(degrees: Double?): String {
        if (degrees == null) return ""
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        return arrows[(((degrees + 22.5) % 360) / 45).toInt()]
    }
}
