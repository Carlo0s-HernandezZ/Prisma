package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.CardDto
import com.example.prisma.api.CardRules
import com.example.prisma.api.CreateCardRequest
import com.example.prisma.api.UpdateCardRequest
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.TarjetasCreditoTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val ISO_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

internal fun parseBank(raw: String): String? =
    raw.trim().takeIf { it.isNotEmpty() && it.length <= CardRules.MAX_BANK_LENGTH }

internal fun parseAlias(raw: String): String? =
    raw.trim().takeIf { it.isNotEmpty() && it.length <= CardRules.MAX_ALIAS_LENGTH }

internal fun parseLastFour(raw: String?): Pair<Boolean, String?> {
    if (raw == null || raw.trim().isEmpty()) return Pair(true, null)
    val trimmed = raw.trim()
    if (trimmed.length == 4 && trimmed.all(Char::isDigit)) return Pair(true, trimmed)
    return Pair(false, null)
}

internal fun parseDay(day: Int?): Pair<Boolean, Int?> {
    if (day == null) return Pair(true, null)
    if (day in CardRules.VALID_DAYS) return Pair(true, day)
    return Pair(false, null)
}

internal fun parseTime(time: String?): Pair<Boolean, String?> {
    if (time == null || time.trim().isEmpty()) return Pair(true, null)
    val trimmed = time.trim()
    val parts = trimmed.split(":")
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) {
            val formatted = "%02d:%02d".format(h, m)
            return Pair(true, formatted)
        }
    }
    return Pair(false, null)
}

private fun ResultRow.toCardDto(): CardDto {
    val registro = this[TarjetasCreditoTable.fechaRegistro]
        ?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES) }
    return CardDto(
        id = this[TarjetasCreditoTable.id].toString(),
        banco = this[TarjetasCreditoTable.banco],
        alias = this[TarjetasCreditoTable.alias],
        ultimosCuatro = this[TarjetasCreditoTable.ultimosCuatro],
        diaCorte = this[TarjetasCreditoTable.diaCorte],
        horaNotificacionCorte = this[TarjetasCreditoTable.horaNotificacionCorte],
        diaPago = this[TarjetasCreditoTable.diaPago],
        horaNotificacionPago = this[TarjetasCreditoTable.horaNotificacionPago],
        fechaRegistro = registro?.format(ISO_MINUTES).orEmpty(),
    )
}

