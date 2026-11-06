package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.CreateIncomeRequest
import com.example.prisma.api.IncomeDto
import com.example.prisma.api.IncomeRules
import com.example.prisma.api.IncomesSummary
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.IngresosTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val MAX_AMOUNT = BigDecimal(IncomeRules.MAX_AMOUNT)
private val ISO_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/**
 * Convierte el texto del cliente en un monto válido para NUMERIC(12,2) con CHECK (monto_neto > 0).
 * Devuelve null si no es un número positivo, tiene más de 2 decimales o excede el máximo.
 */
internal fun parseAmount(raw: String): BigDecimal? {
    val amount = raw.trim().replace(",", "").toBigDecimalOrNull() ?: return null
    if (amount.signum() <= 0 || amount.scale() > 2 || amount > MAX_AMOUNT) return null
    return amount.setScale(2, RoundingMode.UNNECESSARY)
}

/** Nombre del ingreso sin espacios sobrantes; null si queda vacío o excede VARCHAR(100). */
internal fun parseIncomeName(raw: String): String? =
    raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() && it.length <= IncomeRules.MAX_NAME_LENGTH }

private fun ResultRow.toIncomeDto(): IncomeDto {
    val registro = this[IngresosTable.fechaRegistro]
        ?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES) }
    return IncomeDto(
        id = this[IngresosTable.id].toString(),
        nombre = this[IngresosTable.nombre],
        montoNeto = this[IngresosTable.montoNeto].setScale(2).toPlainString(),
        esAuditado = this[IngresosTable.esAuditado],
        fechaRegistro = registro?.format(ISO_MINUTES).orEmpty(),
    )
}

/** Ingresos del usuario autenticado. Nunca se aceptan ni devuelven ingresos de otros usuarios. */
fun Route.incomeRoutes(sessions: SessionStore) {
    route("/api/v1/incomes") {

        get {
            val userId = call.authenticatedUser(sessions) ?: return@get
            val rows = dbQuery {
                IngresosTable.selectAll()
                    .where { IngresosTable.usuarioId eq userId }
                    .orderBy(IngresosTable.fechaRegistro to SortOrder.DESC_NULLS_LAST)
                    .map { it[IngresosTable.montoNeto] to it.toIncomeDto() }
            }

            val now = LocalDateTime.now()
            val monthPrefix = "%04d-%02d".format(now.year, now.monthValue)
            val totalMes = rows
                .filter { (_, dto) -> dto.fechaRegistro.startsWith(monthPrefix) }
                .fold(BigDecimal.ZERO) { acc, (monto, _) -> acc + monto }
                .setScale(2)

            call.respond(IncomesSummary(ingresos = rows.map { it.second }, totalMes = totalMes.toPlainString()))
        }

        post {
            val userId = call.authenticatedUser(sessions) ?: return@post
            val req = call.receiveOrBadRequest<CreateIncomeRequest>() ?: return@post
            val nombre = parseIncomeName(req.nombre)
                ?: return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El nombre del ingreso es obligatorio (máximo ${IncomeRules.MAX_NAME_LENGTH} caracteres).",
                )
            val amount = parseAmount(req.montoNeto)
                ?: return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "El monto debe ser mayor a 0, con máximo 2 decimales y hasta ${IncomeRules.MAX_AMOUNT}.",
                )

            val newId = UUID.randomUUID()
            val created = dbQuery {
                IngresosTable.insert {
                    it[id] = newId
                    it[usuarioId] = userId
                    it[IngresosTable.nombre] = nombre
                    it[montoNeto] = amount
                    it[esAuditado] = false
                }
                IngresosTable.selectAll().where { IngresosTable.id eq newId }.single().toIncomeDto()
            }
            call.respond(HttpStatusCode.Created, created)
        }

        delete("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@delete
            val incomeId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Id inválido.")

            // El filtro por usuario impide borrar ingresos ajenos aunque se conozca su id.
            val deleted = dbQuery {
                IngresosTable.deleteWhere { (IngresosTable.id eq incomeId) and (IngresosTable.usuarioId eq userId) }
            }
            if (deleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Ingreso no encontrado.")
            }
        }
    }
}
