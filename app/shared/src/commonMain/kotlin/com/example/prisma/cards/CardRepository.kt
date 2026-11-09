package com.example.prisma.cards

import com.example.prisma.api.CardDocumentDto
import com.example.prisma.api.CardDto
import com.example.prisma.api.CreateCardRequest
import com.example.prisma.api.UpdateCardRequest
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

enum class CardError { Unauthorized, NotFound, Invalid, Network, Unknown }

class CardException(val error: CardError) : Exception(error.name)

interface CardRepository {
    suspend fun list(): Result<List<CardDto>>
    suspend fun add(banco: String, alias: String, ultimosCuatro: String?): Result<CardDto>
    suspend fun update(id: String, banco: String, alias: String, ultimosCuatro: String?): Result<CardDto>
    suspend fun delete(id: String): Result<Unit>
    suspend fun listDocuments(cardId: String): Result<List<CardDocumentDto>>
    suspend fun uploadDocument(cardId: String, fileName: String, mimeType: String, fileBytes: ByteArray): Result<CardDocumentDto>
    suspend fun deleteDocument(cardId: String, docId: String): Result<Unit>
}

class KtorCardRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : CardRepository {
    private val url get() = "$baseUrl/api/v1/cards"

    override suspend fun list(): Result<List<CardDto>> =
        request({ client.get(url) { withToken(accessToken) } }) { it.body<List<CardDto>>() }

    override suspend fun add(banco: String, alias: String, ultimosCuatro: String?): Result<CardDto> =
        request({
            client.post(url) {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateCardRequest(banco, alias, ultimosCuatro))
            }
        }) { it.body<CardDto>() }

    override suspend fun update(id: String, banco: String, alias: String, ultimosCuatro: String?): Result<CardDto> =
        request({
            client.put("$url/$id") {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateCardRequest(banco, alias, ultimosCuatro))
            }
        }) { it.body<CardDto>() }

    override suspend fun delete(id: String): Result<Unit> =
        request({ client.delete("$url/$id") { withToken(accessToken) } }) { }

    override suspend fun listDocuments(cardId: String): Result<List<CardDocumentDto>> =
        request({ client.get("$url/$cardId/documents") { withToken(accessToken) } }) { it.body<List<CardDocumentDto>>() }

    override suspend fun uploadDocument(cardId: String, fileName: String, mimeType: String, fileBytes: ByteArray): Result<CardDocumentDto> =
        request({
            client.post("$url/$cardId/documents") {
                withToken(accessToken)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", fileBytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }) { it.body<CardDocumentDto>() }

    override suspend fun deleteDocument(cardId: String, docId: String): Result<Unit> =
        request({ client.delete("$url/$cardId/documents/$docId") { withToken(accessToken) } }) { }

    private suspend fun <T> request(send: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> T): Result<T> =
        try {
            val response = send()
            if (response.status.isSuccess()) {
                Result.success(parse(response))
            } else {
                Result.failure(
                    CardException(
                        when (response.status) {
                            HttpStatusCode.Unauthorized -> CardError.Unauthorized
                            HttpStatusCode.NotFound -> CardError.NotFound
                            HttpStatusCode.BadRequest -> CardError.Invalid
                            else -> CardError.Unknown
                        }
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(CardException(CardError.Network))
        }
}
