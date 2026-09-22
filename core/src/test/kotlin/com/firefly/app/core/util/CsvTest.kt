package com.firefly.app.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CsvTest {
    @Test
    fun `escapes commas quotes and newlines`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"l1\nl2\"", Csv.escape("l1\nl2"))
        assertEquals("", Csv.escape(null))
        assertEquals("1,RX,,\"Bar, 2\"", Csv.row(1, "RX", null, "Bar, 2"))
    }
}
