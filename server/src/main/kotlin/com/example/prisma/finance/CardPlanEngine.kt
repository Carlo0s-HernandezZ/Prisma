package com.example.prisma.finance

import com.example.prisma.api.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class PlanCardInput(
    val alias: String,
    val statement: ConfirmedStatementDto?,
    val automatic: AutomaticStatementDto?,
    val payments: StatementPaymentsDto?,
)

object CardPlanEngine {
    private val zero = BigDecimal("0.00")
    private val maximum = BigDecimal("9999999999.99")

    private data class Work(
        var plan: CardPaymentPlan,
        val due: LocalDate?,
        val goal: BigDecimal?,
        val floor: BigDecimal?,
        val rate: BigDecimal,
        val schedule: MutableMap<LocalDate, BigDecimal> = sortedMapOf(),
    )

    fun calculate(
        periodo: String,
        budget: BudgetRequest?,
        cards: List<PlanCardInput>,
        forecasts: List<ForecastIncome>,
        today: LocalDate,
    ): CardPlansDto {
        require(Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(periodo)) {
            "Selecciona un periodo válido."
        }

        val month = runCatching { YearMonth.parse(periodo) }.getOrNull()

        require(month != null && month.year in 1..9999) {
            "El periodo no es válido."
        }
        require(cards.size <= 1000) {
            "Hay demasiadas tarjetas para este análisis."
        }

        val currentMonth = YearMonth.from(today)

        val work = cards.mapNotNull {
            prepare(it, month, currentMonth, today)
        }.toMutableList()

        val notices = mutableListOf<String>()
        val eligible = work.filter { it.plan.accion == null && it.goal != null }

        val totalGoal = eligible.takeIf { it.isNotEmpty() }
            ?.fold(zero) { total, item -> total + item.goal!! }

        val validatedForecasts = validateForecasts(forecasts)
        var computed = false

        if (month < currentMonth) {
            notices += "Consulta histórica: no se programan pagos actuales con este periodo."
        } else if (budget == null) {
            eligible.forEach {
                it.plan = it.plan.copy(accion = "CONFIGURAR_DISPONIBILIDAD")
            }
            notices += "Configura una vez tu dinero disponible, gastos y reserva."
        } else {
            val normalized = BudgetEngine.calculate(
                periodo,
                budget,
                today,
            ).presupuesto

            val base = LocalDate.parse(normalized.fechaBase)

            val hasBudgetIncome = normalized.movimientos.any {
                it.tipo == BudgetMovementType.INGRESO
            }

            val incomeForecasts = if (hasBudgetIncome) {
                emptyList()
            } else {
                validatedForecasts.filter {
                    val date = LocalDate.parse(it.fecha)
                    YearMonth.from(date) == month && date > maxOf(today, base)
                }
            }

            if (hasBudgetIncome && validatedForecasts.isNotEmpty()) {
                notices += "Se usan los cobros del presupuesto, sin sumar otra vez los previstos."
            }
            if (incomeForecasts.isNotEmpty()) {
                notices += "El plan depende de recibir los ingresos previstos en sus fechas."
            }
            if (base < today) {
                notices += "Proyección desde el saldo registrado al $base."
            }

            val days = generateSequence(base) {
                if (it < month.atEndOfMonth()) it.plusDays(1) else null
            }.toList()

            val movements = normalized.movimientos.groupBy {
                LocalDate.parse(it.fecha)
            }
            val extraIncome = incomeForecasts.groupBy {
                LocalDate.parse(it.fecha)
            }

            var balance =
                BigDecimal(normalized.saldoDisponible) -
                        BigDecimal(normalized.reserva)

            var minimum = balance

            val free = days.map { day ->
                movements[day].orEmpty().forEach { movement ->
                    val value = BigDecimal(movement.monto)

                    balance += if (movement.tipo == BudgetMovementType.INGRESO) {
                        value
                    } else value.negate()
                }

                extraIncome[day].orEmpty().forEach {
                    balance += BigDecimal(it.monto)
                }

                minimum = minimum.min(balance)
                balance
            }.toMutableList()

            if (minimum < zero) {
                eligible.forEach {
                    it.plan = it.plan.copy(accion = "AJUSTAR_DISPONIBILIDAD")
                }

                notices += "Faltan ${money(minimum.negate())} para cubrir gastos y reserva en sus fechas."
            } else {
                val first = days.indexOfFirst { it >= today }

                fun allocate(item: Work, desired: BigDecimal) {
                    if (first < 0 || desired <= zero) return

                    val limit = if (item.due!! < today) {
                        month.atEndOfMonth()
                    } else {
                        item.due.coerceAtMost(month.atEndOfMonth())
                    }

                    var remaining = desired

                    for (index in first until days.size) {
                        if (days[index] > limit || remaining <= zero) break

                        val available = free.subList(index, free.size)
                            .minOrNull()!!
                            .max(zero)

                        val assigned = remaining.min(available)
                        if (assigned <= zero) continue

                        item.schedule[days[index]] =
                            (item.schedule[days[index]] ?: zero) + assigned

                        for (later in index until free.size) {
                            free[later] -= assigned
                        }

                        remaining -= assigned
                    }
                }

                eligible.sortedWith(
                    compareBy<Work> { it.due }.thenBy { it.plan.tarjetaId },
                ).forEach {
                    allocate(it, it.floor!!)
                }

                eligible.sortedWith(
                    compareByDescending<Work> { it.rate }
                        .thenBy { it.due }
                        .thenBy { it.plan.tarjetaId },
                ).forEach {
                    allocate(
                        it,
                        it.goal!! - it.schedule.values.fold(zero, BigDecimal::add),
                    )
                }

                eligible.forEach { item ->
                    val assigned = item.schedule.values.fold(
                        zero,
                        BigDecimal::add,
                    )
                    val missing = item.goal!! - assigned
                    val warnings = item.plan.avisos.toMutableList()

                    if (assigned < item.floor!!) {
                        warnings += "El plan no alcanza el mínimo y las mensualidades exigibles."
                    } else if (missing > zero) {
                        warnings += "El pago parcial puede generar intereses."
                    }

                    item.plan = item.plan.copy(
                        recomendado = money(assigned),
                        faltante = money(missing),
                        programacion = item.schedule.map {
                            CardPaymentAllocation(
                                it.key.toString(),
                                money(it.value),
                            )
                        },
                        avisos = warnings,
                    )
                }

                computed = true
            }
        }

        if (work.any { it.plan.recomendado == null }) {
            notices += "Hay tarjetas pendientes; el total programado incluye sólo los planes calculados."
        }

        val recommendations = work.mapNotNull {
            it.plan.recomendado?.toBigDecimalOrNull()
        }

        return CardPlansDto(
            periodo = periodo,
            fechaReferencia = today.toString(),
            presupuestoConfigurado = budget != null,
            estimado = true,
            tarjetas = work.map { it.plan },
            totalObjetivo = totalGoal?.let(::money),
            totalRecomendado = if (computed && recommendations.isNotEmpty()) {
                money(recommendations.fold(zero, BigDecimal::add))
            } else null,
            avisos = notices.distinct(),
        )
    }

