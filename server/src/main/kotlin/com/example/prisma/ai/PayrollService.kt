
package com.example.prisma.ai

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class GeminiObservaciones(
    val resumen: String,
    val observaciones: List<String>,
    val recomendaciones: List<String>,
    val requiereRevision: Boolean
)

class PayrollService(
    private val geminiClient: GeminiClient = GeminiClient()
) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun analizarNomina(
        nomina: PayrollRequest
    ): PayrollAnalysis {

        // 1. Validar información recibida
        require(
            Regex("""\d{4}-(0[1-9]|1[0-2])""")
                .matches(nomina.periodo)
        ) {
            "El periodo debe tener formato AAAA-MM"
        }

        val valores = listOf(
            nomina.salarioBruto,
            nomina.isr,
            nomina.imss,
            nomina.otrasDeducciones,
            nomina.salarioNeto
        )

        require(valores.all { it.isFinite() && it >= 0.0 }) {
            "Los importes deben ser finitos y no negativos"
        }

        // 2. Convertir cantidades a BigDecimal
        val bruto = dinero(nomina.salarioBruto)
        val isr = dinero(nomina.isr)
        val imss = dinero(nomina.imss)
        val otras = dinero(nomina.otrasDeducciones)
        val netoDeclarado = dinero(nomina.salarioNeto)

        // 3. Realizar cálculos con Kotlin
        val totalDeducciones = isr + imss + otras
        val netoCalculado = bruto - totalDeducciones
        val diferencia = netoDeclarado - netoCalculado

        require(totalDeducciones <= bruto) {
            "Las deducciones superan el salario bruto"
        }

        val existeDiferencia =
            diferencia.abs() > BigDecimal("0.01")

        // 4. Preparar instrucciones para Gemini
        val prompt = """
            Eres un asistente educativo especializado
            en recibos de nómina de México.

            Analiza exclusivamente estos importes
            ficticios o previamente anonimizados.

            Periodo: ${nomina.periodo}
            Salario bruto: $bruto MXN
            ISR: $isr MXN
            IMSS: $imss MXN
            Otras deducciones: $otras MXN

            Total de deducciones calculado:
            $totalDeducciones MXN

            Salario neto declarado:
            $netoDeclarado MXN

            Salario neto calculado:
            $netoCalculado MXN

            Diferencia:
            $diferencia MXN

            Existe diferencia aritmética:
            $existeDiferencia

            INSTRUCCIONES:
            - Explica brevemente los resultados.
            - Señala las diferencias aritméticas.
            - Identifica datos faltantes para
              verificar ISR e IMSS.
            - No inventes tarifas fiscales.
            - No afirmes que las retenciones son
              legalmente correctas.
            - No modifiques los importes calculados.
            - Responde exclusivamente en español.

            Devuelve únicamente un objeto JSON:

            {
              "resumen": "Explicación breve",
              "observaciones": [
                "Observación 1",
                "Observación 2"
              ],
              "recomendaciones": [
                "Recomendación 1",
                "Recomendación 2"
              ],
              "requiereRevision": true
            }

            No incluyas bloques Markdown.
            No incluyas campos adicionales.
        """.trimIndent()

        // 5. Consultar Google Gemini
        val respuesta = geminiClient.generarRespuesta(
            prompt = prompt,
            formatoJson = true
        )

        // 6. Convertir el JSON a un objeto Kotlin
        val resultadoIA =
            json.decodeFromString<GeminiObservaciones>(
                respuesta
            )

        require(
            resultadoIA.resumen.isNotBlank() &&
                    resultadoIA.observaciones.isNotEmpty() &&
                    resultadoIA.recomendaciones.isNotEmpty()
        ) {
            "Gemini devolvió un análisis incompleto"
        }

        // 7. Combinar cálculos locales y observaciones
        return PayrollAnalysis(
            resumen = resultadoIA.resumen,

            // Cantidades calculadas por Kotlin
            percepciones = bruto.toDouble(),
            deducciones = totalDeducciones.toDouble(),
            salarioNeto = netoCalculado.toDouble(),

            // Nuevas propiedades
            salarioNetoDeclarado = netoDeclarado.toDouble(),
            diferencia = diferencia.toDouble(),
            existeDiferencia = existeDiferencia,

            // Observaciones generadas por Gemini
            observaciones = resultadoIA.observaciones,
            recomendaciones = resultadoIA.recomendaciones,

            // Todavía no se validan las retenciones fiscales
            requiereRevision = true
        )

    }

    private fun dinero(valor: Double): BigDecimal =
        BigDecimal.valueOf(valor)
            .setScale(2, RoundingMode.HALF_UP)
}
