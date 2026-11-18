package com.example.prisma.ai

import com.example.prisma.api.PayrollConceptKind

/**
 * Catálogos del complemento de nómina 1.2 del SAT (c_TipoPercepcion, c_TipoDeduccion, c_TipoOtroPago)
 * con una explicación en lenguaje sencillo de cada concepto.
 *
 * Las palabras clave sirven para reconocer el concepto en recibos PDF, que no traen la clave del SAT.
 */
object PayrollCatalog {

    data class Entry(
        val clave: String,
        val nombre: String,
        val explicacion: String,
        val keywords: List<String> = emptyList(),
        /** Conceptos que conviene revisar cuando aparecen (descuentos poco comunes). */
        val revisar: Boolean = false,
    )

    val percepciones: Map<String, Entry> = listOf(
        Entry("001", "Sueldos, salarios, rayas y jornales", "Tu sueldo base por los días trabajados en el periodo.",
            listOf("SUELDO", "SALARIO", "RAYA", "JORNAL")),
        Entry("002", "Gratificación anual (aguinaldo)", "Pago anual obligatorio de al menos 15 días de salario, a más tardar el 20 de diciembre.",
            listOf("AGUINALDO", "GRATIFICACION ANUAL")),
        Entry("003", "Participación de los trabajadores en las utilidades (PTU)", "Tu parte de las utilidades de la empresa; se paga una vez al año.",
            listOf("PTU", "UTILIDADES", "REPARTO")),
        Entry("004", "Reembolso de gastos médicos, dentales y hospitalarios", "La empresa te devuelve gastos médicos que pagaste.",
            listOf("REEMBOLSO MEDICO", "GASTOS MEDICOS")),
        Entry("005", "Fondo de ahorro", "Lo que la empresa aporta a tu fondo de ahorro; normalmente se entrega una vez al año.",
            listOf("FONDO DE AHORRO", "FONDO AHORRO")),
        Entry("006", "Caja de ahorro", "Aportación de la empresa a la caja de ahorro.", listOf("CAJA DE AHORRO")),
        Entry("009", "Contribuciones a cargo del trabajador pagadas por el patrón", "Impuestos o cuotas tuyas que la empresa paga por ti."),
        Entry("010", "Premios por puntualidad", "Bono por llegar a tiempo.", listOf("PUNTUALIDAD")),
        Entry("011", "Prima de seguro de vida", "La empresa paga tu seguro de vida.", listOf("SEGURO DE VIDA")),
        Entry("012", "Seguro de gastos médicos mayores", "La empresa paga tu seguro de gastos médicos mayores.", listOf("GASTOS MEDICOS MAYORES", "SGMM")),
        Entry("013", "Cuotas sindicales pagadas por el patrón", "La empresa paga tu cuota sindical."),
        Entry("014", "Subsidios por incapacidad", "Pago por días de incapacidad médica.", listOf("SUBSIDIO POR INCAPACIDAD")),
        Entry("015", "Becas para trabajadores y/o hijos", "Apoyo para estudios.", listOf("BECA")),
        Entry("019", "Horas extra", "Pago por trabajar más de tu jornada; las primeras 9 a la semana se pagan al doble.",
            listOf("HORAS EXTRA", "TIEMPO EXTRA", "HRS EXTRA", "H. EXTRA")),
        Entry("020", "Prima dominical", "25% adicional de tu salario diario por trabajar en domingo.", listOf("PRIMA DOMINICAL")),
        Entry("021", "Prima vacacional", "Pago adicional de al menos 25% del salario de tus días de vacaciones.", listOf("PRIMA VACACIONAL")),
        Entry("022", "Prima por antigüedad", "Pago de 12 días por año trabajado al terminar la relación laboral (con tope).", listOf("PRIMA DE ANTIGUEDAD", "PRIMA ANTIGUEDAD")),
        Entry("023", "Pagos por separación", "Pagos al terminar la relación laboral.", listOf("SEPARACION", "FINIQUITO")),
        Entry("024", "Seguro de retiro", "Aportación de la empresa a un seguro para tu retiro."),
        Entry("025", "Indemnizaciones", "Pago por despido o por riesgo de trabajo.", listOf("INDEMNIZACION", "LIQUIDACION")),
        Entry("026", "Reembolso por funeral", "Apoyo por gastos de funeral."),
        Entry("027", "Cuotas de seguridad social pagadas por el patrón", "Cuotas del IMSS que te corresponderían y la empresa paga por ti."),
        Entry("028", "Comisiones", "Pago por tus ventas u objetivos cumplidos.", listOf("COMISION")),
        Entry("029", "Vales de despensa", "Prestación para comprar alimentos; está exenta de impuestos hasta cierto límite.",
            listOf("DESPENSA", "VALES DE DESPENSA")),
        Entry("030", "Vales de restaurante", "Prestación para comidas.", listOf("RESTAURANTE")),
        Entry("031", "Vales de gasolina", "Prestación para combustible.", listOf("GASOLINA")),
        Entry("032", "Vales de ropa", "Prestación para ropa.", listOf("VALES DE ROPA")),
        Entry("033", "Ayuda para renta", "Apoyo para pagar tu vivienda.", listOf("AYUDA PARA RENTA", "AYUDA RENTA")),
        Entry("034", "Ayuda para artículos escolares", "Apoyo para útiles escolares.", listOf("UTILES", "ESCOLAR")),
        Entry("035", "Ayuda para anteojos", "Apoyo para lentes.", listOf("ANTEOJOS", "LENTES")),
        Entry("036", "Ayuda para transporte", "Apoyo para tu transporte al trabajo.", listOf("TRANSPORTE")),
        Entry("037", "Ayuda para gastos de funeral", "Apoyo para gastos de funeral."),
        Entry("038", "Otros ingresos por salarios", "Otros pagos ligados a tu trabajo (bonos, premios, compensaciones).",
            listOf("BONO", "COMPENSACION", "PREMIO", "INCENTIVO", "GRATIFICACION")),
        Entry("039", "Jubilaciones, pensiones o haberes de retiro", "Pago de tu pensión o jubilación.", listOf("PENSION", "JUBILACION")),
        Entry("046", "Ingresos asimilados a salarios", "Pagos que se tratan como sueldo para efectos de impuestos (p. ej. honorarios asimilados).",
            listOf("ASIMILADOS")),
        Entry("047", "Alimentación", "Prestación en comida.", listOf("ALIMENTACION", "COMEDOR")),
        Entry("048", "Habitación", "Prestación en vivienda.", listOf("HABITACION")),
        Entry("049", "Premios por asistencia", "Bono por no faltar.", listOf("ASISTENCIA")),
        Entry("050", "Viáticos", "Dinero para gastos de viajes de trabajo; debes comprobarlo.", listOf("VIATICOS")),
    ).associateBy { it.clave }

