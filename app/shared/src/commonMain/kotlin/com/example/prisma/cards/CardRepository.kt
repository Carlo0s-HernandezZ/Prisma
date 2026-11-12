package com.example.prisma.cards

import com.example.prisma.api.CardDto
import com.example.prisma.api.CreateCardRequest
import com.example.prisma.api.UpdateCardRequest
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

enum class CardError { Unauthorized, NotFound, Invalid, Network, Unknown }

class CardException(val error: CardError) : Exception(error.name)

interface CardRepository {
    suspend fun list(): Result<List<CardDto>>
    suspend fun add(
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto>

    suspend fun update(
        id: String,
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto>

    suspend fun delete(id: String): Result<Unit>
}

class FakeCardRepository : CardRepository {
    private val cards = mutableListOf<CardDto>(
        CardDto(
            id = "c1",
            banco = "BBVA",
            alias = "Tarjeta Principal",
            ultimosCuatro = "1635",
            diaCorte = 15,
            horaNotificacionCorte = "09:00",
            diaPago = 5,
            horaNotificacionPago = "20:00",
            fechaRegistro = "2026-11-01T12:00",
        ),
        CardDto(
            id = "c2",
            banco = "Nu",
            alias = "Gastos Diarios",
            ultimosCuatro = "3489",
            diaCorte = 20,
            horaNotificacionCorte = "10:00",
            diaPago = 10,
            horaNotificacionPago = "19:00",
            fechaRegistro = "2026-11-02T15:30",
        ),
    )

    override suspend fun list(): Result<List<CardDto>> = Result.success(cards.toList())

    override suspend fun add(
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto> {
        val newCard = CardDto(
            id = "c_${cards.size + 1}",
            banco = banco,
            alias = alias,
            ultimosCuatro = ultimosCuatro,
            diaCorte = diaCorte,
            horaNotificacionCorte = horaNotificacionCorte,
            diaPago = diaPago,
            horaNotificacionPago = horaNotificacionPago,
            fechaRegistro = "2026-11-05T10:00",
        )
        cards.add(0, newCard)
        return Result.success(newCard)
    }

    override suspend fun update(
        id: String,
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto> {
        val idx = cards.indexOfFirst { it.id == id }
        if (idx < 0) return Result.failure(CardException(CardError.NotFound))
        val updated = cards[idx].copy(
            banco = banco,
            alias = alias,
            ultimosCuatro = ultimosCuatro,
            diaCorte = diaCorte,
            horaNotificacionCorte = horaNotificacionCorte,
            diaPago = diaPago,
            horaNotificacionPago = horaNotificacionPago,
        )
        cards[idx] = updated
        return Result.success(updated)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val removed = cards.removeAll { it.id == id }
        return if (removed) Result.success(Unit) else Result.failure(CardException(CardError.NotFound))
    }
}

class KtorCardRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : CardRepository {
    private val url get() = "$baseUrl/api/v1/cards"

    override suspend fun list(): Result<List<CardDto>> =
        request({ client.get(url) { withToken(accessToken) } }) { it.body<List<CardDto>>() }

    override suspend fun add(
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto> =
        request({
            client.post(url) {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateCardRequest(banco, alias, ultimosCuatro, diaCorte, horaNotificacionCorte, diaPago, horaNotificacionPago))
            }
        }) { it.body<CardDto>() }

    override suspend fun update(
        id: String,
        banco: String,
        alias: String,
        ultimosCuatro: String?,
        diaCorte: Int?,
        horaNotificacionCorte: String?,
        diaPago: Int?,
        horaNotificacionPago: String?,
    ): Result<CardDto> =
        request({
            client.put("$url/$id") {
                withToken(accessToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateCardRequest(banco, alias, ultimosCuatro, diaCorte, horaNotificacionCorte, diaPago, horaNotificacionPago))
            }
        }) { it.body<CardDto>() }

    override suspend fun delete(id: String): Result<Unit> =
        request({ client.delete("$url/$id") { withToken(accessToken) } }) { }

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
