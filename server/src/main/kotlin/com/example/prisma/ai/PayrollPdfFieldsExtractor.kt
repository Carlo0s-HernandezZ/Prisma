
package com.example.prisma.ai

import java.math.BigDecimal
import java.math.RoundingMode

data class PayrollPdfFields(
    val salarioBruto: BigDecimal?,
    val isr: BigDecimal?,
    val imss: BigDecimal?,
    val otrasDeducciones: BigDecimal?,
    val salarioNeto: BigDecimal?,
    val camposNoIdentificados: List<String>
)

class PayrollPdfFieldsExtractor {

    // Importes en formato mexicano:
    // $3,546.06
    // $339.52
    // $0.00
    private val patronMoneda = Regex(
        """\$\s*(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{2})?"""
    )

    fun extraer(texto: String): PayrollPdfFields {

        require(texto.length <= 100_000) {
            "El texto del PDF supera el límite permitido."
        }

        // ----------------------------------------
        // 1. TOTAL DE PERCEPCIONES
        // ----------------------------------------

        val bruto = buscarImporte(
            texto,
            listOf(
                "Total de Percepciones",
                "Total Percepciones",
                "Salario Bruto",
                "Sueldo Bruto"
            )
        )

        // ----------------------------------------
        // 2. ISR
        // ----------------------------------------

        val isr = buscarImporte(
            texto,
            listOf(
                "I.S.P.T.",
                "ISR Retenido",
                "Retención ISR",
                "Impuesto Sobre la Renta",
                "ISR"
            )
        )

        // ----------------------------------------
        // 3. IMSS
        // ----------------------------------------

        val imss = buscarImporte(
            texto,
            listOf(
                "I.M.S.S.",
                "Cuota IMSS",
                "Descuento IMSS",
                "Seguridad Social",
                "IMSS"
            )
        )

        // ----------------------------------------
        // 4. TOTAL DE DEDUCCIONES
        // ----------------------------------------

        val totalDeducciones = buscarImporte(
            texto,
            listOf(
                "Total de Deducciones",
                "Total Deducciones"
            )
        )

        // ----------------------------------------
        // 5. OTRAS DEDUCCIONES
        // ----------------------------------------

        val otrasExplicitas = buscarImporte(
            texto,
            listOf("Otras Deducciones")
        )

        // Si tenemos el total, ISR e IMSS,
        // el resto son otras deducciones.
        val otrasCalculadas = if (
            totalDeducciones != null &&
            isr != null &&
            imss != null
        ) {
            val restante = totalDeducciones
                .subtract(isr)
                .subtract(imss)

            restante.takeIf {
                it >= BigDecimal.ZERO
            }
        } else {
            null
        }

        val otras = otrasCalculadas ?: otrasExplicitas

        // ----------------------------------------
        // 6. NETO PAGADO
        // ----------------------------------------

        val neto = buscarImporte(
            texto,
            listOf(
                "Total Pagado",
                "Neto a Pagar",
                "Salario Neto",
                "Sueldo Neto",
                "Total Neto"
            )
        )

        // ----------------------------------------
        // 7. IDENTIFICAR DATOS FALTANTES
        // ----------------------------------------

        val faltantes = mutableListOf<String>()

        if (bruto == null) {
            faltantes.add("Salario bruto")
        }

        if (isr == null) {
            faltantes.add("ISR")
        }

        if (imss == null) {
            faltantes.add("IMSS")
        }

        if (otras == null) {
            faltantes.add("Otras deducciones")
        }

        if (neto == null) {
            faltantes.add("Salario neto")
        }

        return PayrollPdfFields(
            salarioBruto = bruto,
            isr = isr,
            imss = imss,
            otrasDeducciones = otras,
            salarioNeto = neto,
            camposNoIdentificados = faltantes
        )
    }

    // ============================================
    // EXTRAER IMPORTE DESPUÉS DE UNA ETIQUETA
    // ============================================

    private fun buscarImporte(
        texto: String,
        etiquetas: List<String>
    ): BigDecimal? {

        val lineas = texto.lines()

        for (etiqueta in etiquetas) {

            val patronEtiqueta = Regex(
                Regex.escape(etiqueta),
                RegexOption.IGNORE_CASE
            )

            val encontrados = mutableListOf<BigDecimal>()

            for (linea in lineas) {

                val coincidencia = patronEtiqueta.find(linea)
                    ?: continue

                // Buscar únicamente después
                // de la etiqueta de interés.
                val resto = linea.substring(
                    coincidencia.range.last + 1
                )

                val moneda = patronMoneda.find(resto)
                    ?: continue

                val cantidad = convertirMoneda(
                    moneda.value
                ) ?: continue

                encontrados.add(cantidad)
            }

            // Si una etiqueta aparece con importes
            // distintos, no elegimos uno al azar.
            val valoresUnicos = encontrados.distinct()

            if (valoresUnicos.size == 1) {
                return valoresUnicos.first()
            }

            if (valoresUnicos.size > 1) {
                return null
            }
        }

        return null
    }

    // ============================================
    // CONVERSIÓN MONETARIA
    // ============================================

    private fun convertirMoneda(
        valor: String
    ): BigDecimal? {

        val limpio = valor
            .replace("$", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()

        val cantidad = limpio.toBigDecimalOrNull()
            ?: return null

        if (cantidad < BigDecimal.ZERO) {
            return null
        }

        return try {
            cantidad.setScale(
                2,
                RoundingMode.UNNECESSARY
            )
        } catch (e: ArithmeticException) {
            null
        }
    }
}
