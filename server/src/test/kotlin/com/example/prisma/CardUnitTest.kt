package com.example.prisma

import com.example.prisma.routes.parseAlias
import com.example.prisma.routes.parseBank
import com.example.prisma.routes.parseDay
import com.example.prisma.routes.parseLastFour
import com.example.prisma.routes.parseTime
import kotlin.test.*

class CardValidationTest {

    @Test
    fun validatesBankAndAlias() {
        assertEquals("BBVA", parseBank("  BBVA "))
        assertNull(parseBank("   "))
        assertNull(parseBank("x".repeat(101)))

        assertEquals("Tarjeta Principal", parseAlias(" Tarjeta Principal "))
        assertNull(parseAlias("   "))
        assertNull(parseAlias("x".repeat(151)))
    }

    @Test
    fun validatesLastFourDigits() {
        assertEquals(Pair(true, null), parseLastFour(null))
        assertEquals(Pair(true, null), parseLastFour("  "))
        assertEquals(Pair(true, "1234"), parseLastFour("1234"))
        assertEquals(Pair(false, null), parseLastFour("123"))
        assertEquals(Pair(false, null), parseLastFour("abcd"))
    }

    @Test
    fun validatesDayOfMonth() {
        assertEquals(Pair(true, null), parseDay(null))
        assertEquals(Pair(true, 1), parseDay(1))
        assertEquals(Pair(true, 31), parseDay(31))
        assertEquals(Pair(false, null), parseDay(0))
        assertEquals(Pair(false, null), parseDay(32))
    }

    @Test
    fun validatesTimeFormat() {
        assertEquals(Pair(true, null), parseTime(null))
        assertEquals(Pair(true, null), parseTime(" "))
        assertEquals(Pair(true, "09:00"), parseTime("09:00"))
        assertEquals(Pair(true, "20:45"), parseTime("20:45"))
        assertEquals(Pair(false, null), parseTime("25:00"))
        assertEquals(Pair(false, null), parseTime("09:60"))
        assertEquals(Pair(false, null), parseTime("invalid"))
    }
}
