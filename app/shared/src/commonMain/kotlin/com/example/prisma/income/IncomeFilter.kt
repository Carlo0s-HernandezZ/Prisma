package com.example.prisma.income

import com.example.prisma.api.IncomeDto

/** Rangos de monto para filtrar ingresos. [max] es exclusivo; null = sin límite. */
enum class AmountRange(val label: String, val min: Double, val max: Double?) {
    UpTo5k("Hasta \$5,000", 0.0, 5_000.0),
    From5kTo15k("\$5,000 a \$15,000", 5_000.0, 15_000.0),
    From15kTo30k("\$15,000 a \$30,000", 15_000.0, 30_000.0),
    Over30k("Más de \$30,000", 30_000.0, null);

    fun contains(amount: Double): Boolean = amount >= min && (max == null || amount < max)
}

/** Filtro de la lista de ingresos registrados (se aplica en la app, sobre la lista ya cargada). */
sealed interface IncomeFilter {
    val label: String
    fun matches(income: IncomeDto): Boolean

    data object All : IncomeFilter {
        override val label = "Todos"
        override fun matches(income: IncomeDto) = true
    }

    /** [yearMonth] con formato "yyyy-MM", p. ej. "2026-11". */
    data class Month(val yearMonth: String) : IncomeFilter {
        override val label = monthLabel(yearMonth)
        override fun matches(income: IncomeDto) = income.fechaRegistro.startsWith(yearMonth)
    }

    data class Year(val year: String) : IncomeFilter {
        override val label = year
        override fun matches(income: IncomeDto) = income.fechaRegistro.startsWith(year)
    }

    data class Amount(val range: AmountRange) : IncomeFilter {
        override val label = range.label
        override fun matches(income: IncomeDto) = income.montoNeto.toDoubleOrNull()?.let(range::contains) ?: false
    }
}

private val MONTHS = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

/** "2026-11" -> "Noviembre 2026". */
fun monthLabel(yearMonth: String): String {
    val month = yearMonth.substringAfter('-', "").toIntOrNull()
    val name = month?.let { MONTHS.getOrNull(it - 1) } ?: return yearMonth
    return "$name ${yearMonth.substringBefore('-')}"
}

/** Meses ("yyyy-MM") que tienen ingresos, del más reciente al más antiguo. */
fun availableMonths(ingresos: List<IncomeDto>): List<String> =
    ingresos.mapNotNull { it.fechaRegistro.take(7).takeIf { ym -> ym.length == 7 && ym[4] == '-' } }
        .distinct().sortedDescending()

/** Años que tienen ingresos, del más reciente al más antiguo. */
fun availableYears(ingresos: List<IncomeDto>): List<String> =
    ingresos.mapNotNull { it.fechaRegistro.take(4).takeIf { y -> y.length == 4 && y.all(Char::isDigit) } }
        .distinct().sortedDescending()

/** Suma exacta de montos en texto ("15000.50"), trabajando en centavos para no perder precisión. */
fun sumAmounts(ingresos: List<IncomeDto>): String {
    val cents = ingresos.sumOf { income ->
        val parts = income.montoNeto.split('.')
        val whole = parts[0].toLongOrNull() ?: 0L
        val dec = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        whole * 100 + dec
    }
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
