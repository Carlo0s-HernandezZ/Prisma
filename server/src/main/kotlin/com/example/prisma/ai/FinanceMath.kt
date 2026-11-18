package com.example.prisma.ai

import com.example.prisma.api.CardSpendDto
import com.example.prisma.api.CategoryAmountDto
import com.example.prisma.api.FinancialHealthDto
import com.example.prisma.api.HealthFactorDto
import com.example.prisma.api.HealthLevel
import com.example.prisma.api.MonthSpendDto
import com.example.prisma.api.SpendingDashboardDto
import com.example.prisma.api.SpendingTrend
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.YearMonth
import kotlin.math.roundToInt

private val MONTHS_ES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

internal fun monthName(period: YearMonth): String = MONTHS_ES[period.monthValue - 1]

/** Gasto mes con mes, tendencia y proyección del siguiente mes. */
object SpendingMath {

    /** [byCard]: gasto de cada tarjeta en el mes (id de tarjeta → monto). */
    data class MonthTotal(
        val period: YearMonth,
        val total: BigDecimal,
        val hormiga: BigDecimal,
        val byCard: Map<String, BigDecimal> = emptyMap(),
    )

    /** Comparación justa entre dos meses: solo las tarjetas con estado en ambos. */
    data class Comparison(
        val last: BigDecimal,
        val previous: BigDecimal,
        val change: Int?,
        /** Tarjetas que solo aparecen en uno de los dos meses (no entran en la comparación). */
        val missingCards: Set<String>,
    )

    fun compare(last: MonthTotal, previous: MonthTotal): Comparison {
        if (last.byCard.isEmpty() || previous.byCard.isEmpty()) {
            return Comparison(last.total, previous.total, percentChange(previous.total, last.total), emptySet())
        }
        val common = last.byCard.keys intersect previous.byCard.keys
        val lastSum = common.fold(ZERO) { acc, id -> acc + last.byCard.getValue(id) }
        val prevSum = common.fold(ZERO) { acc, id -> acc + previous.byCard.getValue(id) }
        val missing = (last.byCard.keys union previous.byCard.keys) - common
        return Comparison(lastSum, prevSum, if (common.isEmpty()) null else percentChange(prevSum, lastSum), missing)
    }

    /** Cambio menor a este % se considera "estable". */
    private const val STABLE_PERCENT = 5

    fun dashboard(
        months: List<MonthTotal>,
        categories: List<CategoryAmountDto>,
        porTarjeta: List<CardSpendDto>,
        cardId: String?,
        cardNames: Map<String, String> = emptyMap(),
    ): SpendingDashboardDto {
        val sorted = months.sortedBy { it.period }.takeLast(12)
        if (sorted.isEmpty()) {
            return SpendingDashboardDto(
                tarjetaId = cardId,
                porTarjeta = porTarjeta,
                mensaje = "Sube un estado de cuenta en Análisis IA para ver tus gastos mes con mes.",
            )
        }

        val values = sorted.map { it.total }
        val average = values.fold(ZERO, BigDecimal::add).divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)
        val last = sorted.last()
        val previous = sorted.getOrNull(sorted.size - 2)
        val comparison = previous?.let { compare(last, it) }
        val change = comparison?.change
        val trend = when {
            change == null -> SpendingTrend.SIN_DATOS
            change > STABLE_PERCENT -> SpendingTrend.SUBE
            change < -STABLE_PERCENT -> SpendingTrend.BAJA
            else -> SpendingTrend.ESTABLE
        }
        val nextPeriod = last.period.plusMonths(1)
        // Con meses incompletos (faltan estados de alguna tarjeta) la proyección sería engañosa.
        val complete = comparison?.missingCards.isNullOrEmpty()
        val projected = if (complete) projection(values) else null

