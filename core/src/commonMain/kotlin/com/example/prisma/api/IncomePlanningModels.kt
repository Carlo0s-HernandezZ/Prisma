package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
enum class IncomeType {
    RECURRENTE,
    EXTRAORDINARIO,
}

@Serializable
enum class IncomeFrequency {
    SEMANAL,
    CATORCENAL,
    QUINCENAL,
    MENSUAL,
    VARIABLE,
}

/**
 * Información confirmada por el usuario sobre un ingreso recibido.
 *
 * La fecha de cobro es independiente de la fecha de registro.
 * Esta información no representa el saldo que sigue disponible.
 */
@Serializable
data class IncomePlanningRequest(
    val fechaCobro: String,
    val tipoIngreso: IncomeType,
    val frecuencia: IncomeFrequency? = null,
)

@Serializable
data class IncomePlanningDto(
    val ingresoId: String,
    val nombre: String,
    val montoNeto: String,
    val esAuditado: Boolean,
    val fechaCobro: String,
    val tipoIngreso: IncomeType,
    val frecuencia: IncomeFrequency? = null,
    val fechaActualizacion: String,
)

@Serializable
data class IncomePlanningSummary(
    val ingresos: List<IncomePlanningDto>,
    val sinConfigurar: Long,
)