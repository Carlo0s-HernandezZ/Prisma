package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.ConfirmedStatementDto
import com.example.prisma.api.StatementConfirmationRequest
import com.example.prisma.api.StatementFields
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.EstadosCuentaConfirmadosTable
import com.example.prisma.models.TarjetasCreditoTable
import com.example.prisma.services.StatementConfirmationValidator
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private val confirmedStatementJson = Json {
    ignoreUnknownKeys = true
}

private fun ownsStatementCard(
    cardId: UUID,
    userId: UUID,
): Boolean =
    TarjetasCreditoTable.selectAll().where {
        (TarjetasCreditoTable.id eq cardId) and
                (TarjetasCreditoTable.usuarioId eq userId)
    }.any()

private fun ResultRow.toConfirmedStatement(): ConfirmedStatementDto =
    ConfirmedStatementDto(
        tarjetaId = this[EstadosCuentaConfirmadosTable.tarjetaId].toString(),
        campos = confirmedStatementJson.decodeFromString<StatementFields>(
            this[EstadosCuentaConfirmadosTable.campos],
        ),
        version = this[EstadosCuentaConfirmadosTable.version],
        fechaConfirmacion =
            this[EstadosCuentaConfirmadosTable.fechaConfirmacion].toString(),
    )

private fun isStatementConflict(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .filterIsInstance<SQLException>()
        .any {
            it.sqlState in setOf(
                "40001",
                "40P01",
                "23505",
            )
        }

fun Route.statementConfirmationRoutes(
    sessions: SessionStore,
) {
    route("/api/v1/cards/{cardId}/statements") {
        get {
            val userId = call.authenticatedUser(sessions)
                ?: return@get

            val cardId = runCatching {
                UUID.fromString(call.parameters["cardId"])
            }.getOrNull()
                ?: return@get call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La tarjeta no es válida.",
                )

            val records = dbQuery {
                if (!ownsStatementCard(cardId, userId)) {
                    return@dbQuery null
                }

                EstadosCuentaConfirmadosTable.selectAll().where {
                    EstadosCuentaConfirmadosTable.tarjetaId eq cardId
                }.orderBy(
                    EstadosCuentaConfirmadosTable.periodo to SortOrder.DESC,
                ).map {
                    it.toConfirmedStatement()
                }
            } ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ApiErrorCodes.NOT_FOUND,
                "Tarjeta no encontrada.",
            )

            call.respond(records)
        }

        put("/{periodo}") {
            val userId = call.authenticatedUser(sessions)
                ?: return@put

            val cardId = runCatching {
                UUID.fromString(call.parameters["cardId"])
            }.getOrNull()
                ?: return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La tarjeta no es válida.",
                )

            if (!dbQuery { ownsStatementCard(cardId, userId) }) {
                return@put call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Tarjeta no encontrada.",
                )
            }

            val period = StatementConfirmationValidator.validPeriod(
                call.parameters["periodo"],
            ) ?: return@put call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "El periodo debe tener el formato AAAA-MM.",
            )

            val request =
                call.receiveOrBadRequest<StatementConfirmationRequest>()
                    ?: return@put

            val fields = try {
                StatementConfirmationValidator.normalize(
                    period,
                    request,
                )
            } catch (error: IllegalArgumentException) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    error.message ?: "Revisa los datos del estado de cuenta.",
                )
            }

            val now = Instant.now()
            val payload = confirmedStatementJson.encodeToString(fields)

            var cardMissing = false

            val saved = try {
                dbQuery {
                    if (!ownsStatementCard(cardId, userId)) {
                        cardMissing = true
                        return@dbQuery null
                    }

                    val changed = if (request.version == 0) {
                        EstadosCuentaConfirmadosTable.insertIgnore {
                            it[tarjetaId] = cardId
                            it[periodo] = period
                            it[campos] = payload
                            it[version] = 1
                            it[fechaConfirmacion] = now
                        }.insertedCount
                    } else {
                        EstadosCuentaConfirmadosTable.update({
                            (EstadosCuentaConfirmadosTable.tarjetaId eq cardId) and
                                    (EstadosCuentaConfirmadosTable.periodo eq period) and
                                    (EstadosCuentaConfirmadosTable.version eq request.version)
                        }) {
                            it[campos] = payload
                            it[version] = request.version + 1
                            it[fechaConfirmacion] = now
                        }
                    }

                    if (changed == 0) {
                        return@dbQuery null
                    }

                    EstadosCuentaConfirmadosTable.selectAll().where {
                        (EstadosCuentaConfirmadosTable.tarjetaId eq cardId) and
                                (EstadosCuentaConfirmadosTable.periodo eq period)
                    }.single().toConfirmedStatement()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isStatementConflict(error)) {
                    throw error
                }
                null
            }

            when {
                cardMissing -> call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Tarjeta no encontrada.",
                )

                saved == null -> call.respondError(
                    HttpStatusCode.Conflict,
                    "statement_conflict",
                    "Este estado cambió en otra ventana. " +
                            "Recarga los datos guardados antes de actualizarlo.",
                )

                else -> call.respond(saved)
            }
        }
    }
}