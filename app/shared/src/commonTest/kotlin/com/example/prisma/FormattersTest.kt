package com.example.prisma

import com.example.prisma.ui.epochMillisToIsoDate
import com.example.prisma.ui.formatIsoDateTime
import com.example.prisma.ui.formatMxn
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    private val day = 86_400_000L

    @Test
    fun convertsDatesBeforeAndAfter1970() {
        assertEquals("1970-01-01", epochMillisToIsoDate(0))
        assertEquals("1969-12-31", epochMillisToIsoDate(-1))           // un milisegundo antes
        assertEquals("2000-01-31", epochMillisToIsoDate(949_276_800_000))
        assertEquals("2000-02-29", epochMillisToIsoDate(951_782_400_000)) // bisiesto
        assertEquals("1902-03-01", epochMillisToIsoDate(-2_140_819_200_000)) // antes fallaba: "1902-02-29"
        assertEquals("1950-03-10", epochMillisToIsoDate(-625_276_800_000))
        assertEquals("1999-11-26", epochMillisToIsoDate(943_574_400_000 + day / 2)) // mediodía
    }

    @Test
    fun formatsMoneyAndDateTime() {
        assertEquals("\$15,000.50", formatMxn("15000.5"))
        assertEquals("\$0.00", formatMxn("0"))
        assertEquals("02/11/2026 · 20:03", formatIsoDateTime("2026-11-02T20:03"))
    }
}
