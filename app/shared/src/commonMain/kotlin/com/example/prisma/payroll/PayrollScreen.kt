
package com.example.prisma.payroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import kotlin.math.abs
import kotlin.math.round

// ====================================================
// DOCUMENTO SELECCIONADO
// ====================================================

data class PayrollSelectedFile(
    val identificador: Long,
    val nombreArchivo: String,
    val contenido: ByteArray
)

// ====================================================
// FUNCIONES AUXILIARES
// ====================================================

private fun formatoMoneda(cantidad: Double): String {
    val negativo = cantidad < 0
    val centavos = round(abs(cantidad) * 100).toLong()

    val enteros = (centavos / 100)
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

    val decimales = (centavos % 100)
        .toString()
        .padStart(2, '0')

    return "${if (negativo) "-" else ""}\$$enteros.$decimales"
}

private fun mensajeError(error: Throwable): String {
    return when ((error as? PayrollException)?.error) {
        PayrollError.Unauthorized ->
            "Tu sesión expiró. Inicia sesión nuevamente."

        PayrollError.InvalidRequest ->
            "El archivo o los datos no son válidos."

        PayrollError.FileTooLarge ->
            "El documento supera el límite de 10 MB."

        PayrollError.ServiceUnavailable ->
            "El servicio no está disponible."

        PayrollError.Network ->
            "No se pudo conectar con el servidor."

        else ->
            "Ocurrió un error al procesar la solicitud."
    }
}

@Composable
private fun CampoNomina(
    titulo: String,
    valor: String,
    cambiar: (String) -> Unit,
    habilitado: Boolean,
    esPeriodo: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = cambiar,
        label = { Text(titulo) },
        modifier = Modifier.fillMaxWidth(),
        enabled = habilitado,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (esPeriodo) {
                KeyboardType.Text
            } else {
                KeyboardType.Decimal
            }
        )
    )
}

