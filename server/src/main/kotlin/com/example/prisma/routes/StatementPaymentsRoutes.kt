package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.ConfirmedStatementDto
import com.example.prisma.api.StatementFields
import com.example.prisma.api.StatementPayment
import com.example.prisma.api.StatementPaymentsDto
import com.example.prisma.api.StatementPaymentsRequest
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.EstadosCuentaConfirmadosTable
import com.example.prisma.models.PagosEstadoTable
import com.example.prisma.models.TarjetasCreditoTable
import com.example.prisma.services.StatementConfirmationValidator
import com.example.prisma.services.StatementPaymentsEngine
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private val statementPaymentsJson = Json {
    ignoreUnknownKeys = true
}

private val statementPaymentsZone =
    ZoneId.of("America/Mexico_City")

private fun ownsPaymentCard(
    cardId: UUID,
    userId: UUID,
): Boolean =
    TarjetasCreditoTable.selectAll().where {
        (TarjetasCreditoTable.id eq cardId) and
                (TarjetasCreditoTable.usuarioId eq userId)
    }.any()

private fun paymentState(
    cardId: UUID,
    period: String,
    lock: Boolean = false,
): ConfirmedStatementDto? {
    val query = EstadosCuentaConfirmadosTable.selectAll().where {
        (EstadosCuentaConfirmadosTable.tarjetaId eq cardId) and
                (EstadosCuentaConfirmadosTable.periodo eq period)
    }

    if (lock) {
        query.forUpdate()
    }

    return query.singleOrNull()?.let {
        ConfirmedStatementDto(
            tarjetaId = cardId.toString(),
            campos = statementPaymentsJson.decodeFromString<StatementFields>(
                it[EstadosCuentaConfirmadosTable.campos],
            ),
            version = it[EstadosCuentaConfirmadosTable.version],
            fechaConfirmacion =
                it[EstadosCuentaConfirmadosTable.fechaConfirmacion].toString(),
        )
    }
}

private fun paymentLedger(
    cardId: UUID,
    period: String,
): ResultRow? =
    PagosEstadoTable.selectAll().where {
        (PagosEstadoTable.tarjetaId eq cardId) and
                (PagosEstadoTable.periodo eq period)
    }.singleOrNull()

private fun paymentSnapshot(
    state: ConfirmedStatementDto,
    ledger: ResultRow?,
    today: LocalDate,
): StatementPaymentsDto {
    val payments = ledger?.let {
        statementPaymentsJson.decodeFromString<List<StatementPayment>>(
            it[PagosEstadoTable.pagos],
        )
    }.orEmpty()

    val anchor = ledger?.get(
        PagosEstadoTable.versionEstado,
    )

    val review = ledger != null && anchor != state.version

    return StatementPaymentsDto(
        estado = state,
        pagos = payments,
        version = ledger?.get(PagosEstadoTable.version) ?: 0,
        versionEstado = anchor,
        fechaReferencia = today.toString(),
        resumen = if (ledger != null && !review) {
            StatementPaymentsEngine.summarize(
                state.campos,
                payments,
                today,
            )
        } else {
            null
        },
        requiereRevision = review,
        fechaActualizacion = ledger
            ?.get(PagosEstadoTable.fechaActualizacion)
            ?.toString(),
    )
}

private sealed interface PaymentWriteResult {
    data object Missing : PaymentWriteResult

    data object Conflict : PaymentWriteResult

    data class Invalid(
        val message: String,
    ) : PaymentWriteResult

    data class Saved(
        val dto: StatementPaymentsDto,
    ) : PaymentWriteResult
}

private fun isPaymentConflict(
    error: Throwable,
): Boolean =
    generateSequence(error) { it.cause }
        .filterIsInstance<SQLException>()
        .any {
            it.sqlState in setOf(
                "40001",
                "40P01",
                "23505",
            )
        }

