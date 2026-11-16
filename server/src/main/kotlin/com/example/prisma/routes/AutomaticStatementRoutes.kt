package com.example.prisma.routes

import com.example.prisma.api.*
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.finance.CardPlanEngine
import com.example.prisma.finance.PlanCardInput
import com.example.prisma.http.UploadValidation
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.*
import com.example.prisma.services.AutomaticStatementParser
import com.example.prisma.services.StatementConfirmationValidator
import com.example.prisma.services.StatementDocumentReader
import com.example.prisma.services.StatementInputFile
import com.example.prisma.services.StatementOcrUnavailable
import com.example.prisma.services.StatementPaymentsEngine
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

internal val automaticJson = Json {
    ignoreUnknownKeys = true
}

internal fun ownsAutomaticCard(
    cardId: UUID,
    userId: UUID,
    lock: Boolean = false,
): Boolean {
    val query = TarjetasCreditoTable.selectAll().where {
        (TarjetasCreditoTable.id eq cardId) and
                (TarjetasCreditoTable.usuarioId eq userId)
    }

    if (lock) query.forUpdate()
    return query.any()
}

internal fun automaticParent(
    cardId: UUID,
    period: String,
    lock: Boolean = false,
): ConfirmedStatementDto? {
    val query = EstadosCuentaConfirmadosTable.selectAll().where {
        (EstadosCuentaConfirmadosTable.tarjetaId eq cardId) and
                (EstadosCuentaConfirmadosTable.periodo eq period)
    }

    if (lock) query.forUpdate()

    return query.singleOrNull()?.let {
        ConfirmedStatementDto(
            cardId.toString(),
            automaticJson.decodeFromString<StatementFields>(
                it[EstadosCuentaConfirmadosTable.campos],
            ),
            it[EstadosCuentaConfirmadosTable.version],
            it[EstadosCuentaConfirmadosTable.fechaConfirmacion].toString(),
        )
    }
}

internal fun ResultRow.toAutomaticDto(
    checkParent: Boolean = true,
): AutomaticStatementDto {
    var detection = automaticJson.decodeFromString<StatementDetection>(
        this[LecturasEstadoTable.deteccion],
    )

    val anchor = this[LecturasEstadoTable.versionFinanciera]

    if (
        checkParent &&
        detection.estado == StatementDetectionStatus.LISTO &&
        anchor != null
    ) {
        val parent = detection.campos.periodo?.let {
            automaticParent(this[LecturasEstadoTable.tarjetaId], it)
        }

        if (
            parent == null ||
            parent.version != anchor ||
            parent.campos != detection.campos
        ) {
            detection = detection.copy(
                estado = StatementDetectionStatus.REQUIERE_DATO,
                pregunta = "Los datos guardados cambiaron. Utiliza el documento actualizado.",
                opcionesPago = emptyList(),
            )
        }
    }

    return AutomaticStatementDto(
        id = this[LecturasEstadoTable.id].toString(),
        tarjetaId = this[LecturasEstadoTable.tarjetaId].toString(),
        version = this[LecturasEstadoTable.version],
        versionFinanciera = anchor,
        deteccion = detection,
        fechaActualizacion = this[LecturasEstadoTable.fechaActualizacion].toString(),
    )
}

