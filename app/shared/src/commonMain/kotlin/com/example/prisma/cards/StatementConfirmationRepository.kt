package com.example.prisma.cards

import com.example.prisma.api.ConfirmedStatementDto
import com.example.prisma.api.StatementConfirmationRequest
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

class StatementConfirmationException(
    val status: Int?,
    message: String,
    val requiresReload: Boolean = false,
) : Exception(message)

interface StatementConfirmationRepository {
    suspend fun list(
        cardId: String,
    ): Result<List<ConfirmedStatementDto>>

    suspend fun save(
        cardId: String,
        periodo: String,
        request: StatementConfirmationRequest,
    ): Result<ConfirmedStatementDto>
}

class KtorStatementConfirmationRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : StatementConfirmationRepository {

    override suspend fun list(
        cardId: String,
    ): Result<List<ConfirmedStatementDto>> = execute {
        client.get(
            baseUrl + "/api/v1/cards/" + cardId + "/statements",
        ) {
            withToken(token)
        }
    }

    override suspend fun save(
        cardId: String,
        periodo: String,
        request: StatementConfirmationRequest,
    ): Result<ConfirmedStatementDto> = execute {
        client.put(
            baseUrl + "/api/v1/cards/" + cardId +
                    "/statements/" + periodo,
        ) {
            withToken(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private suspend inline fun <reified T> execute(
        crossinline send: suspend () -> HttpResponse,
    ): Result<T> {
        val response = try {
            send()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return Result.failure(
                StatementConfirmationException(
                    null,
                    "No se pudo conectar con el servidor. " +
                            "Los cambios se conservan para reintentar.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                StatementConfirmationException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo completar la solicitud del estado de cuenta.",
                ),
            )
        }

        return try {
            Result.success(response.body<T>())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(
                StatementConfirmationException(
                    null,
                    "La respuesta del servidor no se pudo interpretar. " +
                            "Recarga para comprobar los datos guardados.",
                    requiresReload = true,
                ),
            )
        }
    }
}