        val message = buildString {
            when (trend) {
                SpendingTrend.SUBE -> append(
                    "En ${monthName(last.period)} gastaste $change% más que en ${monthName(previous!!.period)}.",
                )
                SpendingTrend.BAJA -> append(
                    "¡Bien! En ${monthName(last.period)} gastaste ${-change!!}% menos que en ${monthName(previous!!.period)}.",
                )
                SpendingTrend.ESTABLE -> append("Tu gasto se mantuvo casi igual que el mes anterior.")
                SpendingTrend.SIN_DATOS -> append("Sube los estados de otros meses para ver si gastas más o menos.")
            }
            if (projected != null) {
                append(" Si sigues así, en ${monthName(nextPeriod)} gastarías cerca de ${StatementAnalysisService.mxn(projected)}.")
            }
            // Si un mes no tiene el estado de alguna tarjeta, la comparación solo usa las tarjetas de ambos meses.
            comparison?.missingCards?.takeIf { it.isNotEmpty() }?.let { missing ->
                append(
                    if (comparison.change == null) " No hay tarjetas con estado en ambos meses para comparar."
                    else " Solo comparamos las tarjetas con estado en ambos meses.",
                )
                // Qué estado falta exactamente: el del último mes o el del anterior.
                fun names(ids: Collection<String>) = ids.map { cardNames[it] ?: "una tarjeta" }.distinct().joinToString(" y ")
                val noLast = missing.filter { it !in last.byCard }
                val noPrevious = missing.filter { it !in previous!!.byCard }
                val parts = buildList {
                    if (noLast.isNotEmpty()) add("el de ${monthName(last.period)} de ${names(noLast)}")
                    if (noPrevious.isNotEmpty()) add("el de ${monthName(previous!!.period)} de ${names(noPrevious)}")
                }
                append(" Para ver tu total completo, sube " + parts.joinToString(" y ") + ".")
            }
        }

        return SpendingDashboardDto(
            tarjetaId = cardId,
            meses = sorted.map { MonthSpendDto(it.period.toString(), it.total.plain(), it.hormiga.plain()) },
            proyeccion = projected?.let { MonthSpendDto(nextPeriod.toString(), it.plain()) },
            promedio = average.plain(),
            tendencia = trend,
            cambioPorcentaje = change,
            mesMayorGasto = sorted.maxBy { it.total }.period.toString(),
            mesMenorGasto = sorted.minBy { it.total }.period.toString().takeIf { sorted.size > 1 },
            categorias = categories,
            porTarjeta = porTarjeta,
            mensaje = message,
        )
    }

    /**
     * Proyección del siguiente mes: recta de mínimos cuadrados sobre los últimos 6 meses
     * (con 2 meses es el promedio). Nunca es negativa. Null con menos de 2 meses.
     */
    fun projection(values: List<BigDecimal>): BigDecimal? {
        val recent = values.takeLast(6).map { it.toDouble() }
        if (recent.size < 2) return null
        if (recent.size == 2) return BigDecimal(recent.average()).money()
        val n = recent.size
        val xs = (0 until n).map { it.toDouble() }
        val meanX = xs.average()
        val meanY = recent.average()
        val slope = xs.indices.sumOf { (xs[it] - meanX) * (recent[it] - meanY) } /
            xs.sumOf { (it - meanX) * (it - meanX) }
        val next = meanY + slope * (n - meanX)
        return BigDecimal(maxOf(0.0, next)).money()
    }

    /** % de cambio redondeado; null si el valor anterior es 0. */
    fun percentChange(previous: BigDecimal, current: BigDecimal): Int? {
        if (previous.signum() == 0) return null
        return (current - previous).divide(previous, MathContext.DECIMAL64).multiply(BigDecimal(100)).toDouble().roundToInt()
    }
}

/** Puntaje de salud financiera (0..100) con factores explicados en lenguaje sencillo. */
object HealthMath {

    data class Inputs(
        val hasAnalysis: Boolean,
        val debt: BigDecimal = ZERO,
        val monthlyIncome: BigDecimal? = null,
        val paymentsDue: BigDecimal? = null,
        val creditLimit: BigDecimal? = null,
        val lastMonthSpend: BigDecimal? = null,
        /** Gasto del último mes solo de las tarjetas que también tienen el mes anterior. */
        val comparableLastSpend: BigDecimal? = null,
        val previousMonthSpend: BigDecimal? = null,
        val lastMonthHormiga: BigDecimal? = null,
        val lastMonthInterest: BigDecimal? = null,
        val openAlerts: Int = 0,
    )

