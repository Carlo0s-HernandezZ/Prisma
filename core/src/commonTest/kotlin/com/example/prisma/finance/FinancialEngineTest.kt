package com.example.prisma.finance

import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialEngineTest {

    @Test
    fun testFinancialEngineCalculation() {
        val items = listOf(
            Triple("Amazon a Meses", 2850.0, 260.0),
            Triple("Amazon a Meses 2", 1127.35, 142.0),
        )
        val totalMeses = listOf(15, 12)
        val pagosActuales = listOf(4, 4)

        val summary = FinancialEngine.calculate(items, totalMeses, pagosActuales)

        assertEquals(3977.35, summary.deudaTotalPendiente, 0.01)
        assertEquals(402.0, summary.totalPagoRequeridoMes, 0.01)
        assertEquals(11, summary.mesesMaximosParaLiquidar) // 15 - 4 = 11
        assertEquals(2, summary.msiCalculations.size)
        assertEquals(11, summary.msiCalculations[0].mesesRestantes)
        assertEquals(8, summary.msiCalculations[1].mesesRestantes) // 12 - 4 = 8
    }
}
