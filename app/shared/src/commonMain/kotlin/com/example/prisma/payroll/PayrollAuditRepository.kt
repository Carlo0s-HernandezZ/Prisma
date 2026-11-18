package com.example.prisma.payroll

import com.example.prisma.analysis.AnalysisException
import com.example.prisma.api.PayrollAuditDto
import com.example.prisma.api.PayrollAuditListResponse
import com.example.prisma.api.ReminderDto
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
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/** Auditoría de nómina y recordatorios (servidor de Prisma). */
class PayrollAuditRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) {
    suspend fun upload(fileName: String, bytes: ByteArray): Result<PayrollAuditDto> = call {
        client.post("$baseUrl/api/v1/payroll/audits") {
            withToken(token)
            timeout {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "archivo",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "").take(80)}\"")
                            },
                        )
                    },
                ),
            )
        }
    }

    suspend fun list(): Result<PayrollAuditListResponse> = call { client.get("$baseUrl/api/v1/payroll/audits") { withToken(token) } }

    suspend fun delete(id: String): Result<Unit> = noContent { client.delete("$baseUrl/api/v1/payroll/audits/$id") { withToken(token) } }

    suspend fun reminders(): Result<List<ReminderDto>> = call { client.get("$baseUrl/api/v1/reminders") { withToken(token) } }

    suspend fun saveReminder(r: ReminderDto): Result<ReminderDto> = call {
        if (r.id.isBlank()) {
            client.post("$baseUrl/api/v1/reminders") { withToken(token); contentType(ContentType.Application.Json); setBody(r) }
        } else {
            client.put("$baseUrl/api/v1/reminders/${r.id}") { withToken(token); contentType(ContentType.Application.Json); setBody(r) }
        }
    }

    suspend fun deleteReminder(id: String): Result<Unit> = noContent { client.delete("$baseUrl/api/v1/reminders/$id") { withToken(token) } }

    private suspend inline fun <reified T> call(request: () -> HttpResponse): Result<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
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

    private suspend inline fun noContent(request: () -> HttpResponse): Result<Unit> = try {
        val response = request()
        if (response.status.isSuccess()) Result.success(Unit) else Result.failure(response.toError())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.failure(AnalysisException(OFFLINE))
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