fun Route.cardRoutes(sessions: SessionStore) {
    route("/api/v1/cards") {

        get {
            val userId = call.authenticatedUser(sessions) ?: return@get
            val cards = dbQuery {
                TarjetasCreditoTable.selectAll()
                    .where { TarjetasCreditoTable.usuarioId eq userId }
                    .orderBy(TarjetasCreditoTable.fechaRegistro to SortOrder.DESC_NULLS_LAST)
                    .map { it.toCardDto() }
            }
            call.respond(cards)
        }

        post {
            val userId = call.authenticatedUser(sessions) ?: return@post
            val req = call.receiveOrBadRequest<CreateCardRequest>() ?: return@post

            val banco = parseBank(req.banco)
                ?: return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El nombre del banco es obligatorio (máximo ${CardRules.MAX_BANK_LENGTH} caracteres).",
                )
            val alias = parseAlias(req.alias)
                ?: return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El alias de la tarjeta es obligatorio (máximo ${CardRules.MAX_ALIAS_LENGTH} caracteres).",
                )
            val (lastFourValid, lastFour) = parseLastFour(req.ultimosCuatro)
            if (!lastFourValid) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "Los últimos 4 dígitos deben ser exactamente 4 números.",
                )
            }
            val (diaCorteValid, diaCorte) = parseDay(req.diaCorte)
            if (!diaCorteValid) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El día de corte debe ser entre 1 y 31.",
                )
            }
            val (horaCorteValid, horaCorte) = parseTime(req.horaNotificacionCorte)
            if (!horaCorteValid) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "La hora de notificación de corte debe estar en formato HH:mm.",
                )
            }
            val (diaPagoValid, diaPago) = parseDay(req.diaPago)
            if (!diaPagoValid) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El día de pago debe ser entre 1 y 31.",
                )
            }
            val (horaPagoValid, horaPago) = parseTime(req.horaNotificacionPago)
            if (!horaPagoValid) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "La hora de notificación de pago debe estar en formato HH:mm.",
                )
            }

            val newId = UUID.randomUUID()
            val created = dbQuery {
                TarjetasCreditoTable.insert {
                    it[id] = newId
                    it[usuarioId] = userId
                    it[TarjetasCreditoTable.banco] = banco
                    it[TarjetasCreditoTable.alias] = alias
                    it[ultimosCuatro] = lastFour
                    it[TarjetasCreditoTable.diaCorte] = diaCorte
                    it[horaNotificacionCorte] = horaCorte
                    it[TarjetasCreditoTable.diaPago] = diaPago
                    it[horaNotificacionPago] = horaPago
                }
                TarjetasCreditoTable.selectAll().where { TarjetasCreditoTable.id eq newId }.single().toCardDto()
            }

            call.respond(HttpStatusCode.Created, created)
        }

        put("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@put
            val cardId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Id inválido.")

            val req = call.receiveOrBadRequest<UpdateCardRequest>() ?: return@put

            val banco = parseBank(req.banco)
                ?: return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El nombre del banco es obligatorio (máximo ${CardRules.MAX_BANK_LENGTH} caracteres).",
                )
            val alias = parseAlias(req.alias)
                ?: return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El alias de la tarjeta es obligatorio (máximo ${CardRules.MAX_ALIAS_LENGTH} caracteres).",
                )
            val (lastFourValid, lastFour) = parseLastFour(req.ultimosCuatro)
            if (!lastFourValid) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "Los últimos 4 dígitos deben ser exactamente 4 números.",
                )
            }
            val (diaCorteValid, diaCorte) = parseDay(req.diaCorte)
            if (!diaCorteValid) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El día de corte debe ser entre 1 y 31.",
                )
            }
            val (horaCorteValid, horaCorte) = parseTime(req.horaNotificacionCorte)
            if (!horaCorteValid) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "La hora de notificación de corte debe estar en formato HH:mm.",
                )
            }
            val (diaPagoValid, diaPago) = parseDay(req.diaPago)
            if (!diaPagoValid) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El día de pago debe ser entre 1 y 31.",
                )
            }
            val (horaPagoValid, horaPago) = parseTime(req.horaNotificacionPago)
            if (!horaPagoValid) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "La hora de notificación de pago debe estar en formato HH:mm.",
                )
            }

            val updatedCount = dbQuery {
                TarjetasCreditoTable.update({ (TarjetasCreditoTable.id eq cardId) and (TarjetasCreditoTable.usuarioId eq userId) }) {
                    it[TarjetasCreditoTable.banco] = banco
                    it[TarjetasCreditoTable.alias] = alias
                    it[ultimosCuatro] = lastFour
                    it[TarjetasCreditoTable.diaCorte] = diaCorte
                    it[horaNotificacionCorte] = horaCorte
                    it[TarjetasCreditoTable.diaPago] = diaPago
                    it[horaNotificacionPago] = horaPago
                }
            }

            if (updatedCount > 0) {
                val updatedCard = dbQuery {
                    TarjetasCreditoTable.selectAll().where { TarjetasCreditoTable.id eq cardId }.single().toCardDto()
                }
                call.respond(updatedCard)
            } else {
                call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Tarjeta no encontrada.")
            }
        }

        delete("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@delete
            val cardId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Id inválido.")

            val deleted = dbQuery {
                TarjetasCreditoTable.deleteWhere { (TarjetasCreditoTable.id eq cardId) and (TarjetasCreditoTable.usuarioId eq userId) }
            }
            if (deleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Tarjeta no encontrada.")
            }
        }
    }
}
