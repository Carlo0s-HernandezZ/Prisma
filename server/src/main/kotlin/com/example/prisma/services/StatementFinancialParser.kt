package com.example.prisma.services

import com.example.prisma.api.StatementExtractionResponse
import com.example.prisma.api.StatementFields
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Lectura preliminar local.
 * Solo devuelve valores financieros; nunca completa datos faltantes.
 */
object StatementFinancialParser {

    private enum class Kind {
        MONEY,
        DATE,
        RATE,
    }

    private enum class Field(
        val key: String,
        val label: String,
        val kind: Kind,
        pattern: String,
    ) {
        MINIMUM(
            "pagoMinimo",
            "Pago mínimo",
            Kind.MONEY,
            "(?:tu\\s+)?pago\\s+minimo(?:\\s+requerido)?(?!\\s*(?:\\+|mas\\b|y\\b))",
        ),

        INTEREST_FREE(
            "pagoParaNoGenerarIntereses",
            "Pago para no generar intereses",
            Kind.MONEY,
            "(?:tu\\s+)?pago\\s+(?:para\\s+)?(?:no\\s+generar|evitar)\\s+intereses",
        ),

        BALANCE(
            "saldoTotal",
            "Saldo total (opcional)",
            Kind.MONEY,
            "saldo\\s+(?:total(?:\\s+al\\s+corte)?|al\\s+corte|deudor\\s+total)(?!\\s+(?:(?:a|en)\\s+)?(?:meses|msi)\\b)",
        ),

        CUTOFF(
            "fechaCorte",
            "Fecha de corte (opcional)",
            Kind.DATE,
            "fecha\\s+(?:de\\s+)?corte",
        ),

        DEADLINE(
            "fechaLimitePago",
            "Fecha límite de pago",
            Kind.DATE,
            "fecha\\s+(?:(?:limite|maxima)\\s+(?:(?:de|para)\\s+)?pago|de\\s+pago)",
        ),

        RATE(
            "tasaInteresAnual",
            "Tasa ordinaria anual (opcional)",
            Kind.RATE,
            "tasa\\s+(?:(?:de\\s+)?interes\\s+(?:ordinari[oa]\\s+)?anual(?:\\s+(?:ordinaria|variable))?|ordinaria\\s+anual|anual\\s+ordinaria)",
        ),

        MSI(
            "mensualidadesMsi",
            "Mensualidades MSI del periodo (opcional)",
            Kind.MONEY,
            "(?:total\\s+(?:de\\s+)?mensualidades\\s+(?:a\\s+)?(?:meses\\s+sin\\s+intereses|msi)(?:\\s+del\\s+periodo)?|mensualidades\\s+(?:a\\s+)?meses\\s+sin\\s+intereses\\s+del\\s+periodo|pago\\s+mensual\\s+total\\s+(?:de\\s+)?(?:compras\\s+a\\s+)?meses\\s+sin\\s+intereses)",
        );

        val regex = Regex(
            "\\b(?:" + pattern +
                    ")(?=\\b|[0-9]{1,2}(?=[:\\s]|$))",
        )
    }

    private data class Token(
        val range: IntRange,
        val value: String?,
    )

    private val money = Regex(
        "(?<![\\p{L}\\d.,])([+\\-−]?)\\s*\\$?\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]{2})(?![0-9.,]|\\s*%)",
    )

    private val rate = Regex(
        "(?<![\\d.,])([+\\-−]?)\\s*([0-9]{1,4}(?:[.,][0-9]{1,4})?)\\s*%",
    )

    private val iso = Regex(
        "(?<![0-9])([0-9]{4})-([0-9]{2})-([0-9]{2})(?![0-9])",
    )

    private val numeric = Regex(
        "(?<![0-9])([0-9]{1,2})[/-]([0-9]{1,2})[/-]([0-9]{4})(?![0-9])",
    )

    private val spanish = Regex(
        "(?<![0-9])([0-9]{1,2})[\\s/-]+(?:de\\s+)?([a-z]+)\\.?[\\s/-]+(?:de\\s+)?([0-9]{4})(?![0-9])",
    )

    private val months = mapOf(
        "ene" to 1,
        "enero" to 1,
        "feb" to 2,
        "febrero" to 2,
        "mar" to 3,
        "marzo" to 3,
        "abr" to 4,
        "abril" to 4,
        "may" to 5,
        "mayo" to 5,
        "jun" to 6,
        "junio" to 6,
        "jul" to 7,
        "julio" to 7,
        "ago" to 8,
        "agosto" to 8,
        "sep" to 9,
        "sept" to 9,
        "septiembre" to 9,
        "setiembre" to 9,
        "oct" to 10,
        "octubre" to 10,
        "nov" to 11,
        "noviembre" to 11,
        "dic" to 12,
        "diciembre" to 12,
    )