    fun compute(inputs: Inputs): FinancialHealthDto {
        if (!inputs.hasAnalysis) {
            return FinancialHealthDto(
                resumen = "Sube el estado de cuenta de tus tarjetas en Análisis para calcular tu salud financiera.",
            )
        }
        val factors = mutableListOf<HealthFactorDto>()
        val income = inputs.monthlyIncome?.takeIf { it.signum() > 0 }

        if (income != null) {
            val ratio = ratio(inputs.debt, income)
            factors += HealthFactorDto(
                "Deuda vs. ingreso",
                scale(ratio, good = 0.3, bad = 1.5),
                "Tu deuda equivale a ${pct(ratio)} de tu ingreso mensual.",
            )
            inputs.paymentsDue?.takeIf { it.signum() > 0 }?.let { due ->
                val r = ratio(due, income)
                factors += HealthFactorDto(
                    "Pagos del mes",
                    scale(r, good = 0.3, bad = 0.8),
                    "Tus pagos de tarjetas se llevan ${pct(r)} de tu ingreso.",
                )
            }
        }
        inputs.creditLimit?.takeIf { it.signum() > 0 }?.let { limit ->
            val r = ratio(inputs.debt, limit)
            factors += HealthFactorDto("Uso de crédito", scale(r, good = 0.3, bad = 0.9), "Usas ${pct(r)} de tu límite de crédito.")
        }
        val spend = inputs.lastMonthSpend?.takeIf { it.signum() > 0 }
        if (spend != null) {
            inputs.lastMonthHormiga?.let { ants ->
                val r = ratio(ants, spend)
                factors += HealthFactorDto("Gastos hormiga", scale(r, good = 0.05, bad = 0.25), "Son ${pct(r)} de lo que gastaste el último mes.")
            }
            inputs.lastMonthInterest?.let { interest ->
                val r = ratio(interest, spend)
                factors += HealthFactorDto(
                    "Intereses y comisiones",
                    scale(r, good = 0.0, bad = 0.1),
                    if (interest.signum() == 0) "No pagaste intereses el último mes." else "Pagaste ${StatementAnalysisService.mxn(interest)} en intereses y comisiones.",
                )
            }
            inputs.previousMonthSpend?.let { previous ->
                SpendingMath.percentChange(previous, inputs.comparableLastSpend ?: spend)?.let { change ->
                    factors += HealthFactorDto(
                        "Tendencia de gasto",
                        scale(change / 100.0, good = 0.0, bad = 0.3),
                        if (change <= 0) "Gastaste ${-change}% menos que el mes anterior." else "Gastaste $change% más que el mes anterior.",
                    )
                }
            }
        }
        factors += HealthFactorDto(
            "Cargos por revisar",
            when (inputs.openAlerts) { 0 -> 100; 1 -> 70; 2 -> 50; else -> 30 },
            if (inputs.openAlerts == 0) "No hay cargos extraños en tus últimos estados." else "Tienes ${inputs.openAlerts} cargo${if (inputs.openAlerts == 1) "" else "s"} que conviene revisar.",
        )

        val score = factors.map { it.puntaje }.average().roundToInt()
        val level = when {
            score >= 70 -> HealthLevel.SALUDABLE
            score >= 45 -> HealthLevel.ALERTA
            else -> HealthLevel.CRITICA
        }
        val weakest = factors.minBy { it.puntaje }
        val summary = when (level) {
            HealthLevel.SALUDABLE -> "Vas bien. Mantén tus pagos a tiempo."
            HealthLevel.ALERTA -> "Hay puntos por mejorar. Lo que más te afecta: ${weakest.nombre.lowercase()}."
            else -> "Tu salud financiera necesita atención. Empieza por: ${weakest.nombre.lowercase()}."
        } + if (income == null) " Registra tus ingresos para un cálculo más preciso." else ""

        return FinancialHealthDto(score, level, factors, summary)
    }

    /** 100 si el valor es ≤ good, 0 si es ≥ bad, lineal entre ambos. */
    internal fun scale(value: Double, good: Double, bad: Double): Int =
        when {
            value <= good -> 100
            value >= bad -> 0
            else -> (100 * (bad - value) / (bad - good)).roundToInt()
        }

    private fun ratio(part: BigDecimal, whole: BigDecimal): Double =
        part.divide(whole, MathContext.DECIMAL64).toDouble()

    private fun pct(ratio: Double): String = "${(ratio * 100).roundToInt()}%"
}
