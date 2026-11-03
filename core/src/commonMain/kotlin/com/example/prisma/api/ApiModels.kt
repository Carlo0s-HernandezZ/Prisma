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
}

@Serializable
data class ApiError(val code: String, val message: String)

// ---------- Autenticación ----------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val tempToken: String)

@Serializable
data class Verify2faRequest(val tempToken: String, val code: String)

@Serializable
data class Verify2faResponse(val accessToken: String, val email: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val region: String = "MX")

@Serializable
data class RegisterResponse(
    val message: String,
    val secret2fa: String,
    val otpauthUrl: String,
)

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
