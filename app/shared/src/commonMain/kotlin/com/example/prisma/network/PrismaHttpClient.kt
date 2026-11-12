package com.example.prisma.network

import com.example.prisma.api.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Crea el cliente HTTP de cada plataforma con la validación TLS de Prisma:
 * - Android: OkHttp, solo TLS 1.2/1.3, confía únicamente en el certificado de Prisma y aplica
 *   Certificate Pinning (pin SHA-256 de la llave pública).
 * - iOS: Darwin (URLSession), solo acepta el certificado exacto de Prisma (pinning).
 * - Web: el navegador valida el certificado (HTTPS obligatorio).
 */
internal expect fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/** Un solo cliente HTTP para toda la app (reutiliza conexiones; vive lo mismo que el proceso). */
internal val prismaHttpClient: HttpClient by lazy {
    createPlatformHttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
    }
}

internal fun HttpRequestBuilder.withToken(token: String) = bearerAuth(token)

/** Mensaje legible que devuelve el servidor en el cuerpo, si lo hay. */
internal suspend fun HttpResponse.apiErrorMessage(): String? =
    runCatching { body<ApiError>().message }.getOrNull()

/** Código de error que devuelve el servidor en el cuerpo, si lo hay. */
internal suspend fun HttpResponse.apiErrorCode(): String? =
    runCatching { body<ApiError>().code }.getOrNull()