internal fun promoteAutomatic(
    cardId: UUID,
    input: StatementDetection,
): Pair<StatementDetection, Int?> {
    if (input.estado != StatementDetectionStatus.LISTO) return input to null

    val fields = try {
        StatementConfirmationValidator.normalizeDetected(
            input.campos.periodo.orEmpty(),
            input.campos,
        )
    } catch (error: IllegalArgumentException) {
        return input.copy(
            estado = StatementDetectionStatus.REQUIERE_DATO,
            pregunta = error.message
                ?: "Agrega una imagen clara del resumen de pagos.",
            opcionesPago = emptyList(),
        ) to null
    }

    val period = fields.periodo!!
    val current = automaticParent(cardId, period, lock = true)
    val detection = input.copy(campos = fields)

    if (current?.campos == fields) return detection to current.version

    if (
        current != null &&
        current.campos.periodo == fields.periodo &&
        current.campos.fechaCorte == fields.fechaCorte &&
        current.campos.fechaLimitePago == fields.fechaLimitePago &&
        current.campos.pagoMinimo == fields.pagoMinimo &&
        current.campos.pagoParaNoGenerarIntereses == fields.pagoParaNoGenerarIntereses &&
        (
                current.campos.mensualidadesMsi == null ||
                        fields.mensualidadesMsi == null ||
                        current.campos.mensualidadesMsi == fields.mensualidadesMsi
                )
    ) {
        return detection.copy(
            campos = current.campos,
            avisos = (
                    detection.avisos +
                            "Se conservaron los datos financieros previamente verificados."
                    ).distinct(),
        ) to current.version
    }

    if (current != null) {
        val previousAutomatic = LecturasEstadoTable.selectAll().where {
            (LecturasEstadoTable.tarjetaId eq cardId) and
                    (LecturasEstadoTable.periodo eq period) and
                    (LecturasEstadoTable.versionFinanciera eq current.version)
        }.any { row ->
            val prior = automaticJson.decodeFromString<StatementDetection>(
                row[LecturasEstadoTable.deteccion],
            )

            prior.estado == StatementDetectionStatus.LISTO &&
                    prior.campos == current.campos
        }

        if (!previousAutomatic || current.version == Int.MAX_VALUE) {
            return detection.copy(
                estado = StatementDetectionStatus.REQUIERE_DATO,
                pregunta = "El estado guardado difiere. Se conservan sus datos verificados; utiliza el documento actualizado.",
                opcionesPago = emptyList(),
            ) to null
        }
    }

    val newVersion = (current?.version ?: 0) + 1
    val encoded = automaticJson.encodeToString(fields)
    val now = Instant.now()

    if (current == null) {
        EstadosCuentaConfirmadosTable.insert {
            it[tarjetaId] = cardId
            it[periodo] = period
            it[campos] = encoded
            it[version] = newVersion
            it[fechaConfirmacion] = now
        }
    } else {
        check(
            EstadosCuentaConfirmadosTable.update({
                (EstadosCuentaConfirmadosTable.tarjetaId eq cardId) and
                        (EstadosCuentaConfirmadosTable.periodo eq period) and
                        (EstadosCuentaConfirmadosTable.version eq current.version)
            }) {
                it[campos] = encoded
                it[version] = newVersion
                it[fechaConfirmacion] = now
            } == 1,
        )
    }

    return detection to newVersion
}

