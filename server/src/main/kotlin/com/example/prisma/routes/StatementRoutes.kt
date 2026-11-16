package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.UploadValidation
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.respondError
import com.example.prisma.models.TarjetasCreditoTable
import com.example.prisma.services.StatementDocumentReader
import com.example.prisma.services.StatementFinancialParser
import com.example.prisma.services.StatementInputFile
import com.example.prisma.services.StatementOcrUnavailable
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_STATEMENT_FILES = 12
private const val MAX_STATEMENT_TOTAL = 20 * 1024 * 1024

private val statementReaders = Semaphore(2)

private val statementMimeTypes = setOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
)

private class StatementUploadError(
    val status: HttpStatusCode,
    message: String,
) : Exception(message)

fun Route.statementRoutes(sessions: SessionStore) {
    post("/api/v1/cards/{cardId}/statement-extraction") {
        val userId = call.authenticatedUser(sessions)
            ?: return@post

        val cardId = call.parameters["cardId"]?.let {
            runCatching {
                UUID.fromString(it)
            }.getOrNull()
        } ?: return@post call.respondError(
            HttpStatusCode.BadRequest,
            ApiErrorCodes.INVALID_REQUEST,
            "El identificador de la tarjeta no es válido.",
        )

        val ownsCard = dbQuery {
            TarjetasCreditoTable
                .selectAll()
                .where {
                    (TarjetasCreditoTable.id eq cardId) and
                            (TarjetasCreditoTable.usuarioId eq userId)
                }
                .any()
        }

        if (!ownsCard) {
            return@post call.respondError(
                HttpStatusCode.NotFound,
                ApiErrorCodes.NOT_FOUND,
                "Tarjeta no encontrada.",
            )
        }

        val files = mutableListOf<StatementInputFile>()
        var totalBytes = 0

        try {
            val multipart = call.receiveMultipart(
                formFieldLimit = MAX_STATEMENT_TOTAL.toLong(),
            )

            multipart.forEachPart { part ->
                try {
                    if (part !is PartData.FileItem) {
                        throw StatementUploadError(
                            HttpStatusCode.BadRequest,
                            "Envía únicamente archivos del estado de cuenta.",
                        )
                    }

                    if (files.size >= MAX_STATEMENT_FILES) {
                        throw StatementUploadError(
                            HttpStatusCode.BadRequest,
                            "Puedes adjuntar hasta 12 archivos del mismo estado de cuenta.",
                        )
                    }

                    val remaining = MAX_STATEMENT_TOTAL -
                            totalBytes

                    val limit = minOf(
                        UploadValidation.MAX_FILE_BYTES,
                        remaining,
                    )

                    val bytes = part.provider()
                        .toInputStream()
                        .use {
                            it.readNBytes(limit + 1)
                        }

                    if (
                        bytes.size > UploadValidation.MAX_FILE_BYTES ||
                        bytes.size > remaining
                    ) {
                        throw StatementUploadError(
                            HttpStatusCode.PayloadTooLarge,
                            "El límite es 10 MB por archivo y 20 MB en total.",
                        )
                    }

                    val mime = UploadValidation.detectMime(bytes)

                    if (mime !in statementMimeTypes) {
                        throw StatementUploadError(
                            HttpStatusCode.UnsupportedMediaType,
                            "Para esta lectura utiliza archivos PDF, JPG o PNG.",
                        )
                    }

                    files += StatementInputFile(
                        bytes,
                        requireNotNull(mime),
                    )

                    totalBytes += bytes.size
                } finally {
                    part.release()
                }
            }

            if (files.isEmpty()) {
                throw StatementUploadError(
                    HttpStatusCode.BadRequest,
                    "Adjunta un PDF o imágenes del estado de cuenta.",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: StatementUploadError) {
            return@post call.respondError(
                error.status,
                ApiErrorCodes.INVALID_REQUEST,
                error.message.orEmpty(),
            )
        } catch (_: Exception) {
            return@post call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "No se pudieron leer los archivos enviados.",
            )
        }

        val result = try {
            statementReaders.withPermit {
                runInterruptible(Dispatchers.IO) {
                    val document = StatementDocumentReader.read(
                        files,
                    )

                    StatementFinancialParser.parse(
                        document.pages,
                        files.size,
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
                error.message
                    ?: "El lector OCR no está disponible en el servidor.",
            )
        } catch (_: IllegalArgumentException) {
            return@post call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "No se pudo leer el documento. Usa PDF sin contraseña, " +
                        "JPG o PNG, con un máximo de 12 páginas en total.",
            )
        } catch (_: IOException) {
            return@post call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.INVALID_REQUEST,
                "El archivo está dañado o no se puede leer. " +
                        "Prueba con otra copia.",
            )
        }

        call.respond(result)
    }
}