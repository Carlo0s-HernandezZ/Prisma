package com.example.prisma.budget

import com.example.prisma.api.BudgetDto
import com.example.prisma.api.BudgetRequest
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

class BudgetException(
    val status: Int?,
    message: String,
) : Exception(message)

interface BudgetRepository {
    suspend fun get(
        periodo: String,
    ): Result<BudgetDto>

    suspend fun save(
        periodo: String,
        request: BudgetRequest,
    ): Result<BudgetDto>
}

class KtorBudgetRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : BudgetRepository {

    override suspend fun get(
        periodo: String,
    ): Result<BudgetDto> = send(periodo, null)

    override suspend fun save(
        periodo: String,
        request: BudgetRequest,
    ): Result<BudgetDto> = send(periodo, request)

    private suspend fun send(
        periodo: String,
        request: BudgetRequest?,
    ): Result<BudgetDto> {
        val url = baseUrl + "/api/v1/budgets/" + periodo

        val response = try {
            if (request == null) {
                client.get(url) {
                    withToken(token)
                }
            } else {
                client.put(url) {
                    withToken(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return Result.failure(
                BudgetException(
                    null,
                    "Sin conexión con el servidor. Puedes reintentar.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                BudgetException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo completar la solicitud del presupuesto.",
                ),
            )
        }

        return try {
            Result.success(response.body<BudgetDto>())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Result.failure(
                BudgetException(
                    null,
                    "El servidor devolvió un presupuesto que no se pudo leer.",
                ),
            )
        }
    }
}