package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.IncomeFrequency
import com.example.prisma.api.IncomePlanningDto
import com.example.prisma.api.IncomePlanningRequest
import com.example.prisma.api.IncomePlanningSummary
import com.example.prisma.api.IncomeType
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.IngresosPlanificacionTable
import com.example.prisma.models.IngresosTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val INCOME_PLANNING_ZONE =
    ZoneId.of("America/Mexico_City")

private fun ResultRow.toIncomePlanningDto(): IncomePlanningDto {
    return IncomePlanningDto(
        ingresoId = this[IngresosTable.id].toString(),
        nombre = this[IngresosTable.nombre],
        montoNeto = this[IngresosTable.montoNeto]
            .setScale(2)
            .toPlainString(),
        esAuditado = this[IngresosTable.esAuditado],
        fechaCobro = this[
            IngresosPlanificacionTable.fechaCobro
        ].toString(),
        tipoIngreso = IncomeType.valueOf(
            this[IngresosPlanificacionTable.tipoIngreso],
        ),
        frecuencia = this[
            IngresosPlanificacionTable.frecuencia
        ]?.let { storedFrequency ->
            IncomeFrequency.valueOf(storedFrequency)
        },
        fechaActualizacion = this[
            IngresosPlanificacionTable.fechaActualizacion
        ].toString(),
    )
}

fun Route.incomePlanningRoutes(sessions: SessionStore) {
    route("/api/v1/income-planning") {

        get {
            val userId = call.authenticatedUser(sessions)
                ?: return@get

            val summary = dbQuery {
                val configured = (
                        IngresosTable innerJoin
                                IngresosPlanificacionTable
                        )
                    .selectAll()
                    .where {
                        IngresosTable.usuarioId eq userId
                    }
                    .orderBy(
                        IngresosPlanificacionTable.fechaCobro to
                                SortOrder.DESC,
                        IngresosTable.id to SortOrder.ASC,
                    )
                    .map { row ->
                        row.toIncomePlanningDto()
                    }

                val totalIncomes = IngresosTable
                    .selectAll()
                    .where {
                        IngresosTable.usuarioId eq userId
                    }
                    .count()

                IncomePlanningSummary(
                    ingresos = configured,
                    sinConfigurar = totalIncomes -
                            configured.size.toLong(),
                )
            }

            call.respond(summary)
        }

        put("/{incomeId}") {
            val userId = call.authenticatedUser(sessions)
                ?: return@put

            val incomeId = call.parameters["incomeId"]
                ?.let { rawId ->
                    runCatching {
                        UUID.fromString(rawId)
                    }.getOrNull()
                }
                ?: return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "El identificador del ingreso no es válido.",
                )

            val request = call.receiveOrBadRequest<
                    IncomePlanningRequest
                    >() ?: return@put

            val paymentDate = runCatching {
                LocalDate.parse(request.fechaCobro.trim())
            }.getOrNull()

            if (
                paymentDate == null ||
                paymentDate.year !in 1..9999
            ) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La fecha de cobro debe tener el formato AAAA-MM-DD.",
                )
            }

            val today = LocalDate.now(INCOME_PLANNING_ZONE)

            if (paymentDate.isAfter(today)) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Este registro corresponde a un ingreso recibido. " +
                            "La fecha de cobro no puede ser futura.",
                )
            }

            if (
                request.tipoIngreso == IncomeType.RECURRENTE &&
                request.frecuencia == null
            ) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Selecciona la frecuencia del ingreso recurrente.",
                )
            }

            if (
                request.tipoIngreso == IncomeType.EXTRAORDINARIO &&
                request.frecuencia != null
            ) {
                return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Un ingreso extraordinario no debe tener frecuencia.",
                )
            }

            val saved = dbQuery {
                val ownedIncomeExists = IngresosTable
                    .selectAll()
                    .where {
                        (IngresosTable.id eq incomeId) and
                                (IngresosTable.usuarioId eq userId)
                    }
                    .any()

                if (!ownedIncomeExists) {
                    return@dbQuery null
                }

                IngresosPlanificacionTable.upsert(
                    IngresosPlanificacionTable.ingresoId,
                ) { statement ->
                    statement[
                        IngresosPlanificacionTable.ingresoId
                    ] = incomeId

                    statement[
                        IngresosPlanificacionTable.fechaCobro
                    ] = paymentDate

                    statement[
                        IngresosPlanificacionTable.tipoIngreso
                    ] = request.tipoIngreso.name

                    statement[
                        IngresosPlanificacionTable.frecuencia
                    ] = request.frecuencia?.name

                    statement[
                        IngresosPlanificacionTable.fechaActualizacion
                    ] = Instant.now()
                }

                (
                        IngresosTable innerJoin
                                IngresosPlanificacionTable
                        )
                    .selectAll()
                    .where {
                        (IngresosTable.id eq incomeId) and
                                (IngresosTable.usuarioId eq userId)
                    }
                    .single()
                    .toIncomePlanningDto()
            }

            if (saved == null) {
                call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Ingreso no encontrado.",
                )
            } else {
                call.respond(saved)
            }
        }
    }
}