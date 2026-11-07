
package com.example.prisma.ai

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Identifica el periodo de pago dentro de un PDF
 * de nómina.
 *
 * Reconoce:
 * 19/Oct/2026 Al: 25/Oct/2026
 * 19/Oct/2026 25/Oct/2026
 * 19/10/2026 al 25/10/2026
 *
 * Devuelve AAAA-MM o null.
 */
class PayrollPeriodExtractor {

    private val patronFecha = Regex(
        """(?<!\d)\d{1,2}/(?:[A-Za-z]{3,4}|\d{1,2})/\d{4}(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    private val meses = mapOf(
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

    private data class Candidato(
        val inicio: LocalDate,
        val fin: LocalDate,
        val posicion: Int,
        val puntuacion: Int
    )

    fun extraerPeriodo(texto: String): String? {

        if (texto.isBlank()) return null

        val coincidencias = patronFecha
            .findAll(texto)
            .toList()

        if (coincidencias.size < 2) {
            return null
        }

        val candidatos = mutableListOf<Candidato>()

        for (i in 0 until coincidencias.size - 1) {

            val primera = coincidencias[i]
            val segunda = coincidencias[i + 1]

            val inicio = convertirFecha(primera.value)
                ?: continue

            val fin = convertirFecha(segunda.value)
                ?: continue

            val dias = ChronoUnit.DAYS.between(inicio, fin)

            // Evitar fechas invertidas o rangos demasiado largos.
            if (dias !in 0..31) {
                continue
            }

            // Separación entre las fechas en el texto.
            val separacion = segunda.range.first -
                    primera.range.last - 1

            if (separacion > 80) {
                continue
            }

            val textoIntermedio = texto.substring(
                primera.range.last + 1,
                segunda.range.first
            )

            // Aceptar fechas separadas por:
            // espacios, saltos de línea, "Al:" o "a".
            val separacionValida = Regex(
                """^\s*(?:(?:al|a)\s*:?\s*)?$""",
                RegexOption.IGNORE_CASE
            ).matches(textoIntermedio)

            if (!separacionValida) {
                continue
            }

            // Revisar texto cercano para priorizar
            // la sección del periodo de pago.
            val inicioContexto = maxOf(
                0,
                primera.range.first - 180
            )

            val contexto = texto.substring(
                inicioContexto,
                primera.range.first
            ).lowercase(Locale.ROOT)

            val tieneEtiqueta =
                contexto.contains("periodo de pago") ||
                        contexto.contains("período de pago") ||
                        contexto.contains("periodo pagado")

            val puntuacion = when {

                tieneEtiqueta -> 3

                textoIntermedio.contains(
                    Regex("""\bal\b""", RegexOption.IGNORE_CASE)
                ) -> 2

                else -> 1
            }

            candidatos.add(
                Candidato(
                    inicio = inicio,
                    fin = fin,
                    posicion = primera.range.first,
                    puntuacion = puntuacion
                )
            )
        }

        // Elegir el candidato más asociado al
        // periodo de pago y no al timbrado.
        val elegido = candidatos
            .sortedWith(
                compareByDescending<Candidato> {
                    it.puntuacion
                }.thenBy {
                    it.posicion
                }
            )
            .firstOrNull()
            ?: return null

        val anio = elegido.inicio.year

        val mes = elegido.inicio.monthValue
            .toString()
            .padStart(2, '0')

        return "$anio-$mes"
    }

    private fun convertirFecha(
        texto: String
    ): LocalDate? {

        val partes = texto.split("/")

        if (partes.size != 3) {
            return null
        }

        val dia = partes[0].toIntOrNull()
            ?: return null

        val mesTexto = partes[1]
            .uppercase(Locale.ROOT)

        val mes = mesTexto.toIntOrNull()
            ?: meses[mesTexto]
            ?: return null

        val anio = partes[2].toIntOrNull()
            ?: return null

        return try {

            LocalDate.of(anio, mes, dia)

        } catch (e: Exception) {

            null
        }
    }
}
