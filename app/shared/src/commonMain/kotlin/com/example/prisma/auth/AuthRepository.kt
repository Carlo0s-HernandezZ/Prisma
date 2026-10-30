package com.example.prisma.auth

import com.example.prisma.defaultBaseUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

enum class AuthError { InvalidCredentials, InvalidCode, SessionExpired, Network, Unknown }

class AuthException(val error: AuthError) : Exception(error.name)

@Serializable
data class ApiRegisterResponse(
    val message: String,
    val secret2fa: String,
    val qrCodeUrl: String,
    val otpauthUrl: String
)

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<String>
    suspend fun verifyTwoFactor(tempToken: String, code: String): Result<String>
    suspend fun register(email: String, password: String): Result<ApiRegisterResponse>
}

class FakeAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): Result<String> {
        delay(700)
        return if (email.contains('@') && password.isNotBlank()) {
            Result.success("demo_temp_token")
        } else {
            Result.failure(AuthException(AuthError.InvalidCredentials))
        }
    }

    override suspend fun verifyTwoFactor(tempToken: String, code: String): Result<String> {
        delay(700)
        return if (code == "123456") {
            Result.success("demo_access_token")
        } else {
            Result.failure(AuthException(AuthError.InvalidCode))
        }
    }

    override suspend fun register(email: String, password: String): Result<ApiRegisterResponse> {
        delay(700)
        return if (email.contains('@') && password.isNotBlank()) {
            Result.success(ApiRegisterResponse("Success", "JBSWY3DPEHPK3PXP", "", ""))
        } else {
            Result.failure(Exception("Registration failed"))
        }
    }
}

@Serializable
data class ApiLoginRequest(val email: String, val password: String)

@Serializable
data class ApiLoginResponse(val tempToken: String)

@Serializable
data class ApiVerify2faRequest(val tempToken: String, val code: String)

@Serializable
data class ApiVerify2faResponse(val accessToken: String)

class KtorAuthRepository(private val baseUrl: String = defaultBaseUrl()) : AuthRepository {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun login(email: String, password: String): Result<String> {
        return try {
            val response = client.post("$baseUrl/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(ApiLoginRequest(email, password))
            }
            if (response.status.isSuccess()) {
                val body: ApiLoginResponse = response.body()
                Result.success(body.tempToken)
            } else {
                Result.failure(AuthException(AuthError.InvalidCredentials))
            }
        } catch (e: Exception) {
            println("KTOR LOGIN ERROR: ${e.message} - ${e::class.simpleName}")
            Result.failure(AuthException(AuthError.Network))
        }
    }

    override suspend fun verifyTwoFactor(tempToken: String, code: String): Result<String> {
        return try {
            val response = client.post("$baseUrl/api/v1/auth/verify-2fa") {
                contentType(ContentType.Application.Json)
                setBody(ApiVerify2faRequest(tempToken, code))
            }
            if (response.status.isSuccess()) {
                val body: ApiVerify2faResponse = response.body()
                Result.success(body.accessToken)
            } else {
                Result.failure(AuthException(AuthError.InvalidCode))
            }
        } catch (e: Exception) {
            println("KTOR 2FA ERROR: ${e.message} - ${e::class.simpleName}")
            Result.failure(AuthException(AuthError.Network))
        }
    }

    override suspend fun register(email: String, password: String): Result<ApiRegisterResponse> {
        return try {
            val response = client.post("$baseUrl/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(ApiLoginRequest(email, password))
            }
            if (response.status.isSuccess()) {
                val body: ApiRegisterResponse = response.body()
                Result.success(body)
            } else {
                Result.failure(Exception("Registration failed"))
            }
        } catch (e: Exception) {
            println("KTOR REGISTER ERROR: ${e.message} - ${e::class.simpleName}")
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
