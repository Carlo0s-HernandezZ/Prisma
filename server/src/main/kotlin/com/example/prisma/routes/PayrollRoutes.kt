
package com.example.prisma.routes

import com.example.prisma.ai.PayrollPdfFieldsExtractor
import com.example.prisma.ai.PayrollPdfParser
import com.example.prisma.ai.PayrollRequest
import com.example.prisma.ai.PayrollService
import com.example.prisma.ai.PayrollXmlParser
import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.auth.SessionStore
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.ai.PayrollPeriodExtractor

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

import java.io.ByteArrayOutputStream

// ====================================================
// CONFIGURACIÓN
// ====================================================

private const val MAX_PAYROLL_FILE_BYTES =
    10 * 1024 * 1024

// ====================================================
// MODELOS DE RESPUESTA
// ====================================================

@Serializable
private data class PayrollXmlResponse(
    val periodo: String,
    val fechaPago: String,
    val percepciones: String,
    val deducciones: String,
    val otrosPagos: String,
    val isr: String,
    val seguridadSocial: String,
    val netoCfdi: String
)

@Serializable
private data class PayrollPdfFieldsResponse(
    val salarioBruto: String?,
    val isr: String?,
    val imss: String?,
    val otrasDeducciones: String?,
    val salarioNeto: String?,
    val camposNoIdentificados: List<String>
)

@Serializable
private data class PayrollPdfResponse(
    val numeroPaginas: Int,
    val caracteresExtraidos: Int,
    val requiereOcr: Boolean,
    val periodo: String? = null,
    val datosNomina: PayrollPdfFieldsResponse? = null
)

@Serializable
private data class PayrollUploadResponse(
    val mensaje: String,
    val formato: String,
    val tamanoBytes: Long,
    val procesado: Boolean,
    val datosXml: PayrollXmlResponse? = null,
    val datosPdf: PayrollPdfResponse? = null
)

// ====================================================
// RUTAS DE AUDITORÍA DE NÓMINA
// ====================================================

