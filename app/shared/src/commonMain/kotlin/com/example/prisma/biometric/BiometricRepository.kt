package com.example.prisma.biometric

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.BiometricDeviceRequest
import com.example.prisma.api.BiometricEnrollRequest
import com.example.prisma.api.BiometricEnrollResponse
import com.example.prisma.api.BiometricLoginRequest
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
import kotlin.coroutines.cancellation.CancellationException

enum class BiometricApiError {
    /** Pasaron más de 24 h desde el último código 2FA: hay que entrar con contraseña + código. */
    TwoFactorRequired,
    /** El dispositivo ya no está registrado en el servidor. */
    Revoked,
    Unauthorized,
    Network,
    Unknown,
}

class BiometricApiException(val error: BiometricApiError) : Exception(error.name)

class BiometricRepository(
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) {
    private val base get() = "$baseUrl/api/v1/auth/biometric"

    suspend fun enroll(accessToken: String, plataforma: String): Result<BiometricEnrollResponse> = request({
        client.post("$base/enroll") {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(BiometricEnrollRequest(plataforma))
        }
    }) { it.body<BiometricEnrollResponse>() }

    suspend fun login(deviceToken: String): Result<Verify2faResponse> = request({
        client.post("$base/login") {
            contentType(ContentType.Application.Json)
            setBody(BiometricLoginRequest(deviceToken))
        }
    }) { it.body<Verify2faResponse>() }

    suspend fun renew(accessToken: String, deviceId: String): Result<Unit> = request({
        client.post("$base/renew") {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(BiometricDeviceRequest(deviceId))
        }
    }) { }

    suspend fun revoke(accessToken: String, deviceId: String): Result<Unit> = request({
        client.post("$base/revoke") {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(BiometricDeviceRequest(deviceId))
        }
    }) { }

    private suspend fun <T> request(send: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T): Result<T> =
        try {
            val response = send()
            if (response.status.isSuccess()) {
                Result.success(parse(response))
            } else {
                val code = response.apiErrorCode()
                Result.failure(
                    BiometricApiException(
                        when {
                            code == ApiErrorCodes.TWO_FACTOR_REQUIRED -> BiometricApiError.TwoFactorRequired
                            code == ApiErrorCodes.BIOMETRIC_REVOKED || response.status == HttpStatusCode.NotFound -> BiometricApiError.Revoked
                            response.status == HttpStatusCode.Unauthorized -> BiometricApiError.Unauthorized
                            else -> BiometricApiError.Unknown
                        }
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(BiometricApiException(BiometricApiError.Network))
        }
}
