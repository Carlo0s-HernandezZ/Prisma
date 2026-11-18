package com.example.prisma

import com.example.prisma.ai.GovernmentSource
import com.example.prisma.ai.PayrollAuditEngine
import com.example.prisma.ai.PayrollCatalog
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.PayrollConceptKind
import com.example.prisma.api.ReminderDto
import com.example.prisma.api.ReminderFrequency
import com.example.prisma.api.ReminderKind
import com.example.prisma.api.ReminderSchedule
import com.example.prisma.api.ReminderSchedule.Day
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nóminas FICTICIAS para probar la auditoría (no son datos reales). */
object SamplePayrolls {
    fun cfdi(
        sueldo: String = "7500.00",
        despensa: String = "500.00",
        isr: String = "780.50",
        imss: String = "190.25",
        extraDeduction: String = "",
        total: String? = null,
        subsidio: String = "0.00",
    ): ByteArray {
        val deducciones = listOf(isr.toBigDecimal(), imss.toBigDecimal()) +
            listOfNotNull(extraDeduction.takeIf { it.isNotBlank() }?.substringAfter('|')?.toBigDecimal())
        val totalDed = deducciones.reduce { a, b -> a + b }
        val totalPer = sueldo.toBigDecimal() + despensa.toBigDecimal()
        val neto = total ?: (totalPer + subsidio.toBigDecimal() - totalDed).setScale(2).toPlainString()
        val extra = extraDeduction.takeIf { it.isNotBlank() }?.let {
            val (tipo, monto) = it.split('|')
            """<nomina12:Deduccion TipoDeduccion="$tipo" Clave="099" Concepto="Descuento especial" Importe="$monto"/>"""
        }.orEmpty()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4" xmlns:nomina12="http://www.sat.gob.mx/nomina12"
                Version="4.0" TipoDeComprobante="N" Total="$neto" SubTotal="${totalPer.setScale(2)}" Moneda="MXN">
              <cfdi:Complemento>
                <nomina12:Nomina Version="1.2" TipoNomina="O" FechaPago="2026-10-15" FechaInicialPago="2026-10-01"
                    FechaFinalPago="2026-10-15" NumDiasPagados="15" TotalPercepciones="${totalPer.setScale(2)}"
                    TotalDeducciones="${totalDed.setScale(2)}" TotalOtrosPagos="$subsidio">
                  <nomina12:Receptor PeriodicidadPago="04"/>
                  <nomina12:Percepciones TotalSueldos="${totalPer.setScale(2)}" TotalGravado="$sueldo" TotalExento="$despensa">
                    <nomina12:Percepcion TipoPercepcion="001" Clave="P001" Concepto="Sueldo quincenal" ImporteGravado="$sueldo" ImporteExento="0.00"/>
                    <nomina12:Percepcion TipoPercepcion="029" Clave="P029" Concepto="Vales de despensa" ImporteGravado="0.00" ImporteExento="$despensa"/>
                  </nomina12:Percepciones>
                  <nomina12:Deducciones TotalOtrasDeducciones="${imss}" TotalImpuestosRetenidos="$isr">
                    <nomina12:Deduccion TipoDeduccion="002" Clave="D002" Concepto="ISR" Importe="$isr"/>
                    <nomina12:Deduccion TipoDeduccion="001" Clave="D001" Concepto="IMSS" Importe="$imss"/>
                    $extra
                  </nomina12:Deducciones>
                  <nomina12:OtrosPagos>
                    <nomina12:OtroPago TipoOtroPago="002" Clave="O002" Concepto="Subsidio para el empleo" Importe="$subsidio">
                      <nomina12:SubsidioAlEmpleo SubsidioCausado="$subsidio"/>
                    </nomina12:OtroPago>
                  </nomina12:OtrosPagos>
                </nomina12:Nomina>
              </cfdi:Complemento>
            </cfdi:Comprobante>
        """.trimIndent().toByteArray()
    }

    /** Texto de un recibo PDF con percepciones y deducciones en dos columnas. */
    val pdfText = """
        EMPRESA EJEMPLO S.A. DE C.V. (DOCUMENTO FICTICIO)
        RECIBO DE NÓMINA
        PERIODO DEL 01/10/2026 AL 15/10/2026   FECHA DE PAGO 15/10/2026
        DÍAS PAGADOS 15.00   SALARIO DIARIO 500.00
        PERCEPCIONES                         DEDUCCIONES
        001 SUELDO 15.00 7,500.00             002 ISR 780.50
        029 VALES DE DESPENSA 500.00          001 IMSS 190.25
        019 HORAS EXTRA 3.00 450.00           020 FALTAS 1.00 500.00
        TOTAL PERCEPCIONES 8,450.00           TOTAL DEDUCCIONES 1,470.75
        NETO A PAGAR $ 6,979.25
    """.trimIndent()
}

class PayrollAuditTest {

    @Test
    fun xmlBreakdownExplainsEveryConcept() {
        val parsed = PayrollAuditEngine.fromXml(SamplePayrolls.cfdi())
        val audit = PayrollAuditEngine.audit(parsed, null)

        assertEquals("XML", audit.fuente)
        assertEquals("2026-10", audit.periodo)
        assertEquals("Quincenal", audit.periodicidad)
        assertEquals(2, audit.percepciones.size)
        assertEquals(2, audit.deducciones.size)
        assertEquals("ISR (Impuesto sobre la renta)", audit.deducciones.single { it.claveSat == "002" }.nombre)
        assertEquals("500.00", audit.percepciones.single { it.claveSat == "029" }.exento)
        assertTrue(audit.percepciones.all { it.explicacion.isNotBlank() } && audit.deducciones.all { it.explicacion.isNotBlank() })
        assertEquals("7029.25", audit.netoCalculado)
        assertEquals(audit.netoCalculado, audit.netoDeclarado)
        assertTrue(audit.alertas.none { it.severidad != AlertSeverity.BAJA }, audit.alertas.toString())
        assertTrue(audit.consejos.all { GovernmentSource.isOfficialUrl(it.fuenteUrl) })
        assertTrue("No encontramos irregularidades" in audit.resumen, audit.resumen)
    }

    @Test
    fun detectsIrregularities() {
        // Neto del recibo distinto, ISR por encima del 35 % y un descuento por "errores" (clave 014).
        val parsed = PayrollAuditEngine.fromXml(
            SamplePayrolls.cfdi(isr = "3000.00", extraDeduction = "014|350.00", total = "4000.00"),
        )
        val audit = PayrollAuditEngine.audit(parsed, null)
        val titles = audit.alertas.map { it.titulo }
        assertTrue(titles.any { "neto no cuadra" in it }, titles.toString())
        assertTrue(titles.any { "ISR más alto" in it }, titles.toString())
        assertTrue(titles.any { "Descuento especial" in it }, titles.toString())
        assertEquals(AlertSeverity.ALTA, audit.alertas.first().severidad)
        assertTrue(audit.consejos.any { it.fuenteNombre == "PROFEDET" })
    }

    @Test
    fun comparesWithPreviousPayroll() {
        val previous = PayrollAuditEngine.audit(PayrollAuditEngine.fromXml(SamplePayrolls.cfdi()), null)
        val now = PayrollAuditEngine.audit(
            PayrollAuditEngine.fromXml(SamplePayrolls.cfdi(sueldo = "6000.00", extraDeduction = "011|400.00")),
            previous,
        )
        val titles = now.alertas.map { it.titulo }
        assertTrue(titles.any { "neto bajó" in it }, titles.toString())
        assertTrue(titles.any { "Nuevo descuento" in it }, titles.toString())
    }

    @Test
    fun rejectsNonPayrollAndXxe() {
        assertFailsWith<Exception> { PayrollAuditEngine.fromXml("<html></html>".toByteArray()) }
        val xxe = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><cfdi:Comprobante xmlns:cfdi="http://www.sat.gob.mx/cfd/4">&e;</cfdi:Comprobante>"""
        assertFailsWith<Exception> { PayrollAuditEngine.fromXml(xxe.toByteArray()) }
    }

