package com.example.prisma.cards

import com.example.prisma.api.StatementPaymentsDto
import com.example.prisma.api.StatementPaymentsRequest
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

class StatementPaymentsException(
    val status: Int?,
    message: String,
    val requiresReload: Boolean = false,
) : Exception(message)

interface StatementPaymentsRepository {
    suspend fun get(
        cardId: String,
        periodo: String,
    ): Result<StatementPaymentsDto>

    suspend fun save(
        cardId: String,
        periodo: String,
        request: StatementPaymentsRequest,
    ): Result<StatementPaymentsDto>
}

class KtorStatementPaymentsRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : StatementPaymentsRepository {

    override suspend fun get(
        cardId: String,
        periodo: String,
    ): Result<StatementPaymentsDto> = execute {
        client.get(url(cardId, periodo)) {
            withToken(token)
        }
    }

    override suspend fun save(
        cardId: String,
        periodo: String,
        request: StatementPaymentsRequest,
    ): Result<StatementPaymentsDto> = execute {
        client.put(url(cardId, periodo)) {
            withToken(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun url(
        cardId: String,
        periodo: String,
    ): String =
        baseUrl + "/api/v1/cards/" + cardId +
                "/statements/" + periodo + "/payments"

    private suspend inline fun <reified T> execute(
        crossinline send: suspend () -> HttpResponse,
    ): Result<T> {
        val response = try {
            send()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return Result.failure(
                StatementPaymentsException(
                    null,
                    "No se pudo conectar. " +
                            "Los cambios se conservan para reintentar.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                StatementPaymentsException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo completar la solicitud de pagos.",
                ),
            )
        }

        return try {
            Result.success(response.body<T>())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(
                StatementPaymentsException(
                    null,
                    "No se pudo interpretar la respuesta. " +
                            "Recarga para comprobar los pagos guardados.",
                    requiresReload = true,
                ),
            )
        }
    }
}