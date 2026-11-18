package com.example.prisma.routes

import com.example.prisma.ai.PayrollAuditEngine
import com.example.prisma.ai.PayrollPdfParser
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.HomeMessageDto
import com.example.prisma.api.HomeMessageType
import com.example.prisma.api.PayrollAuditDto
import com.example.prisma.api.PayrollAuditListResponse
import com.example.prisma.api.ReminderDto
import com.example.prisma.api.ReminderFrequency
import com.example.prisma.api.ReminderKind
import com.example.prisma.api.ReminderSchedule
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.AuditoriasNominaTable
import com.example.prisma.models.RecordatoriosTable
import com.example.prisma.models.TarjetasCreditoTable
import com.example.prisma.security.DataCipher
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

private val auditJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val mexicoZone: ZoneId = ZoneId.of("America/Mexico_City")
private const val MAX_PAYROLL_BYTES = 10 * 1024 * 1024

fun Route.payrollAuditRoutes(sessions: SessionStore) {

    route("/api/v1/payroll/audits") {

        // Sube un recibo de nómina (PDF) o su CFDI (XML) y devuelve el desglose auditado.
        post {
            val userId = call.authenticatedUser(sessions) ?: return@post
            var bytes: ByteArray? = null
            var tooBig = false
            try {
                call.receiveMultipart(formFieldLimit = MAX_PAYROLL_BYTES + 1L).forEachPart { part ->
                    try {
                        if (part is PartData.FileItem && bytes == null) {
                            val read = part.provider().toInputStream().use { it.readNBytes(MAX_PAYROLL_BYTES + 1) }
                            if (read.size > MAX_PAYROLL_BYTES) tooBig = true else bytes = read
                        }
                    } finally {
                        part.release()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "No se pudo recibir el documento.")
            }
            if (tooBig) return@post call.respondError(HttpStatusCode.PayloadTooLarge, ApiErrorCodes.INVALID_REQUEST, "El archivo puede pesar hasta 10 MB.")
            val file = bytes?.takeIf { it.isNotEmpty() }
                ?: return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Selecciona el PDF o el XML de tu nómina.")

            val isPdf = file.size > 4 && String(file, 0, 5, Charsets.ISO_8859_1) == "%PDF-"
            val head = String(file, 0, minOf(file.size, 300), Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
            val isXml = head.startsWith("<?xml") || head.startsWith("<cfdi:") || head.startsWith("<Comprobante")
            if (!isPdf && !isXml) {
                return@post call.respondError(HttpStatusCode.UnsupportedMediaType, ApiErrorCodes.INVALID_REQUEST, "Solo se aceptan el PDF del recibo o el XML (CFDI) de la nómina.")
            }

            val fingerprint = MessageDigest.getInstance("SHA-256").digest(file).joinToString("") { "%02x".format(it) }
            dbQuery { PayrollStore.byFingerprint(userId, fingerprint) }?.let { return@post call.respond(it) }

            val parsed = try {
                runInterruptible(Dispatchers.IO) {
                    if (isXml) {
                        PayrollAuditEngine.fromXml(file)
                    } else {
                        val pdf = PayrollPdfParser().extraer(file)
                        require(!pdf.requiereOcr) { "El PDF es una imagen escaneada. Sube el PDF original o el XML de tu nómina." }
                        PayrollAuditEngine.fromPdf(pdf.textoExtraido)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message ?: "No se pudo leer la nómina.")
            } catch (_: Exception) {
                return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "No se pudo leer la nómina. Verifica que sea el PDF o XML correcto.")
            }
            if (parsed.conceptos.isEmpty() && parsed.netoDeclarado == null) {
                return@post call.respondError(HttpStatusCode.UnprocessableEntity, ApiErrorCodes.INVALID_REQUEST,
                    "No encontramos conceptos de nómina en el documento. Si tienes el XML (CFDI), súbelo.")
            }

            val reference = parsed.fechaPago ?: parsed.fechaInicial ?: parsed.periodo?.let { "$it-01" } ?: LocalDate.now(mexicoZone).toString()
            val saved = dbQuery {
                val previous = PayrollStore.previous(userId, reference)
                val audit = PayrollAuditEngine.audit(parsed, previous)
                PayrollStore.save(userId, fingerprint, reference, audit)
            }
            call.respond(saved)
        }

        get {
            val userId = call.authenticatedUser(sessions) ?: return@get
            call.respond(PayrollAuditListResponse(dbQuery { PayrollStore.list(userId) }))
        }

        delete("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@delete
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "La auditoría no es válida.")
            val deleted = dbQuery { AuditoriasNominaTable.deleteWhere { (AuditoriasNominaTable.id eq id) and (AuditoriasNominaTable.usuarioId eq userId) } }
            if (deleted > 0) call.respond(HttpStatusCode.NoContent)
            else call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Auditoría no encontrada.")
        }
    }

    // ---------- Recordatorios ----------
    route("/api/v1/reminders") {
        get {
            val userId = call.authenticatedUser(sessions) ?: return@get
            call.respond(dbQuery { ReminderStore.list(userId) })
        }

        post {
            val userId = call.authenticatedUser(sessions) ?: return@post
            val body = call.receiveOrBadRequest<ReminderDto>() ?: return@post
            ReminderSchedule.validate(body)?.let { return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, it) }
            val saved = dbQuery { ReminderStore.upsert(userId, null, body) }
                ?: return@post call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Tarjeta no encontrada.")
            call.respond(HttpStatusCode.Created, saved)
        }

        put("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@put
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: return@put call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "El recordatorio no es válido.")
            val body = call.receiveOrBadRequest<ReminderDto>() ?: return@put
            ReminderSchedule.validate(body)?.let { return@put call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, it) }
            val saved = dbQuery { ReminderStore.upsert(userId, id, body) }
                ?: return@put call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Recordatorio no encontrado.")
            call.respond(saved)
        }

        delete("/{id}") {
            val userId = call.authenticatedUser(sessions) ?: return@delete
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: return@delete call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "El recordatorio no es válido.")
            val deleted = dbQuery { RecordatoriosTable.deleteWhere { (RecordatoriosTable.id eq id) and (RecordatoriosTable.usuarioId eq userId) } }
            if (deleted > 0) call.respond(HttpStatusCode.NoContent)
            else call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Recordatorio no encontrado.")
        }
    }
}

