package com.example.prisma.cards

import com.example.prisma.api.StatementExtractionResponse
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

data class StatementSelectedFile(
    val id: Long,
    val nombre: String,
    val bytes: ByteArray,
)

class StatementExtractionException(
    val status: Int?,
    message: String,
) : Exception(message)

interface StatementExtractionRepository {
    suspend fun extract(
        cardId: String,
        files: List<StatementSelectedFile>,
    ): Result<StatementExtractionResponse>
}

class KtorStatementExtractionRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : StatementExtractionRepository {

    override suspend fun extract(
        cardId: String,
        files: List<StatementSelectedFile>,
    ): Result<StatementExtractionResponse> {
        val response = try {
            client.post(
                baseUrl + "/api/v1/cards/" +
                        cardId + "/statement-extraction",
            ) {
                withToken(token)

                timeout {
                    requestTimeoutMillis = 420_000
                    socketTimeoutMillis = 420_000
                }

                setBody(
                    MultiPartFormDataContent(
                        formData {
                            files.forEachIndexed { index, file ->
                                append(
                                    "files",
                                    file.bytes,
                                    Headers.build {
                                        append(
                                            HttpHeaders.ContentType,
                                            "application/octet-stream",
                                        )

                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "filename=\"archivo-" +
                                                    (index + 1) + ".bin\"",
                                        )
                                    },
                                )
                            }
                        },
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return Result.failure(
                StatementExtractionException(
                    null,
                    "No se pudo conectar con el lector. " +
                            "La selección se conserva para reintentar.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                StatementExtractionException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo leer el estado de cuenta.",
                ),
            )
        }

        return try {
            Result.success(
                response.body<StatementExtractionResponse>(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.failure(
                StatementExtractionException(
                    null,
                    "La respuesta del lector no se pudo interpretar.",
                ),
            )
        }
    }
}