    private val maximum = BigDecimal("9999999999.99")

    private enum class Area {
        GENERIC,
        HEADER,
        SIMULATION,
        SUMMARY,
        USAGE,
        IGNORE,
    }

    private data class Hit(
        val value: String,
        val page: Int,
        val priority: Int,
    )

    fun parse(
        pages: List<String>,
        archivos: Int,
        warnings: List<String> = emptyList(),
    ): StatementExtractionResponse {
        val hits = Field.entries.associateWith {
            mutableListOf<Hit>()
        }

        val blocked = Field.entries.associateWith {
            mutableSetOf<Int>()
        }

        pages.forEachIndexed { pageIndex, page ->
            val lines = page.lines()
                .map(::normalize)
                .filter { it.isNotBlank() }

            var area = Area.GENERIC

            lines.forEachIndexed lineLoop@ { index, line ->
                when {
                    "tu pago requerido este periodo" in line -> {
                        area = Area.HEADER
                        return@lineLoop
                    }

                    line.startsWith("cuanto pagarias") -> {
                        area = Area.SIMULATION
                        return@lineLoop
                    }

                    "resumen de cargos y abonos del periodo" in line ||
                            line.startsWith("indicadores del costo anual") -> {
                        area = Area.SUMMARY
                        return@lineLoop
                    }

                    "nivel de uso de tu tarjeta" in line -> {
                        area = Area.USAGE
                        return@lineLoop
                    }

                    Regex(
                        "^(?:glosario|notas aclaratorias|notas:)(?:\\b|.*)",
                    ).containsMatchIn(line) -> {
                        area = Area.IGNORE
                        return@lineLoop
                    }
                }

                if (
                    area == Area.SIMULATION ||
                    area == Area.IGNORE
                ) {
                    return@lineLoop
                }

                if (
                    Regex(
                        "\\b(ejemplo|hipotetico|si\\s+usted|si\\s+paga)\\b",
                    ).containsMatchIn(line)
                ) {
                    return@lineLoop
                }

                val priority = when (area) {
                    Area.HEADER -> 3
                    Area.SUMMARY,
                    Area.USAGE,
                        -> 2

                    else -> 1
                }

                val labels = Field.entries.mapNotNull { field ->
                    field.regex.find(line)?.let {
                        field to it
                    }
                }.sortedBy {
                    it.second.range.first
                }

                if (labels.isEmpty()) {
                    return@lineLoop
                }

                // Las definiciones no deben invalidar el resumen financiero.
                if (
                    priority == 1 &&
                    line.substring(
                        0,
                        labels.first().second.range.first,
                    ).any { it.isLetter() }
                ) {
                    return@lineLoop
                }

                if (priority == 1 && labels.size > 1) {
                    labels.forEach {
                        blocked.getValue(it.first).add(priority)
                    }
                    return@lineLoop
                }

                labels.forEachIndexed labelLoop@ {
                        labelIndex,
                        (field, label),
                    ->

                    if (
                        area == Area.HEADER &&
                        field !in listOf(
                            Field.MINIMUM,
                            Field.INTEREST_FREE,
                            Field.CUTOFF,
                            Field.DEADLINE,
                        )
                    ) {
                        return@labelLoop
                    }

                    if (
                        field == Field.RATE &&
                        "mensual" in line
                    ) {
                        return@labelLoop
                    }

                    if (
                        field == Field.BALANCE &&
                        Regex(
                            "\\b(msi|meses|disponible)\\b|\\ba\\s+favor\\b",
                        ).containsMatchIn(line)
                    ) {
                        return@labelLoop
                    }

                    val end = labels.getOrNull(labelIndex + 1)
                        ?.second
                        ?.range
                        ?.first
                        ?: line.length

                    var suffix = stripFootnote(
                        line.substring(
                            label.range.last + 1,
                            end,
                        ),
                        field.kind,
                    )

                    if (
                        field == Field.MINIMUM &&
                        Regex(
                            "^\\s*(?:requerido\\s*)?(?:\\+|mas\\b|y\\b)",
                        ).containsMatchIn(suffix)
                    ) {
                        return@labelLoop
                    }

                    var values = tokens(suffix, field.kind)

                    if (
                        field == Field.RATE &&
                        priority >= 2
                    ) {
                        val tableRate = rateFromPanel(
                            lines,
                            index,
                            label.range.first,
                        )

                        if (tableRate != null) {
                            hits.getValue(field).add(
                                Hit(
                                    tableRate,
                                    pageIndex + 1,
                                    priority,
                                ),
                            )
                            return@labelLoop
                        }
                    }

                    if (
                        field == Field.RATE &&
                        "cat" in line
                    ) {
                        return@labelLoop
                    }

                    val mayLookNext = suffix.all {
                        it.isWhitespace() || it in ":;.-()"
                    }

                    if (values.isEmpty() && mayLookNext) {
                        val next = lines.getOrNull(index + 1)

                        if (
                            next != null &&
                            Field.entries.none {
                                it.regex.containsMatchIn(next)
                            }
                        ) {
                            val nextValues = tokens(
                                next,
                                field.kind,
                            )

                            if (
                                onlyValues(next, nextValues) ||
                                (
                                        priority >= 2 &&
                                                nextValues.size == 1
                                        )
                            ) {
                                suffix = next
                                values = nextValues
                            }
                        }
                    }

                    if (values.isEmpty()) {
                        return@labelLoop
                    }

                    if (
                        priority == 1 &&
                        field.kind == Kind.MONEY &&
                        !onlyValues(
                            suffix.take(values.first().range.first),
                            emptyList(),
                            allowEmpty = true,
                        )
                    ) {
                        return@labelLoop
                    }

                    if (
                        values.any { it.value == null } ||
                        values.map { it.value }.distinct().size > 1
                    ) {
                        blocked.getValue(field).add(priority)
                    } else {
                        hits.getValue(field).add(
                            Hit(
                                values.first().value!!,
                                pageIndex + 1,
                                priority,
                            ),
                        )
                    }
                }
            }
        }

        val notices = warnings.toMutableList()
        val origins = linkedMapOf<String, Int>()

        val resolved = Field.entries.associateWith { field ->
            val highest = maxOf(
                hits.getValue(field).maxOfOrNull {
                    it.priority
                } ?: 0,
                blocked.getValue(field).maxOrNull() ?: 0,
            )

            val best = hits.getValue(field)
                .filter { it.priority == highest }
                .distinctBy { it.value }

            if (
                highest > 0 &&
                (
                        highest in blocked.getValue(field) ||
                                best.size > 1
                        )
            ) {
                notices +=
                    "No se pudo asociar un valor único a: " +
                            field.label +
                            ". Revísalo manualmente."

                null
            } else {
                best.singleOrNull()?.let {
                    origins[field.key] = it.page
                    it.value
                }
            }
        }.toMutableMap()

        val balance = resolved[Field.BALANCE]?.toBigDecimalOrNull()
        val payment = resolved[Field.INTEREST_FREE]?.toBigDecimalOrNull()

        if (
            balance != null &&
            payment != null &&
            balance < payment
        ) {
            resolved[Field.BALANCE] = null
            origins.remove(Field.BALANCE.key)

            notices +=
                "El saldo total impreso es menor que el pago para no generar intereses. " +
                        "El documento presenta una inconsistencia; confirma el saldo con el banco."
        }

        val cutoff = resolved[Field.CUTOFF]
        val period = cutoff?.take(7)

        if (period != null) {
            origins["periodo"] = origins.getValue(Field.CUTOFF.key)
        }

        val fields = StatementFields(
            periodo = period,
            fechaCorte = cutoff,
            fechaLimitePago = resolved[Field.DEADLINE],
            saldoTotal = resolved[Field.BALANCE],
            pagoMinimo = resolved[Field.MINIMUM],
            pagoParaNoGenerarIntereses = resolved[Field.INTEREST_FREE],
            tasaInteresAnual = resolved[Field.RATE],
            mensualidadesMsi = resolved[Field.MSI],
        )

        val pending = mutableListOf<String>()

        if (period == null) {
            pending += "Periodo del estado de cuenta"
        }

        Field.entries
            .filter { resolved[it] == null }
            .forEach { pending += it.label }

        notices +=
            "Lectura preliminar: confirma los importes y las fechas " +
                    "contra el estado de cuenta antes de planificar pagos."

        if (fields.tasaInteresAnual == null) {
            notices +=
                "La tasa ordinaria anual no se identificó. " +
                        "No se sustituye por CAT ni por una tasa mensual."
        }

        return StatementExtractionResponse(
            fields,
            pages.size,
            archivos,
            pending,
            notices.distinct(),
            origins,
        )
    }

