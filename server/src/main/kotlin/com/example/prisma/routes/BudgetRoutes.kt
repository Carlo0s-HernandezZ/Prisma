package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.BudgetDto
import com.example.prisma.api.BudgetMovement
import com.example.prisma.api.BudgetRequest
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.finance.BudgetEngine
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.PresupuestosTable
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
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

private val budgetZone = ZoneId.of("America/Mexico_City")

private val budgetJson = Json {
    ignoreUnknownKeys = true
}

private val budgetConflictStates = setOf(
    "40001",
    "40P01",
    "23505",
)

private fun validBudgetPeriod(value: String?): String? {
    if (
        value == null ||
        !Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(value)
    ) {
        return null
    }

    return runCatching {
        YearMonth.parse(value)
            .takeIf { it.year in 1..9999 }
            ?.toString()
    }.getOrNull()
}

private fun ResultRow.toBudgetDto(
    today: LocalDate,
): BudgetDto {
    val period = this[PresupuestosTable.periodo]

    val request = BudgetRequest(
        fechaBase = this[
            PresupuestosTable.fechaBase
        ].toString(),
        saldoDisponible = this[
            PresupuestosTable.saldoDisponible
        ].setScale(2).toPlainString(),
        reserva = this[
            PresupuestosTable.reserva
        ].setScale(2).toPlainString(),
        movimientos = budgetJson.decodeFromString<
                List<BudgetMovement>
                >(
            this[PresupuestosTable.movimientos],
        ),
        version = this[PresupuestosTable.version],
    )

    val calculation = BudgetEngine.calculate(
        period,
        request,
        today,
    )

    return BudgetDto(
        periodo = period,
        configurado = true,
        presupuesto = calculation.presupuesto,
        resumen = calculation.resumen,
        fechaActualizacion = this[
            PresupuestosTable.fechaActualizacion
        ].toString(),
    )
}

private fun isBudgetConflict(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .filterIsInstance<SQLException>()
        .any {
            it.sqlState in budgetConflictStates
        }

fun Route.budgetRoutes(sessions: SessionStore) {
    route("/api/v1/budgets") {
        get("/{periodo}") {
            val userId = call.authenticatedUser(sessions)
                ?: return@get

            val period = validBudgetPeriod(
                call.parameters["periodo"],
            ) ?: return@get call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "El periodo debe tener el formato AAAA-MM.",
            )

            val today = LocalDate.now(budgetZone)

            val budget = dbQuery {
                PresupuestosTable
                    .selectAll()
                    .where {
                        (PresupuestosTable.usuarioId eq userId) and
                                (PresupuestosTable.periodo eq period)
                    }
                    .singleOrNull()
                    ?.toBudgetDto(today)
            }

            call.respond(
                budget ?: BudgetDto(
                    periodo = period,
                    configurado = false,
                ),
            )
        }

        put("/{periodo}") {
            val userId = call.authenticatedUser(sessions)
                ?: return@put

            val period = validBudgetPeriod(
                call.parameters["periodo"],
            ) ?: return@put call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "El periodo debe tener el formato AAAA-MM.",
            )

            val request = call.receiveOrBadRequest<
                    BudgetRequest
                    >() ?: return@put

            if (request.version == Int.MAX_VALUE) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Se alcanzó el límite de revisiones de este presupuesto.",
                )
            }

            val today = LocalDate.now(budgetZone)

            val calculation = try {
                BudgetEngine.calculate(
                    period,
                    request,
                    today,
                )
            } catch (error: IllegalArgumentException) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    error.message ?: "Revisa los datos del presupuesto.",
                )
            }

            val normalized = calculation.presupuesto
            val newVersion = normalized.version + 1
            val now = Instant.now()

            val movements = budgetJson.encodeToString(
                normalized.movimientos,
            )

            val saved = try {
                dbQuery {
                    val changed = if (normalized.version == 0) {
                        PresupuestosTable.insertIgnore {
                            it[usuarioId] = userId
                            it[periodo] = period
                            it[fechaBase] = LocalDate.parse(
                                normalized.fechaBase,
                            )
                            it[saldoDisponible] = BigDecimal(
                                normalized.saldoDisponible,
                            )
                            it[reserva] = BigDecimal(
                                normalized.reserva,
                            )
                            it[movimientos] = movements
                            it[version] = newVersion
                            it[fechaActualizacion] = now
                        }.insertedCount
                    } else {
                        PresupuestosTable.update({
                            (PresupuestosTable.usuarioId eq userId) and
                                    (PresupuestosTable.periodo eq period) and
                                    (PresupuestosTable.version eq normalized.version)
                        }) {
                            it[fechaBase] = LocalDate.parse(
                                normalized.fechaBase,
                            )
                            it[saldoDisponible] = BigDecimal(
                                normalized.saldoDisponible,
                            )
                            it[reserva] = BigDecimal(
                                normalized.reserva,
                            )
                            it[movimientos] = movements
                            it[version] = newVersion
                            it[fechaActualizacion] = now
                        }
                    }

                    if (changed == 0) {
                        return@dbQuery null
                    }

                    PresupuestosTable
                        .selectAll()
                        .where {
                            (PresupuestosTable.usuarioId eq userId) and
                                    (PresupuestosTable.periodo eq period)
                        }
                        .single()
                        .toBudgetDto(today)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isBudgetConflict(error)) {
                    throw error
                }

                null
            }

            if (saved == null) {
                call.respondError(
                    HttpStatusCode.Conflict,
                    "budget_conflict",
                    "El presupuesto cambió en otra ventana. Recárgalo antes de guardar.",
                )
            } else {
                call.respond(saved)
            }
        }
    }
}