internal fun automaticConflict(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .filterIsInstance<SQLException>()
        .any { it.sqlState in setOf("40001", "40P01", "23505") }

private val automaticReaders = Semaphore(2)

private class AutomaticUploadError(
    val status: HttpStatusCode,
    message: String,
) : Exception(message)

private suspend fun ApplicationCall.automaticFiles(): List<StatementInputFile> {
    val files = mutableListOf<StatementInputFile>()
    var total = 0

    receiveMultipart(
        formFieldLimit = 20L * 1024 * 1024,
    ).forEachPart { part ->
        try {
            if (part !is PartData.FileItem || files.size >= 12) {
                throw AutomaticUploadError(
                    HttpStatusCode.BadRequest,
                    "Selecciona hasta 12 archivos del mismo estado.",
                )
            }

            val limit = minOf(
                10 * 1024 * 1024,
                20 * 1024 * 1024 - total,
            )

            val bytes = part.provider().toInputStream().use {
                it.readNBytes(limit + 1)
            }

            if (bytes.size > limit) {
                throw AutomaticUploadError(
                    HttpStatusCode.PayloadTooLarge,
                    "El límite es 10 MB por archivo y 20 MB en total.",
                )
            }

            val mime = UploadValidation.detectMime(bytes)

            if (mime !in setOf("application/pdf", "image/jpeg", "image/png")) {
                throw AutomaticUploadError(
                    HttpStatusCode.UnsupportedMediaType,
                    "Selecciona PDF, JPG o PNG.",
                )
            }

            if (files.any { it.bytes.contentEquals(bytes) }) {
                throw AutomaticUploadError(
                    HttpStatusCode.BadRequest,
                    "Elimina los archivos repetidos de la selección.",
                )
            }

            files += StatementInputFile(bytes, requireNotNull(mime))
            total += bytes.size
        } finally {
            part.release()
        }
    }

    if (files.isEmpty()) {
        throw AutomaticUploadError(
            HttpStatusCode.BadRequest,
            "Selecciona un PDF o imágenes del mismo estado.",
        )
    }

    return files
}

private fun automaticFingerprint(files: List<StatementInputFile>): String {
    val digest = MessageDigest.getInstance("SHA-256")

    files.forEach {
        digest.update(ByteBuffer.allocate(4).putInt(it.bytes.size).array())
        digest.update(it.bytes)
    }

    return digest.digest().joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
}

fun Route.automaticStatementRoutes(sessions: SessionStore) {
    route("/api/v1/cards/{cardId}/automatic-statements") {
        get {
            val userId = call.authenticatedUser(sessions) ?: return@get

            val cardId = runCatching {
                UUID.fromString(call.parameters["cardId"])
            }.getOrNull() ?: return@get call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "La tarjeta no es válida.",
            )

            val records = dbQuery {
                if (!ownsAutomaticCard(cardId, userId)) return@dbQuery null

                LecturasEstadoTable.selectAll().where {
                    LecturasEstadoTable.tarjetaId eq cardId
                }.map {
                    it.toAutomaticDto()
                }.sortedWith(
                    compareByDescending<AutomaticStatementDto> {
                        it.deteccion.campos.fechaCorte.orEmpty()
                    }.thenByDescending {
                        it.fechaActualizacion
                    },
                )
            } ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ApiErrorCodes.NOT_FOUND,
                "Tarjeta no encontrada.",
            )

            call.respond(records)
        }

        post {
            val userId = call.authenticatedUser(sessions) ?: return@post

            val cardId = runCatching {
                UUID.fromString(call.parameters["cardId"])
            }.getOrNull() ?: return@post call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "La tarjeta no es válida.",
            )

            if (!dbQuery { ownsAutomaticCard(cardId, userId) }) {
                return@post call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Tarjeta no encontrada.",
                )
            }

            val files = try {
                call.automaticFiles()
            } catch (error: CancellationException) {
                throw error
            } catch (error: AutomaticUploadError) {
                return@post call.respondError(
                    error.status,
                    ApiErrorCodes.INVALID_REQUEST,
                    error.message.orEmpty(),
                )
            } catch (_: Exception) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "No se pudieron abrir los archivos.",
                )
            }

            val fingerprint = automaticFingerprint(files)

            val existing = dbQuery {
                if (!ownsAutomaticCard(cardId, userId)) return@dbQuery null

                LecturasEstadoTable.selectAll().where {
                    (LecturasEstadoTable.tarjetaId eq cardId) and
                            (LecturasEstadoTable.huella eq fingerprint)
                }.singleOrNull()?.toAutomaticDto()
            }

            if (existing != null) return@post call.respond(existing)

            val detection = try {
                automaticReaders.withPermit {
                    runInterruptible(Dispatchers.IO) {
                        val document = StatementDocumentReader.read(files)

                        AutomaticStatementParser.detect(
                            document.pages,
                            document.warnings,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: StatementOcrUnavailable) {
                return@post call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "ocr_unavailable",
                    error.message ?: "El lector OCR no está disponible.",
                )
            } catch (_: IllegalArgumentException) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Usa un PDF sin contraseña o imágenes claras, con hasta 12 páginas.",
                )
            } catch (_: IOException) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "No se pudo leer el documento. Prueba con otra copia.",
                )
            }

            val saved = try {
                dbQuery {
                    if (!ownsAutomaticCard(cardId, userId, lock = true)) {
                        return@dbQuery null
                    }

                    val duplicate = LecturasEstadoTable.selectAll().where {
                        (LecturasEstadoTable.tarjetaId eq cardId) and
                                (LecturasEstadoTable.huella eq fingerprint)
                    }.singleOrNull()

                    if (duplicate != null) {
                        return@dbQuery duplicate.toAutomaticDto()
                    }

                    val (normalized, anchor) = promoteAutomatic(
                        cardId,
                        detection,
                    )

                    val id = UUID.randomUUID()

                    LecturasEstadoTable.insert {
                        it[LecturasEstadoTable.id] = id
                        it[tarjetaId] = cardId
                        it[huella] = fingerprint
                        it[periodo] = normalized.campos.periodo
                        it[deteccion] = automaticJson.encodeToString(normalized)
                        it[version] = 1
                        it[versionFinanciera] = anchor
                        it[fechaActualizacion] = Instant.now()
                    }

                    LecturasEstadoTable.selectAll().where {
                        LecturasEstadoTable.id eq id
                    }.single().toAutomaticDto()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!automaticConflict(error)) throw error

                return@post call.respondError(
                    HttpStatusCode.Conflict,
                    "statement_import_conflict",
                    "El estado cambió mientras se leía. Reintenta la carga.",
                )
            } ?: return@post call.respondError(
                HttpStatusCode.NotFound,
                ApiErrorCodes.NOT_FOUND,
                "Tarjeta no encontrada.",
            )

            call.respond(saved)
        }

        post("/{id}/resolve") {
            val userId = call.authenticatedUser(sessions) ?: return@post

            val cardId = runCatching {
                UUID.fromString(call.parameters["cardId"])
            }.getOrNull()

            val id = runCatching {
                UUID.fromString(call.parameters["id"])
            }.getOrNull()

            if (cardId == null || id == null) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La lectura no es válida.",
                )
            }

            if (!dbQuery { ownsAutomaticCard(cardId, userId) }) {
                return@post call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Lectura no encontrada.",
                )
            }

            val request =
                call.receiveOrBadRequest<StatementResolutionRequest>()
                    ?: return@post

            if (
                request.version !in 1 until Int.MAX_VALUE ||
                request.pagoObjetivo.length > 40
            ) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La aclaración no es válida.",
                )
            }

            var conflict = false
            var invalid = false

            val saved = try {
                dbQuery {
                    if (!ownsAutomaticCard(cardId, userId, lock = true)) {
                        return@dbQuery null
                    }

                    val row = LecturasEstadoTable.selectAll().where {
                        (LecturasEstadoTable.id eq id) and
                                (LecturasEstadoTable.tarjetaId eq cardId)
                    }.forUpdate().singleOrNull() ?: return@dbQuery null

                    val old = row.toAutomaticDto(checkParent = false)

                    if (old.version != request.version) {
                        conflict = true
                        return@dbQuery null
                    }

                    if (
                        old.deteccion.estado !=
                        StatementDetectionStatus.IMPORTES_DISTINTOS ||
                        request.pagoObjetivo !in old.deteccion.opcionesPago
                    ) {
                        invalid = true
                        return@dbQuery null
                    }

                    val chosen = old.deteccion.copy(
                        campos = old.deteccion.campos.copy(
                            pagoParaNoGenerarIntereses = request.pagoObjetivo,
                        ),
                        estado = StatementDetectionStatus.LISTO,
                        pregunta = null,
                        opcionesPago = emptyList(),
                        avisos = (
                                old.deteccion.avisos +
                                        "Importe aclarado por el usuario."
                                ).distinct(),
                    )

                    val (normalized, anchor) =
                        promoteAutomatic(cardId, chosen)

                    LecturasEstadoTable.update({
                        LecturasEstadoTable.id eq id
                    }) {
                        it[deteccion] = automaticJson.encodeToString(normalized)
                        it[version] = old.version + 1
                        it[versionFinanciera] = anchor
                        it[fechaActualizacion] = Instant.now()
                    }

                    LecturasEstadoTable.selectAll().where {
                        LecturasEstadoTable.id eq id
                    }.single().toAutomaticDto()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!automaticConflict(error)) throw error
                conflict = true
                null
            }

            when {
                invalid -> call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "Selecciona uno de los importes detectados en el documento.",
                )

                conflict -> call.respondError(
                    HttpStatusCode.Conflict,
                    "statement_import_conflict",
                    "La lectura cambió. Recárgala antes de aclararla.",
                )

                saved == null -> call.respondError(
                    HttpStatusCode.NotFound,
                    ApiErrorCodes.NOT_FOUND,
                    "Lectura no encontrada.",
                )

                else -> call.respond(saved)
            }
        }
    }
}