    val deducciones: Map<String, Entry> = listOf(
        Entry("001", "Seguridad social (IMSS)", "Tu cuota al IMSS: te da derecho a servicio médico, incapacidades, guarderías y pensión.",
            listOf("IMSS", "SEGURO SOCIAL", "SEGURIDAD SOCIAL", "CUOTA OBRERA")),
        Entry("002", "ISR (Impuesto sobre la renta)", "Impuesto que la empresa te retiene y entrega al SAT según cuánto ganas.",
            listOf("ISR", "IMPUESTO SOBRE LA RENTA", "RETENCION ISR")),
        Entry("003", "Aportaciones a retiro, cesantía en edad avanzada y vejez", "Ahorro para tu retiro que va a tu cuenta de AFORE.",
            listOf("RCV", "CESANTIA", "RETIRO")),
        Entry("004", "Otros", "Descuento que no entra en otra categoría del SAT; revisa a qué corresponde.", revisar = true),
        Entry("005", "Aportaciones a fondo de vivienda", "Aportación para tu cuenta de vivienda.", listOf("APORTACION VIVIENDA")),
        Entry("006", "Descuento por incapacidad", "Descuento por días de incapacidad; el IMSS te paga un subsidio por esos días.",
            listOf("INCAPACIDAD"), revisar = true),
        Entry("007", "Pensión alimenticia", "Descuento ordenado por un juez para pensión alimenticia.", listOf("PENSION ALIMENTICIA")),
        Entry("008", "Renta", "Descuento por renta de vivienda que te da la empresa.", listOf("RENTA CASA")),
        Entry("009", "Préstamos del INFONAVIT", "Pago de tu crédito de vivienda INFONAVIT.", listOf("INFONAVIT", "CREDITO VIVIENDA")),
        Entry("010", "Pago por crédito de vivienda", "Pago de un crédito de vivienda.", listOf("VIVIENDA")),
        Entry("011", "Pago de abonos INFONACOT", "Pago de tu crédito FONACOT.", listOf("FONACOT", "INFONACOT")),
        Entry("012", "Anticipo de salarios", "Descuento de un adelanto de sueldo que te dieron.", listOf("ANTICIPO", "ADELANTO"), revisar = true),
        Entry("013", "Pagos hechos con exceso al trabajador", "Te descuentan algo que te pagaron de más antes.", listOf("PAGO EN EXCESO", "EXCESO"), revisar = true),
        Entry("014", "Errores", "Descuento por errores.", listOf("ERRORES"), revisar = true),
        Entry("015", "Pérdidas", "Descuento por pérdidas.", listOf("PERDIDAS"), revisar = true),
        Entry("016", "Averías", "Descuento por daños a equipo o material.", listOf("AVERIAS"), revisar = true),
        Entry("017", "Adquisición de artículos producidos por la empresa", "Compras que hiciste a la empresa.", listOf("ARTICULOS")),
        Entry("018", "Cuotas para cooperativas y cajas de ahorro", "Lo que aportas a la caja de ahorro o cooperativa.", listOf("CAJA DE AHORRO", "COOPERATIVA", "FONDO DE AHORRO")),
        Entry("019", "Cuotas sindicales", "Tu cuota al sindicato.", listOf("SINDICAL", "SINDICATO")),
        Entry("020", "Ausencia (ausentismo)", "Descuento por días que no trabajaste.", listOf("FALTA", "AUSENCIA", "AUSENTISMO", "INASISTENCIA"), revisar = true),
        Entry("021", "Cuotas obrero patronales", "Cuotas de seguridad social.", listOf("OBRERO PATRONAL")),
        Entry("022", "Impuestos locales", "Impuestos estatales o municipales.", listOf("IMPUESTO ESTATAL", "IMPUESTO LOCAL")),
        Entry("023", "Aportaciones voluntarias", "Ahorro voluntario que tú pediste.", listOf("VOLUNTARIA")),
    ).associateBy { it.clave }

