
package com.example.prisma.payroll

import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

// ====================================================
// CONFIGURACIÓN
// ====================================================

private const val MAX_PAYROLL_FILE_BYTES =
    10 * 1024 * 1024

// ====================================================
// MODELOS PARA ANÁLISIS CON GEMINI
// ====================================================

@Serializable
data class PayrollRequestDto(
    val salarioBruto: Double,
    val isr: Double,
    val imss: Double,
    val otrasDeducciones: Double = 0.0,
    val salarioNeto: Double,
    val periodo: String
)

@Serializable
data class PayrollAnalysisDto(
    val resumen: String,
    val percepciones: Double,
    val deducciones: Double,
    val salarioNeto: Double,
    val salarioNetoDeclarado: Double,
    val diferencia: Double,
    val existeDiferencia: Boolean,
    val observaciones: List<String>,
    val recomendaciones: List<String>,
    val requiereRevision: Boolean
)

// ====================================================
// MODELOS PARA LECTURA XML
// ====================================================

@Serializable
data class PayrollXmlDto(
    val periodo: String,
    val fechaPago: String,
    val percepciones: String,
    val deducciones: String,
    val otrosPagos: String,
    val isr: String,
    val seguridadSocial: String,
    val netoCfdi: String
)

// ====================================================
// MODELOS PARA LECTURA PDF
// ====================================================

@Serializable
data class PayrollPdfFieldsDto(
    val salarioBruto: String? = null,
    val isr: String? = null,
    val imss: String? = null,
    val otrasDeducciones: String? = null,
    val salarioNeto: String? = null,
    val camposNoIdentificados: List<String> = emptyList()
)

@Serializable
data class PayrollPdfDto(
    val numeroPaginas: Int,
    val caracteresExtraidos: Int,
    val requiereOcr: Boolean,
    val periodo: String? = null,
    val datosNomina: PayrollPdfFieldsDto? = null
)

// ====================================================
// RESPUESTA DEL ENDPOINT /upload
// ====================================================

@Serializable
data class PayrollUploadDto(
    val mensaje: String,
    val formato: String,
    val tamanoBytes: Long,
    val procesado: Boolean,
    val datosXml: PayrollXmlDto? = null,
    val datosPdf: PayrollPdfDto? = null
)

// ====================================================
// MANEJO DE ERRORES
// ====================================================

enum class PayrollError {
    Unauthorized,
    InvalidRequest,
    FileTooLarge,
    ServiceUnavailable,
    Network,
    Unknown
}

class PayrollException(
    val error: PayrollError
) : Exception(error.name)

// ====================================================
// REPOSITORIO DE NÓMINA
// ====================================================

class PayrollRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient
) {

    private val payrollBaseUrl =
        "${baseUrl.trimEnd('/')}/api/v1/ai/payroll"

    private val analyzeUrl =
        "$payrollBaseUrl/analyze"

    private val uploadUrl =
        "$payrollBaseUrl/upload"

    // =================================================
    // 1. ANÁLISIS MANUAL CON GEMINI
    // =================================================

    suspend fun analizarNomina(
        solicitud: PayrollRequestDto
    ): Result<PayrollAnalysisDto> {

        return try {

            val response = client.post(analyzeUrl) {

                withToken(accessToken)

                contentType(ContentType.Application.Json)

                setBody(solicitud)
            }

            if (response.status.isSuccess()) {

                Result.success(
                    response.body<PayrollAnalysisDto>()
                )

            } else {

                Result.failure(
                    PayrollException(
                        mapearError(response.status)
                    )
                )
            }

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {

            Result.failure(
                PayrollException(PayrollError.Network)
            )
        }
    }

    // =================================================
    // 2. CARGAR ARCHIVO PDF O XML
    // =================================================

    suspend fun subirNomina(
        nombreArchivo: String,
        contenido: ByteArray
    ): Result<PayrollUploadDto> {

        // ---------------------------------------------
        // VALIDAR NOMBRE Y EXTENSIÓN
        // ---------------------------------------------

        val nombreSeguro = nombreArchivo
            .substringAfterLast('/')
            .substringAfterLast('\\')

        val nombreValido = nombreSeguro.matches(
            Regex("[A-Za-z0-9._-]{1,120}")
        )

        if (
            !nombreValido ||
            nombreSeguro.startsWith(".")
        ) {
            return Result.failure(
                PayrollException(
                    PayrollError.InvalidRequest
                )
            )
        }

        val tipoMime = when {

            nombreSeguro.endsWith(
                ".pdf",
                ignoreCase = true
            ) -> "application/pdf"

            nombreSeguro.endsWith(
                ".xml",
                ignoreCase = true
            ) -> "application/xml"

            else -> null
        }

        if (tipoMime == null) {

            return Result.failure(
                PayrollException(
                    PayrollError.InvalidRequest
                )
            )
        }

        // ---------------------------------------------
        // VALIDAR CONTENIDO
        // ---------------------------------------------

        if (contenido.isEmpty()) {

            return Result.failure(
                PayrollException(
                    PayrollError.InvalidRequest
                )
            )
        }

        if (contenido.size > MAX_PAYROLL_FILE_BYTES) {

            return Result.failure(
                PayrollException(
                    PayrollError.FileTooLarge
                )
            )
        }

        // ---------------------------------------------
        // ENVIAR MULTIPART AL BACKEND
        // ---------------------------------------------

        return try {

            val response = client.post(uploadUrl) {

                withToken(accessToken)

                setBody(
                    MultiPartFormDataContent(
                        formData {

                            append(
                                "archivo",
                                contenido,
                                Headers.build {

                                    append(
                                        HttpHeaders.ContentType,
                                        tipoMime
                                    )

                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "filename=\"$nombreSeguro\""
                                    )
                                }
                            )
                        }
                    )
                )
            }

            if (response.status.isSuccess()) {

                Result.success(
                    response.body<PayrollUploadDto>()
                )

            } else {

                Result.failure(
                    PayrollException(
                        mapearError(response.status)
                    )
                )
            }

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {

            Result.failure(
                PayrollException(PayrollError.Network)
            )
        }
    }

    // =================================================
    // 3. MAPEAR ERRORES HTTP
    // =================================================

    private fun mapearError(
        status: HttpStatusCode
    ): PayrollError {

        return when (status) {

            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden ->
                PayrollError.Unauthorized

            HttpStatusCode.BadRequest,
            HttpStatusCode.UnsupportedMediaType ->
                PayrollError.InvalidRequest

            HttpStatusCode.PayloadTooLarge ->
                PayrollError.FileTooLarge

            HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.GatewayTimeout ->
                PayrollError.ServiceUnavailable

            else ->
                PayrollError.Unknown
        }
    }
}