private val cardPlanZone = ZoneId.of("America/Mexico_City")

private fun planBudget(
    userId: UUID,
    period: String,
): BudgetRequest? =
    PresupuestosTable.selectAll().where {
        (PresupuestosTable.usuarioId eq userId) and
                (PresupuestosTable.periodo eq period)
    }.singleOrNull()?.let {
        BudgetRequest(
            fechaBase = it[PresupuestosTable.fechaBase].toString(),
            saldoDisponible = it[PresupuestosTable.saldoDisponible]
                .setScale(2).toPlainString(),
            reserva = it[PresupuestosTable.reserva]
                .setScale(2).toPlainString(),
            movimientos = automaticJson.decodeFromString<List<BudgetMovement>>(
                it[PresupuestosTable.movimientos],
            ),
            version = it[PresupuestosTable.version],
        )
    }

private fun planPayments(
    statement: ConfirmedStatementDto,
    today: LocalDate,
): StatementPaymentsDto? {
    val cardId = UUID.fromString(statement.tarjetaId)

    val row = PagosEstadoTable.selectAll().where {
        (PagosEstadoTable.tarjetaId eq cardId) and
                (PagosEstadoTable.periodo eq statement.campos.periodo!!)
    }.singleOrNull() ?: return null

    val payments = automaticJson.decodeFromString<List<StatementPayment>>(
        row[PagosEstadoTable.pagos],
    )

    val anchor = row[PagosEstadoTable.versionEstado]
    val review = anchor != statement.version

    return StatementPaymentsDto(
        estado = statement,
        pagos = payments,
        version = row[PagosEstadoTable.version],
        versionEstado = anchor,
        fechaReferencia = today.toString(),
        requiereRevision = review,
        resumen = if (review) null else {
            StatementPaymentsEngine.summarize(
                statement.campos,
                payments,
                today,
            )
        },
        fechaActualizacion = row[PagosEstadoTable.fechaActualizacion].toString(),
    )
}

