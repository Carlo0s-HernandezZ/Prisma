package com.example.prisma.api

import kotlinx.serialization.Serializable

// ---------- Análisis de estados de cuenta con IA ----------
//
// Los montos viajan como texto decimal con 2 decimales ("1500.00"), igual que el resto de la API.

/** Quién hizo la lectura del estado: Gemini o el lector local de Prisma (sin IA). */
@Serializable
enum class AnalysisSource { GEMINI, LOCAL }

@Serializable
enum class AlertSeverity { ALTA, MEDIA, BAJA }

/** Compra diferida a meses, con o sin intereses. */
@Serializable
data class InstallmentPurchaseDto(
    val descripcion: String,
    val montoOriginal: String? = null,
    val mensualidad: String? = null,
    val pagoActual: Int? = null,
    val pagosTotales: Int? = null,
    val saldoPendiente: String,
    val conIntereses: Boolean,
    /** Tasa anual en %, solo para compras con intereses. */
    val tasaAnual: String? = null,
)

/** Una deuda concreta de la tarjeta: saldo de compras normales o cada plan a meses. */
@Serializable
data class DebtItemDto(
    val concepto: String,
    val monto: String,
    /** "REVOLVENTE", "MSI" o "CON_INTERESES". */
    val tipo: String,
)

@Serializable
data class HormigaMerchantDto(
    val nombre: String,
    val veces: Int,
    val total: String,
)

/** Gastos hormiga: compras pequeñas y repetidas que suman mucho al mes. */
@Serializable
data class HormigaSummaryDto(
    val total: String = "0.00",
    val compras: Int = 0,
    /** Lo que sumarían en un año si se mantiene el ritmo. */
    val proyeccionAnual: String = "0.00",
    val comercios: List<HormigaMerchantDto> = emptyList(),
)

/** Cargo que conviene revisar (duplicado, en el extranjero, monto atípico, etc.). */
@Serializable
data class ChargeAlertDto(
    val descripcion: String,
    val monto: String,
    val fecha: String? = null,
    val motivo: String,
    val severidad: AlertSeverity = AlertSeverity.MEDIA,
)

/** Consejo de salud financiera con su fuente oficial del Gobierno de México. */
@Serializable
data class AdviceDto(
    val titulo: String,
    val texto: String,
    val fuenteNombre: String,
    val fuenteUrl: String,
)

@Serializable
data class CategoryAmountDto(
    val categoria: String,
    val monto: String,
)

@Serializable
data class StatementAnalysisDto(
    val id: String,
    val tarjetaId: String,
    val banco: String,
    val alias: String,
    val ultimosCuatro: String? = null,
    /** Mes del estado, "AAAA-MM". */
    val periodo: String,
    val fuente: AnalysisSource,
    val fechaAnalisis: String,
    val resumen: String,
    val deudaTotal: String,
    val pagoMinimo: String? = null,
    val pagoSinIntereses: String? = null,
    val fechaLimitePago: String? = null,
    val limiteCredito: String? = null,
    /** Compras y cargos del periodo (sin pagos ni devoluciones). */
    val totalGastos: String,
    val interesesYComisiones: String = "0.00",
    val comprasMeses: List<InstallmentPurchaseDto> = emptyList(),
    val deudas: List<DebtItemDto> = emptyList(),
    val gastosHormiga: HormigaSummaryDto = HormigaSummaryDto(),
    val alertas: List<ChargeAlertDto> = emptyList(),
    val consejos: List<AdviceDto> = emptyList(),
    val categorias: List<CategoryAmountDto> = emptyList(),
    val movimientosLeidos: Int = 0,
    /** Aviso para el usuario cuando algo no se pudo leer o la IA no estuvo disponible. */
    val aviso: String? = null,
)

@Serializable
data class AnalysisListResponse(
    val analisis: List<StatementAnalysisDto>,
    /** false si el servidor no tiene configurado Gemini: se usa el lector local. */
    val iaDisponible: Boolean,
)

// ---------- Dashboards de gastos ----------

@Serializable
data class MonthSpendDto(
    val periodo: String,
    val total: String,
    val hormiga: String = "0.00",
)

@Serializable
enum class SpendingTrend { SUBE, BAJA, ESTABLE, SIN_DATOS }

@Serializable
data class CardSpendDto(
    val tarjetaId: String,
    val nombre: String,
    val ultimosCuatro: String? = null,
    val gastoUltimoMes: String,
    val deuda: String,
)

@Serializable
data class SpendingDashboardDto(
    /** null = todas las tarjetas. */
    val tarjetaId: String? = null,
    val meses: List<MonthSpendDto> = emptyList(),
    /** Gasto estimado para el siguiente mes según la tendencia reciente. */
    val proyeccion: MonthSpendDto? = null,
    val promedio: String = "0.00",
    val tendencia: SpendingTrend = SpendingTrend.SIN_DATOS,
    /** Cambio del último mes contra el anterior, en % (puede ser negativo). */
    val cambioPorcentaje: Int? = null,
    val mesMayorGasto: String? = null,
    val mesMenorGasto: String? = null,
    val categorias: List<CategoryAmountDto> = emptyList(),
    val porTarjeta: List<CardSpendDto> = emptyList(),
    val mensaje: String = "",
)

// ---------- Inicio ----------

@Serializable
enum class HealthLevel { SALUDABLE, ALERTA, CRITICA, SIN_DATOS }

@Serializable
data class HealthFactorDto(
    val nombre: String,
    /** 0..100, más alto es mejor. */
    val puntaje: Int,
    val detalle: String,
)

@Serializable
data class FinancialHealthDto(
    val puntaje: Int? = null,
    val nivel: HealthLevel = HealthLevel.SIN_DATOS,
    val factores: List<HealthFactorDto> = emptyList(),
    val resumen: String = "",
)

@Serializable
enum class HomeMessageType { PAGO, CORTE, ALERTA, MESES, CONSEJO, NOMINA, RECORDATORIO }

@Serializable
data class HomeMessageDto(
    /** Con valor por defecto: una app anterior que no conozca un tipo nuevo lo muestra como aviso genérico. */
    val tipo: HomeMessageType = HomeMessageType.CONSEJO,
    val titulo: String,
    val texto: String,
    val tarjetaId: String? = null,
    val fuenteNombre: String? = null,
    val fuenteUrl: String? = null,
    /** true para lo que requiere atención pronto (pago en 3 días o menos, cargo sospechoso). */
    val urgente: Boolean = false,
)

@Serializable
data class HomeCardDto(
    val tarjetaId: String,
    val banco: String,
    val alias: String,
    val ultimosCuatro: String? = null,
    val deuda: String? = null,
    val proximoPago: String? = null,
    val diasParaPago: Int? = null,
    val pagoSinIntereses: String? = null,
)

@Serializable
data class HomeSummaryDto(
    val userName: String,
    val salud: FinancialHealthDto,
    val tarjetas: List<HomeCardDto> = emptyList(),
    val deudaTotal: String = "0.00",
    val totalAPagar: String = "0.00",
    val ingresosMes: String = "0.00",
    val ingresoMensualEstimado: String = "0.00",
    val gastoUltimoMes: String? = null,
    val gastoMesAnterior: String? = null,
    /** Cambio del último mes contra el anterior, solo con tarjetas que tienen estado en ambos meses. */
    val cambioPorcentaje: Int? = null,
    /** true si algún mes no tiene el estado de todas las tarjetas (la comparación es parcial). */
    val comparacionParcial: Boolean = false,
    val mesesGasto: List<MonthSpendDto> = emptyList(),
    val mensajes: List<HomeMessageDto> = emptyList(),
)
