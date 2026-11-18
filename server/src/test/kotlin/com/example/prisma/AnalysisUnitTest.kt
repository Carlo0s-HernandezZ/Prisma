package com.example.prisma

import com.example.prisma.ai.AdvicePolicy
import com.example.prisma.ai.AnalysisCard
import com.example.prisma.ai.Categorizer
import com.example.prisma.ai.DeadlineFinder
import com.example.prisma.ai.LocalInstallmentParser
import com.example.prisma.ai.GovernmentSource
import com.example.prisma.ai.HealthMath
import com.example.prisma.ai.InsightEngine
import com.example.prisma.ai.LocalMovementParser
import com.example.prisma.ai.Movement
import com.example.prisma.ai.MovementType
import com.example.prisma.ai.SpendingMath
import com.example.prisma.ai.StatementAnalysisService
import com.example.prisma.ai.StatementAnonymizer
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.AnalysisSource
import com.example.prisma.api.HealthLevel
import com.example.prisma.api.SpendingTrend
import com.example.prisma.routes.FinanceStore
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Estados de cuenta FICTICIOS de "Banco Ejemplo" para probar el análisis sin datos reales. */
object SampleStatements {

    private fun d(period: YearMonth, monthOffset: Long, day: Int): String {
        val date = period.plusMonths(monthOffset).atDay(day)
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }

    private fun m(value: Double): String = String.format(java.util.Locale.US, "%,.2f", value)

    /** Páginas de texto de un estado con corte el día 10 de [period]; [factor] escala las compras. */
    fun pages(period: YearMonth, factor: Double = 1.0): List<String> {
        val p = period
        val purchases = listOf(
            Triple(d(p, -1, 13), "OXXO AV VALLARTA", 45.50),
            Triple(d(p, -1, 13), "STARBUCKS ANDARES", 89.00),
            Triple(d(p, -1, 14), "WALMART SUPERCENTER", 1245.80 * factor),
            Triple(d(p, -1, 15), "OXXO AV VALLARTA", 38.00),
            Triple(d(p, -1, 16), "NETFLIX.COM", 299.00),
            Triple(d(p, -1, 17), "UBER TRIP", 112.40 * factor),
            Triple(d(p, -1, 18), "STARBUCKS ANDARES", 89.00),
            Triple(d(p, -1, 19), "PEMEX GAS", 900.00 * factor),
            Triple(d(p, -1, 20), "OXXO AV VALLARTA", 52.00),
            Triple(d(p, -1, 21), "RESTAURANTE LA TIA", 640.00 * factor),
            Triple(d(p, -1, 22), "AMAZON MX MKTPLACE", 1899.00),
            Triple(d(p, -1, 22), "AMAZON MX MKTPLACE", 1899.00),
            Triple(d(p, -1, 24), "SPOTIFY USD", 129.00),
            Triple(d(p, -1, 25), "TIENDA ONLINE XYZ", 3.00),
            Triple(d(p, -1, 26), "STARBUCKS ANDARES", 95.00),
            Triple(d(p, -1, 28), "LIVERPOOL PERISUR", 8500.00 * factor),
            Triple(d(p, 0, 2), "OXXO AV VALLARTA", 41.00),
            Triple(d(p, 0, 5), "SORIANA HIPER", 980.30 * factor),
            Triple(d(p, 0, 8), "INTERESES ORDINARIOS", 320.50),
            Triple(d(p, 0, 8), "IVA INTERESES", 51.28),
        )
        val spent = purchases.sumOf { it.third } - 500.0
        val plans = 6000.0 + 3000.0 + 2000.0
        val balance = spent + plans
        val noInterest = spent + 1000.0 + 600.0 + 410.0

        val summary = """
            BANCO EJEMPLO S.A. (DOCUMENTO FICTICIO DE PRUEBA)
            ESTADO DE CUENTA TARJETA DE CRÉDITO
            Titular: CARLOS PRUEBA LOPEZ
            Correo: carlos.prueba@ejemplo.com
            Domicilio: CALLE FALSA 123 COL. CENTRO C.P. 44100
            Número de tarjeta: 4152 3138 1234 5678
            RFC: PULC800101AB1
            Teléfono: 33 1234 5678
            Tu pago requerido este periodo
            Pago para no generar intereses: $${m(noInterest)}
            Pago mínimo: $${m(noInterest * 0.12)}
            Fecha de corte: ${d(p, 0, 10)}
            Fecha límite de pago: ${d(p, 0, 28)}
            Saldo total al corte: $${m(balance)}
            Límite de crédito: $60,000.00
        """.trimIndent()

        val movements = buildString {
            appendLine("DESGLOSE DE MOVIMIENTOS")
            appendLine("COMPRAS Y CARGOS DIFERIDOS A MESES SIN INTERESES")
            appendLine("Fecha Descripción Monto original Saldo pendiente Pago requerido Núm. de pago Tasa")
            appendLine("${d(p, -4, 15)} LIVERPOOL INSURGENTES 12,000.00 6,000.00 1,000.00 6 de 12 0.00%")
            appendLine("${d(p, -1, 2)} AMAZON MX 3,600.00 3,000.00 600.00 2 de 6 0.00%")
            appendLine("COMPRAS Y CARGOS DIFERIDOS A MESES CON INTERESES")
            appendLine("${d(p, -7, 20)} ELEKTRA CENTRO 4,000.00 2,000.00 410.00 11 de 12 45.00%")
            appendLine("CARGOS, ABONOS Y COMPRAS REGULARES (NO A MESES)")
            appendLine("${d(p, -1, 12)} SU PAGO GRACIAS -5,000.00")
            purchases.forEach { (date, text, amount) -> appendLine("$date $text ${m(amount)}") }
            appendLine("${d(p, 0, 9)} DEVOLUCION LIVERPOOL -500.00")
        }
        return listOf(summary, movements)
    }

