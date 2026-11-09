package com.example.prisma.finance

import kotlinx.serialization.Serializable

@Serializable
data class MsiCalculation(
    val descripcion: String,
    val saldoPendiente: Double,
    val pagoRequerido: Double,
    val mesesRestantes: Int,
    val tasaInteres: Double = 0.0,
)

@Serializable
data class FinancialSummary(
    val deudaTotalPendiente: Double,
    val totalPagoRequeridoMes: Double,
    val mesesMaximosParaLiquidar: Int,
    val msiCalculations: List<MsiCalculation>,
)

object FinancialEngine {
    /**
     * Calcula la deuda total, pagos requeridos y tiempo de liquidación para MSI y deudas.
     */
    fun calculate(
        items: List<Triple<String, Double, Double>>, // Triple(descripcion, saldoPendiente, pagoRequerido)
        totalMesesList: List<Int>,
        pagoActualList: List<Int>,
        tasaInteresList: List<Double> = emptyList(),
    ): FinancialSummary {
        var deudaTotal = 0.0
        var totalPagoMes = 0.0
        var maxMeses = 0
        val msiCalcs = mutableListOf<MsiCalculation>()

        for (i in items.indices) {
            val (desc, saldo, pago) = items[i]
            val totalMeses = totalMesesList.getOrElse(i) { 12 }
            val pagoActual = pagoActualList.getOrElse(i) { 1 }
            val tasa = tasaInteresList.getOrElse(i) { 0.0 }
            val restantes = maxOf(0, totalMeses - pagoActual)

            deudaTotal += saldo
            totalPagoMes += pago
            if (restantes > maxMeses) {
                maxMeses = restantes
            }

            msiCalcs.add(
                MsiCalculation(
                    descripcion = desc,
                    saldoPendiente = saldo,
                    pagoRequerido = pago,
                    mesesRestantes = restantes,
                    tasaInteres = tasa,
                )
            )
        }

        return FinancialSummary(
            deudaTotalPendiente = deudaTotal,
            totalPagoRequeridoMes = totalPagoMes,
            mesesMaximosParaLiquidar = maxMeses,
            msiCalculations = msiCalcs,
        )
    }
}