    private fun prepare(
        input: PlanCardInput,
        month: YearMonth,
        current: YearMonth,
        today: LocalDate,
    ): Work? {
        val automatic = input.automatic
        val state = input.statement
        val id = automatic?.tarjetaId ?: state?.tarjetaId ?: return null
        val fields = automatic?.deteccion?.campos ?: state!!.campos
        val detail = automatic?.deteccion?.detalle

        val cutoff = date(fields.fechaCorte)
        val due = date(fields.fechaLimitePago)
        val original = amount(fields.pagoParaNoGenerarIntereses)
        val originalMinimum = amount(fields.pagoMinimo)

        val rate = fields.tasaInteresAnual
            ?.toBigDecimalOrNull()
            ?.takeIf { it >= zero && it <= BigDecimal("1000") }
            ?: zero

        val base = CardPaymentPlan(
            tarjetaId = id,
            alias = input.alias,
            estado = state,
            automatico = automatic,
            tipo = detail?.tipo ?: StatementPurchaseType.DESCONOCIDO,
            objetivo = original?.let(::money),
            cuotasMsi = detail?.cuotasMsi,
            comprasUnPago = detail?.comprasUnPago,
        )

        fun blocked(
            action: String,
            message: String,
            showGoal: Boolean = true,
        ) = Work(
            plan = base.copy(
                objetivo = if (showGoal) base.objetivo else null,
                accion = action,
                avisos = listOf(message),
            ),
            due = due,
            goal = null,
            floor = null,
            rate = rate,
        )

        if (due != null && month < current) {
            if (YearMonth.from(due) != month) return null

            return blocked(
                "HISTORICO",
                "Este es el importe del documento histórico, no una deuda actual.",
            )
        }

        if (due != null) {
            if (month > current && YearMonth.from(due) != month) return null
            if (month == current && due > month.atEndOfMonth()) return null

            if (month == current && due < today.minusDays(45)) {
                return blocked(
                    "ACTUALIZAR_ESTADO",
                    "Agrega un estado reciente para calcular el plan vigente.",
                    false,
                )
            }
        }

        if (
            cutoff == null || due == null ||
            original == null || originalMinimum == null ||
            due < cutoff || cutoff > today
        ) {
            return blocked(
                "REVISAR_ESTADO",
                "Falta identificar un dato del estado de cuenta.",
            )
        }

        if (
            automatic != null && (
                    automatic.deteccion.estado != StatementDetectionStatus.LISTO ||
                            state == null ||
                            automatic.versionFinanciera != state.version ||
                            state.tarjetaId != id ||
                            state.campos.periodo != fields.periodo ||
                            state.campos.fechaCorte != fields.fechaCorte ||
                            state.campos.fechaLimitePago != fields.fechaLimitePago ||
                            amount(state.campos.pagoParaNoGenerarIntereses) != original ||
                            amount(state.campos.pagoMinimo) != originalMinimum
                    )
        ) {
            return blocked(
                "REVISAR_ESTADO",
                automatic.deteccion.pregunta
                    ?: "El estado necesita una aclaración antes de calcular el plan.",
            )
        }

        val payments = input.payments
        var applied = zero

        if (payments != null && payments.version > 0) {
            if (
                state == null ||
                payments.estado.tarjetaId != id ||
                payments.estado.campos.periodo != fields.periodo ||
                payments.estado.version != state.version ||
                payments.versionEstado != state.version ||
                payments.requiereRevision ||
                payments.resumen == null
            ) {
                return blocked(
                    "REVISAR_PAGOS",
                    "El estado cambió; revisa los pagos registrados.",
                )
            }

            val ids = mutableSetOf<String>()

            for (payment in payments.pagos) {
                val value = amount(payment.monto)
                val day = date(payment.fechaAplicacion)

                if (
                    value == null || value <= zero || day == null ||
                    day <= cutoff || day > today || !ids.add(payment.id)
                ) {
                    return blocked(
                        "REVISAR_PAGOS",
                        "Hay un pago que necesita revisión.",
                    )
                }

                applied += value
            }

            if (
                amount(
                    payments.resumen!!.totalAplicado,
                    bounded = false,
                ) != applied
            ) {
                return blocked(
                    "REVISAR_PAGOS",
                    "No se pudo comprobar el total de pagos registrados.",
                )
            }
        } else if (
            payments != null &&
            (payments.pagos.isNotEmpty() || payments.resumen != null)
        ) {
            return blocked(
                "REVISAR_PAGOS",
                "Confirma la lista de pagos aplicada a este estado.",
            )
        }

        val goal = original.subtract(applied).max(zero)

        if (
            due < today &&
            (payments == null || payments.version == 0) &&
            original > zero
        ) {
            return blocked(
                "CONFIRMAR_PAGOS",
                "¿Ya pagaste este estado de cuenta?",
            )
        }

        val installments = detail?.planes.orEmpty().fold(zero) { total, item ->
            total + (amount(item.cuota) ?: zero)
        }

        val hasInstallments = detail?.tipo in setOf(
            StatementPurchaseType.MSI,
            StatementPurchaseType.MIXTO,
            StatementPurchaseType.CON_INTERESES,
        )

        val floorOriginal = when {
            detail == null || detail.tipo == StatementPurchaseType.DESCONOCIDO ->
                original

            hasInstallments ->
                (
                        amount(detail.minimoConCuotas)
                            ?: (
                                    originalMinimum +
                                            installments.max(amount(detail.cuotasMsi) ?: zero)
                                    )
                        ).max(originalMinimum).min(original)

            else -> originalMinimum.min(original)
        }

        val floor = floorOriginal.subtract(applied).max(zero).min(goal)

        val warnings = mutableListOf(
            "Importes estimados del estado; se descuentan sólo los pagos registrados.",
        )

        if (due < today) {
            warnings += "La fecha límite ya pasó; pueden existir intereses y cargos posteriores."
        }

        return Work(
            plan = base.copy(
                objetivo = money(goal),
                minimoSeguro = money(floor),
                avisos = warnings,
            ),
            due = due,
            goal = goal,
            floor = floor,
            rate = rate,
        )
    }