    /** Escribe los estados de prueba como PDF (para subirlos desde la app). */
    fun writePdf(file: File, period: YearMonth, factor: Double) {
        file.parentFile.mkdirs()
        PDDocument().use { doc ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            pages(period, factor).forEach { text ->
                val page = PDPage(PDRectangle.LETTER)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { s ->
                    s.beginText()
                    s.setFont(font, 9f)
                    s.setLeading(12f)
                    s.newLineAtOffset(40f, 740f)
                    text.lines().forEach { line -> s.showText(line); s.newLine() }
                    s.endText()
                }
            }
            doc.save(file)
        }
    }
}

class AnalysisUnitTest {

    private val october = YearMonth.of(2026, 10)
    private val card = AnalysisCard("00000000-0000-0000-0000-000000000001", "Banco Ejemplo", "Oro", "5678")

    private fun analyzeLocal(period: YearMonth = october, factor: Double = 1.0) = runBlocking {
        StatementAnalysisService(gemini = null).analyze(
            pages = SampleStatements.pages(period, factor),
            warnings = emptyList(),
            card = card,
            holderNames = listOf("Carlos Prueba", "Lopez"),
            allowAi = true,
            today = LocalDate.of(2026, 10, 15),
        )
    }

    // ---------- Privacidad ----------

    @Test
    fun anonymizerRemovesPersonalDataButKeepsMerchantsAndAmounts() {
        val text = SampleStatements.pages(october).joinToString("\n")
        val clean = StatementAnonymizer.anonymize(text, listOf("Carlos Prueba", "López"))

        listOf("carlos.prueba@ejemplo.com", "4152 3138 1234 5678", "PULC800101AB1", "33 1234 5678", "CALLE FALSA", "CARLOS", "PRUEBA", "LOPEZ")
            .forEach { assertFalse(it in clean, "No debe quedar \"$it\" en el texto enviado a la IA") }
        listOf("[CORREO]", "[NÚMERO]", "[RFC]", "[TELÉFONO]", "[DOMICILIO]", "[TITULAR]").forEach { assertTrue(it in clean, "Falta $it") }
        assertTrue("STARBUCKS ANDARES 89.00" in clean)
        assertTrue("LIVERPOOL INSURGENTES 12,000.00 6,000.00" in clean)
    }

