package com.example.prisma.finance

import com.example.prisma.api.BudgetDayBalance
import com.example.prisma.api.BudgetMovementType
import com.example.prisma.api.BudgetRequest
import com.example.prisma.api.BudgetSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class BudgetCalculation(
    val presupuesto: BudgetRequest,
    val resumen: BudgetSummary,
)

object BudgetEngine {
    private val moneyPattern =
        Regex("[0-9]{1,10}(?:\\.[0-9]{1,2})?")

    private val monthPattern =
        Regex("[0-9]{4}-(?:0[1-9]|1[0-2])")

    private val datePattern =
        Regex("[0-9]{4}-(?:0[1-9]|1[0-2])-[0-9]{2}")

    private val maximum = BigDecimal("9999999999.99")
    private val zero = BigDecimal("0.00")

    fun calculate(
        periodo: String,
        request: BudgetRequest,
        today: LocalDate,
    ): BudgetCalculation {
        require(monthPattern.matches(periodo)) {
            "El periodo debe tener formato AAAA-MM."
        }

        val month = runCatching {
            YearMonth.parse(periodo)
        }.getOrNull()

        require(month != null && month.year in 1..9999) {
            "El periodo no es válido."
        }

        val baseDate = parseDate(
            request.fechaBase,
            "La fecha base",
        )

        require(YearMonth.from(baseDate) == month) {
            "La fecha base debe pertenecer al periodo seleccionado."
        }

        require(!baseDate.isAfter(today)) {
            "La fecha base no puede ser futura: confirma un saldo que ya tienes."
        }

        require(request.version >= 0) {
            "La versión del presupuesto no es válida."
        }

        require(request.movimientos.size <= 100) {
            "Puedes registrar hasta 100 movimientos pendientes por presupuesto."
        }

        val balance = parseMoney(
            request.saldoDisponible,
            "El saldo disponible",
            positive = false,
        )

        val reserve = parseMoney(
            request.reserva,
            "La reserva",
            positive = false,
        )

        val ids = mutableSetOf<String>()

        val movements = request.movimientos.map { movement ->
            require(
                movement.id.isNotBlank() &&
                        movement.id.length <= 64,
            ) {
                "Cada movimiento debe tener un identificador de hasta 64 caracteres."
            }

            require(ids.add(movement.id)) {
                "Hay movimientos con identificadores repetidos."
            }

            val label = movement.concepto.trim()

            require(
                label.isNotEmpty() &&
                        label.length <= 100 &&
                        !label.any { it.isISOControl() },
            ) {
                "El concepto es obligatorio, sin caracteres de control y de hasta 100 caracteres."
            }

            val normalizedLabel = label.replace(
                Regex("\\s+"),
                " ",
            )

            val date = parseDate(
                movement.fecha,
                "La fecha del movimiento",
            )

            require(
                !date.isBefore(baseDate) &&
                        YearMonth.from(date) == month,
            ) {
                "Los movimientos deben estar entre la fecha base y el último día del periodo."
            }

            val amount = parseMoney(
                movement.monto,
                "El monto del movimiento",
                positive = true,
            )

            movement.copy(
                concepto = normalizedLabel,
                fecha = date.toString(),
                monto = amount.toPlainString(),
            )
        }.sortedWith(
            compareBy(
                { it.fecha },
                { it.id },
            ),
        )

        var incomeTotal = zero
        var expenseTotal = zero
        var freeBalance = balance.subtract(reserve)

        // El dinero pendiente del mismo día aún no forma
        // parte del saldo inicial confirmado.
        var minimum = freeBalance

        val dailyBalances = linkedMapOf(
            baseDate.toString() to freeBalance,
        )

        movements
            .groupBy { it.fecha }
            .toSortedMap()
            .forEach { (date, dayMovements) ->
                var dayIncome = zero
                var dayExpense = zero

                dayMovements.forEach { movement ->
                    val amount = BigDecimal(movement.monto)

                    when (movement.tipo) {
                        BudgetMovementType.INGRESO -> {
                            dayIncome = dayIncome.add(amount)
                        }

                        BudgetMovementType.GASTO -> {
                            dayExpense = dayExpense.add(amount)
                        }
                    }
                }

                incomeTotal = incomeTotal.add(dayIncome)
                expenseTotal = expenseTotal.add(dayExpense)

                // Proyección al cierre de cada fecha.
                freeBalance = freeBalance
                    .add(dayIncome)
                    .subtract(dayExpense)

                minimum = minimum.min(freeBalance)
                dailyBalances[date] = freeBalance
            }

        dailyBalances[
            month.atEndOfMonth().toString()
        ] = freeBalance

        val shortage = minimum.negate().max(zero)
        val maximumAtBase = minimum.max(zero)

        val maximumInMonth = if (shortage.signum() == 0) {
            freeBalance.max(zero)
        } else {
            zero
        }

        return BudgetCalculation(
            presupuesto = request.copy(
                fechaBase = baseDate.toString(),
                saldoDisponible = balance.toPlainString(),
                reserva = reserve.toPlainString(),
                movimientos = movements,
            ),
            resumen = BudgetSummary(
                ingresosPendientes = incomeTotal.toPlainString(),
                gastosPendientes = expenseTotal.toPlainString(),
                saldoLibreCierre = freeBalance.toPlainString(),
                maximoEnFechaBase = maximumAtBase.toPlainString(),
                maximoEnMes = maximumInMonth.toPlainString(),
                faltante = shortage.toPlainString(),
                saldos = dailyBalances.map { (date, amount) ->
                    BudgetDayBalance(
                        fecha = date,
                        saldoLibre = amount.toPlainString(),
                    )
                },
            ),
        )
    }

    private fun parseDate(
        raw: String,
        label: String,
    ): LocalDate {
        require(datePattern.matches(raw)) {
            label + " debe tener formato AAAA-MM-DD."
        }

        val date = runCatching {
            LocalDate.parse(raw)
        }.getOrNull()

        require(date != null && date.year in 1..9999) {
            label + " no es válida."
        }

        return date
    }

    private fun parseMoney(
        raw: String,
        label: String,
        positive: Boolean,
    ): BigDecimal {
        val input = raw.trim()

        require(moneyPattern.matches(input)) {
            label + " debe ser decimal, con punto y hasta dos decimales."
        }

        val amount = BigDecimal(input)

        require(
            amount <= maximum &&
                    if (positive) {
                        amount.signum() > 0
                    } else {
                        amount.signum() >= 0
                    },
        ) {
            label + if (positive) {
                " debe ser mayor a cero y no exceder 9999999999.99."
            } else {
                " no puede ser negativo ni exceder 9999999999.99."
            }
        }

        return amount.setScale(
            2,
            RoundingMode.UNNECESSARY,
        )
    }
}