/** Consultas de auditorías de nómina (dentro de dbQuery, siempre filtradas por usuario). */
internal object PayrollStore {

    private fun ResultRow.toDto(): PayrollAuditDto =
        auditJson.decodeFromString<PayrollAuditDto>(DataCipher.decrypt(this[AuditoriasNominaTable.resultado])).copy(
            id = this[AuditoriasNominaTable.id].toString(),
            fechaAnalisis = LocalDateTime.ofInstant(this[AuditoriasNominaTable.fechaAnalisis], mexicoZone).truncatedTo(ChronoUnit.MINUTES).toString(),
        )

    fun byFingerprint(userId: UUID, fingerprint: String): PayrollAuditDto? =
        AuditoriasNominaTable.selectAll().where {
            (AuditoriasNominaTable.usuarioId eq userId) and (AuditoriasNominaTable.huella eq fingerprint)
        }.singleOrNull()?.let { runCatching { it.toDto() }.getOrNull() }

    fun list(userId: UUID): List<PayrollAuditDto> =
        AuditoriasNominaTable.selectAll().where { AuditoriasNominaTable.usuarioId eq userId }
            .sortedByDescending { it[AuditoriasNominaTable.fechaReferencia] }
            .mapNotNull { runCatching { it.toDto() }.getOrNull() }

    /** La nómina más reciente anterior a [reference] ("AAAA-MM-DD"), para comparar. */
    fun previous(userId: UUID, reference: String): PayrollAuditDto? =
        AuditoriasNominaTable.selectAll().where { AuditoriasNominaTable.usuarioId eq userId }
            .filter { it[AuditoriasNominaTable.fechaReferencia] < reference }
            .maxByOrNull { it[AuditoriasNominaTable.fechaReferencia] }
            ?.let { runCatching { it.toDto() }.getOrNull() }

    fun save(userId: UUID, fingerprint: String, reference: String, audit: PayrollAuditDto): PayrollAuditDto {
        val id = UUID.randomUUID()
        AuditoriasNominaTable.insert {
            it[AuditoriasNominaTable.id] = id
            it[usuarioId] = userId
            it[periodo] = audit.periodo.take(7)
            it[fechaReferencia] = reference.take(10)
            it[fuente] = audit.fuente
            it[huella] = fingerprint
            it[resultado] = DataCipher.encrypt(auditJson.encodeToString(audit))
            it[neto] = audit.netoCalculado.toBigDecimal()
            it[alertas] = audit.alertas.count { a -> a.severidad != AlertSeverity.BAJA }
            it[fechaAnalisis] = Instant.now()
        }
        return AuditoriasNominaTable.selectAll().where { AuditoriasNominaTable.id eq id }.single().toDto()
    }