    val otrosPagos: Map<String, Entry> = listOf(
        Entry("001", "Reintegro de ISR pagado en exceso", "Te devuelven ISR que te retuvieron de más."),
        Entry("002", "Subsidio para el empleo", "Apoyo del gobierno para sueldos bajos que reduce tu ISR o se te paga directo.",
            listOf("SUBSIDIO PARA EL EMPLEO", "SUBSIDIO AL EMPLEO", "SUBSIDIO EMPLEO")),
        Entry("003", "Viáticos", "Dinero para gastos de viajes de trabajo.", listOf("VIATICOS")),
        Entry("004", "Aplicación de saldo a favor por compensación anual", "Te aplican un saldo a favor de tu cálculo anual de ISR.", listOf("SALDO A FAVOR")),
        Entry("005", "Reintegro de ISR retenido en exceso de ejercicio anterior", "Devolución de ISR del año pasado."),
        Entry("007", "ISR ajustado por subsidio", "Ajuste del ISR por el subsidio para el empleo."),
        Entry("008", "Subsidio efectivamente entregado que no correspondía", "Ajuste por un subsidio que no te correspondía.", revisar = true),
        Entry("999", "Pagos distintos a los listados", "Otro pago que no es sueldo; revisa a qué corresponde."),
    ).associateBy { it.clave }

    fun catalog(kind: PayrollConceptKind): Map<String, Entry> = when (kind) {
        PayrollConceptKind.PERCEPCION -> percepciones
        PayrollConceptKind.DEDUCCION -> deducciones
        PayrollConceptKind.OTRO_PAGO -> otrosPagos
    }

    /** Busca por clave del SAT; las deducciones 024-107 son ajustes de impuestos. */
    fun byCode(kind: PayrollConceptKind, clave: String?): Entry? {
        val code = clave?.trim()?.padStart(3, '0') ?: return null
        catalog(kind)[code]?.let { return it }
        if (kind == PayrollConceptKind.DEDUCCION && code.toIntOrNull() in 24..107) {
            return Entry(code, "Ajuste de impuestos", "Corrección del ISR o del subsidio de periodos anteriores; pide a tu empresa que te explique el ajuste.", revisar = true)
        }
        return null
    }

    /** Reconoce el concepto de un recibo PDF por sus palabras (sin clave del SAT). */
    fun byText(kind: PayrollConceptKind, text: String): Entry? {
        val normalized = " " + normalizeText(text) + " "
        // Primero las palabras más largas (más específicas): "SUBSIDIO POR INCAPACIDAD" antes que "INCAPACIDAD".
        return catalog(kind).values
            .flatMap { entry -> entry.keywords.map { it to entry } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (keyword, _) -> Regex("(^|[^A-Z])" + Regex.escape(keyword) + "(?:E?S)?($|[^A-Z])").containsMatchIn(normalized) }
            ?.second
    }

    /** ¿Este texto suele ser deducción aunque esté en otra columna? (ISR, IMSS, INFONAVIT...) */
    fun looksLikeDeduction(text: String): Boolean {
        val t = " " + normalizeText(text) + " "
        return listOf("ISR", "IMSS", "INFONAVIT", "FONACOT", "CUOTA", "DESCUENTO", "RETENCION", "PRESTAMO", "PENSION ALIMENTICIA", "SINDICAL")
            .any { Regex("(^|[^A-Z])$it(?:E?S)?($|[^A-Z])").containsMatchIn(t) } && !t.contains("SUBSIDIO")
    }
}