@Composable
private fun FilaFinanciera(
    titulo: String,
    cantidad: Double
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${formatoMoneda(cantidad)} MXN",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// ====================================================
// PANTALLA PRINCIPAL
// ====================================================

@Composable
fun PayrollScreen(
    token: String,
    documentoSeleccionado: PayrollSelectedFile? = null,
    onSeleccionarDocumento: (() -> Unit)? = null
) {
    val repository = remember(token) {
        PayrollRepository(token)
    }

    val scope = rememberCoroutineScope()

    // ================================================
    // FORMULARIO: TODOS LOS CAMPOS INICIAN VACÍOS
    // ================================================

    var periodo by remember(token) {
        mutableStateOf("")
    }

    var salarioBruto by remember(token) {
        mutableStateOf("")
    }

    var isr by remember(token) {
        mutableStateOf("")
    }

    var imss by remember(token) {
        mutableStateOf("")
    }

    var otrasDeducciones by remember(token) {
        mutableStateOf("")
    }

    var salarioNeto by remember(token) {
        mutableStateOf("")
    }

    // ================================================
    // ESTADOS DE OPERACIONES
    // ================================================

    var analizando by remember(token) {
        mutableStateOf(false)
    }

    var subiendo by remember(token) {
        mutableStateOf(false)
    }

    var resultado by remember(token) {
        mutableStateOf<PayrollAnalysisDto?>(null)
    }

    var resultadoArchivo by remember(token) {
        mutableStateOf<PayrollUploadDto?>(null)
    }

    var error by remember(token) {
        mutableStateOf<String?>(null)
    }

    var aviso by remember(token) {
        mutableStateOf<String?>(null)
    }

    val ocupado = analizando || subiendo

    // ================================================
    // PROCESAR DOCUMENTO PDF O XML
    // ================================================

    LaunchedEffect(
        documentoSeleccionado?.identificador,
        token
    ) {
        val archivo = documentoSeleccionado
            ?: return@LaunchedEffect

        // Limpiar información de la nómina anterior
        periodo = ""
        salarioBruto = ""
        isr = ""
        imss = ""
        otrasDeducciones = ""
        salarioNeto = ""

        resultado = null
        resultadoArchivo = null
        error = null
        aviso = null

        if (archivo.contenido.isEmpty()) {
            error = "El archivo seleccionado está vacío."
            return@LaunchedEffect
        }

        subiendo = true

        try {
            repository.subirNomina(
                nombreArchivo = archivo.nombreArchivo,
                contenido = archivo.contenido
            ).onSuccess { respuesta ->

                resultadoArchivo = respuesta

                // ====================================
                // IMPORTACIÓN DESDE XML
                // ====================================

                respuesta.datosXml?.let { xml ->

                    // Periodo obtenido del backend
                    periodo = xml.periodo

                    val percepciones =
                        xml.percepciones.toDoubleOrNull()

                    val otrosPagos =
                        xml.otrosPagos.toDoubleOrNull()

                    if (
                        percepciones != null &&
                        otrosPagos != null
                    ) {
                        salarioBruto = (
                                percepciones + otrosPagos
                                ).toString()
                    }

                    isr = xml.isr
                    salarioNeto = xml.netoCfdi

                    // Estos conceptos requieren revisión
                    // y no se asignan automáticamente.
                    imss = ""
                    otrasDeducciones = ""

                    aviso =
                        "Periodo obtenido del XML: " +
                                periodo +
                                ". Revisa las percepciones y " +
                                "completa IMSS y otras deducciones " +
                                "antes de analizar."
                }

                // ====================================
                // IMPORTACIÓN DESDE PDF
                // ====================================

                respuesta.datosPdf?.let { pdf ->

                    if (pdf.requiereOcr) {

                        aviso =
                            "El PDF no contiene texto " +
                                    "seleccionable. Podría necesitar OCR."

                    } else {

                        // Periodo detectado por el backend
                        periodo = pdf.periodo.orEmpty()

                        pdf.datosNomina?.let { datos ->

                            salarioBruto =
                                datos.salarioBruto.orEmpty()

                            isr =
                                datos.isr.orEmpty()

                            imss =
                                datos.imss.orEmpty()

                            otrasDeducciones =
                                datos.otrasDeducciones.orEmpty()

                            salarioNeto =
                                datos.salarioNeto.orEmpty()
                        }

                        val faltantes = pdf.datosNomina
                            ?.camposNoIdentificados
                            .orEmpty()

                        aviso = when {

                            periodo.isBlank() ->
                                "Se extrajeron los datos del PDF, " +
                                        "pero no fue posible identificar " +
                                        "el periodo de pago. Verifícalo " +
                                        "en el recibo."

                            faltantes.isNotEmpty() ->
                                "Periodo detectado: $periodo. " +
                                        "Revisa y completa los conceptos " +
                                        "que no se identificaron."

                            else ->
                                "Periodo detectado: $periodo. " +
                                        "Los importes se cargaron " +
                                        "correctamente. Revísalos " +
                                        "antes de analizar."
                        }
                    }
                }

            }.onFailure { fallo ->
                error = mensajeError(fallo)
            }

        } catch (e: CancellationException) {
            throw e

        } finally {
            subiendo = false
        }
    }

    // ================================================
    // INTERFAZ PRINCIPAL
    // ================================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Auditoría de Nómina",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Carga un recibo PDF/XML o introduce " +
                    "manualmente los importes para " +
                    "analizarlos con Gemini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // ============================================
        // SELECCIONAR DOCUMENTO
        // ============================================

        Text(
            text = "Importar recibo de nómina",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "PDF o XML. Tamaño máximo: 10 MB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (onSeleccionarDocumento != null) {

            Button(
                onClick = onSeleccionarDocumento,
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seleccionar PDF o XML")
            }

        } else {

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Selector de archivos no " +
                            "disponible en esta plataforma.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        documentoSeleccionado?.let { archivo ->

            Text(
                text = "Archivo: ${archivo.nombreArchivo}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (subiendo) {

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )

            Text("Procesando documento...")
        }

        // ============================================
        // RESULTADO DE LA IMPORTACIÓN
        // ============================================

        resultadoArchivo?.let { carga ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = "Documento procesado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(carga.mensaje)

                    Text("Formato: ${carga.formato}")

                    carga.datosXml?.let { xml ->
                        Text(
                            text = "Periodo: ${xml.periodo}"
                        )
                    }

                    carga.datosPdf?.let { pdf ->

                        Text(
                            text = "Páginas: ${pdf.numeroPaginas}"
                        )

                        pdf.periodo?.let { valor ->
                            Text("Periodo: $valor")
                        }

                        pdf.datosNomina?.let { datos ->

                            if (
                                datos.camposNoIdentificados.isNotEmpty()
                            ) {
                                Text(
                                    text = "Campos pendientes: " +
                                            datos.camposNoIdentificados
                                                .joinToString(", ")
                                )
                            }
                        }
                    }

                    Text(
                        text = "Revisa los datos antes " +
                                "de solicitar el análisis.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        aviso?.let { texto ->

            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        // ============================================
        // FORMULARIO DE NÓMINA
        // ============================================

        Text(
            text = "Datos de la nómina",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        CampoNomina(
            titulo = "Periodo (AAAA-MM)",
            valor = periodo,
            cambiar = {
                periodo = it
                resultado = null
            },
            habilitado = !ocupado,
            esPeriodo = true
        )

        CampoNomina(
            titulo = "Salario bruto",
            valor = salarioBruto,
            cambiar = {
                salarioBruto = it
                resultado = null
            },
            habilitado = !ocupado
        )

        CampoNomina(
            titulo = "ISR",
            valor = isr,
            cambiar = {
                isr = it
                resultado = null
            },
            habilitado = !ocupado
        )

        CampoNomina(
            titulo = "IMSS",
            valor = imss,
            cambiar = {
                imss = it
                resultado = null
            },
            habilitado = !ocupado
        )

        CampoNomina(
            titulo = "Otras deducciones",
            valor = otrasDeducciones,
            cambiar = {
                otrasDeducciones = it
                resultado = null
            },
            habilitado = !ocupado
        )

        CampoNomina(
            titulo = "Salario neto declarado",
            valor = salarioNeto,
            cambiar = {
                salarioNeto = it
                resultado = null
            },
            habilitado = !ocupado
        )

        // ============================================
        // BOTÓN ANALIZAR CON GEMINI
        // ============================================

        Button(
            onClick = {

                val bruto = salarioBruto.toDoubleOrNull()
                val valorIsr = isr.toDoubleOrNull()
                val valorImss = imss.toDoubleOrNull()

                val otras =
                    otrasDeducciones.toDoubleOrNull()

                val neto = salarioNeto.toDoubleOrNull()

                val periodoValido = Regex(
                    """\d{4}-(0[1-9]|1[0-2])"""
                ).matches(periodo.trim())

                val importes = listOf(
                    bruto,
                    valorIsr,
                    valorImss,
                    otras,
                    neto
                )

                val importesValidos = importes.all {
                    it != null &&
                            it.isFinite() &&
                            it >= 0.0
                }

                when {

                    !periodoValido -> {
                        error =
                            "El periodo debe tener " +
                                    "el formato AAAA-MM."
                        resultado = null
                    }

                    !importesValidos -> {
                        error =
                            "Todos los importes deben " +
                                    "ser números válidos y no negativos."
                        resultado = null
                    }

                    else -> {

                        val b = requireNotNull(bruto)
                        val i = requireNotNull(valorIsr)
                        val m = requireNotNull(valorImss)
                        val o = requireNotNull(otras)
                        val n = requireNotNull(neto)

                        if (i + m + o > b) {

                            error =
                                "Las deducciones superan " +
                                        "el salario bruto."
                            resultado = null

                        } else {

                            analizando = true
                            resultado = null
                            error = null

                            scope.launch {

                                try {

                                    val solicitud =
                                        PayrollRequestDto(
                                            salarioBruto = b,
                                            isr = i,
                                            imss = m,
                                            otrasDeducciones = o,
                                            salarioNeto = n,
                                            periodo = periodo.trim()
                                        )

                                    repository
                                        .analizarNomina(solicitud)
                                        .onSuccess {
                                            resultado = it
                                            error = null
                                        }
                                        .onFailure {
                                            error = mensajeError(it)
                                        }

                                } catch (e: CancellationException) {
                                    throw e

                                } finally {
                                    analizando = false
                                }
                            }
                        }
                    }
                }
            },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (analizando) {
                    "Analizando..."
                } else {
                    "Analizar con Gemini"
                }
            )
        }

        // ============================================
        // LIMPIAR FORMULARIO
        // ============================================


        OutlinedButton(
            onClick = {
                // Limpiar todos los campos
                periodo = ""
                salarioBruto = ""
                isr = ""
                imss = ""
                otrasDeducciones = ""
                salarioNeto = ""

                // Limpiar resultados y mensajes
                resultado = null
                resultadoArchivo = null
                error = null
                aviso = null
            },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Limpiar formulario")
        }


        if (analizando) {

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Gemini está analizando la información..."
            )
        }

        // ============================================
        // MENSAJES DE ERROR
        // ============================================

        error?.let { mensaje ->

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = mensaje,
                    modifier = Modifier.padding(16.dp),
                    color =
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // ============================================
        // RESULTADOS DE GEMINI
        // ============================================

        resultado?.let { analisis ->

            HorizontalDivider()

            Text(
                text = "Resultado de la auditoría",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Análisis general",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(analisis.resumen)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        text = "Resumen financiero",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    FilaFinanciera(
                        "Percepciones",
                        analisis.percepciones
                    )

                    FilaFinanciera(
                        "Deducciones",
                        analisis.deducciones
                    )

                    HorizontalDivider()

                    FilaFinanciera(
                        "Salario neto declarado",
                        analisis.salarioNetoDeclarado
                    )

                    FilaFinanciera(
                        "Salario neto calculado",
                        analisis.salarioNeto
                    )

                    HorizontalDivider()

                    Text(
                        text = "Diferencia detectada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${formatoMoneda(analisis.diferencia)} MXN",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (analisis.existeDiferencia) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (analisis.existeDiferencia) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = if (analisis.existeDiferencia) {
                            "Diferencia aritmética detectada"
                        } else {
                            "Sin diferencias aritméticas"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (analisis.existeDiferencia) {
                            "El salario neto declarado " +
                                    "no coincide con el calculado."
                        } else {
                            "El salario neto declarado " +
                                    "coincide con el calculado."
                        }
                    )
                }
            }

            Text(
                text = "Observaciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            analisis.observaciones.forEach { observacion ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• $observacion",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Text(
                text = "Recomendaciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            analisis.recomendaciones.forEach { recomendacion ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• $recomendacion",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (analisis.requiereRevision) {

                Text(
                    text = "Este análisis es orientativo " +
                            "y no constituye validación fiscal. " +
                            "Verifica las retenciones con " +
                            "fuentes oficiales del SAT e IMSS.",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