    /** Mensaje de Inicio si la nómina más reciente (últimos 45 días) tiene irregularidades. */
    fun homeMessage(userId: UUID, today: LocalDate): HomeMessageDto? {
        val latest = AuditoriasNominaTable.selectAll().where { AuditoriasNominaTable.usuarioId eq userId }
            .maxByOrNull { it[AuditoriasNominaTable.fechaReferencia] } ?: return null
        if (latest[AuditoriasNominaTable.alertas] == 0) return null
        val analyzed = LocalDate.ofInstant(latest[AuditoriasNominaTable.fechaAnalisis], mexicoZone)
        if (ChronoUnit.DAYS.between(analyzed, today) > 45) return null
        val audit = runCatching { latest.toDto() }.getOrNull() ?: return null
        val first = audit.alertas.first()
        val count = audit.alertas.count { it.severidad != AlertSeverity.BAJA }
        return HomeMessageDto(
            tipo = HomeMessageType.NOMINA,
            titulo = "Revisa tu nómina${if (count > 1) ": $count puntos" else ""}",
            texto = "${first.titulo}. ${first.detalle}",
            urgente = audit.alertas.any { it.severidad == AlertSeverity.ALTA },
        )
    }
}

/** Consultas de recordatorios. */
internal object ReminderStore {

    private fun ResultRow.toDto() = ReminderDto(
        id = this[RecordatoriosTable.id].toString(),
        tipo = ReminderKind.valueOf(this[RecordatoriosTable.tipo]),
        tarjetaId = this[RecordatoriosTable.tarjetaId]?.toString(),
        titulo = this[RecordatoriosTable.titulo],
        frecuencia = ReminderFrequency.valueOf(this[RecordatoriosTable.frecuencia]),
        dia = this[RecordatoriosTable.dia],
        dia2 = this[RecordatoriosTable.dia2],
        fechaInicio = this[RecordatoriosTable.fechaInicio],
        hora = this[RecordatoriosTable.hora],
        activo = this[RecordatoriosTable.activo],
    )

    fun list(userId: UUID): List<ReminderDto> =
        RecordatoriosTable.selectAll().where { RecordatoriosTable.usuarioId eq userId }
            .sortedBy { it[RecordatoriosTable.fechaCreacion] }
            .map { it.toDto() }

    /** Crea (id null) o actualiza un recordatorio. Null si la tarjeta o el recordatorio no son del usuario. */
    fun upsert(userId: UUID, id: UUID?, r: ReminderDto): ReminderDto? {
        val card = if (r.tipo == ReminderKind.ESTADO_CUENTA) {
            val cardId = runCatching { UUID.fromString(r.tarjetaId) }.getOrNull() ?: return null
            TarjetasCreditoTable.selectAll().where { (TarjetasCreditoTable.id eq cardId) and (TarjetasCreditoTable.usuarioId eq userId) }
                .singleOrNull() ?: return null
            cardId
        } else {
            null
        }
        val target = id ?: UUID.randomUUID()
        if (id == null) {
            RecordatoriosTable.insert {
                it[RecordatoriosTable.id] = target
                it[usuarioId] = userId
                fill(it, r, card)
            }
        } else {
            val updated = RecordatoriosTable.update({ (RecordatoriosTable.id eq id) and (RecordatoriosTable.usuarioId eq userId) }) { fill(it, r, card) }
            if (updated == 0) return null
        }
        return RecordatoriosTable.selectAll().where { RecordatoriosTable.id eq target }.single().toDto()
    }

    private fun fill(it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, r: ReminderDto, card: UUID?) {
        it[RecordatoriosTable.tipo] = r.tipo.name
        it[RecordatoriosTable.tarjetaId] = card
        it[RecordatoriosTable.titulo] = r.titulo.trim().take(80)
        it[RecordatoriosTable.frecuencia] = r.frecuencia.name
        it[RecordatoriosTable.dia] = r.dia
        it[RecordatoriosTable.dia2] = r.dia2
        it[RecordatoriosTable.fechaInicio] = r.fechaInicio?.take(10)
        it[RecordatoriosTable.hora] = r.hora
        it[RecordatoriosTable.activo] = r.activo
    }

    /** Mensajes de Inicio para los recordatorios de hoy y de mañana. */
    fun homeMessages(userId: UUID, today: LocalDate): List<HomeMessageDto> {
        val todayDay = ReminderSchedule.Day(today.year, today.monthValue, today.dayOfMonth)
        val tomorrow = today.plusDays(1).let { ReminderSchedule.Day(it.year, it.monthValue, it.dayOfMonth) }
        return list(userId).filter { it.activo }.mapNotNull { r ->
            val whenText = when {
                ReminderSchedule.occursOn(r, todayDay) -> "hoy"
                ReminderSchedule.occursOn(r, tomorrow) -> "mañana"
                else -> return@mapNotNull null
            }
            val what = if (r.tipo == ReminderKind.NOMINA) "tu nómina en Tarjetas → Auditoría de nómina" else "tu estado de cuenta en Análisis"
            HomeMessageDto(
                tipo = HomeMessageType.RECORDATORIO,
                titulo = r.titulo,
                texto = "Recordatorio para $whenText: sube $what.",
                tarjetaId = r.tarjetaId,
                urgente = false,
            )
        }
    }
}
