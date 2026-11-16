package com.example.prisma.services

import com.example.prisma.api.StatementConfirmationRequest
import com.example.prisma.api.StatementFields
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

object StatementConfirmationValidator {
    private val maxMoney = BigDecimal("9999999999.99")

    private val moneyPattern = Regex(
        "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?",
    )

    fun validPeriod(value: String?): String? {
        if (
            value == null ||
            !Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(value)
        ) return null

        return runCatching {
            YearMonth.parse(value)
                .takeIf { it.year in 1..9999 }
                ?.toString()
        }.getOrNull()
    }

    fun normalize(
        periodo: String,
        request: StatementConfirmationRequest,
    ): StatementFields {
        require(request.confirmadoPorUsuario) {
            "Confirma que revisaste los datos contra el estado de cuenta."
        }
        require(request.version in 0 until Int.MAX_VALUE) {
            "La versión del estado de cuenta no es válida."
        }
        return normalizeDetected(periodo, request.campos)
    }

    fun normalizeDetected(
        periodo: String,
        fields: StatementFields,
    ): StatementFields {
        require(validPeriod(periodo) != null && fields.periodo == periodo) {
            "Selecciona un periodo válido con formato AAAA-MM."
        }

        val cutoff = date(fields.fechaCorte, "fecha de corte")
        val due = date(fields.fechaLimitePago, "fecha límite de pago")

        require(cutoff.toString().take(7) == periodo) {
            "El periodo debe corresponder al mes de la fecha de corte."
        }
        require(due >= cutoff) {
            "La fecha límite de pago debe ser igual o posterior a la fecha de corte."
        }

        val minimum = money(
            fields.pagoMinimo,
            "Pago mínimo",
            required = true,
        )!!

        val payment = money(
            fields.pagoParaNoGenerarIntereses,
            "Pago para no generar intereses",
            required = true,
        )!!

        val balance = money(fields.saldoTotal, "Saldo total")
        val installments = money(fields.mensualidadesMsi, "Mensualidades MSI")

        require(BigDecimal(minimum) <= BigDecimal(payment)) {
            "El pago mínimo no puede superar el pago para no generar intereses. Revisa el documento."
        }

        require(balance == null || BigDecimal(balance) >= BigDecimal(payment)) {
            "El saldo total es menor que el pago para no generar intereses. Corrígelo o déjalo pendiente."
        }

        return StatementFields(
            periodo = periodo,
            fechaCorte = cutoff.toString(),
            fechaLimitePago = due.toString(),
            saldoTotal = balance,
            pagoMinimo = minimum,
            pagoParaNoGenerarIntereses = payment,
            tasaInteresAnual = annualRate(fields.tasaInteresAnual),
            mensualidadesMsi = installments,
        )
    }

    private fun date(raw: String?, label: String): LocalDate {
        require(
            raw != null &&
                    raw.length <= 10 &&
                    Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw),
        ) {
            "Selecciona la $label en el calendario."
        }

        val value = runCatching { LocalDate.parse(raw) }.getOrNull()

        require(value != null && value.year in 1..9999) {
            "La $label no es válida."
        }

        return value
    }

    private fun money(
        raw: String?,
        label: String,
        required: Boolean = false,
    ): String? {
        require(raw == null || raw.length <= 40) {
            "$label: el importe es demasiado largo."
        }

        val value = raw?.trim().orEmpty()

        if (value.isEmpty()) {
            require(!required) { "$label es obligatorio." }
            return null
        }

        require(moneyPattern.matches(value)) {
            "$label: escribe un importe desde cero, con punto decimal y hasta dos decimales."
        }

        val number = value.replace(",", "").toBigDecimalOrNull()

        require(number != null && number >= BigDecimal.ZERO && number <= maxMoney) {
            "$label está fuera del rango permitido."
        }

        return number.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    }

    private fun annualRate(raw: String?): String? {
        require(raw == null || raw.length <= 32) {
            "La tasa anual es demasiado larga."
        }

        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        require(Regex("[0-9]{1,4}(?:[.,][0-9]{1,4})?").matches(value)) {
            "Escribe la tasa anual como porcentaje, con hasta cuatro decimales."
        }

        val number = value.replace(',', '.').toBigDecimalOrNull()

        require(
            number != null &&
                    number >= BigDecimal.ZERO &&
                    number <= BigDecimal("1000"),
        ) {
            "La tasa anual debe estar entre 0 y 1000 %."
        }

        return number.stripTrailingZeros().toPlainString()
    }
}