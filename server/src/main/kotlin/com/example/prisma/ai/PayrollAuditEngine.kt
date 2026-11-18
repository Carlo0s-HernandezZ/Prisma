package com.example.prisma.ai

import com.example.prisma.api.AdviceDto
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.PayrollAlertDto
import com.example.prisma.api.PayrollAuditDto
import com.example.prisma.api.PayrollConceptDto
import com.example.prisma.api.PayrollConceptKind
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * Auditoría de nómina sin IA:
 * 1. Lee el CFDI de nómina (XML) o el texto de un recibo PDF y arma el desglose de cada concepto.
 * 2. Explica cada concepto con el catálogo del SAT ([PayrollCatalog]).
 * 3. Revisa reglas de irregularidades y compara contra la nómina anterior del usuario.
 */
object PayrollAuditEngine {

    /** Datos leídos del documento, antes de auditar. */
    data class Parsed(
        val fuente: String,
        val periodo: String?,
        val fechaPago: String? = null,
        val fechaInicial: String? = null,
        val fechaFinal: String? = null,
        val diasPagados: String? = null,
        val periodicidad: String? = null,
        val conceptos: List<PayrollConceptDto>,
        val totalPercepciones: BigDecimal? = null,
        val totalDeducciones: BigDecimal? = null,
        val totalOtrosPagos: BigDecimal? = null,
        val netoDeclarado: BigDecimal? = null,
        val avisos: List<String> = emptyList(),
    )

    private val PERIODICIDAD = mapOf(
        "01" to "Diaria", "02" to "Semanal", "03" to "Catorcenal", "04" to "Quincenal", "05" to "Mensual",
        "06" to "Bimestral", "07" to "Unidad de obra", "08" to "Comisión", "09" to "Precio alzado", "10" to "Decenal", "99" to "Otra",
    )

    // ------------------------------------------------------------------ CFDI XML

