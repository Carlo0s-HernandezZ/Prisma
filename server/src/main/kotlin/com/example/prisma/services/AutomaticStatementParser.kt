package com.example.prisma.services

import com.example.prisma.api.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

object AutomaticStatementParser {
    private enum class Area {
        GENERIC, HEADER, SUMMARY, USAGE, SIMULATION, IGNORE
    }

    private enum class Value(pattern: String) {
        REGULAR("cargos\\s+regulares\\s*\\(\\s*no\\s+a\\s+meses\\s*\\)"),
        CAPITAL("cargos\\s+(?:y\\s+)?(?:de\\s+)?compras\\s+a\\s+meses\\s*\\(capital\\)"),
        MONTHS("saldo\\s+cargos?\\s+a\\s+meses"),
        COMBINED("pago\\s+minimo\\s*\\+\\s*compras\\s+y\\s+cargos?\\s+diferidos\\s+a\\s+meses");

        val regex = Regex(
            "\\b(?:$pattern)(?=\\b|[0-9]{1,2}(?=[:\\s]|$)|[:\\s]|$)",
        )
    }

    private data class Candidate(val value: String, val priority: Int)

    private val moneyPattern =
        "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]{2}"

    private val money = Regex(
        "(?<![\\p{L}\\d.,])([+\\-−]?)\\s*\\$?\\s*($moneyPattern)(?![0-9.,]|\\s*%)",
    )

    private val operationDate =
        "(?:[0-9]{1,2}[-/ ]+[a-z]{3,10}[-/ ]+[0-9]{4}|[0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{4}|[0-9]{4}-[0-9]{2}-[0-9]{2})"

    private val rowStart = Regex("^$operationDate\\s+")

