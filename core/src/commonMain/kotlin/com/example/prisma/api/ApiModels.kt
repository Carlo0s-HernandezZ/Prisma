package com.example.prisma.api

import kotlinx.serialization.Serializable

/**
 * Contrato de la API REST compartido entre el servidor (:server) y la app (:app:shared).
 * Al vivir en un solo lugar, cliente y servidor no pueden desincronizarse.
 */
object ApiRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val TOTP_CODE_LENGTH = 6
    const val MAX_NAME_LENGTH = 100

    /** Con acceso biométrico, el código 2FA por correo se pide de nuevo cada estas horas. */
    const val TWO_FACTOR_WINDOW_HOURS = 24
}

/** Códigos de error estables que el cliente puede interpretar sin depender del texto. */
object ApiErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val INVALID_CODE = "invalid_code"
    const val SESSION_EXPIRED = "session_expired"
    const val UNAUTHORIZED = "unauthorized"
    const val EMAIL_TAKEN = "email_taken"
    const val WEAK_PASSWORD = "weak_password"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
    const val NOT_FOUND = "not_found"
    /** El acceso biométrico expiró: hay que volver a entrar con contraseña + código 2FA. */
    const val TWO_FACTOR_REQUIRED = "two_factor_required"
    /** El dispositivo ya no tiene acceso biométrico (desactivado o eliminado). */
    const val BIOMETRIC_REVOKED = "biometric_revoked"
    /** El servidor no pudo enviar el código 2FA por correo. */
    const val EMAIL_FAILED = "email_failed"
    /** Faltan casillas obligatorias de Términos / Aviso de Privacidad al registrarse. */
    const val CONSENT_REQUIRED = "consent_required"
}

@Serializable
data class ApiError(val code: String, val message: String)

// ---------- Autenticación ----------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class GeneralResponse(val message: String)

@Serializable
data class LoginResponse(val tempToken: String)

@Serializable
data class Verify2faRequest(val tempToken: String, val code: String)

@Serializable
data class Verify2faResponse(val accessToken: String, val email: String)

/**
 * Casillas que aparecen al crear la cuenta (documento "Términos y Condiciones / Aviso de Privacidad").
 * Las [required] son obligatorias para usar PRISMA: sin ellas el servidor rechaza el registro.
 * Las demás son opcionales y no afectan el servicio.
 */
@Serializable
enum class Consent(val required: Boolean, val text: String) {
    TerminosUso(true, "He leído y acepto los Términos y Condiciones de Uso (Parte A)."),
    AvisoPrivacidad(true, "He leído el Aviso de Privacidad Integral (Parte B)."),
    DatosFinancieros(true, "Otorgo mi consentimiento expreso para el tratamiento de mis datos financieros y patrimoniales para las finalidades necesarias."),
    EnvioIa(true, "Autorizo el envío de mis documentos, ya anonimizados, al proveedor de IA para su análisis."),
    Estadisticas(false, "Acepto el uso de datos agregados o anonimizados para estadísticas y mejora del producto."),
    Encuestas(false, "Acepto recibir invitaciones a encuestas de usabilidad."),
    Novedades(false, "Acepto recibir novedades de PRISMA.");

    companion object {
        val requiredOnes: List<Consent> get() = entries.filter { it.required }
        val optionalOnes: List<Consent> get() = entries.filterNot { it.required }

        /** Versión del documento aceptado; se guarda junto con cada consentimiento. */
        const val DOCUMENT_VERSION = "2026-11"
    }
}

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val region: String = "MX",
    /** Casillas marcadas por el usuario. Debe incluir todas las obligatorias. */
    val consentimientos: Set<Consent> = emptySet(),
)

@Serializable
data class RegisterResponse(val message: String)

// ---------- Perfil ----------

@Serializable
data class PersonalData(
    val nombre: String,
    val apellido: String,
    /** Fecha ISO-8601 (yyyy-MM-dd) o null. */
    val fechaNacimiento: String? = null,
)