    private fun stripFootnote(
        suffix: String,
        kind: Kind,
    ): String {
        var value = suffix.replace(
            Regex("^[0-9]{1,2}(?=[:\\s]|$)"),
            "",
        )

        if (kind == Kind.MONEY) {
            value = value.replace(
                Regex("^\\s*:\\s*[0-9]{1,2}\\s+(?=\\$)"),
                "",
            )
        }

        if (kind == Kind.DATE) {
            value = value.replace(
                Regex(
                    "^\\s*:\\s*[0-9]{1,2}\\s+(?=(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b)",
                ),
                "",
            )
        }

        return value
    }

    /**
     * Identifica columnas CAT/tasa cuando sus encabezados están ordenados
     * y la fila contiene exactamente dos porcentajes.
     */
    private fun rateFromPanel(
        lines: List<String>,
        index: Int,
        labelStart: Int,
    ): String? {
        val header = lines[index]

        val cat = Regex("\\bcat(?:[:0-9\\s]|$)")
            .find(header)
            ?.range
            ?.first
            ?: return null

        if (cat >= labelStart) {
            return null
        }

        for (offset in 1..4) {
            val line = lines.getOrNull(index + offset) ?: break
            val values = tokens(line, Kind.RATE)

            if (values.isNotEmpty()) {
                return if (
                    values.size == 2 &&
                    values.all { it.value != null } &&
                    "sin iva" in line
                ) {
                    values[1].value
                } else {
                    null
                }
            }
        }

        return null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\t\\u00a0 ]+"), " ")
            .trim()

