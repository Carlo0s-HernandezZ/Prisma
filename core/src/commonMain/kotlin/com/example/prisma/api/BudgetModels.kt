package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
enum class BudgetMovementType {
    INGRESO,
    GASTO,
}

@Serializable
data class BudgetMovement(
    val id: String,
    val tipo: BudgetMovementType,
    val concepto: String,
    val fecha: String,
    val monto: String,
)

@Serializable
data class BudgetRequest(
    val fechaBase: String,
    val saldoDisponible: String,
    val reserva: String,
    val movimientos: List<BudgetMovement> = emptyList(),
    val version: Int = 0,
)

@Serializable
data class BudgetDayBalance(
    val fecha: String,
    val saldoLibre: String,
)

@Serializable
data class BudgetSummary(
    val ingresosPendientes: String,
    val gastosPendientes: String,
    val saldoLibreCierre: String,
    val maximoEnFechaBase: String,
    val maximoEnMes: String,
    val faltante: String,
    val saldos: List<BudgetDayBalance>,
)

@Serializable
data class BudgetDto(
    val periodo: String,
    val configurado: Boolean,
    val presupuesto: BudgetRequest? = null,
    val resumen: BudgetSummary? = null,
    val fechaActualizacion: String? = null,
)