    private fun validateForecasts(
        values: List<ForecastIncome>,
    ): List<ForecastIncome> {
        require(values.size <= 100) {
            "Puedes proyectar hasta 100 cobros por mes."
        }

        val ids = mutableSetOf<String>()

        return values.map {
            require(
                it.id.isNotBlank() &&
                        it.id.length <= 64 &&
                        ids.add(it.id),
            ) {
                "Los cobros previstos deben tener identificadores únicos."
            }

            val day = date(it.fecha)
            val amount = amount(it.monto)

            require(day != null && amount != null && amount > zero) {
                "Revisa las fechas e importes de los cobros previstos."
            }

            it.copy(
                fecha = day.toString(),
                monto = money(amount),
            )
        }
    }

    private fun date(raw: String?): LocalDate? =
        raw?.takeIf {
            Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it)
        }?.let {
            runCatching {
                LocalDate.parse(it).takeIf { day -> day.year in 1..9999 }
            }.getOrNull()
        }

    private fun amount(
        raw: String?,
        bounded: Boolean = true,
    ): BigDecimal? =
        raw?.takeIf {
            it.length <= 40 &&
                    Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(it)
        }?.toBigDecimalOrNull()
            ?.takeIf { it >= zero && (!bounded || it <= maximum) }
            ?.setScale(2, RoundingMode.UNNECESSARY)

    private fun money(value: BigDecimal): String =
        value.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}