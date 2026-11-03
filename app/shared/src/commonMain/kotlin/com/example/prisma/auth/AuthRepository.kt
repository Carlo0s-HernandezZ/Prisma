package com.example.prisma.auth

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.LoginRequest
import com.example.prisma.api.LoginResponse
import com.example.prisma.api.RegisterRequest
import com.example.prisma.api.RegisterResponse
import com.example.prisma.api.Verify2faRequest
import com.example.prisma.api.Verify2faResponse
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorCode
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

enum class AuthError {
    InvalidCredentials, InvalidCode, SessionExpired, EmailTaken, WeakPassword, TooManyAttempts, Network, Unknown
}

class AuthException(val error: AuthError) : Exception(error.name)

interface AuthRepository {
    /** Devuelve el token temporal para el paso de 2FA. */
    suspend fun login(email: String, password: String): Result<String>
    suspend fun verifyTwoFactor(tempToken: String, code: String): Result<Verify2faResponse>
    suspend fun register(email: String, password: String): Result<RegisterResponse>
    suspend fun logout(accessToken: String)
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

    override suspend fun verifyTwoFactor(tempToken: String, code: String): Result<Verify2faResponse> {
        delay(700)
        return if (code == "123456") {
            Result.success(Verify2faResponse("demo_access_token", "demo@prisma.com"))
        } else {
            Result.failure(AuthException(AuthError.InvalidCode))
        }
    }

    override suspend fun register(email: String, password: String): Result<RegisterResponse> {
        delay(700)
        return Result.success(RegisterResponse("Success", "DEMODEMODEMODEMODEMODEMODEMODEMO", ""))
    }

    override suspend fun logout(accessToken: String) = Unit
}

class KtorAuthRepository(
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<String> =
        request(default401 = AuthError.InvalidCredentials, send = {
            client.post("$baseUrl/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        }) { it.body<LoginResponse>().tempToken }

    override suspend fun verifyTwoFactor(tempToken: String, code: String): Result<Verify2faResponse> =
        request(default401 = AuthError.InvalidCode, send = {
            client.post("$baseUrl/api/v1/auth/verify-2fa") {
                contentType(ContentType.Application.Json)
                setBody(Verify2faRequest(tempToken, code))
            }
        }) { it.body<Verify2faResponse>() }

    override suspend fun register(email: String, password: String): Result<RegisterResponse> =
        request(default401 = AuthError.Unknown, send = {
            client.post("$baseUrl/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, password))
            }
        }) { it.body<RegisterResponse>() }

    override suspend fun logout(accessToken: String) {
        // Mejor esfuerzo: aunque falle la red, la sesión local se cierra igual.
        try {
            client.post("$baseUrl/api/v1/auth/logout") { withToken(accessToken) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun <T> request(
        default401: AuthError,
        send: suspend () -> HttpResponse,
        parse: suspend (HttpResponse) -> T,
    ): Result<T> = try {
        val response = send()
        if (response.status.isSuccess()) {
            Result.success(parse(response))
        } else {
            Result.failure(AuthException(errorFor(response, default401)))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("PRISMA auth network error: ${e::class.simpleName}")
        Result.failure(AuthException(AuthError.Network))
    }

    private suspend fun errorFor(response: HttpResponse, default401: AuthError): AuthError {
        val code = response.apiErrorCode()
        return when {
            code == ApiErrorCodes.WEAK_PASSWORD -> AuthError.WeakPassword
            code == ApiErrorCodes.EMAIL_TAKEN || response.status == HttpStatusCode.Conflict -> AuthError.EmailTaken
            code == ApiErrorCodes.SESSION_EXPIRED || response.status == HttpStatusCode.Gone -> AuthError.SessionExpired
            code == ApiErrorCodes.TOO_MANY_ATTEMPTS || response.status == HttpStatusCode.TooManyRequests -> AuthError.TooManyAttempts
            code == ApiErrorCodes.INVALID_CODE -> AuthError.InvalidCode
            code == ApiErrorCodes.INVALID_CREDENTIALS -> AuthError.InvalidCredentials
            response.status == HttpStatusCode.Unauthorized -> default401
            else -> AuthError.Unknown
        }
    }
}