    private val row = Regex(
        "^($operationDate)\\s+(.+?)\\s+\\$?\\s*($moneyPattern)\\s+\\$?\\s*($moneyPattern)\\s+\\$?\\s*($moneyPattern)\\s+([0-9]{1,4})\\s*(?:de|/)\\s*([0-9]{1,4})\\s+([0-9]{1,4}(?:[.,][0-9]{1,4})?)\\s*%\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private val maxMoney = BigDecimal("9999999999.99")

    fun detect(
        pages: List<String>,
        warnings: List<String> = emptyList(),
    ): StatementDetection {
        val summary = StatementFinancialParser.parse(pages, 1, warnings)
        val values = Value.entries.associateWith { mutableListOf<Candidate>() }
        val plans = mutableListOf<StatementInstallment>()

        var area = Area.GENERIC
        var section: Boolean? = null
        var pending: String? = null
        var pendingPage = 0
        var brokenRows = false

        pages.forEachIndexed { pageIndex, page ->
            val lines = page.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            lines.forEachIndexed lineLoop@ { index, printed ->
                val line = normalize(printed)

                when {
                    "tu pago requerido este periodo" in line ->
                        area = Area.HEADER

                    line.startsWith("cuanto pagarias") ->
                        area = Area.SIMULATION

                    "resumen de cargos y abonos del periodo" in line ->
                        area = Area.SUMMARY

                    "nivel de uso de tu tarjeta" in line ->
                        area = Area.USAGE

                    Regex("^(?:glosario|notas aclaratorias|notas:)(?:\\b|.*)")
                        .containsMatchIn(line) -> {
                        if (pending != null) brokenRows = true
                        area = Area.IGNORE
                        section = null
                        pending = null
                    }

                    "desglose de movimientos" in line -> {
                        area = Area.GENERIC
                        section = null
                    }
                }

                if (area == Area.IGNORE || area == Area.SIMULATION) {
                    return@lineLoop
                }

                val priority = when (area) {
                    Area.HEADER -> 3
                    Area.SUMMARY, Area.USAGE -> 2
                    else -> 0
                }

                if (priority > 0) {
                    Value.entries.forEach { key ->
                        val label = key.regex.find(line) ?: return@forEach
                        val suffix = line.substring(label.range.last + 1)

                        val candidate = amountAfter(suffix)
                            ?: if (suffix.matches(Regex("[0-9:; .()]*"))) {
                                (1..2).firstNotNullOfOrNull { offset ->
                                    lines.getOrNull(index + offset)
                                        ?.let(::normalize)
                                        ?.takeIf {
                                            Regex("^[+:; ]*\\$?\\s*[0-9][0-9,.]*")
                                                .containsMatchIn(it)
                                        }
                                        ?.let(::amountAfter)
                                }
                            } else null

                        if (candidate != null) {
                            values.getValue(key).add(Candidate(candidate, priority))
                        }
                    }
                }

                val heading = line + " " +
                        lines.getOrNull(index + 1).orEmpty().let(::normalize)

                if (
                    Regex(
                        "^compras\\s+y\\s+cargos\\s+diferidos\\s+a\\s+meses\\s+(?:sin|con)\\s+intereses\\b",
                    ).containsMatchIn(heading)
                ) {
                    if (pending != null) brokenRows = true
                    section = "meses sin intereses" in heading
                    pending = null
                    return@lineLoop
                }

                if (
                    Regex(
                        "^(?:cargos,?\\s+abonos|compras\\s+y\\s+cargos\\s+regulares|saldo\\s+sobre|disposiciones\\s+de\\s+efectivo|total\\s+de\\s+(?:cargos|compras))\\b",
                    ).containsMatchIn(line)
                ) {
                    if (pending != null) brokenRows = true
                    section = null
                    pending = null
                }

                val interestFree = section ?: return@lineLoop

                if (rowStart.containsMatchIn(line)) {
                    if (pending != null) brokenRows = true
                    pending = printed
                    pendingPage = pageIndex + 1
                } else if (pending != null) {
                    if (pending!!.length + printed.length > 2000) {
                        brokenRows = true
                        pending = null
                        return@lineLoop
                    }
                    pending += " " + printed
                }

                val candidate = pending ?: return@lineLoop
                val matched = row.matchEntire(candidate) ?: return@lineLoop

                val original = canonicalMoney(matched.groupValues[3])
                val balance = canonicalMoney(matched.groupValues[4])
                val installment = canonicalMoney(matched.groupValues[5])
                val current = matched.groupValues[6].toIntOrNull()
                val total = matched.groupValues[7].toIntOrNull()

                val rate = matched.groupValues[8]
                    .replace(',', '.')
                    .toBigDecimalOrNull()

                val description = matched.groupValues[2]
                    .replace(
                        Regex("(?i)[; ]*tarjeta\\s+(?:digital|titular)\\b.*"),
                        "",
                    )
                    .replace(Regex("[0-9]{8,}"), "…")
                    .trim()
                    .take(160)

                if (
                    original == null || balance == null || installment == null ||
                    current == null || total == null || total !in 1..1200 ||
                    current !in 1..total || rate == null ||
                    rate < BigDecimal.ZERO || rate > BigDecimal("1000") ||
                    (interestFree && rate.signum() != 0) || description.isBlank()
                ) {
                    brokenRows = true
                } else {
                    plans += StatementInstallment(
                        descripcion = description,
                        montoOriginal = original,
                        saldoPendiente = balance,
                        cuota = installment,
                        pagoActual = current,
                        totalPagos = total,
                        tasa = rate.stripTrailingZeros().toPlainString(),
                        sinIntereses = interestFree,
                        pagina = pendingPage,
                    )
                }

                pending = null
            }
        }

        if (pending != null) brokenRows = true

        fun resolve(key: Value): String? {
            val entries = values.getValue(key)
            val highest = entries.maxOfOrNull { it.priority } ?: return null

            return entries.filter { it.priority == highest }
                .map { it.value }
                .distinct()
                .singleOrNull()
        }

        val regular = resolve(Value.REGULAR)
        val capital = resolve(Value.CAPITAL)
        val monthBalance = resolve(Value.MONTHS)
        val combined = resolve(Value.COMBINED)
        val msi = plans.filter { it.sinIntereses }
        val hasInterest = plans.any { !it.sinIntereses }

        val msiTotal = if (msi.isNotEmpty()) {
            msi.fold(BigDecimal("0.00")) { sum, plan ->
                sum + BigDecimal(plan.cuota)
            }.toPlainString()
        } else if (capital == "0.00" && monthBalance == "0.00") {
            "0.00"
        } else null

        var incomplete = brokenRows || plans.size > 100

        if (
            plans.isNotEmpty() && !hasInterest && capital != null &&
            BigDecimal(capital).compareTo(BigDecimal(msiTotal!!)) != 0
        ) incomplete = true

        if (
            plans.isNotEmpty() && monthBalance != null &&
            plans.fold(BigDecimal.ZERO) { sum, plan ->
                sum + BigDecimal(plan.saldoPendiente)
            }.compareTo(BigDecimal(monthBalance)) != 0
        ) incomplete = true

        val nonRegular = msi.isNotEmpty() || hasInterest

        val kind = when {
            incomplete -> StatementPurchaseType.DESCONOCIDO
            nonRegular && regular?.toBigDecimalOrNull()?.signum() == 1 ->
                StatementPurchaseType.MIXTO
            hasInterest && msi.isNotEmpty() -> StatementPurchaseType.MIXTO
            hasInterest -> StatementPurchaseType.CON_INTERESES
            msi.isNotEmpty() -> StatementPurchaseType.MSI
            capital == "0.00" && monthBalance == "0.00" && regular != null ->
                StatementPurchaseType.UN_PAGO
            else -> StatementPurchaseType.DESCONOCIDO
        }

        val fields = summary.campos.copy(
            mensualidadesMsi = if (!incomplete) msiTotal else null,
        )

        val required = listOf(
            fields.periodo,
            fields.fechaCorte,
            fields.fechaLimitePago,
            fields.pagoMinimo,
            fields.pagoParaNoGenerarIntereses,
        )

        val invalidCritical = required.any { it == null } || runCatching {
            LocalDate.parse(fields.fechaLimitePago) <
                    LocalDate.parse(fields.fechaCorte) ||
                    BigDecimal(fields.pagoMinimo) >
                    BigDecimal(fields.pagoParaNoGenerarIntereses)
        }.getOrDefault(true)

        val mismatch = !invalidCritical && combined != null &&
                BigDecimal(combined) > BigDecimal(fields.pagoParaNoGenerarIntereses) &&
                nonRegular

        val status = when {
            invalidCritical || incomplete ||
                    kind == StatementPurchaseType.DESCONOCIDO ->
                StatementDetectionStatus.REQUIERE_DATO

            mismatch -> StatementDetectionStatus.IMPORTES_DISTINTOS
            else -> StatementDetectionStatus.LISTO
        }

        val notices = mutableListOf<String>()

        if (summary.advertencias.any { "inconsistencia" in it }) {
            notices += "El saldo total es contradictorio; no se utiliza para calcular el pago."
        }
        if (incomplete) {
            notices += "Falta leer una fila o los totales de las compras a meses no coinciden."
        }
        if (mismatch) {
            notices += "El banco imprime objetivos de pago distintos."
        }

        return StatementDetection(
            campos = fields,
            detalle = StatementBreakdown(
                tipo = kind,
                comprasUnPago = regular,
                cuotasMsi = if (!incomplete) msiTotal else null,
                saldoMeses = monthBalance,
                minimoConCuotas = combined,
                planes = plans.take(100),
            ),
            estado = status,
            avisos = notices,
            pregunta = when (status) {
                StatementDetectionStatus.REQUIERE_DATO ->
                    "Agrega una copia nítida del resumen y del detalle de compras a meses."

                StatementDetectionStatus.IMPORTES_DISTINTOS ->
                    "¿Qué importe confirmó el banco para no generar intereses?"

                else -> null
            },
            opcionesPago = if (mismatch) {
                listOf(
                    fields.pagoParaNoGenerarIntereses!!,
                    combined!!,
                ).distinct()
            } else emptyList(),
            paginasLeidas = pages.size,
        )
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\t\\u00a0 ]+"), " ")
            .trim()

    private fun canonicalMoney(value: String): String? =
        value.replace(",", "")
            .toBigDecimalOrNull()
            ?.takeIf { it >= BigDecimal.ZERO && it <= maxMoney }
            ?.setScale(2, RoundingMode.UNNECESSARY)
            ?.toPlainString()

    private fun amountAfter(value: String): String? {
        val found = money.find(value) ?: return null
        if (found.groupValues[1] in listOf("-", "−")) return null
        return canonicalMoney(found.groupValues[2])
    }
}