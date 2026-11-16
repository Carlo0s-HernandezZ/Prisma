package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
enum class StatementPurchaseType {
    UN_PAGO, MSI, CON_INTERESES, MIXTO, DESCONOCIDO
}

@Serializable
enum class StatementDetectionStatus {
    LISTO, REQUIERE_DATO, IMPORTES_DISTINTOS
}

@Serializable
data class StatementInstallment(
    val descripcion: String,
    val montoOriginal: String,
    val saldoPendiente: String,
    val cuota: String,
    val pagoActual: Int,
    val totalPagos: Int,
    val tasa: String,
    val sinIntereses: Boolean,
    val pagina: Int,
)

@Serializable
data class StatementBreakdown(
    val tipo: StatementPurchaseType = StatementPurchaseType.DESCONOCIDO,
    val comprasUnPago: String? = null,
    val cuotasMsi: String? = null,
    val saldoMeses: String? = null,
    val minimoConCuotas: String? = null,
    val planes: List<StatementInstallment> = emptyList(),
)

@Serializable
data class StatementDetection(
    val campos: StatementFields,
    val detalle: StatementBreakdown,
    val estado: StatementDetectionStatus,
    val avisos: List<String> = emptyList(),
    val pregunta: String? = null,
    val opcionesPago: List<String> = emptyList(),
    val paginasLeidas: Int = 0,
)

@Serializable
data class AutomaticStatementDto(
    val id: String,
    val tarjetaId: String,
    val version: Int,
    val versionFinanciera: Int? = null,
    val deteccion: StatementDetection,
    val fechaActualizacion: String,
)

@Serializable
data class StatementResolutionRequest(
    val version: Int,
    val pagoObjetivo: String,
)

@Serializable
data class ForecastIncome(
    val id: String,
    val fecha: String,
    val monto: String,
)

@Serializable
data class CardPlansRequest(
    val ingresosPrevistos: List<ForecastIncome> = emptyList(),
)

@Serializable
data class CardPaymentAllocation(
    val fecha: String,
    val monto: String,
)

@Serializable
data class CardPaymentPlan(
    val tarjetaId: String,
    val alias: String,
    val estado: ConfirmedStatementDto? = null,
    val automatico: AutomaticStatementDto? = null,
    val tipo: StatementPurchaseType = StatementPurchaseType.DESCONOCIDO,
    val objetivo: String? = null,
    val minimoSeguro: String? = null,
    val recomendado: String? = null,
    val faltante: String? = null,
    val cuotasMsi: String? = null,
    val comprasUnPago: String? = null,
    val programacion: List<CardPaymentAllocation> = emptyList(),
    val accion: String? = null,
    val avisos: List<String> = emptyList(),
)

@Serializable
data class CardPlansDto(
    val periodo: String,
    val fechaReferencia: String,
    val presupuestoConfigurado: Boolean,
    val estimado: Boolean = true,
    val tarjetas: List<CardPaymentPlan>,
    val totalObjetivo: String? = null,
    val totalRecomendado: String? = null,
    val avisos: List<String> = emptyList(),
)