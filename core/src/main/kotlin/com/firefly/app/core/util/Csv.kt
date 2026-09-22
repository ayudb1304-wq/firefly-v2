package com.firefly.app.core.util

/** RFC 4180-style escaping for the field log export. */
object Csv {
    fun escape(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(vararg values: Any?): String = values.joinToString(",") { escape(it?.toString()) }
}