    // ---------- Consejos: solo fuentes oficiales y sin inversión ----------

    @Test
    fun adviceOnlyFromOfficialSourcesAndNeverAboutInvesting() {
        assertNotNull(AdvicePolicy.accept("Paga más del mínimo", "Así reduces intereses.", "CONDUSEF"))
        assertNull(AdvicePolicy.accept("Invierte en CETES", "Te dan buen rendimiento.", "BANXICO"))
        assertNull(AdvicePolicy.accept("Compra acciones", "La bolsa de valores sube.", "CNBV"))
        assertNull(AdvicePolicy.accept("Usa cripto", "Bitcoin es el futuro.", "PROFECO"))
        assertNull(AdvicePolicy.accept("Ahorra", "Abre una cuenta.", "BLOG_FINANZAS"))
        GovernmentSource.entries.forEach { assertTrue(GovernmentSource.isOfficialUrl(it.url), it.url) }
        assertFalse(GovernmentSource.isOfficialUrl("https://finanzas-blog.com"))
        assertFalse(GovernmentSource.isOfficialUrl("https://gob.mx.estafa.com"))
    }

    // ---------- Lector local ----------

    @Test
    fun localParserReadsMovementLines() {
        val p = october
        val purchase = LocalMovementParser.parseLine("13/09/2026 STARBUCKS ANDARES 89.00", p)!!
        assertEquals(MovementType.COMPRA, purchase.tipo)
        assertEquals(Categorizer.ANTOJOS, purchase.categoria)
        assertEquals(LocalDate.of(2026, 9, 13), purchase.fecha)

        assertEquals(MovementType.PAGO, LocalMovementParser.parseLine("12/09/2026 SU PAGO GRACIAS -5,000.00", p)!!.tipo)
        assertEquals(MovementType.DEVOLUCION, LocalMovementParser.parseLine("09/10/2026 DEVOLUCION LIVERPOOL -500.00", p)!!.tipo)
        assertEquals(MovementType.CARGO_FINANCIERO, LocalMovementParser.parseLine("08/10/2026 INTERESES ORDINARIOS 320.50", p)!!.tipo)
        assertEquals(LocalDate.of(2026, 9, 15), LocalMovementParser.parseLine("15 SEP OXXO CENTRO 45.00", p)!!.fecha)
        assertNull(LocalMovementParser.parseLine("Saldo total al corte: $10.00", p))
        assertNull(LocalMovementParser.parseLine("15/06/2026 LIVERPOOL 12,000.00 6,000.00 1,000.00 6 de 12 0.00%", p))
    }

    // ---------- Análisis completo con el lector local (sin IA) ----------

    @Test
    fun localAnalysisFindsDebtsInstallmentsAntsAndStrangeCharges() {
        val outcome = analyzeLocal()
        val dto = outcome.dto

        assertEquals(AnalysisSource.LOCAL, dto.fuente)
        assertEquals("2026-10", dto.periodo)
        assertEquals("2026-10-28", dto.fechaLimitePago)

        // Compras a meses: 2 sin intereses y 1 con intereses (lector determinista).
        assertEquals(2, dto.comprasMeses.count { !it.conIntereses })
        assertEquals(1, dto.comprasMeses.count { it.conIntereses })

        // Deuda total = saldo al corte; desglose: revolvente + cada plan.
        val debtSum = dto.deudas.sumOf { it.monto.toBigDecimal() }
        assertEquals(dto.deudaTotal.toBigDecimal(), debtSum)
        assertTrue(dto.deudas.any { it.tipo == "REVOLVENTE" }, dto.deudas.toString())
        assertEquals(3, dto.deudas.count { it.tipo != "REVOLVENTE" })

        // Gastos hormiga: OXXO y Starbucks repetidos.
        assertTrue(dto.gastosHormiga.compras >= 7, "hormiga=${dto.gastosHormiga}")
        assertTrue(dto.gastosHormiga.comercios.any { it.nombre.startsWith("Oxxo") }, dto.gastosHormiga.toString())
        assertEquals(dto.gastosHormiga.total.toBigDecimal() * BigDecimal(12), dto.gastosHormiga.proyeccionAnual.toBigDecimal())

        // Cargos extraños: duplicado (alta), en dólares, monto atípico y cargo de prueba.
        val motives = dto.alertas.map { it.motivo }
        assertTrue(dto.alertas.first().severidad == AlertSeverity.ALTA && "repetido" in dto.alertas.first().motivo, dto.alertas.toString())
        assertTrue(motives.any { "extranjero" in it }, motives.toString())
        assertTrue(motives.any { "mayor que tus compras" in it }, motives.toString())
        assertTrue(motives.any { "pequeño" in it }, motives.toString())

        // Intereses y mensualidades en categorías; consejos solo oficiales.
        assertEquals(BigDecimal("371.78"), dto.interesesYComisiones.toBigDecimal())
        assertTrue(dto.categorias.any { it.categoria == Categorizer.MENSUALIDADES && it.monto == "2010.00" }, dto.categorias.toString())
        assertTrue(dto.consejos.isNotEmpty() && dto.consejos.all { GovernmentSource.isOfficialUrl(it.fuenteUrl) }, dto.consejos.toString())
        assertTrue(dto.resumen.startsWith("Debes $"), dto.resumen)
        assertNotNull(dto.aviso)
    }