@Serializable
data class PersonalDataResponse(val message: String, val profileId: Int)

// ---------- Dashboard ----------

@Serializable
data class PaymentAlertDto(val bankName: String, val lastFour: String, val daysLeft: Int)

@Serializable
data class DashboardResponse(
    val userName: String,
    val status: String,
    val radarAxes: List<String>,
    val alerts: List<PaymentAlertDto>,
    val totalToPayMxn: Double,
)

// ---------- Ingresos ----------

/**
 * Los montos viajan como texto decimal con 2 decimales (p. ej. "15000.50") para no perder
 * precisión: en la BD son NUMERIC(12,2) y un Double puede redondear centavos.
 */
@Serializable
data class CreateIncomeRequest(
    /** Nombre o concepto del ingreso, p. ej. "Salario". */
    val nombre: String,
    val montoNeto: String,
)

/** Edición de un ingreso existente (PUT /api/v1/incomes/{id}). Mismo formato que al crear. */
@Serializable
data class UpdateIncomeRequest(
    val nombre: String,
    val montoNeto: String,
)

@Serializable
data class IncomeDto(
    val id: String,
    val nombre: String,
    val montoNeto: String,
    val esAuditado: Boolean,
    /** Fecha y hora local del registro, formato ISO "yyyy-MM-ddTHH:mm". */
    val fechaRegistro: String,
)

@Serializable
data class IncomesSummary(
    /** Ingresos del usuario, del más reciente al más antiguo. */
    val ingresos: List<IncomeDto>,
    /** Suma de los ingresos registrados en el mes en curso. */
    val totalMes: String,
)

object IncomeRules {
    /** NUMERIC(12,2): hasta 10 dígitos enteros. */
    const val MAX_AMOUNT = "9999999999.99"

    /** VARCHAR(100) en la columna ingresos.nombre. */
    const val MAX_NAME_LENGTH = 100
}

// ---------- Tarjetas ----------

@Serializable
data class CardDto(
    val id: String,
    val banco: String,
    val alias: String,
    val ultimosCuatro: String? = null,
)

object CardRules {
    const val MAX_BANK_LENGTH = 100
    const val MAX_ALIAS_LENGTH = 150
    const val LAST_FOUR_LENGTH = 4
}

@Serializable
data class CreateCardRequest(
    val banco: String,
    val alias: String,
    val ultimosCuatro: String? = null,
)

@Serializable
data class UpdateCardRequest(
    val banco: String,
    val alias: String,
    val ultimosCuatro: String? = null,
)

@Serializable
data class CardDocumentDto(
    val id: String,
    val tarjetaId: String,
    val nombreArchivo: String,
    val tipoMime: String,
    val urlODatos: String,
    val montoPesos: String? = null,
    val periodo: String? = null,
    val fechaCorte: String? = null,
    val fechaPago: String? = null,
    val fechaRegistro: String? = null,
)

@Serializable
data class CreateCardDocumentRequest(
    val nombreArchivo: String,
    val tipoMime: String,
    val urlODatos: String,
    val montoPesos: String? = null,
    val periodo: String? = null,
    val fechaCorte: String? = null,
    val fechaPago: String? = null,
)

// ---------- Acceso biométrico ----------

/** plataforma: "android" o "ios". Requiere sesión (Bearer) obtenida con 2FA. */
@Serializable
data class BiometricEnrollRequest(val plataforma: String)

/**
 * deviceId identifica el registro (no es secreto, sirve para renovar/desactivar).
 * deviceToken es secreto: el teléfono lo guarda protegido por huella / Face ID.
 */
@Serializable
data class BiometricEnrollResponse(val deviceId: String, val deviceToken: String)

/** Login biométrico: responde Verify2faResponse si el último 2FA fue hace menos de 24 h. */
@Serializable
data class BiometricLoginRequest(val deviceToken: String)

/** Para renovar la ventana de 24 h tras un login con 2FA, o para desactivar el acceso. */
@Serializable
data class BiometricDeviceRequest(val deviceId: String)
