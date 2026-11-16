package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
data class StatementPayment(
    val id: String,
    val fechaAplicacion: String,
    val monto: String,
)

@Serializable
data class StatementPaymentsRequest(
    val pagos: List<StatementPayment>,
    val version: Int = 0,
    val versionEstado: Int,
    val confirmadoPorUsuario: Boolean = false,
)

@Serializable
data class StatementPaymentsSummary(
    val totalAplicado: String,
    val aplicadoHastaFechaLimite: String,
    val faltanteMinimo: String,
    val faltanteParaNoGenerarIntereses: String,
    val faltanteMinimoAlVencimiento: String,
    val faltanteParaNoGenerarInteresesAlVencimiento: String,
    val plazoVencido: Boolean,
    val advertencias: List<String> = emptyList(),
)

@Serializable
data class StatementPaymentsDto(
    val estado: ConfirmedStatementDto,
    val pagos: List<StatementPayment> = emptyList(),
    val version: Int = 0,
    val versionEstado: Int? = null,
    val fechaReferencia: String,
    val resumen: StatementPaymentsSummary? = null,
    val requiereRevision: Boolean = false,
    val fechaActualizacion: String? = null,
)