    @Test
    fun hormigaRulesUseAmountCategoryAndRepetition() {
        val day = LocalDate.of(2026, 10, 1)
        fun buy(text: String, amount: String) = Movement(day, text, BigDecimal(amount), MovementType.COMPRA, Categorizer.categorize(text))
        val (marked, summary) = InsightEngine.hormiga(
            listOf(buy("STARBUCKS", "85.00"), buy("WALMART", "150.00"), buy("WALMART", "1500.00"), buy("PAPELERIA LUNA", "40.00")),
        )
        assertTrue(marked[0].esHormiga)
        assertFalse(marked[1].esHormiga)
        assertFalse(marked[2].esHormiga)
        assertEquals(1, summary.compras)
    }

    // ---------- Dashboards ----------

    @Test
    fun dashboardShowsTrendAndProjection() {
        val months = listOf(
            SpendingMath.MonthTotal(YearMonth.of(2026, 8), BigDecimal("10000"), BigDecimal("500")),
            SpendingMath.MonthTotal(YearMonth.of(2026, 9), BigDecimal("11000"), BigDecimal("600")),
            SpendingMath.MonthTotal(YearMonth.of(2026, 10), BigDecimal("12100"), BigDecimal("700")),
        )
        val dashboard = SpendingMath.dashboard(months, emptyList(), emptyList(), null)
        assertEquals(SpendingTrend.SUBE, dashboard.tendencia)
        assertEquals(10, dashboard.cambioPorcentaje)
        assertEquals("2026-11", dashboard.proyeccion?.periodo)
        assertTrue(dashboard.proyeccion!!.total.toBigDecimal() > BigDecimal("12100"))
        assertEquals("2026-10", dashboard.mesMayorGasto)
        assertEquals("2026-08", dashboard.mesMenorGasto)
        assertTrue("octubre" in dashboard.mensaje && "noviembre" in dashboard.mensaje, dashboard.mensaje)

        val empty = SpendingMath.dashboard(emptyList(), emptyList(), emptyList(), null)
        assertEquals(SpendingTrend.SIN_DATOS, empty.tendencia)
        assertNull(SpendingMath.projection(listOf(BigDecimal.TEN)))
    }

    // ---------- Salud financiera ----------

