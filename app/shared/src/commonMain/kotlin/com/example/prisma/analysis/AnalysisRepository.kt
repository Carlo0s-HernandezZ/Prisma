package com.example.prisma.analysis

import com.example.prisma.api.AnalysisListResponse
import com.example.prisma.api.HomeSummaryDto
import com.example.prisma.api.SpendingDashboardDto
import com.example.prisma.api.StatementAnalysisDto
import com.example.prisma.cards.StatementUploadFile
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/** Error de la API de análisis; [sessionExpired] indica que hay que volver a iniciar sesión. */
class AnalysisException(message: String, val sessionExpired: Boolean = false) : Exception(message)

interface AnalysisRepository {
    suspend fun list(cardId: String? = null): Result<AnalysisListResponse>
    suspend fun upload(cardId: String, files: List<StatementUploadFile>): Result<StatementAnalysisDto>
    suspend fun delete(id: String): Result<Unit>
    suspend fun dashboard(cardId: String? = null): Result<SpendingDashboardDto>
    suspend fun home(): Result<HomeSummaryDto>
}

class KtorAnalysisRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : AnalysisRepository {

    override suspend fun list(cardId: String?): Result<AnalysisListResponse> = call {
        client.get("$baseUrl/api/v1/analysis") {
            withToken(token)
            cardId?.let { parameter("tarjeta", it) }
        }
    }

    override suspend fun upload(cardId: String, files: List<StatementUploadFile>): Result<StatementAnalysisDto> = call {
        client.post("$baseUrl/api/v1/cards/$cardId/analysis") {
            withToken(token)
            // Leer el documento y consultar a la IA puede tardar.
            timeout {
                requestTimeoutMillis = 300_000
                socketTimeoutMillis = 300_000
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        files.forEachIndexed { index, file ->
                            append(
                                "files",
                                file.bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, file.mimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"estado-${index + 1}\"")
                                },
                            )
                        }
                    },
                ),
            )
        }
    }

    override suspend fun delete(id: String): Result<Unit> = try {
        val response = client.delete("$baseUrl/api/v1/analysis/$id") { withToken(token) }
        if (response.status.isSuccess()) Result.success(Unit) else Result.failure(response.toError())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.failure(AnalysisException(OFFLINE))
    }

    override suspend fun dashboard(cardId: String?): Result<SpendingDashboardDto> = call {
        client.get("$baseUrl/api/v1/analysis/dashboard") {
            withToken(token)
            cardId?.let { parameter("tarjeta", it) }
        }
    }

    override suspend fun home(): Result<HomeSummaryDto> = call {
        client.get("$baseUrl/api/v1/home/summary") { withToken(token) }
    }

    private suspend inline fun <reified T> call(request: () -> HttpResponse): Result<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { // En web (wasm) un fallo de red llega como kotlin.Error.
            return Result.failure(AnalysisException(OFFLINE))
        }
        if (!response.status.isSuccess()) return Result.failure(response.toError())
        return try {
            Result.success(response.body<T>())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Result.failure(AnalysisException("La respuesta del servidor no se pudo interpretar."))
        }
    }

    private suspend fun HttpResponse.toError(): AnalysisException =
        if (status == HttpStatusCode.Unauthorized) {
            AnalysisException("Tu sesión expiró.", sessionExpired = true)
        } else {
            AnalysisException(apiErrorMessage() ?: "No se pudo completar la operación. Inténtalo de nuevo.")
        }

    private companion object {
        const val OFFLINE = "Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo."
    }
}