fun Route.statementPaymentsRoutes(
    sessions: SessionStore,
) {
    route("/api/v1/cards/{cardId}/statements/{periodo}/payments") {
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

            val period = StatementConfirmationValidator.validPeriod(
                call.parameters["periodo"],
            ) ?: return@get call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "Selecciona un periodo válido.",
            )

            val today = LocalDate.now(statementPaymentsZone)

            val dto = dbQuery {
                if (!ownsPaymentCard(cardId, userId)) {
                    return@dbQuery null
                }

                val state = paymentState(
                    cardId,
                    period,
                ) ?: return@dbQuery null

                paymentSnapshot(
                    state,
                    paymentLedger(cardId, period),
                    today,
                )
            } ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ApiErrorCodes.NOT_FOUND,
                "Estado confirmado no encontrado.",
            )

            call.respond(dto)
        }

        put {
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

            val period = StatementConfirmationValidator.validPeriod(
                call.parameters["periodo"],
            ) ?: return@put call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "Selecciona un periodo válido.",
            )

            if (!dbQuery { ownsPaymentCard(cardId, userId) }) {
                return@put call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Estado confirmado no encontrado.",
                )
            }

            val request =
                call.receiveOrBadRequest<StatementPaymentsRequest>()
                    ?: return@put

            if (
                request.version !in 0 until Int.MAX_VALUE ||
                request.versionEstado <= 0
            ) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La versión de los pagos o del estado no es válida.",
                )
            }

            val today = LocalDate.now(statementPaymentsZone)

            val result = try {
                dbQuery {
                    if (!ownsPaymentCard(cardId, userId)) {
                        return@dbQuery PaymentWriteResult.Missing
                    }

                    val state = paymentState(
                        cardId,
                        period,
                        lock = true,
                    ) ?: return@dbQuery PaymentWriteResult.Missing

                    if (request.versionEstado != state.version) {
                        return@dbQuery PaymentWriteResult.Conflict
                    }

                    val payments = try {
                        StatementPaymentsEngine.normalize(
                            state.campos,
                            request,
                            today,
                        )
                    } catch (error: IllegalArgumentException) {
                        return@dbQuery PaymentWriteResult.Invalid(
                            error.message ?: "Revisa los pagos registrados.",
                        )
                    }

                    val payload =
                        statementPaymentsJson.encodeToString(payments)

                    val now = Instant.now()

                    val changed = if (request.version == 0) {
                        PagosEstadoTable.insertIgnore {
                            it[tarjetaId] = cardId
                            it[periodo] = period
                            it[pagos] = payload
                            it[version] = 1
                            it[versionEstado] = state.version
                            it[fechaActualizacion] = now
                        }.insertedCount
                    } else {
                        PagosEstadoTable.update({
                            (PagosEstadoTable.tarjetaId eq cardId) and
                                    (PagosEstadoTable.periodo eq period) and
                                    (PagosEstadoTable.version eq request.version)
                        }) {
                            it[pagos] = payload
                            it[version] = request.version + 1
                            it[versionEstado] = state.version
                            it[fechaActualizacion] = now
                        }
                    }

                    if (changed == 0) {
                        return@dbQuery PaymentWriteResult.Conflict
                    }

                    PaymentWriteResult.Saved(
                        paymentSnapshot(
                            state,
                            paymentLedger(cardId, period),
                            today,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isPaymentConflict(error)) {
                    throw error
                }

                PaymentWriteResult.Conflict
            }

            when (result) {
                PaymentWriteResult.Missing -> call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Estado confirmado no encontrado.",
                )

                PaymentWriteResult.Conflict -> call.respondError(
                    HttpStatusCode.Conflict,
                    "statement_payments_conflict",
                    "El estado o sus pagos cambiaron. " +
                            "Recarga antes de guardar nuevamente.",
                )

                is PaymentWriteResult.Invalid -> call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    result.message,
                )

                is PaymentWriteResult.Saved -> call.respond(
                    result.dto,
                )
            }
        }
    }
}