    @Test
    fun healthScoreReflectsDebtAntsAndAlerts() {
        assertEquals(HealthLevel.SIN_DATOS, HealthMath.compute(HealthMath.Inputs(hasAnalysis = false)).nivel)

        val good = HealthMath.compute(
            HealthMath.Inputs(
                hasAnalysis = true, debt = BigDecimal("3000"), monthlyIncome = BigDecimal("30000"),
                paymentsDue = BigDecimal("3000"), creditLimit = BigDecimal("50000"),
                lastMonthSpend = BigDecimal("8000"), previousMonthSpend = BigDecimal("9000"),
                lastMonthHormiga = BigDecimal("200"), lastMonthInterest = BigDecimal.ZERO,
            ),
        )
        assertEquals(HealthLevel.SALUDABLE, good.nivel)
        assertEquals(100, good.puntaje)

        val bad = HealthMath.compute(
            HealthMath.Inputs(
                hasAnalysis = true, debt = BigDecimal("60000"), monthlyIncome = BigDecimal("20000"),
                paymentsDue = BigDecimal("18000"), creditLimit = BigDecimal("62000"),
                lastMonthSpend = BigDecimal("20000"), previousMonthSpend = BigDecimal("12000"),
                lastMonthHormiga = BigDecimal("5000"), lastMonthInterest = BigDecimal("2500"), openAlerts = 3,
            ),
        )
        assertEquals(HealthLevel.CRITICA, bad.nivel)
        assertTrue(bad.factores.all { it.detalle.isNotBlank() })
    }

    @Test
    fun nextPaymentDayHandlesShortMonths() {
        assertEquals(LocalDate.of(2026, 10, 20), FinanceStore.nextDayOfMonth(20, LocalDate.of(2026, 10, 15)))
        assertEquals(LocalDate.of(2026, 11, 10), FinanceStore.nextDayOfMonth(10, LocalDate.of(2026, 10, 15)))
        assertEquals(LocalDate.of(2026, 2, 28), FinanceStore.nextDayOfMonth(31, LocalDate.of(2026, 2, 1)))
    }

    // ---------- Formatos reales de bancos mexicanos (datos inventados) ----------

    @Test
    fun installmentTablesWithNaRateAndMercadoPagoLayout() {
        val pages = listOf(
            """
            COMPRAS Y CARGOS DIFERIDOS A MESES SIN INTERESES
            04-ago-2026 TIENDA EJEMPLO           ABC 140618P37MX $12,000.00 $9,000.00 $1,000.00 3 de 12 NA
            04-jul-2026 TIENDA EJEMPLO           ABC 140618P37MX $600.00 $0.00 $100.00 6 de 6 NA
            CARGOS, ABONOS Y COMPRAS REGULARES (NO A MESES)
            Movimiento Monto total Tasa anual Mes Monto mensual Saldo restante
            MERPAGO*TIENDAUNO $ 900.00 0% 1 de 3 $ 300.00 $ 600.00
            MERPAGO*TIENDADOS $ 1,200.00 35.5% 2 de 6 $ 230.00 $ 900.00
            """.trimIndent(),
        )
        val plans = LocalInstallmentParser.parse(pages)
        assertEquals(3, plans.size, plans.toString()) // la liquidada (saldo 0) no se incluye
        assertEquals("TIENDA EJEMPLO", plans[0].descripcion)
        assertFalse(plans[0].conIntereses)
        assertEquals("9000.00", plans[0].saldoPendiente)
        assertFalse(plans[1].conIntereses)
        assertTrue(plans[2].conIntereses)
        assertEquals("35.5", plans[2].tasaAnual)
    }

    @Test
    fun deadlineOnNextLineOrInDateTable() {
        val today = LocalDate.of(2026, 11, 17)
        val bbva = listOf("Fecha de corte: 03-sep-2026\nFecha límite de pago:1\nmiércoles, 23-sep-2026")
        assertEquals(LocalDate.of(2026, 9, 23), DeadlineFinder.find(bbva, LocalDate.of(2026, 9, 3), null, today))

        val table = listOf("Fecha de corte Fecha límite de pago Período\n13 octubre 23 octubre 14 septiembre - 13 octubre")
        val cutoff = DeadlineFinder.findCutoff(table, today)
        assertEquals(LocalDate.of(2026, 10, 13), cutoff)
        assertEquals(LocalDate.of(2026, 10, 23), DeadlineFinder.find(table, cutoff, null, today))

        val glossary = listOf("Fecha límite de pago: es el día límite que tienes para pagar.")
        assertNull(DeadlineFinder.find(glossary, null, null, today))
    }