fun Route.payrollRoutes(sessions: SessionStore) {

    route("/api/v1/ai/payroll") {

        // ============================================
        // ENDPOINT 1: ANÁLISIS MANUAL CON GEMINI
        // ============================================

        post("/analyze") {

            // Validar sesión
            call.authenticatedUser(sessions)
                ?: return@post

            // Recibir solicitud JSON
            val solicitud =
                call.receiveOrBadRequest<PayrollRequest>()
                    ?: return@post

            val resultado = try {

                PayrollService().analizarNomina(solicitud)

            } catch (e: CancellationException) {
                throw e

            } catch (e: IllegalArgumentException) {

                call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    e.message ?: "Datos de nómina inválidos."
                )

                return@post

            } catch (e: Exception) {

                call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "AI_UNAVAILABLE",
                    "No fue posible completar el análisis de nómina."
                )

                return@post
            }

            call.respond(
                HttpStatusCode.OK,
                resultado
            )
        }

        // ============================================
        // ENDPOINT 2: CARGAR ARCHIVO PDF O XML
        // ============================================

        post("/upload") {

            // ----------------------------------------
            // 1. AUTENTICACIÓN
            // ----------------------------------------

            call.authenticatedUser(sessions)
                ?: return@post

            val contentType = call.request.headers[
                HttpHeaders.ContentType
            ].orEmpty()

            if (!contentType.startsWith(
                    "multipart/form-data",
                    ignoreCase = true
                )
            ) {

                call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "La solicitud debe ser multipart/form-data."
                )

                return@post
            }

            // ----------------------------------------
            // 2. VARIABLES DE CONTROL
            // ----------------------------------------

            var cantidadArchivos = 0
            var formato: String? = null
            var contenido: ByteArray? = null
            var tamano = 0L
            var errorArchivo: String? = null
            var excedeLimite = false

            // ----------------------------------------
            // 3. RECIBIR DOCUMENTO
            // ----------------------------------------

            try {

                val multipart = call.receiveMultipart(
                    formFieldLimit =
                        MAX_PAYROLL_FILE_BYTES.toLong() + 1
                )

                multipart.forEachPart { parte ->

                    try {

                        if (parte is PartData.FileItem) {

                            cantidadArchivos++

                            if (cantidadArchivos > 1) {

                                errorArchivo =
                                    "Solo se permite un archivo."

                            } else if (parte.name != "archivo") {

                                errorArchivo =
                                    "El campo debe llamarse 'archivo'."

                            } else {

                                // Identificar extensión
                                val nombre = parte.originalFileName
                                    ?.lowercase()
                                    .orEmpty()

                                val tipo = when {

                                    nombre.endsWith(".pdf") ->
                                        "PDF"

                                    nombre.endsWith(".xml") ->
                                        "XML"

                                    else ->
                                        null
                                }

                                if (tipo == null) {

                                    errorArchivo =
                                        "Solo se aceptan archivos PDF y XML."

                                } else {

                                    // Leer el documento por bloques
                                    val canal = parte.provider()
                                    val salida = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)

                                    while (true) {

                                        val leidos = canal.readAvailable(
                                            buffer,
                                            0,
                                            buffer.size
                                        )

                                        if (leidos == -1) {
                                            break
                                        }

                                        if (leidos == 0) {
                                            continue
                                        }

                                        tamano += leidos

                                        if (
                                            tamano >
                                            MAX_PAYROLL_FILE_BYTES
                                        ) {

                                            excedeLimite = true
                                            break
                                        }

                                        salida.write(
                                            buffer,
                                            0,
                                            leidos
                                        )
                                    }

                                    if (!excedeLimite) {

                                        val bytes = salida.toByteArray()

                                        if (bytes.isEmpty()) {

                                            errorArchivo =
                                                "El archivo está vacío."

                                        } else if (
                                            !formatoValido(tipo, bytes)
                                        ) {

                                            errorArchivo =
                                                "El contenido no coincide " +
                                                        "con el formato declarado."

                                        } else {

                                            formato = tipo
                                            contenido = bytes
                                        }
                                    }
                                }
                            }
                        }

                    } finally {
                        parte.dispose()
                    }
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    "No fue posible recibir el documento."
                )

                return@post
            }

            // ----------------------------------------
            // 4. VALIDACIONES FINALES
            // ----------------------------------------

            if (excedeLimite) {

                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    ApiErrorCodes.INVALID_REQUEST,
                    "El archivo supera los 10 MB."
                )

                return@post
            }

            val tipoArchivo = formato
            val bytesArchivo = contenido

            if (
                cantidadArchivos != 1 ||
                errorArchivo != null ||
                tipoArchivo == null ||
                bytesArchivo == null
            ) {

                call.respondError(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.INVALID_REQUEST,
                    errorArchivo ?: "Se requiere un archivo válido."
                )

                return@post
            }

            // ========================================
            // 5. PROCESAR XML DE NÓMINA
            // ========================================

            val datosXml = if (tipoArchivo == "XML") {

                val datos = try {

                    PayrollXmlParser().extraer(
                        bytesArchivo
                    )

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {

                    call.respondError(
                        HttpStatusCode.BadRequest,
                        ApiErrorCodes.INVALID_REQUEST,
                        "El XML está mal formado o no contiene " +
                                "un CFDI de nómina compatible."
                    )

                    return@post
                }

                PayrollXmlResponse(
                    periodo = datos.periodo,
                    fechaPago = datos.fechaPago,
                    percepciones =
                        datos.percepciones.toPlainString(),
                    deducciones =
                        datos.deducciones.toPlainString(),
                    otrosPagos =
                        datos.otrosPagos.toPlainString(),
                    isr =
                        datos.isr.toPlainString(),
                    seguridadSocial =
                        datos.seguridadSocial.toPlainString(),
                    netoCfdi =
                        datos.netoCfdi.toPlainString()
                )

            } else {
                null
            }

            // ========================================
            // 6. PROCESAR PDF DE NÓMINA
            // ========================================

            val datosPdf = if (tipoArchivo == "PDF") {

                val datos = try {

                    PayrollPdfParser().extraer(
                        bytesArchivo
                    )

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {

                    call.respondError(
                        HttpStatusCode.BadRequest,
                        ApiErrorCodes.INVALID_REQUEST,
                        "No fue posible procesar el PDF. " +
                                "Verifica su formato, protección y tamaño."
                    )

                    return@post
                }

                // ------------------------------------
                // IDENTIFICAR CONCEPTOS FINANCIEROS
                // ------------------------------------

                val campos = if (!datos.requiereOcr) {

                    val extraidos = try {

                        PayrollPdfFieldsExtractor().extraer(
                            datos.textoExtraido
                        )

                    } catch (e: CancellationException) {
                        throw e

                    } catch (e: Exception) {

                        call.respondError(
                            HttpStatusCode.BadRequest,
                            ApiErrorCodes.INVALID_REQUEST,
                            "No fue posible identificar los " +
                                    "conceptos del documento PDF."
                        )

                        return@post
                    }

                    PayrollPdfFieldsResponse(
                        salarioBruto =
                            extraidos.salarioBruto?.toPlainString(),
                        isr =
                            extraidos.isr?.toPlainString(),
                        imss =
                            extraidos.imss?.toPlainString(),
                        otrasDeducciones =
                            extraidos.otrasDeducciones?.toPlainString(),
                        salarioNeto =
                            extraidos.salarioNeto?.toPlainString(),
                        camposNoIdentificados =
                            extraidos.camposNoIdentificados
                    )

                } else {
                    null
                }

                PayrollPdfResponse(
                    numeroPaginas = datos.numeroPaginas,
                    caracteresExtraidos = datos.caracteresExtraidos,
                    requiereOcr = datos.requiereOcr,
                    periodo = if (!datos.requiereOcr) {
                        PayrollPeriodExtractor().extraerPeriodo(
                            datos.textoExtraido
                        )
                    } else {
                        null
                    },
                    datosNomina = campos
                )

            } else {
                null
            }

            // ========================================
            // 7. PREPARAR MENSAJE DE RESPUESTA
            // ========================================

            val mensaje = when {

                datosXml != null ->
                    "XML de nómina leído correctamente."

                datosPdf?.requiereOcr == true ->
                    "El PDF no contiene texto extraíble. " +
                            "Podría necesitar OCR."

                datosPdf?.datosNomina
                    ?.camposNoIdentificados
                    ?.isEmpty() == true ->
                    "Se identificaron todos los conceptos " +
                            "de nómina previstos en el PDF."

                datosPdf != null ->
                    "PDF leído. Algunos conceptos " +
                            "no pudieron identificarse."

                else ->
                    "No fue posible procesar el documento."
            }

            // ========================================
            // 8. RESPUESTA FINAL
            // ========================================

            val procesado =
                datosXml != null ||
                        (
                                datosPdf != null &&
                                        !datosPdf.requiereOcr
                                )

            call.respond(
                HttpStatusCode.OK,
                PayrollUploadResponse(
                    mensaje = mensaje,
                    formato = tipoArchivo,
                    tamanoBytes = tamano,
                    procesado = procesado,
                    datosXml = datosXml,
                    datosPdf = datosPdf
                )
            )
        }
    }
}