    private fun tokens(
        text: String,
        kind: Kind,
    ): List<Token> = when (kind) {
        Kind.MONEY -> money.findAll(text).map { match ->
            val amount = match.groupValues[2]
                .replace(",", "")
                .toBigDecimalOrNull()

            Token(
                match.range,
                amount?.takeIf {
                    match.groupValues[1] !in listOf("-", "−") &&
                            it.signum() >= 0 &&
                            it <= maximum
                }?.setScale(
                    2,
                    RoundingMode.UNNECESSARY,
                )?.toPlainString(),
            )
        }.toList()

        Kind.RATE -> rate.findAll(text).map { match ->
            Token(
                match.range,
                match.groupValues[2]
                    .replace(',', '.')
                    .toBigDecimalOrNull()
                    ?.takeIf {
                        match.groupValues[1] !in listOf("-", "−") &&
                                it >= BigDecimal.ZERO &&
                                it <= BigDecimal("1000")
                    }
                    ?.stripTrailingZeros()
                    ?.toPlainString(),
            )
        }.toList()

        Kind.DATE -> buildList {
            iso.findAll(text).forEach {
                add(
                    Token(
                        it.range,
                        date(
                            it.groupValues[1],
                            it.groupValues[2],
                            it.groupValues[3],
                        ),
                    ),
                )
            }

            numeric.findAll(text).forEach {
                add(
                    Token(
                        it.range,
                        date(
                            it.groupValues[3],
                            it.groupValues[2],
                            it.groupValues[1],
                        ),
                    ),
                )
            }

            spanish.findAll(text).forEach {
                val month = months[it.groupValues[2]]

                add(
                    Token(
                        it.range,
                        month?.let { monthNumber ->
                            date(
                                it.groupValues[3],
                                monthNumber.toString(),
                                it.groupValues[1],
                            )
                        },
                    ),
                )
            }
        }.distinctBy {
            it.range
        }.sortedBy {
            it.range.first
        }
    }

    private fun date(
        year: String,
        month: String,
        day: String,
    ): String? = runCatching {
        LocalDate.of(
            year.toInt(),
            month.toInt(),
            day.toInt(),
        ).takeIf {
            it.year in 1..9999
        }?.toString()
    }.getOrNull()

    private fun onlyValues(
        text: String,
        values: List<Token>,
        allowEmpty: Boolean = false,
    ): Boolean {
        if (values.isEmpty() && !allowEmpty) {
            return false
        }

        var remaining = text

        values.sortedByDescending {
            it.range.first
        }.forEach {
            remaining = remaining.removeRange(it.range)
        }

        return remaining
            .replace(Regex("\\b(mxn|pesos|mn)\\b"), "")
            .all {
                it.isWhitespace() || it in "$:;.,()-−"
            }
    }
}