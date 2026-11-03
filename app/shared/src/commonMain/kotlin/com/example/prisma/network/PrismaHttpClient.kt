package com.example.prisma.network

import com.example.prisma.api.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Un solo cliente HTTP para toda la app (reutiliza conexiones; vive lo mismo que el proceso). */
internal val prismaHttpClient: HttpClient by lazy {
    HttpClient {
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

/** Código de error que devuelve el servidor en el cuerpo, si lo hay. */
internal suspend fun HttpResponse.apiErrorCode(): String? =
    runCatching { body<ApiError>().code }.getOrNull()