    @Test
    fun installmentPaymentsListedAsMovementsAreNotCountedTwice() {
        val p = october
        val monthly = LocalMovementParser.parseLine("04-sep-2026 04-sep-2026 TIENDA A MESES 003 de 024 + $708.29", p)!!
        assertEquals(Categorizer.MENSUALIDADES, monthly.categoria)
        val plans = listOf(com.example.prisma.api.InstallmentPurchaseDto("TIENDA", null, "708.29", 3, 24, "14000.00", false))
        val cats = InsightEngine.categories(listOf(monthly), plans)
        assertEquals("708.29", cats.single { it.categoria == Categorizer.MENSUALIDADES }.monto)
        assertEquals(0, InsightEngine.hormiga(listOf(monthly)).second.compras)
    }

    @Test
    fun creditBalanceReducesTotalDebt() {
        val plans = listOf(com.example.prisma.api.InstallmentPurchaseDto("TIENDA", null, null, 1, 12, "24000.00", false))
        val (total, debts) = InsightEngine.debts(BigDecimal("22000.00"), plans)
        assertEquals(BigDecimal("22000.00"), total)
        assertEquals("2000.00", debts.single { it.tipo == "A_FAVOR" }.monto)
    }

    @Test
    fun cleansMerchantNoise() {
        assertEquals("AMAZON A MESES", LocalMovementParser.cleanDescription("AMAZON A MESES           ANE 140618P37MX"))
        assertEquals("Mercadopago *Tienda", LocalMovementParser.cleanDescription("Mercadopago *Tienda | RFC: S.I."))
        assertEquals("MERPAGO*TIENDA", LocalMovementParser.cleanDescription("Compra en MERPAGO*TIENDA"))
        assertEquals(Categorizer.MASCOTAS, Categorizer.categorize("PETCO MX"))
        assertEquals(Categorizer.SUPERMERCADO, Categorizer.categorize("WAL MART VALLARTA"))
        assertEquals(Categorizer.TRAMITES, Categorizer.categorize("SRIA FNZ DL EDO"))
    }

    @Test
    fun monthComparisonOnlyUsesCardsPresentInBothMonths() {
        val sep = SpendingMath.MonthTotal(YearMonth.of(2026, 9), BigDecimal("23000"), BigDecimal.ZERO,
            mapOf("banco" to BigDecimal("18000"), "nu" to BigDecimal("5000")))
        val oct = SpendingMath.MonthTotal(YearMonth.of(2026, 10), BigDecimal("13500"), BigDecimal.ZERO,
            mapOf("banco" to BigDecimal("10000"), "mp" to BigDecimal("3500")))
        val cmp = SpendingMath.compare(oct, sep)
        assertEquals(-44, cmp.change) // 10,000 vs 18,000 (solo "banco"), no 13,500 vs 23,000
        assertEquals(setOf("nu", "mp"), cmp.missingCards)

        val dash = SpendingMath.dashboard(listOf(sep, oct), emptyList(), emptyList(), null, mapOf("nu" to "Nu •••• 0082", "mp" to "Mercado Pago"))
        assertTrue("Nu •••• 0082" in dash.mensaje && "Mercado Pago" in dash.mensaje, dash.mensaje)
        assertFalse("gastarías" in dash.mensaje, "Sin proyección cuando faltan estados: ${dash.mensaje}")
        assertNull(dash.proyeccion)
        assertTrue("el de octubre de Nu •••• 0082" in dash.mensaje && "el de septiembre de Mercado Pago" in dash.mensaje, dash.mensaje)
    }

    /** Genera los PDF ficticios en server/build/estados-prueba/ para probar desde la app. */
    @Test
    fun writeSampleStatementPdfs() {
        val dir = File("build/estados-prueba")
        listOf(YearMonth.of(2026, 8) to 0.8, YearMonth.of(2026, 9) to 0.9, YearMonth.of(2026, 10) to 1.1).forEach { (period, factor) ->
            val file = File(dir, "banco-ejemplo-$period.pdf")
            SampleStatements.writePdf(file, period, factor)
            assertTrue(file.length() > 0)
        }
    }
}
