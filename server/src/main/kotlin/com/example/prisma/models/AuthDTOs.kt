package com.example.prisma.models

import kotlinx.serialization.Serializable
import com.example.prisma.models.RegisterRequest
import com.example.prisma.models.RegisterResponse

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val status: String, //
    // "PENDING_2FA"
    val temp_token: String,
    val message: String
)

@Serializable
data class Verify2FaRequest(
    val temp_token: String,
    val code: String
)

@Serializable
data class Verify2FaResponse(
    val status: String, // "AUTHENTICATED"
    val access_token: String,
    val refresh_token: String,
    val expires_in_seconds: Long
)

@Serializable
data class ErrorResponse(
    val error_code: String,
    val message: String,
    val timestamp: String
)


@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val region: String
)

@Serializable
data class RegisterResponse(
    val message: String,
    val secret_2fa: String,
    val otpauth_uri: String
)