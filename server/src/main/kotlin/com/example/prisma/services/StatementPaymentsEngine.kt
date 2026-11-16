package com.example.prisma.services

import com.example.prisma.api.StatementFields
import com.example.prisma.api.StatementPayment
import com.example.prisma.api.StatementPaymentsRequest
import com.example.prisma.api.StatementPaymentsSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

object StatementPaymentsEngine {
    private val zero = BigDecimal("0.00")
    private val maximum = BigDecimal("9999999999.99")

    private val amountPattern = Regex(
        "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?",
    )

    private val uuidPattern = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
    )

    fun normalize(
        fields: StatementFields,
        request: StatementPaymentsRequest,
        today: LocalDate,
    ): List<StatementPayment> {
        require(request.confirmadoPorUsuario) {
            "Confirma que los pagos registrados ya se aplicaron y corresponden a este estado."
        }

        require(request.version in 0 until Int.MAX_VALUE) {
            "La versión de los pagos no es válida."
        }

        require(request.versionEstado > 0) {
            "La versión del estado no es válida."
        }

        require(request.pagos.size <= 100) {
            "Puedes registrar hasta 100 pagos por estado de cuenta."
        }

        val cutoff = date(
            fields.fechaCorte,
            "La fecha de corte",
        )

        val ids = mutableSetOf<String>()

        return request.pagos.map { payment ->
            require(uuidPattern.matches(payment.id)) {
                "Cada pago debe tener un identificador UUID válido."
            }

            val id = UUID.fromString(payment.id).toString()

            require(ids.add(id)) {
                "Hay pagos con identificadores repetidos."
            }

            val applied = date(
                payment.fechaAplicacion,
                "La fecha de aplicación",
            )

            require(applied > cutoff && applied <= today) {
                "La fecha de aplicación debe ser posterior al corte y no puede ser futura."
            }

            payment.copy(
                id = id,
                fechaAplicacion = applied.toString(),
                monto = amount(
                    payment.monto,
                    "El pago",
                    positive = true,
                ).toPlainString(),
            )
        }.sortedWith(
            compareBy(
                { it.fechaAplicacion },
                { it.id },
            ),
        )
    }

    fun summarize(
        fields: StatementFields,
        pagos: List<StatementPayment>,
        today: LocalDate,
    ): StatementPaymentsSummary {
        val due = date(
            fields.fechaLimitePago,
            "La fecha límite de pago",
        )

        val minimum = amount(
            fields.pagoMinimo,
            "El pago mínimo",
            positive = false,
        )

        val interestFree = amount(
            fields.pagoParaNoGenerarIntereses,
            "El pago para no generar intereses",
            positive = false,
        )

        var total = zero
        var timely = zero
        var hasLatePayments = false

        pagos.forEach { payment ->
            val value = amount(
                payment.monto,
                "El pago",
                positive = true,
            )

            total = total.add(value)

            if (
                date(
                    payment.fechaAplicacion,
                    "La fecha de aplicación",
                ) <= due
            ) {
                timely = timely.add(value)
            } else {
                hasLatePayments = true
            }
        }

        val expired = today > due

        val warnings = mutableListOf(
            "Estimación respecto al estado confirmado: " +
                    "no incluye intereses ni cargos posteriores " +
                    "y no representa el saldo bancario actual.",
        )

        if (expired) {
            warnings +=
                "La fecha límite del estado ya pasó. " +
                        "Confirma con el banco el importe vigente."
        }

        if (hasLatePayments) {
            warnings +=
                "Hay pagos posteriores a la fecha límite. " +
                        "Reducen el faltante estimado, pero no acreditan " +
                        "que el importe se cubrió a tiempo."
        }

        if (total > interestFree) {
            warnings +=
                "El exceso respecto al objetivo del estado " +
                        "no se interpreta como saldo a favor."
        }

        return StatementPaymentsSummary(
            totalAplicado = total.toPlainString(),
            aplicadoHastaFechaLimite = timely.toPlainString(),
            faltanteMinimo = minimum
                .subtract(total)
                .max(zero)
                .toPlainString(),
            faltanteParaNoGenerarIntereses = interestFree
                .subtract(total)
                .max(zero)
                .toPlainString(),
            faltanteMinimoAlVencimiento = minimum
                .subtract(timely)
                .max(zero)
                .toPlainString(),
            faltanteParaNoGenerarInteresesAlVencimiento = interestFree
                .subtract(timely)
                .max(zero)
                .toPlainString(),
            plazoVencido = expired,
            advertencias = warnings,
        )
    }

    private fun date(
        raw: String?,
        label: String,
    ): LocalDate {
        require(
            raw != null &&
                    Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw),
        ) {
            "$label debe tener el formato AAAA-MM-DD."
        }

        val value = runCatching {
            LocalDate.parse(raw)
        }.getOrNull()

        require(value != null && value.year in 1..9999) {
            "$label no es válida."
        }

        return value
    }

    private fun amount(
        raw: String?,
        label: String,
        positive: Boolean,
    ): BigDecimal {
        require(raw != null && raw.length <= 40) {
            "$label no es válido."
        }

        val value = raw.trim()

        require(amountPattern.matches(value)) {
            "$label debe usar punto decimal y hasta dos decimales."
        }

        val number = value
            .replace(",", "")
            .toBigDecimalOrNull()

        require(
            number != null &&
                    number <= maximum &&
                    if (positive) number > zero else number >= zero,
        ) {
            "$label debe ser ${if (positive) "mayor a" else "desde"} " +
                    "cero y no superar 9999999999.99."
        }

        return number.setScale(
            2,
            RoundingMode.UNNECESSARY,
        )
    }
}