private fun planCard(
    cardId: UUID,
    alias: String,
    today: LocalDate,
): PlanCardInput? {
    val statements = EstadosCuentaConfirmadosTable.selectAll().where {
        EstadosCuentaConfirmadosTable.tarjetaId eq cardId
    }.map {
        ConfirmedStatementDto(
            cardId.toString(),
            automaticJson.decodeFromString<StatementFields>(
                it[EstadosCuentaConfirmadosTable.campos],
            ),
            it[EstadosCuentaConfirmadosTable.version],
            it[EstadosCuentaConfirmadosTable.fechaConfirmacion].toString(),
        )
    }

    val readings = LecturasEstadoTable.selectAll().where {
        LecturasEstadoTable.tarjetaId eq cardId
    }.map { it.toAutomaticDto() }

    val latestStatement = statements.maxByOrNull {
        it.campos.fechaCorte.orEmpty()
    }

    val latestReading = readings.maxWithOrNull(
        compareBy<AutomaticStatementDto> {
            it.deteccion.campos.fechaCorte.orEmpty()
        }.thenBy { it.fechaActualizacion },
    )

    if (latestStatement == null && latestReading == null) return null

    val chosenAuto = if (
        latestReading != null &&
        latestReading.deteccion.campos.fechaCorte.orEmpty() >=
        latestStatement?.campos?.fechaCorte.orEmpty()
    ) {
        latestReading
    } else {
        readings.filter {
            it.deteccion.campos.periodo == latestStatement?.campos?.periodo
        }.maxByOrNull { it.fechaActualizacion }
    }

    val chosenStatement = if (
        chosenAuto != null &&
        chosenAuto.deteccion.campos.fechaCorte.orEmpty() >=
        latestStatement?.campos?.fechaCorte.orEmpty()
    ) {
        statements.singleOrNull {
            it.campos.periodo == chosenAuto.deteccion.campos.periodo
        }
    } else latestStatement

    return PlanCardInput(
        alias = alias,
        statement = chosenStatement,
        automatic = chosenAuto,
        payments = chosenStatement?.let { planPayments(it, today) },
    )
}

fun Route.cardPlanRoutes(sessions: SessionStore) {
    post("/api/v1/card-plans/{periodo}") {
        val userId = call.authenticatedUser(sessions) ?: return@post

        val period = StatementConfirmationValidator.validPeriod(
            call.parameters["periodo"],
        ) ?: return@post call.respondError(
            HttpStatusCode.BadRequest,
            ApiErrorCodes.INVALID_REQUEST,
            "Selecciona un periodo válido.",
        )

        val request = call.receiveOrBadRequest<CardPlansRequest>()
            ?: return@post

        val today = LocalDate.now(cardPlanZone)

        val (budget, cards) = dbQuery {
            val records = TarjetasCreditoTable.selectAll().where {
                TarjetasCreditoTable.usuarioId eq userId
            }.mapNotNull {
                planCard(
                    it[TarjetasCreditoTable.id],
                    it[TarjetasCreditoTable.alias],
                    today,
                )
            }

            planBudget(userId, period) to records
        }

        val result = try {
            CardPlanEngine.calculate(
                period,
                budget,
                cards,
                request.ingresosPrevistos,
                today,
            )
        } catch (error: IllegalArgumentException) {
            return@post call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                error.message ?: "Revisa la disponibilidad para tus tarjetas.",
            )
        }

        call.respond(result)
    }
}