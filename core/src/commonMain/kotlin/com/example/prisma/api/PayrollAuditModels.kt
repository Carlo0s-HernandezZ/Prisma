package com.example.prisma.api

import kotlinx.serialization.Serializable

// ---------- Auditoría de nómina (PDF o CFDI XML) ----------
//
// Los montos viajan como texto decimal con 2 decimales ("1500.00"), igual que el resto de la API.

@Serializable
enum class PayrollConceptKind { PERCEPCION, DEDUCCION, OTRO_PAGO }

/** Un concepto del recibo, con su explicación en lenguaje sencillo. */
@Serializable
data class PayrollConceptDto(
    val tipo: PayrollConceptKind,
    /** Clave del catálogo del SAT (p. ej. "002" = ISR), si se conoce. */
    val claveSat: String? = null,
    /** Texto tal como aparece en el recibo. */
    val concepto: String,
    /** Nombre oficial del concepto en el catálogo del SAT. */
    val nombre: String,
    /** Qué es y por qué se paga o se descuenta. */
    val explicacion: String,
    val importe: String,
    val gravado: String? = null,
    val exento: String? = null,
    /** true si el concepto merece revisión (también aparece en alertas). */
    val revisar: Boolean = false,
)

@Serializable
data class PayrollAlertDto(
    val titulo: String,
    val detalle: String,
    val severidad: AlertSeverity = AlertSeverity.MEDIA,
)

@Serializable
data class PayrollAuditDto(
    val id: String = "",
    /** "XML" (CFDI de nómina) o "PDF". */
    val fuente: String,
    /** Mes de la nómina, "AAAA-MM". */
    val periodo: String,
    val fechaPago: String? = null,
    val fechaInicial: String? = null,
    val fechaFinal: String? = null,
    val diasPagados: String? = null,
    /** "Quincenal", "Semanal", etc. */
    val periodicidad: String? = null,
    val percepciones: List<PayrollConceptDto> = emptyList(),
    val deducciones: List<PayrollConceptDto> = emptyList(),
    val otrosPagos: List<PayrollConceptDto> = emptyList(),
    val totalPercepciones: String = "0.00",
    val totalDeducciones: String = "0.00",
    val totalOtrosPagos: String = "0.00",
    /** Neto que dice el recibo (si lo trae). */
    val netoDeclarado: String? = null,
    /** Percepciones + otros pagos − deducciones. */
    val netoCalculado: String = "0.00",
    val alertas: List<PayrollAlertDto> = emptyList(),
    val resumen: String = "",
    val consejos: List<AdviceDto> = emptyList(),
    val fechaAnalisis: String = "",
    val aviso: String? = null,
)

@Serializable
data class PayrollAuditListResponse(val auditorias: List<PayrollAuditDto>)

// ---------- Recordatorios para subir nómina o estados de cuenta ----------

@Serializable
enum class ReminderKind { NOMINA, ESTADO_CUENTA }

@Serializable
enum class ReminderFrequency {
    /** Un día del mes ([ReminderDto.dia]). */
    MENSUAL,

    /** Dos días del mes ([ReminderDto.dia] y [ReminderDto.dia2]), p. ej. 15 y 30. */
    QUINCENAL,

    /** Un día de la semana ([ReminderDto.dia]: 1 = lunes … 7 = domingo). */
    SEMANAL,

    /** Cada 14 días a partir de [ReminderDto.fechaInicio]. */
    CATORCENAL,
}

@Serializable
data class ReminderDto(
    val id: String = "",
    val tipo: ReminderKind,
    /** Solo para estados de cuenta: la tarjeta a la que pertenece. */
    val tarjetaId: String? = null,
    val titulo: String,
    val frecuencia: ReminderFrequency,
    val dia: Int? = null,
    val dia2: Int? = null,
    /** "AAAA-MM-DD", solo para CATORCENAL. */
    val fechaInicio: String? = null,
    /** "HH:mm". */
    val hora: String = "09:00",
    val activo: Boolean = true,
)
