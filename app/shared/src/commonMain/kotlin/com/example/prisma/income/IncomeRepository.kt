package com.example.prisma.income

import com.example.prisma.api.CreateIncomeRequest
import com.example.prisma.api.IncomeDto
import com.example.prisma.api.IncomePlanningDto
import com.example.prisma.api.IncomePlanningRequest
import com.example.prisma.api.IncomePlanningSummary
import com.example.prisma.api.IncomesSummary
import com.example.prisma.api.UpdateIncomeRequest
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
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

enum class IncomeError {
    Unauthorized,
    NotFound,
    Invalid,
    Network,
    Unknown,
}

class IncomeException(
    val error: IncomeError,
    val serverMessage: String? = null,
) : Exception(serverMessage ?: error.name)

interface IncomeRepository {
    suspend fun list(): Result<IncomesSummary>

    suspend fun add(
        nombre: String,
        montoNeto: String,
    ): Result<IncomeDto>

    suspend fun update(
        id: String,
        nombre: String,
        montoNeto: String,
    ): Result<IncomeDto>

    suspend fun delete(id: String): Result<Unit>

    suspend fun listPlanning(): Result<IncomePlanningSummary>

    suspend fun savePlanning(
        incomeId: String,
        request: IncomePlanningRequest,
    ): Result<IncomePlanningDto>
}

class KtorIncomeRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : IncomeRepository {

    private val url
        get() = baseUrl + "/api/v1/incomes"

    private val planningUrl
        get() = baseUrl + "/api/v1/income-planning"

    override suspend fun list(): Result<IncomesSummary> =
        request({
            client.get(url) {
                withToken(accessToken)
            }
        }) {
            it.body<IncomesSummary>()
        }

    override suspend fun add(
        nombre: String,
        montoNeto: String,
    ): Result<IncomeDto> = request({
        client.post(url) {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateIncomeRequest(nombre, montoNeto))
        }
    }) {
        it.body<IncomeDto>()
    }

    override suspend fun update(
        id: String,
        nombre: String,
        montoNeto: String,
    ): Result<IncomeDto> = request({
        client.put(url + "/" + id) {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(UpdateIncomeRequest(nombre, montoNeto))
        }
    }) {
        it.body<IncomeDto>()
    }

    override suspend fun delete(id: String): Result<Unit> =
        request({
            client.delete(url + "/" + id) {
                withToken(accessToken)
            }
        }) {
            Unit
        }

    override suspend fun listPlanning(): Result<IncomePlanningSummary> =
        request({
            client.get(planningUrl) {
                withToken(accessToken)
            }
        }) {
            it.body<IncomePlanningSummary>()
        }

    override suspend fun savePlanning(
        incomeId: String,
        request: IncomePlanningRequest,
    ): Result<IncomePlanningDto> = this.request({
        client.put(planningUrl + "/" + incomeId) {
            withToken(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }) {
        it.body<IncomePlanningDto>()
    }

    private suspend fun <T> request(
        send: suspend () -> HttpResponse,
        parse: suspend (HttpResponse) -> T,
    ): Result<T> {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return Result.failure(
                IncomeException(IncomeError.Network),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                IncomeException(
                    error = when (response.status) {
                        HttpStatusCode.Unauthorized ->
                            IncomeError.Unauthorized

                        HttpStatusCode.NotFound ->
                            IncomeError.NotFound

                        HttpStatusCode.BadRequest ->
                            IncomeError.Invalid

                        else ->
                            IncomeError.Unknown
                    },
                    serverMessage = response.apiErrorMessage(),
                ),
            )
        }

        return try {
            Result.success(parse(response))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Result.failure(
                IncomeException(IncomeError.Unknown),
            )
        }
    }
}