    @Test
    fun pdfTwoColumnReceipt() {
        val audit = PayrollAuditEngine.audit(PayrollAuditEngine.fromPdf(SamplePayrolls.pdfText), null)
        assertEquals("PDF", audit.fuente)
        val per = audit.percepciones.associate { it.concepto to it.importe }
        val ded = audit.deducciones.associate { it.concepto to it.importe }
        assertEquals("7500.00", per["SUELDO"], audit.percepciones.toString())
        assertEquals("450.00", per["HORAS EXTRA"])
        assertEquals("780.50", ded["ISR"], audit.deducciones.toString())
        assertEquals("500.00", ded["FALTAS"])
        assertEquals("002", audit.deducciones.single { it.concepto == "ISR" }.claveSat)
        assertEquals("6979.25", audit.netoDeclarado)
        assertEquals("6979.25", audit.netoCalculado)
        assertTrue(audit.alertas.any { "FALTAS" in it.titulo }, audit.alertas.toString())
        assertEquals("2026-10-01", audit.fechaInicial)
        assertNotNull(audit.aviso)
    }

    @Test
    fun catalogRecognizesReceiptWords() {
        assertEquals("021", PayrollCatalog.byText(PayrollConceptKind.PERCEPCION, "PRIMA VACACIONAL")?.clave)
        assertEquals("009", PayrollCatalog.byText(PayrollConceptKind.DEDUCCION, "AMORTIZACION INFONAVIT")?.clave)
        assertEquals("002", PayrollCatalog.byText(PayrollConceptKind.OTRO_PAGO, "SUBSIDIO PARA EL EMPLEO")?.clave)
        assertEquals("Ajuste de impuestos", PayrollCatalog.byCode(PayrollConceptKind.DEDUCCION, "101")?.nombre)
        assertNull(PayrollCatalog.byText(PayrollConceptKind.PERCEPCION, "ZZZ"))
    }

