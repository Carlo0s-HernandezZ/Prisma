package com.example.prisma.ai

import com.example.prisma.api.AdviceDto
import com.example.prisma.api.AnalysisSource
import com.example.prisma.api.InstallmentPurchaseDto
import com.example.prisma.api.StatementAnalysisDto
import com.example.prisma.services.AutomaticStatementParser
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Datos de la tarjeta que se muestran junto al análisis (nunca se envían a la IA). */
data class AnalysisCard(
    val id: String,
    val banco: String,
    val alias: String,
    val ultimosCuatro: String?,
)

/** Resultado del análisis: lo que ve el usuario y los movimientos que alimentan los dashboards. */
data class AnalysisOutcome(
    val dto: StatementAnalysisDto,
    val movements: List<Movement>,
    val totalHormiga: BigDecimal,
)

// ---------- Respuesta esperada de Gemini (todo opcional: se valida antes de usarse) ----------

@Serializable
private data class AiMovement(
    val fecha: String? = null,
    val descripcion: String? = null,
    val monto: String? = null,
    val tipo: String? = null,
    val categoria: String? = null,
)

@Serializable
private data class AiInstallment(
    val descripcion: String? = null,
    val montoOriginal: String? = null,
    val mensualidad: String? = null,
    val pagoActual: Int? = null,
    val pagosTotales: Int? = null,
    val saldoPendiente: String? = null,
    val conIntereses: Boolean? = null,
    val tasaAnual: String? = null,
)

@Serializable
private data class AiFlagged(
    val descripcion: String? = null,
    val monto: String? = null,
    val fecha: String? = null,
    val motivo: String? = null,
)

@Serializable
private data class AiAdvice(
    val titulo: String? = null,
    val texto: String? = null,
    val fuenteId: String? = null,
)

@Serializable
private data class AiStatement(
    val periodo: String? = null,
    val fechaCorte: String? = null,
    val fechaLimitePago: String? = null,
    val saldoTotal: String? = null,
    val pagoMinimo: String? = null,
    val pagoSinIntereses: String? = null,
    val limiteCredito: String? = null,
    val movimientos: List<AiMovement> = emptyList(),
    val comprasMeses: List<AiInstallment> = emptyList(),
    val cargosSospechosos: List<AiFlagged> = emptyList(),
    val resumen: String? = null,
    val consejos: List<AiAdvice> = emptyList(),
)

/**
 * Analiza un estado de cuenta ya convertido a texto.
 *
 * 1. El lector determinista de Prisma (AutomaticStatementParser) saca saldos, fechas y planes a meses.
 * 2. Si hay Gemini y el usuario dio su consentimiento, se le envía el texto ANONIMIZADO para leer
 *    movimientos, compras a meses, cargos raros y proponer consejos de fuentes oficiales.
 * 3. Si no hay IA (o falla), el lector local saca los movimientos.
 * 4. Todos los totales, gastos hormiga, alertas y deudas los calcula Kotlin (InsightEngine).
 */