    /** Lee el CFDI de nómina 1.2 (CFDI 3.3 o 4.0). Protegido contra XXE como PayrollXmlParser. */
    fun fromXml(xml: ByteArray): Parsed {
        require(xml.isNotEmpty() && xml.size <= 10 * 1024 * 1024) { "El XML está vacío o es demasiado grande." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val comprobante = doc.documentElement
        require(comprobante.localName == "Comprobante") { "El archivo no es un CFDI." }
        require(comprobante.getAttribute("TipoDeComprobante") == "N") { "El CFDI no es de nómina." }
        val nomina = comprobante.getElementsByTagNameNS("*", "Nomina").let {
            require(it.length >= 1) { "El CFDI no trae complemento de nómina." }
            it.item(0) as Element
        }
        val receptor = nomina.getElementsByTagNameNS("*", "Receptor").let { if (it.length > 0) it.item(0) as Element else null }

        fun elements(name: String) = nomina.getElementsByTagNameNS("*", name).let { list -> (0 until list.length).map { list.item(it) as Element } }
        fun money(e: Element, attr: String) = e.getAttribute(attr).takeIf { it.isNotBlank() }?.toBigDecimalOrNull()?.money()

        val conceptos = buildList {
            elements("Percepcion").forEach { p ->
                val gravado = money(p, "ImporteGravado") ?: ZERO
                val exento = money(p, "ImporteExento") ?: ZERO
                add(concept(PayrollConceptKind.PERCEPCION, p.getAttribute("TipoPercepcion"), p.getAttribute("Concepto"), gravado + exento, gravado, exento))
            }
            elements("Deduccion").forEach { d ->
                add(concept(PayrollConceptKind.DEDUCCION, d.getAttribute("TipoDeduccion"), d.getAttribute("Concepto"), money(d, "Importe") ?: ZERO))
            }
            elements("OtroPago").forEach { o ->
                add(concept(PayrollConceptKind.OTRO_PAGO, o.getAttribute("TipoOtroPago"), o.getAttribute("Concepto"), money(o, "Importe") ?: ZERO))
            }
        }

        val fechaPago = nomina.getAttribute("FechaPago").takeIf { it.isNotBlank() }
        val fechaInicial = nomina.getAttribute("FechaInicialPago").takeIf { it.isNotBlank() }
        return Parsed(
            fuente = "XML",
            periodo = (fechaInicial ?: fechaPago)?.take(7),
            fechaPago = fechaPago,
            fechaInicial = fechaInicial,
            fechaFinal = nomina.getAttribute("FechaFinalPago").takeIf { it.isNotBlank() },
            diasPagados = nomina.getAttribute("NumDiasPagados").takeIf { it.isNotBlank() },
            periodicidad = receptor?.getAttribute("PeriodicidadPago")?.let { PERIODICIDAD[it] ?: it.takeIf(String::isNotBlank) },
            conceptos = conceptos,
            totalPercepciones = money(nomina, "TotalPercepciones"),
            totalDeducciones = money(nomina, "TotalDeducciones"),
            totalOtrosPagos = money(nomina, "TotalOtrosPagos"),
            netoDeclarado = money(comprobante, "Total"),
        )
    }

    // ------------------------------------------------------------------ PDF

    private val amountRx = "\\$?\\s?(\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})"

    /** Texto seguido de uno o más números: "001 SUELDO 15.00 7,500.00" → concepto SUELDO, importe 7,500.00. */
    private val chunkRx = Regex(
        "(?:\\b\\d{1,4}\\s+)?([A-Za-zÁÉÍÓÚÑÜáéíóúñü][A-Za-zÁÉÍÓÚÑÜáéíóúñü0-9.()/%*&\\- ]{1,60}?)\\s*((?:$amountRx\\s*)+)",
    )
    private val totalRx = Regex("TOTAL\\s+(?:DE\\s+)?(PERCEPCIONES|DEDUCCIONES|OTROS PAGOS)\\D{0,15}?$amountRx")
    private val netRx = Regex("(?:NETO\\s+(?:A\\s+PAGAR|PAGADO|RECIBIDO)?|TOTAL\\s+(?:A\\s+PAGAR|NETO))\\D{0,15}?$amountRx")
    private val periodRx = Regex("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\D{1,12}?(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})")
    private val skipRx = Regex(
        "TOTAL|NETO|SALARIO DIARIO|S\\.?D\\.?I|DIAS|PERIODO|FECHA|RFC|CURP|N\\.?S\\.?S|REGISTRO|SUCURSAL|CUENTA|CLABE|PAGINA|IMPORTE|CONCEPTO|CLAVE",
    )

    fun fromPdf(text: String): Parsed {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var section: String? = null
        var totalP: BigDecimal? = null
        var totalD: BigDecimal? = null
        var totalO: BigDecimal? = null
        var net: BigDecimal? = null
        val conceptos = mutableListOf<PayrollConceptDto>()

        lines.forEach { raw ->
            val line = normalizeText(raw)
            totalRx.findAll(line).forEach { m ->
                val value = parseMoney(m.groupValues[2])
                when (m.groupValues[1]) {
                    "PERCEPCIONES" -> totalP = value
                    "DEDUCCIONES" -> totalD = value
                    else -> totalO = value
                }
            }
            netRx.find(line)?.let { net = parseMoney(it.groupValues[1]) ?: net }

            val hasP = "PERCEPCIONES" in line
            val hasD = "DEDUCCIONES" in line
            val hasO = "OTROS PAGOS" in line
            if ((hasP || hasD || hasO) && totalRx.find(line) == null) {
                section = when {
                    hasP && hasD -> "AMBAS"
                    hasP -> "P"
                    hasD -> "D"
                    else -> "O"
                }
                return@forEach
            }
            val current = section ?: return@forEach
            if (totalRx.containsMatchIn(line) || netRx.containsMatchIn(line)) return@forEach

            chunkRx.findAll(raw).toList().forEachIndexed { index, m ->
                val text = m.groupValues[1].trim().trimEnd('-', '.', ' ')
                if (text.length < 3 || skipRx.containsMatchIn(normalizeText(text))) return@forEachIndexed
                val amount = Regex(amountRx).findAll(m.groupValues[2]).lastOrNull()?.groupValues?.get(1)?.let(::parseMoney)
                    ?.takeIf { it.signum() > 0 } ?: return@forEachIndexed
                val normalized = normalizeText(text)
                val kind = when {
                    "SUBSIDIO" in normalized && "INCAPACIDAD" !in normalized -> PayrollConceptKind.OTRO_PAGO
                    PayrollCatalog.looksLikeDeduction(text) -> PayrollConceptKind.DEDUCCION
                    current == "P" -> PayrollConceptKind.PERCEPCION
                    current == "D" -> PayrollConceptKind.DEDUCCION
                    current == "O" -> PayrollConceptKind.OTRO_PAGO
                    else -> if (index == 0) PayrollConceptKind.PERCEPCION else PayrollConceptKind.DEDUCCION
                }
                val entry = PayrollCatalog.byText(kind, text)
                conceptos += concept(kind, entry?.clave, text, amount)
            }
        }

        val period = PayrollPeriodExtractor().extraerPeriodo(text)
        val dates = periodRx.find(text)?.destructured?.let { (d1, m1, y1, d2, m2, y2) ->
            "%s-%02d-%02d".format(y1, m1.toInt(), d1.toInt()) to "%s-%02d-%02d".format(y2, m2.toInt(), d2.toInt())
        }
        val avisos = buildList {
            if (conceptos.isEmpty()) add("No pudimos leer los conceptos de este recibo. Si tienes el XML (CFDI) de tu nómina, súbelo: es más preciso.")
            else add("Recibo leído desde PDF. Para un desglose exacto con las claves del SAT, sube el XML (CFDI) de tu nómina.")
        }
        return Parsed(
            fuente = "PDF",
            periodo = period ?: dates?.first?.take(7),
            fechaInicial = dates?.first,
            fechaFinal = dates?.second,
            conceptos = conceptos.distinctBy { it.tipo.name + normalizeText(it.concepto) + it.importe },
            totalPercepciones = totalP,
            totalDeducciones = totalD,
            totalOtrosPagos = totalO,
            netoDeclarado = net,
            avisos = avisos,
        )
    }

    private fun concept(
        kind: PayrollConceptKind,
        claveSat: String?,
        text: String,
        amount: BigDecimal,
        gravado: BigDecimal? = null,
        exento: BigDecimal? = null,
    ): PayrollConceptDto {
        val entry = PayrollCatalog.byCode(kind, claveSat) ?: PayrollCatalog.byText(kind, text)
        val label = text.trim().ifBlank { entry?.nombre ?: "Concepto" }.take(80)
        return PayrollConceptDto(
            tipo = kind,
            claveSat = entry?.clave ?: claveSat?.takeIf(String::isNotBlank),
            concepto = label,
            nombre = entry?.nombre ?: label,
            explicacion = entry?.explicacion ?: when (kind) {
                PayrollConceptKind.PERCEPCION -> "Pago que recibes. Pregunta a tu empresa a qué corresponde si no lo reconoces."
                PayrollConceptKind.DEDUCCION -> "Descuento a tu pago. Pide a tu empresa que te explique a qué corresponde."
                PayrollConceptKind.OTRO_PAGO -> "Otro pago que no es sueldo."
            },
            importe = amount.plain(),
            gravado = gravado?.plain(),
            exento = exento?.plain(),
            revisar = entry?.revisar == true,
        )
    }

    // ------------------------------------------------------------------ Auditoría

    fun audit(parsed: Parsed, previous: PayrollAuditDto?): PayrollAuditDto {
        val per = parsed.conceptos.filter { it.tipo == PayrollConceptKind.PERCEPCION }
        val ded = parsed.conceptos.filter { it.tipo == PayrollConceptKind.DEDUCCION }
        val otros = parsed.conceptos.filter { it.tipo == PayrollConceptKind.OTRO_PAGO }
        fun sum(list: List<PayrollConceptDto>) = list.fold(ZERO) { acc, c -> acc + c.importe.toBigDecimal() }

        val totalP = parsed.totalPercepciones ?: sum(per)
        val totalD = parsed.totalDeducciones ?: sum(ded)
        val totalO = parsed.totalOtrosPagos ?: sum(otros)
        val netCalc = (totalP + totalO - totalD).money()
        val alerts = mutableListOf<PayrollAlertDto>()

        // 1. El neto del recibo no cuadra con la suma de los conceptos.
        parsed.netoDeclarado?.let { declared ->
            val diff = (declared - netCalc).abs()
            if (diff > BigDecimal.ONE) {
                alerts += PayrollAlertDto(
                    "El neto no cuadra",
                    "El recibo dice ${mxn(declared)}, pero percepciones + otros pagos − deducciones da ${mxn(netCalc)} " +
                        "(diferencia de ${mxn(diff)}). Pide a tu empresa que lo revise.",
                    AlertSeverity.ALTA,
                )
            }
        }
        // 2. Los totales del recibo no coinciden con la suma de sus conceptos (posible concepto oculto).
        if (parsed.fuente == "XML") {
            parsed.totalDeducciones?.let { stated ->
                if ((stated - sum(ded)).abs() > BigDecimal.ONE) {
                    alerts += PayrollAlertDto(
                        "Las deducciones no suman lo que dice el total",
                        "El total de deducciones es ${mxn(stated)} pero los conceptos suman ${mxn(sum(ded))}.",
                        AlertSeverity.ALTA,
                    )
                }
            }
        }

        val gravado = per.mapNotNull { it.gravado?.toBigDecimal() }.takeIf { it.isNotEmpty() }?.fold(ZERO, BigDecimal::add) ?: totalP
        val isr = ded.filter { it.claveSat == "002" }.let(::sum)
        val imss = ded.filter { it.claveSat == "001" }.let(::sum)

        // 3. ISR por encima de la tasa máxima (35 %).
        if (gravado.signum() > 0 && isr.signum() > 0) {
            val rate = isr.divide(gravado, MathContext.DECIMAL64).toDouble()
            if (rate > 0.35) {
                alerts += PayrollAlertDto(
                    "ISR más alto de lo normal",
                    "Te retuvieron ${pct(rate)} de lo gravado; la tasa máxima del ISR es 35 %. Puede ser un ajuste o un error.",
                    AlertSeverity.ALTA,
                )
            }
        }
        // 4. Cuota del IMSS fuera de lo habitual (la parte del trabajador suele rondar 2 a 3 % del sueldo).
        if (totalP.signum() > 0 && imss.signum() > 0) {
            val rate = imss.divide(totalP, MathContext.DECIMAL64).toDouble()
            if (rate > 0.05) {
                alerts += PayrollAlertDto(
                    "Cuota del IMSS alta",
                    "Tu cuota del IMSS es ${pct(rate)} de tus percepciones; normalmente es de 2 a 3 %. Verifica tu salario registrado en el IMSS.",
                    AlertSeverity.MEDIA,
                )
            }
        }
        // 5. Descuentos que se llevan más de la mitad de lo que ganas.
        if (totalP.signum() > 0) {
            val rate = totalD.divide(totalP, MathContext.DECIMAL64).toDouble()
            if (rate > 0.5) {
                alerts += PayrollAlertDto(
                    "Descuentos muy altos",
                    "Las deducciones son ${pct(rate)} de tus percepciones. La Ley Federal del Trabajo limita los descuentos; revisa cada uno.",
                    AlertSeverity.ALTA,
                )
            }
        }
        // 6. Descuentos poco comunes que conviene confirmar.
        ded.filter { it.revisar }.forEach {
            alerts += PayrollAlertDto(
                "Revisa el descuento \"${it.concepto}\"",
                "${it.nombre}: ${mxn(it.importe.toBigDecimal())}. ${it.explicacion} Confirma que te corresponde.",
                AlertSeverity.MEDIA,
            )
        }
        otros.filter { it.revisar }.forEach {
            alerts += PayrollAlertDto("Revisa \"${it.concepto}\"", "${it.nombre}: ${mxn(it.importe.toBigDecimal())}.", AlertSeverity.BAJA)
        }
        if (per.isNotEmpty() && per.none { it.claveSat == "001" || it.claveSat == "046" || it.claveSat == "039" }) {
            alerts += PayrollAlertDto(
                "No encontramos tu sueldo base",
                "El recibo no trae el concepto de sueldo (clave 001 del SAT). Verifica que tu pago esté bien registrado.",
                AlertSeverity.BAJA,
            )
        }

        // 7. Comparación con tu nómina anterior.
        previous?.let { prev ->
            val prevNet = prev.netoCalculado.toBigDecimal()
            if (prevNet.signum() > 0 && (parsed.periodicidad == null || prev.periodicidad == null || parsed.periodicidad == prev.periodicidad)) {
                val change = (netCalc - prevNet).divide(prevNet, MathContext.DECIMAL64).toDouble()
                if (change < -0.10) {
                    alerts += PayrollAlertDto(
                        "Tu pago neto bajó",
                        "Recibiste ${mxn(netCalc)}, ${pct(-change)} menos que en tu nómina anterior (${mxn(prevNet)}). Revisa qué cambió.",
                        AlertSeverity.MEDIA,
                    )
                }
            }
            val before = prev.deducciones.map { it.claveSat ?: normalizeText(it.concepto) }.toSet()
            ded.filter { (it.claveSat ?: normalizeText(it.concepto)) !in before }.forEach {
                alerts += PayrollAlertDto(
                    "Nuevo descuento: ${it.concepto}",
                    "Este descuento de ${mxn(it.importe.toBigDecimal())} no aparecía en tu nómina anterior. ${it.explicacion}",
                    AlertSeverity.MEDIA,
                )
            }
        }

        val sortedAlerts = alerts.distinctBy { it.titulo }.sortedBy { it.severidad.ordinal }
        val summary = buildString {
            append("Ganaste ${mxn(totalP + totalO)} y te descontaron ${mxn(totalD)}: recibes ${mxn(netCalc)}.")
            if (isr.signum() > 0) append(" De los descuentos, ${mxn(isr)} son ISR")
            if (imss.signum() > 0) append(if (isr.signum() > 0) " y ${mxn(imss)} cuota del IMSS." else " ${mxn(imss)} son cuota del IMSS.")
            else if (isr.signum() > 0) append(".")
            append(
                if (sortedAlerts.none { it.severidad != AlertSeverity.BAJA }) " No encontramos irregularidades."
                else " Encontramos ${sortedAlerts.count { it.severidad != AlertSeverity.BAJA }} punto(s) para revisar.",
            )
        }

        return PayrollAuditDto(
            fuente = parsed.fuente,
            periodo = parsed.periodo ?: parsed.fechaPago?.take(7) ?: "",
            fechaPago = parsed.fechaPago,
            fechaInicial = parsed.fechaInicial,
            fechaFinal = parsed.fechaFinal,
            diasPagados = parsed.diasPagados,
            periodicidad = parsed.periodicidad,
            percepciones = per,
            deducciones = ded,
            otrosPagos = otros,
            totalPercepciones = totalP.plain(),
            totalDeducciones = totalD.plain(),
            totalOtrosPagos = totalO.plain(),
            netoDeclarado = parsed.netoDeclarado?.plain(),
            netoCalculado = netCalc.plain(),
            alertas = sortedAlerts,
            resumen = summary,
            consejos = advice(sortedAlerts.isNotEmpty(), parsed.fuente),
            aviso = parsed.avisos.joinToString(" ").ifBlank { null },
        )
    }

    private fun advice(hasAlerts: Boolean, source: String): List<AdviceDto> = buildList {
        if (hasAlerts) {
            add(
                AdviceDto(
                    "Pide la aclaración por escrito",
                    "Si un descuento no te corresponde, pide a Recursos Humanos que te lo explique por escrito. " +
                        "Si no te resuelven, la Procuraduría de la Defensa del Trabajo te asesora gratis.",
                    GovernmentSource.PROFEDET.nombre, GovernmentSource.PROFEDET.url,
                ),
            )
        }
        add(
            AdviceDto(
                "Revisa tus CFDI de nómina",
                "En el portal del SAT puedes consultar los recibos de nómina que tu empresa timbró y confirmar que coinciden con lo que recibes.",
                GovernmentSource.SAT.nombre, GovernmentSource.SAT.url,
            ),
        )
        add(
            AdviceDto(
                "Verifica tu salario registrado en el IMSS",
                "Consulta tus semanas cotizadas y tu salario registrado; un salario menor al real reduce tus incapacidades y tu pensión.",
                GovernmentSource.IMSS.nombre, GovernmentSource.IMSS.url,
            ),
        )
        if (source == "PDF") {
            add(
                AdviceDto(
                    "Guarda el XML de cada nómina",
                    "El XML (CFDI) es el comprobante fiscal oficial de tu pago; pídelo a tu empresa o descárgalo del portal del SAT.",
                    GovernmentSource.SAT.nombre, GovernmentSource.SAT.url,
                ),
            )
        }
    }

    private fun mxn(v: BigDecimal) = StatementAnalysisService.mxn(v)
    private fun pct(r: Double) = "${(r * 100).roundToInt()} %"
}
