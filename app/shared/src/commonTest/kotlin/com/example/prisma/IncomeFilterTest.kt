package com.example.prisma

import com.example.prisma.api.IncomeDto
import com.example.prisma.income.AmountRange
import com.example.prisma.income.IncomeFilter
import com.example.prisma.income.availableMonths
import com.example.prisma.income.availableYears
import com.example.prisma.income.monthLabel
import com.example.prisma.income.sumAmounts
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomeFilterTest {
    private fun income(monto: String, fecha: String) = IncomeDto("id-$monto-$fecha", "Ingreso", monto, false, fecha)

    private val ingresos = listOf(
        income("15000.50", "2026-11-02T20:03"),
        income("4999.99", "2026-11-01T09:00"),
        income("5000.00", "2026-10-15T10:00"),
        income("30000.00", "2025-12-31T23:59"),
    )

    @Test
    fun filtersByMonthAndYear() {
        assertEquals(2, ingresos.count(IncomeFilter.Month("2026-11")::matches))
        assertEquals(3, ingresos.count(IncomeFilter.Year("2026")::matches))
        assertEquals(4, ingresos.count(IncomeFilter.All::matches))
    }

    @Test
    fun amountRangesHaveExclusiveUpperBound() {
        assertEquals(listOf("4999.99"), ingresos.filter(IncomeFilter.Amount(AmountRange.UpTo5k)::matches).map { it.montoNeto })
        assertEquals(listOf("5000.00"), ingresos.filter(IncomeFilter.Amount(AmountRange.From5kTo15k)::matches).map { it.montoNeto })
        assertEquals(listOf("15000.50"), ingresos.filter(IncomeFilter.Amount(AmountRange.From15kTo30k)::matches).map { it.montoNeto })
        assertEquals(listOf("30000.00"), ingresos.filter(IncomeFilter.Amount(AmountRange.Over30k)::matches).map { it.montoNeto })
    }

    @Test
    fun availableOptionsAreNewestFirst() {
        assertEquals(listOf("2026-11", "2026-10", "2025-12"), availableMonths(ingresos))
        assertEquals(listOf("2026", "2025"), availableYears(ingresos))
        assertEquals("Noviembre 2026", monthLabel("2026-11"))
    }

    @Test
    fun sumIsExactInCents() {
        assertEquals("20000.49", sumAmounts(ingresos.take(2)))
        assertEquals("0.00", sumAmounts(emptyList()))
    }
}