class StatementAnalysisService(
    private val gemini: GeminiClient? = if (GeminiClient.isConfigured()) GeminiClient() else null,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val aiAvailable: Boolean get() = gemini != null

    suspend fun analyze(
        pages: List<String>,
        warnings: List<String>,
        card: AnalysisCard,
        holderNames: List<String>,
        allowAi: Boolean,
        today: LocalDate = LocalDate.now(),
    ): AnalysisOutcome {
        val detection = runCatching { AutomaticStatementParser.detect(pages, warnings) }.getOrNull()
        val fields = detection?.campos
        val notices = mutableListOf<String>()

        val ai: AiStatement? = if (allowAi && gemini != null) {
            try {
                askGemini(pages, holderNames)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                notices += "Gemini no respondió; mostramos el análisis del lector local de Prisma."
                null
            }
        } else {
            notices += if (gemini == null) {
                "Análisis hecho con el lector local de Prisma (la IA no está configurada en el servidor)."
            } else {
                "Análisis hecho con el lector local: no autorizaste el envío de documentos a la IA."
            }
            null
        }

        // ---- Periodo del estado ----
        val cutoff = listOfNotNull(fields?.fechaCorte, ai?.fechaCorte).firstNotNullOfOrNull(::parseIsoDate)
            ?: DeadlineFinder.findCutoff(pages, today)
        val period = listOfNotNull(fields?.periodo, ai?.periodo).firstNotNullOfOrNull(::parsePeriod)
            ?: cutoff?.let(YearMonth::from)

        // ---- Movimientos ----
        val aiMovements = ai?.movimientos.orEmpty().mapNotNull { it.toMovement(period) }
        var movements = aiMovements.ifEmpty { LocalMovementParser.parse(pages, period) }
        val finalPeriod = period
            ?: movements.mapNotNull { it.fecha }.maxOrNull()?.let(YearMonth::from)
            ?: YearMonth.from(today)
        if (movements.isEmpty()) {
            notices += "No pudimos leer los movimientos de este estado; los dashboards no incluirán sus compras."
        }

        // ---- Compras a meses: primero el lector determinista, después la IA ----
        val installments = detection?.detalle?.planes.orEmpty().map {
            InstallmentPurchaseDto(
                descripcion = it.descripcion,
                montoOriginal = it.montoOriginal,
                mensualidad = it.cuota,
                pagoActual = it.pagoActual,
                pagosTotales = it.totalPagos,
                saldoPendiente = it.saldoPendiente,
                conIntereses = !it.sinIntereses,
                tasaAnual = it.tasa.takeUnless { _ -> it.sinIntereses },
            )
        }.ifEmpty { LocalInstallmentParser.parse(pages) }
            .ifEmpty { ai?.comprasMeses.orEmpty().mapNotNull { it.toDto() } }

        // ---- Cálculos deterministas ----
        val (withAnts, hormiga) = InsightEngine.hormiga(movements)
        val aiFlags = ai?.cargosSospechosos.orEmpty().mapNotNull { f ->
            val description = f.descripcion?.trim()?.takeIf { it.length >= 2 } ?: return@mapNotNull null
            InsightEngine.AiFlag(description, parseMoney(f.monto), parseIsoDate(f.fecha), f.motivo.orEmpty())
        }
        val (marked, alerts) = InsightEngine.suspicious(withAnts, aiFlags)
        movements = marked

        val minimum = parseMoney(fields?.pagoMinimo) ?: parseMoney(ai?.pagoMinimo)
        val noInterest = parseMoney(fields?.pagoParaNoGenerarIntereses) ?: parseMoney(ai?.pagoSinIntereses)
        val statedBalance = parseMoney(fields?.saldoTotal) ?: parseMoney(ai?.saldoTotal) ?: statementBalance(pages)
        // Lo que debes nunca es menor que lo que tienes que pagar para no generar intereses.
        val balance = listOfNotNull(statedBalance, noInterest).maxOrNull()
        val (totalDebt, debts) = InsightEngine.debts(balance, installments)
        val categories = InsightEngine.categories(movements, installments)
        val totalSpent = categories.fold(ZERO) { acc, c -> acc + c.monto.toBigDecimal() }
        val finance = movements.filter { it.tipo == MovementType.CARGO_FINANCIERO }.fold(ZERO) { acc, m -> acc + m.monto }
        val deadline = listOfNotNull(fields?.fechaLimitePago, ai?.fechaLimitePago).firstNotNullOfOrNull(::parseIsoDate)
            ?: DeadlineFinder.find(pages, cutoff, period, today)

        // ---- Consejos: los de la IA que pasan la política + los locales que apliquen ----
        val triggers = buildSet {
            if (debts.any { it.tipo == "REVOLVENTE" }) add(AdviceTrigger.DEUDA_REVOLVENTE)
            if (installments.any { it.conIntereses } || finance.signum() > 0) add(AdviceTrigger.CON_INTERESES)
            if (installments.any { !it.conIntereses }) add(AdviceTrigger.MSI)
            if (hormiga.compras > 0) add(AdviceTrigger.HORMIGA)
            if (alerts.isNotEmpty()) add(AdviceTrigger.ALERTAS)
            if (minimum != null && noInterest != null && noInterest > minimum * BigDecimal(3)) add(AdviceTrigger.SOLO_MINIMO)
        }
        val aiAdvice = ai?.consejos.orEmpty().mapNotNull { AdvicePolicy.accept(it.titulo, it.texto, it.fuenteId) }
        val advice: List<AdviceDto> = (aiAdvice + LocalAdvice.forTriggers(triggers, limit = 5))
            .distinctBy { normalizeText(it.titulo) }
            .take(5)

        val summary = buildSummary(totalDebt, installments, hormiga.total.toBigDecimal(), hormiga.compras, alerts.size, finance)
        val aiSummary = ai?.resumen?.trim()?.take(400)?.takeIf { AdvicePolicy.isAllowed("resumen", it) }

        val dto = StatementAnalysisDto(
            id = "",
            tarjetaId = card.id,
            banco = card.banco,
            alias = card.alias,
            ultimosCuatro = card.ultimosCuatro,
            periodo = finalPeriod.toString(),
            fuente = if (ai != null) AnalysisSource.GEMINI else AnalysisSource.LOCAL,
            fechaAnalisis = "",
            resumen = listOfNotNull(summary, aiSummary).joinToString("\n\n"),
            deudaTotal = totalDebt.plain(),
            pagoMinimo = minimum?.plain(),
            pagoSinIntereses = noInterest?.plain(),
            fechaLimitePago = deadline?.toString(),
            limiteCredito = (parseMoney(ai?.limiteCredito) ?: labeledAmount(pages, LIMIT_LABEL))?.plain(),
            totalGastos = totalSpent.plain(),
            interesesYComisiones = finance.plain(),
            comprasMeses = installments,
            deudas = debts,
            gastosHormiga = hormiga,
            alertas = alerts,
            consejos = advice,
            categorias = categories,
            movimientosLeidos = movements.size,
            aviso = notices.distinct().joinToString(" ").ifBlank { null },
        )
        return AnalysisOutcome(dto, movements, hormiga.total.toBigDecimal())
    }

    /** Consejo del día a partir de la situación general (sin datos personales). Null si la IA no responde bien. */
    suspend fun dailyTip(situation: List<String>): AdviceDto? {
        val client = gemini ?: return null
        val prompt = """
            Eres un asistente de educación financiera para personas en México.
            Situación general del usuario (sin datos personales): ${situation.joinToString("; ").ifBlank { "sin datos" }}.

            Escribe UN consejo breve y práctico para mejorar su salud financiera día a día.
            Reglas obligatorias:
            - Solo hábitos: presupuesto, pagos a tiempo, uso responsable de tarjetas, gastos hormiga, comparar precios.
            - PROHIBIDO recomendar inversiones, instrumentos, productos financieros, bancos o dónde poner su dinero.
            - Basado en materiales de: ${GovernmentSource.entries.joinToString { "${it.name} (${it.nombre})" }}.
            - "fuenteId" debe ser exactamente uno de: ${GovernmentSource.entries.joinToString { it.name }}.
            - Español de México, tono cercano, máximo 2 frases.

            Devuelve solo JSON: {"titulo": "máximo 6 palabras", "texto": "...", "fuenteId": "..."}
        """.trimIndent()
        val answer = client.generarRespuesta(prompt, formatoJson = true, temperatura = 0.7)
        val advice = json.decodeFromString<AiAdvice>(GeminiClient.cleanJson(answer))
        return AdvicePolicy.accept(advice.titulo, advice.texto, advice.fuenteId)
    }

    private suspend fun askGemini(pages: List<String>, holderNames: List<String>): AiStatement {
        val text = StatementAnonymizer.anonymize(
            pages.joinToString("\n\n----- página -----\n\n"),
            holderNames,
        ).take(MAX_AI_CHARS)

        val prompt = """
            Eres un analista de estados de cuenta de tarjetas de crédito de bancos de México.
            El texto ya está anonimizado ([TITULAR], [NÚMERO], etc.). No intentes deducir datos personales.

            Extrae la información del estado de cuenta. Reglas:
            - Usa solo lo que aparece en el texto; si un dato no aparece, usa null. No inventes montos.
            - Montos como texto con punto decimal y sin símbolos: "1234.50". Fechas "AAAA-MM-DD". Periodo "AAAA-MM" (mes del corte).
            - "movimientos": cada compra, cargo, pago o devolución del periodo. Monto siempre positivo.
              tipo: COMPRA, CARGO_FINANCIERO (intereses, comisiones, IVA, anualidad), PAGO o DEVOLUCION.
              categoria: una de ${Categorizer.all.joinToString(", ") { "\"$it\"" }}.
              NO incluyas en "movimientos" las mensualidades de compras a meses: esas van en "comprasMeses".
            - "comprasMeses": compras diferidas. conIntereses=false para meses sin intereses (MSI), true si generan intereses.
            - "cargosSospechosos": cargos que el titular debería revisar (duplicados, comercios o países poco comunes,
              montos atípicos, cargos que parecen de prueba). Explica el motivo en una frase.
            - "resumen": 1 o 2 frases con observaciones generales del comportamiento de gasto, SIN repetir montos.
            - "consejos": de 2 a 4 consejos prácticos para mejorar su salud financiera según lo que veas.
              Solo hábitos (pagar más del mínimo, evitar gastos hormiga, comparar el CAT, presupuesto, reportar cargos).
              PROHIBIDO recomendar inversiones, instrumentos financieros, productos, bancos o dónde invertir.
              "fuenteId" debe ser exactamente uno de: ${GovernmentSource.entries.joinToString { "${it.name} (${it.nombre})" }}.
            - Responde en español de México.

            Devuelve únicamente este JSON:
            {
              "periodo": "AAAA-MM", "fechaCorte": "AAAA-MM-DD", "fechaLimitePago": "AAAA-MM-DD",
              "saldoTotal": "0.00", "pagoMinimo": "0.00", "pagoSinIntereses": "0.00", "limiteCredito": "0.00",
              "movimientos": [{"fecha": "AAAA-MM-DD", "descripcion": "", "monto": "0.00", "tipo": "COMPRA", "categoria": ""}],
              "comprasMeses": [{"descripcion": "", "montoOriginal": "0.00", "mensualidad": "0.00", "pagoActual": 1,
                                "pagosTotales": 12, "saldoPendiente": "0.00", "conIntereses": false, "tasaAnual": null}],
              "cargosSospechosos": [{"descripcion": "", "monto": "0.00", "fecha": "AAAA-MM-DD", "motivo": ""}],
              "resumen": "",
              "consejos": [{"titulo": "", "texto": "", "fuenteId": ""}]
            }

            ESTADO DE CUENTA:
            $text
        """.trimIndent()

        val answer = requireNotNull(gemini).generarRespuesta(prompt, formatoJson = true, temperatura = 0.1)
        return json.decodeFromString<AiStatement>(GeminiClient.cleanJson(answer))
    }

    private fun AiMovement.toMovement(period: YearMonth?): Movement? {
        val description = descripcion?.trim()?.takeIf { it.length >= 2 }?.take(120) ?: return null
        val amount = parseMoney(monto)?.takeIf { it.signum() > 0 } ?: return null
        val type = MovementType.entries.firstOrNull { it.name.equals(tipo?.trim(), ignoreCase = true) } ?: MovementType.COMPRA
        val date = parseIsoDate(fecha) ?: fecha?.let { LocalMovementParser.parseDate(it, period) }
        val category = if (type == MovementType.CARGO_FINANCIERO) Categorizer.INTERESES else Categorizer.validOrGuess(categoria, description)
        return Movement(date, description, amount, type, category)
    }

    private fun AiInstallment.toDto(): InstallmentPurchaseDto? {
        val pending = parseMoney(saldoPendiente) ?: return null
        return InstallmentPurchaseDto(
            descripcion = descripcion?.trim()?.take(120)?.ifBlank { null } ?: "Compra a meses",
            montoOriginal = parseMoney(montoOriginal)?.plain(),
            mensualidad = parseMoney(mensualidad)?.plain(),
            pagoActual = pagoActual?.takeIf { it in 0..120 },
            pagosTotales = pagosTotales?.takeIf { it in 1..120 },
            saldoPendiente = pending.plain(),
            conIntereses = conIntereses ?: false,
            tasaAnual = tasaAnual?.takeIf { conIntereses == true },
        )
    }

    companion object {
        private const val MAX_AI_CHARS = 120_000

        /** Etiquetas del saldo total, de la más confiable a la menos. */
        private val BALANCE_LABELS = listOf(
            "saldo\\s+deudor\\s+total",
            "saldo\\s+total\\s+(?:del|al)\\s+(?:periodo|corte)",
            "saldo\\s+total(?!\\s+(?:a|en)?\\s*(?:meses|msi))",
            "saldo\\s+al\\s+corte",
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val LIMIT_LABEL = Regex("l[ií]mite\\s+de\\s+cr[eé]dito", RegexOption.IGNORE_CASE)

        /** Monto después de la etiqueta; tolera notas al pie ("total12 $0.00", "total: 1 1 $ 37,187.78"). */
        private val AMOUNT_AFTER = Regex(
            "^[^\\d$\\n]{0,20}?(?:\\d{1,2}\\s*){0,2}[:\\s]*([+-]?)\\s*\\$\\s*(\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})|^[\\s:]*([+-]?)\\s*(\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})",
        )

        /** Saldo total del estado; ignora el saldo del periodo anterior. */
        internal fun statementBalance(pages: List<String>): BigDecimal? =
            BALANCE_LABELS.firstNotNullOfOrNull { label ->
                labeledAmount(pages.map { page -> page.lines().filterNot { "anterior" in it.lowercase() }.joinToString("\n") }, label)
            }

        /** Primer monto escrito justo después de la etiqueta (respaldo cuando el lector principal no lo encuentra). */
        internal fun labeledAmount(pages: List<String>, label: Regex): BigDecimal? =
            pages.asSequence().flatMap { it.lineSequence() }.firstNotNullOfOrNull { line ->
                label.find(line)?.let { match -> AMOUNT_AFTER.find(line.substring(match.range.last + 1)) }
                    ?.groupValues?.let { g -> g[2].ifEmpty { g[4] } }?.let(::parseMoney)
            }

        internal fun parsePeriod(value: String?): YearMonth? =
            value?.trim()?.let { runCatching { YearMonth.parse(it.take(7)) }.getOrNull() }

        internal fun parseIsoDate(value: String?): LocalDate? =
            value?.trim()?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

        /** Resumen en lenguaje sencillo, calculado sin IA (los montos siempre son exactos). */
        internal fun buildSummary(
            debt: BigDecimal,
            installments: List<InstallmentPurchaseDto>,
            hormiga: BigDecimal,
            hormigaCount: Int,
            alerts: Int,
            finance: BigDecimal,
        ): String {
            val parts = mutableListOf<String>()
            parts += "Debes ${mxn(debt)} en esta tarjeta."
            val msi = installments.count { !it.conIntereses }
            val mci = installments.count { it.conIntereses }
            if (msi + mci > 0) {
                parts += buildString {
                    append("Tienes ${msi + mci} compra${if (msi + mci == 1) "" else "s"} a meses")
                    append(" ($msi sin intereses y $mci con intereses).")
                }
            }
            if (finance.signum() > 0) parts += "Pagaste ${mxn(finance)} en intereses y comisiones."
            if (hormigaCount > 0) {
                parts += "Detectamos $hormigaCount gasto${if (hormigaCount == 1) "" else "s"} hormiga que suman ${mxn(hormiga)}."
            }
            parts += if (alerts > 0) {
                "Hay $alerts cargo${if (alerts == 1) "" else "s"} que conviene revisar."
            } else {
                "No vimos cargos extraños."
            }
            return parts.joinToString(" ")
        }

        internal fun mxn(amount: BigDecimal): String =
            "$" + String.format(java.util.Locale.US, "%,.2f", amount.money())
    }
}
