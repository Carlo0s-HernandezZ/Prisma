package com.example.prisma.income

import com.example.prisma.api.CreateIncomeRequest
import com.example.prisma.api.IncomeDto
import com.example.prisma.api.IncomesSummary
import com.example.prisma.api.UpdateIncomeRequest
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

enum class IncomeError { Unauthorized, NotFound, Invalid, Network, Unknown }

class IncomeException(val error: IncomeError) : Exception(error.name)

interface IncomeRepository {
    suspend fun list(): Result<IncomesSummary>
    /** [montoNeto] en formato decimal con punto, p. ej. "15000.50". */
    suspend fun add(nombre: String, montoNeto: String): Result<IncomeDto>
    suspend fun update(id: String, nombre: String, montoNeto: String): Result<IncomeDto>
    suspend fun delete(id: String): Result<Unit>
}

class KtorIncomeRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : IncomeRepository {
    private val url get() = "$baseUrl/api/v1/incomes"

    override suspend fun list(): Result<IncomesSummary> =
        request({ client.get(url) { withToken(accessToken) } }) { it.body<IncomesSummary>() }

    override suspend fun add(nombre: String, montoNeto: String): Result<IncomeDto> =
        request({
            client.post(url) {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateIncomeRequest(nombre, montoNeto))
            }
        }) { it.body<IncomeDto>() }

    override suspend fun update(id: String, nombre: String, montoNeto: String): Result<IncomeDto> =
        request({
            client.put("$url/$id") {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateIncomeRequest(nombre, montoNeto))
            }
        }) { it.body<IncomeDto>() }

    override suspend fun delete(id: String): Result<Unit> =
        request({ client.delete("$url/$id") { withToken(accessToken) } }) { }

    private suspend fun <T> request(send: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T): Result<T> =
        try {
            val response = send()
            if (response.status.isSuccess()) {
                Result.success(parse(response))
            } else {
                Result.failure(
                    IncomeException(
                        when (response.status) {
                            HttpStatusCode.Unauthorized -> IncomeError.Unauthorized
                            HttpStatusCode.NotFound -> IncomeError.NotFound
                            HttpStatusCode.BadRequest -> IncomeError.Invalid
                            else -> IncomeError.Unknown
                        }
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { // En web (wasm) un fallo de red llega como kotlin.Error, no Exception.
            Result.failure(IncomeException(IncomeError.Network))
        }
}