// ====================================================
// VALIDACIÓN BÁSICA DEL FORMATO DEL ARCHIVO
// ====================================================

private fun formatoValido(
    formato: String,
    bytes: ByteArray
): Boolean {

    return when (formato) {

        "PDF" -> {

            bytes.size >= 5 &&
                    bytes.copyOfRange(0, 5)
                        .decodeToString() == "%PDF-"
        }

        "XML" -> {

            val cabecera = bytes
                .copyOfRange(
                    0,
                    minOf(bytes.size, 512)
                )
                .decodeToString()
                .trimStart(
                    '\uFEFF',
                    ' ',
                    '\n',
                    '\r',
                    '\t'
                )

            cabecera.startsWith("<?xml") ||
                    cabecera.startsWith("<cfdi:Comprobante") ||
                    cabecera.startsWith("<Comprobante")
        }

        else -> false
    }
}


/**
 * Extrae el periodo AAAA-MM de las fechas
 * inicial y final de pago de una nómina PDF.
 *
 * Ejemplos:
 * 19/Oct/2026 al 25/Oct/2026 -> 2026-10
 * 19/10/2026 al 25/10/2026   -> 2026-10
 */
private fun extraerPeriodoPdf(texto: String): String? {

    val meses = mapOf(
        "ENE" to 1,
        "FEB" to 2,
        "MAR" to 3,
        "ABR" to 4,
        "MAY" to 5,
        "JUN" to 6,
        "JUL" to 7,
        "AGO" to 8,
        "SEP" to 9,
        "SEPT" to 9,
        "OCT" to 10,
        "NOV" to 11,
        "DIC" to 12
    )

    val fechaRegex = Regex(
        """\b(\d{1,2})/([A-Za-z]{3,4}|\d{1,2})/(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    fun interpretar(
        coincidencia: MatchResult
    ): Triple<Int, Int, Int>? {

        val dia = coincidencia.groupValues[1]
            .toIntOrNull() ?: return null

        val mesTexto = coincidencia.groupValues[2]
            .uppercase()

        val mes = mesTexto.toIntOrNull()
            ?: meses[mesTexto]
            ?: return null

        val anio = coincidencia.groupValues[3]
            .toIntOrNull() ?: return null

        if (mes !in 1..12) return null

        // Validación básica del día.
        // La validación completa del calendario
        // puede realizarse en una etapa posterior.
        if (dia !in 1..31) return null

        return Triple(anio, mes, dia)
    }

    fun buscarPareja(fragmento: String): String? {

        val fechas = fechaRegex
            .findAll(fragmento)
            .toList()

        for (i in 0 until fechas.size - 1) {

            val primera = fechas[i]
            val segunda = fechas[i + 1]

            // Evitar asociar dos fechas lejanas.
            val distancia =
                segunda.range.first - primera.range.last

            if (distancia > 40) {
                continue
            }

            val inicio = interpretar(primera) ?: continue
            val fin = interpretar(segunda) ?: continue

            // El inicio debe preceder al final.
            val inicioOrden = inicio.first * 10000 +
                    inicio.second * 100 + inicio.third

            val finOrden = fin.first * 10000 +
                    fin.second * 100 + fin.third

            if (inicioOrden > finOrden) {
                continue
            }

            val anio = inicio.first
            val mes = inicio.second
                .toString()
                .padStart(2, '0')

            return "$anio-$mes"
        }

        return null
    }

    // Prioridad: fechas próximas al encabezado
    // "Periodo de Pago".
    val etiqueta = Regex(
        """per[ií]odo\s+de\s+pago""",
        RegexOption.IGNORE_CASE
    ).find(texto)

    if (etiqueta != null) {

        val inicio = etiqueta.range.last + 1

        val fragmento = texto.substring(
            inicio,
            minOf(texto.length, inicio + 180)
        )

        buscarPareja(fragmento)?.let {
            return it
        }
    }

    // Respaldo para PDFs donde PDFBox cambia
    // el orden de lectura de los encabezados:
    // buscar fechas cercanas con "Al" entre ellas.
    val patronPeriodo = Regex(
        """\b\d{1,2}/(?:[A-Za-z]{3,4}|\d{1,2})/\d{4}\s+(?:al|a)\s*:?\s*\d{1,2}/(?:[A-Za-z]{3,4}|\d{1,2})/\d{4}\b""",
        RegexOption.IGNORE_CASE
    )

    val pareja = patronPeriodo.find(texto)
        ?: return null

    return buscarPareja(pareja.value)
}