    // ---------- Recordatorios ----------

    private fun reminder(f: ReminderFrequency, dia: Int? = null, dia2: Int? = null, inicio: String? = null) =
        ReminderDto(tipo = ReminderKind.NOMINA, titulo = "Subir nómina", frecuencia = f, dia = dia, dia2 = dia2, fechaInicio = inicio, hora = "09:00")

    @Test
    fun reminderDates() {
        val from = Day(2026, 11, 17) // martes
        assertEquals(listOf(Day(2026, 11, 30), Day(2026, 12, 31), Day(2027, 1, 31)),
            ReminderSchedule.nextDates(reminder(ReminderFrequency.MENSUAL, 31), from, 3))
        assertEquals(listOf(Day(2026, 11, 30), Day(2026, 12, 15), Day(2026, 12, 30)),
            ReminderSchedule.nextDates(reminder(ReminderFrequency.QUINCENAL, 15, 30), from, 3))
        assertEquals(Day(2027, 2, 28), ReminderSchedule.nextDates(reminder(ReminderFrequency.MENSUAL, 30), Day(2027, 2, 1), 1).single())
        assertEquals(listOf(Day(2026, 11, 20), Day(2026, 11, 27)),
            ReminderSchedule.nextDates(reminder(ReminderFrequency.SEMANAL, 5), from, 2)) // viernes
        assertEquals(listOf(Day(2026, 11, 27), Day(2026, 12, 11)),
            ReminderSchedule.nextDates(reminder(ReminderFrequency.CATORCENAL, inicio = "2026-10-30"), from, 2))
        assertEquals(2, ReminderSchedule.dayOfWeek(Day(2026, 11, 17)))
        assertEquals(Day(2026, 11, 17), ReminderSchedule.fromEpochDay(ReminderSchedule.epochDay(Day(2026, 11, 17))))
    }

    @Test
    fun reminderValidation() {
        assertNull(ReminderSchedule.validate(reminder(ReminderFrequency.MENSUAL, 5)))
        assertNotNull(ReminderSchedule.validate(reminder(ReminderFrequency.MENSUAL, 40)))
        assertNotNull(ReminderSchedule.validate(reminder(ReminderFrequency.QUINCENAL, 15, 15)))
        assertNotNull(ReminderSchedule.validate(reminder(ReminderFrequency.MENSUAL, 5).copy(hora = "25:00")))
        assertNotNull(ReminderSchedule.validate(reminder(ReminderFrequency.MENSUAL, 5).copy(tipo = ReminderKind.ESTADO_CUENTA)))
        assertFalse(ReminderSchedule.describe(reminder(ReminderFrequency.SEMANAL, 5)).isBlank())
    }

    /** Escribe nóminas FICTICIAS en server/build/nominas-prueba/ para probar desde la app. */
    @Test
    fun writeSamplePayrollFiles() {
        val dir = java.io.File("build/nominas-prueba").apply { mkdirs() }
        java.io.File(dir, "nomina-ficticia-oct-1.xml").writeBytes(SamplePayrolls.cfdi())
        java.io.File(dir, "nomina-ficticia-oct-2-irregular.xml").writeBytes(
            SamplePayrolls.cfdi(sueldo = "6000.00", isr = "2500.00", extraDeduction = "014|350.00", total = "3000.00"),
        )
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER)
            doc.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { s ->
                s.beginText()
                s.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10f)
                s.setLeading(14f)
                s.newLineAtOffset(40f, 740f)
                SamplePayrolls.pdfText.lines().forEach { line -> s.showText(line); s.newLine() }
                s.endText()
            }
            doc.save(java.io.File(dir, "recibo-ficticio-oct.pdf"))
        }
        assertTrue(dir.listFiles().orEmpty().size >= 3)
    }
}
