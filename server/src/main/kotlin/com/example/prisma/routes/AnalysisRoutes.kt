package com.example.prisma.routes

import com.example.prisma.ai.AdviceTrigger
import com.example.prisma.ai.AnalysisCard
import com.example.prisma.ai.Categorizer
import com.example.prisma.ai.GovernmentSource
import com.example.prisma.ai.HealthMath
import com.example.prisma.ai.LocalAdvice
import com.example.prisma.ai.SpendingMath
import com.example.prisma.ai.StatementAnalysisService
import com.example.prisma.api.AdviceDto
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.AnalysisListResponse
import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.CardSpendDto
import com.example.prisma.api.CategoryAmountDto
import com.example.prisma.api.Consent
import com.example.prisma.api.HomeCardDto
import com.example.prisma.api.HomeMessageDto
import com.example.prisma.api.HomeMessageType
import com.example.prisma.api.HomeSummaryDto
import com.example.prisma.api.IncomeFrequency
import com.example.prisma.api.IncomeType
import com.example.prisma.api.MonthSpendDto
import com.example.prisma.api.StatementAnalysisDto
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.respondError
import com.example.prisma.models.AnalisisEstadosTable
import com.example.prisma.models.ConsejosDiariosTable
import com.example.prisma.models.ConsentimientosUsuarioTable
import com.example.prisma.models.DatosPersonalesTable
import com.example.prisma.models.IngresosPlanificacionTable
import com.example.prisma.models.IngresosTable
import com.example.prisma.models.MovimientosAnalizadosTable
import com.example.prisma.models.TarjetasCreditoTable
import com.example.prisma.security.DataCipher
import com.example.prisma.services.StatementDocumentReader
import com.example.prisma.services.StatementOcrUnavailable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

private val analysisJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val mexico: ZoneId = ZoneId.of("America/Mexico_City")
private fun today(): LocalDate = LocalDate.now(mexico)

/** Se crea una sola vez: decide al arrancar si hay Gemini configurado. */
private val analysisService by lazy { StatementAnalysisService() }

/** Tipos de movimiento que cuentan como gasto en los dashboards. */
private val SPEND_TYPES = listOf("COMPRA", "CARGO_FINANCIERO", "MENSUALIDAD")

fun Route.analysisRoutes(sessions: SessionStore) {

    // ---------- Subir y analizar un estado de cuenta ----------
    post("/api/v1/cards/{cardId}/analysis") {
        val userId = call.authenticatedUser(sessions) ?: return@post
        val cardId = call.uuidParam("cardId") ?: return@post call.badRequest("La tarjeta no es válida.")
        val card = dbQuery { FinanceStore.card(userId, cardId) }
            ?: return@post call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Tarjeta no encontrada.")

        val files = try {
            call.automaticFiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AutomaticUploadError) {
            return@post call.respondError(e.status, ApiErrorCodes.INVALID_REQUEST, e.message.orEmpty())
        } catch (_: Exception) {
            return@post call.badRequest("No se pudieron abrir los archivos.")
        }

        // El mismo documento no se vuelve a enviar a la IA.
        val fingerprint = automaticFingerprint(files)
        dbQuery { FinanceStore.analysisByFingerprint(userId, cardId, fingerprint) }?.let {
            return@post call.respond(it)
        }

        val document = try {
            automaticReaders.withPermit {
                runInterruptible(Dispatchers.IO) { StatementDocumentReader.read(files) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StatementOcrUnavailable) {
            return@post call.respondError(
                HttpStatusCode.ServiceUnavailable, "ocr_unavailable",
                "Esta imagen necesita OCR y no está disponible en el servidor. Sube el PDF original de tu banco.",
            )
        } catch (_: IllegalArgumentException) {
            return@post call.badRequest("Usa un PDF sin contraseña o imágenes claras, con hasta 12 páginas.")
        } catch (_: IOException) {
            return@post call.badRequest("No se pudo leer el documento. Prueba con otra copia.")
        }

        if (document.pages.joinToString("").isBlank()) {
            return@post call.badRequest("El documento no tiene texto legible. Sube el PDF original de tu banco.")
        }

        val (holderNames, allowAi) = dbQuery { FinanceStore.holderNames(userId) to FinanceStore.acceptedAi(userId) }
        val outcome = analysisService.analyze(
            pages = document.pages,
            warnings = document.warnings,
            card = card,
            holderNames = holderNames,
            allowAi = allowAi,
            today = today(),
        )

        val saved = dbQuery { FinanceStore.save(userId, cardId, fingerprint, outcome) }
        call.respond(saved)
    }

    // ---------- Lista de análisis (todas las tarjetas o una) ----------
    get("/api/v1/analysis") {
        val userId = call.authenticatedUser(sessions) ?: return@get
        val cardId = call.request.queryParameters["tarjeta"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val list = dbQuery { FinanceStore.analyses(userId, cardId) }
        call.respond(AnalysisListResponse(list, analysisService.aiAvailable))
    }

    delete("/api/v1/analysis/{id}") {
        val userId = call.authenticatedUser(sessions) ?: return@delete
        val id = call.uuidParam("id") ?: return@delete call.badRequest("El análisis no es válido.")
        val deleted = dbQuery {
            AnalisisEstadosTable.deleteWhere { (AnalisisEstadosTable.id eq id) and (AnalisisEstadosTable.usuarioId eq userId) }
        }
        if (deleted > 0) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Análisis no encontrado.")
        }
    }

    // ---------- Dashboard de gastos mes con mes ----------
    get("/api/v1/analysis/dashboard") {
        val userId = call.authenticatedUser(sessions) ?: return@get
        val cardId = call.request.queryParameters["tarjeta"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val dashboard = dbQuery {
            if (cardId != null && FinanceStore.card(userId, cardId) == null) return@dbQuery null
            val months = FinanceStore.monthTotals(userId, cardId)
            val lastPeriod = months.maxOfOrNull { it.period }
            val categories = lastPeriod?.let { FinanceStore.categories(userId, cardId, it) }.orEmpty()
            val perCard = if (cardId == null) FinanceStore.perCard(userId) else emptyList()
            SpendingMath.dashboard(months, categories, perCard, cardId?.toString(), FinanceStore.cardNames(userId))
        } ?: return@get call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Tarjeta no encontrada.")
        call.respond(dashboard)
    }
}

fun Route.homeRoutes(sessions: SessionStore) {
    get("/api/v1/home/summary") {
        val userId = call.authenticatedUser(sessions) ?: return@get
        val today = today()
        val base = dbQuery { FinanceStore.homeBase(userId, today) }

        // Consejo del día: una sola vez al día por usuario (Gemini si está disponible; si no, uno oficial local).
        val tip = dbQuery { FinanceStore.storedTip(userId, today) } ?: run {
            val triggers = base.triggers
            val fromAi = if (base.allowAi) {
                try {
                    withTimeoutOrNull(8_000) { analysisService.dailyTip(base.situation) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val local = LocalAdvice.ofTheDay(today.dayOfYear, triggers)
            val chosen = fromAi ?: local.toDto()
            val sourceId = GovernmentSource.entries.firstOrNull { it.url == chosen.fuenteUrl }?.name ?: local.fuente.name
            dbQuery { FinanceStore.storeTip(userId, today, chosen, sourceId) }
            chosen
        }

        val messages = base.messages + HomeMessageDto(
            tipo = HomeMessageType.CONSEJO,
            titulo = tip.titulo,
            texto = tip.texto,
            fuenteNombre = tip.fuenteNombre,
            fuenteUrl = tip.fuenteUrl,
        )
        call.respond(base.summary.copy(mensajes = messages))
    }
}

private fun ApplicationCall.uuidParam(name: String): UUID? =
    runCatching { UUID.fromString(parameters[name]) }.getOrNull()

private suspend fun ApplicationCall.badRequest(message: String) =
    respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, message)

/** Consultas a la base de datos. Todas se ejecutan dentro de dbQuery { } y filtran por usuario. */
internal object FinanceStore {

    fun card(userId: UUID, cardId: UUID): AnalysisCard? =
        TarjetasCreditoTable.selectAll()
            .where { (TarjetasCreditoTable.id eq cardId) and (TarjetasCreditoTable.usuarioId eq userId) }
            .singleOrNull()
            ?.let { AnalysisCard(cardId.toString(), it[TarjetasCreditoTable.banco], it[TarjetasCreditoTable.alias], it[TarjetasCreditoTable.ultimosCuatro]) }

    fun holderNames(userId: UUID): List<String> =
        DatosPersonalesTable.selectAll().where { DatosPersonalesTable.usuarioId eq userId }
            .flatMap { row ->
                listOf(row[DatosPersonalesTable.nombre], row[DatosPersonalesTable.apellido])
                    .mapNotNull { runCatching { DataCipher.decrypt(it) }.getOrNull() }
            }

    /** Consentimiento "EnvioIa": sin él, nunca se envían documentos a la IA. */
    fun acceptedAi(userId: UUID): Boolean =
        ConsentimientosUsuarioTable.selectAll().where {
            (ConsentimientosUsuarioTable.usuarioId eq userId) and
                (ConsentimientosUsuarioTable.consentimiento eq Consent.EnvioIa.name) and
                (ConsentimientosUsuarioTable.aceptado eq true)
        }.any()

    private fun ResultRow.toAnalysisDto(): StatementAnalysisDto =
        analysisJson.decodeFromString<StatementAnalysisDto>(DataCipher.decrypt(this[AnalisisEstadosTable.resultado])).copy(
            id = this[AnalisisEstadosTable.id].toString(),
            fechaAnalisis = LocalDateTime.ofInstant(this[AnalisisEstadosTable.fechaAnalisis], mexico)
                .truncatedTo(ChronoUnit.MINUTES).toString(),
        )

    fun analysisByFingerprint(userId: UUID, cardId: UUID, fingerprint: String): StatementAnalysisDto? =
        AnalisisEstadosTable.selectAll().where {
            (AnalisisEstadosTable.usuarioId eq userId) and (AnalisisEstadosTable.tarjetaId eq cardId) and
                (AnalisisEstadosTable.huella eq fingerprint)
        }.singleOrNull()?.toAnalysisDto()

    fun analyses(userId: UUID, cardId: UUID?): List<StatementAnalysisDto> =
        AnalisisEstadosTable.selectAll().where {
            if (cardId == null) {
                AnalisisEstadosTable.usuarioId eq userId
            } else {
                (AnalisisEstadosTable.usuarioId eq userId) and (AnalisisEstadosTable.tarjetaId eq cardId)
            }
        }.mapNotNull { row -> runCatching { row.toAnalysisDto() }.getOrNull() }
            .sortedWith(compareByDescending<StatementAnalysisDto> { it.periodo }.thenBy { it.banco })

    /** Guarda el análisis (reemplaza el del mismo mes y tarjeta) y sus movimientos. */
    fun save(
        userId: UUID,
        cardId: UUID,
        fingerprint: String,
        outcome: com.example.prisma.ai.AnalysisOutcome,
    ): StatementAnalysisDto {
        val period = outcome.dto.periodo
        AnalisisEstadosTable.deleteWhere { (AnalisisEstadosTable.tarjetaId eq cardId) and (AnalisisEstadosTable.periodo eq period) }

        val id = UUID.randomUUID()
        AnalisisEstadosTable.insert {
            it[AnalisisEstadosTable.id] = id
            it[usuarioId] = userId
            it[tarjetaId] = cardId
            it[periodo] = period
            it[huella] = fingerprint
            it[fuente] = outcome.dto.fuente.name
            it[resultado] = DataCipher.encrypt(analysisJson.encodeToString(outcome.dto))
            it[deudaTotal] = outcome.dto.deudaTotal.toBigDecimal()
            it[totalGastos] = outcome.dto.totalGastos.toBigDecimal()
            it[totalHormiga] = outcome.totalHormiga
            it[fechaAnalisis] = Instant.now()
        }

        data class Row(
            val fecha: LocalDate?, val descripcion: String, val categoria: String, val tipo: String,
            val monto: BigDecimal, val hormiga: Boolean, val sospechoso: Boolean,
        )
        val rows = outcome.movements.map {
            Row(it.fecha, it.descripcion, it.categoria, it.tipo.name, it.monto, it.esHormiga, it.sospechoso)
        }.toMutableList()
        // Las mensualidades a meses del periodo también son gasto del mes (no vienen como movimiento).
        outcome.dto.categorias.firstOrNull { it.categoria == Categorizer.MENSUALIDADES }
            ?.takeIf { outcome.movements.none { m -> m.categoria == Categorizer.MENSUALIDADES } }
            ?.let {
            rows += Row(null, "Mensualidades de compras a meses", Categorizer.MENSUALIDADES, "MENSUALIDAD", it.monto.toBigDecimal(), false, false)
        }
        MovimientosAnalizadosTable.batchInsert(rows) { r ->
            this[MovimientosAnalizadosTable.id] = UUID.randomUUID()
            this[MovimientosAnalizadosTable.analisisId] = id
            this[MovimientosAnalizadosTable.usuarioId] = userId
            this[MovimientosAnalizadosTable.tarjetaId] = cardId
            this[MovimientosAnalizadosTable.periodo] = period
            this[MovimientosAnalizadosTable.fecha] = r.fecha
            this[MovimientosAnalizadosTable.descripcion] = DataCipher.encrypt(r.descripcion)
            this[MovimientosAnalizadosTable.categoria] = r.categoria.take(40)
            this[MovimientosAnalizadosTable.tipo] = r.tipo
            this[MovimientosAnalizadosTable.monto] = r.monto
            this[MovimientosAnalizadosTable.esHormiga] = r.hormiga
            this[MovimientosAnalizadosTable.sospechoso] = r.sospechoso
        }

        return AnalisisEstadosTable.selectAll().where { AnalisisEstadosTable.id eq id }.single().toAnalysisDto()
    }

    private fun movements(userId: UUID, cardId: UUID?) =
        MovimientosAnalizadosTable.selectAll().where {
            val mine = MovimientosAnalizadosTable.usuarioId eq userId
            if (cardId == null) mine else mine and (MovimientosAnalizadosTable.tarjetaId eq cardId)
        }

    /** Gasto por mes: compras + intereses + mensualidades − devoluciones. */
    fun monthTotals(userId: UUID, cardId: UUID?): List<SpendingMath.MonthTotal> =
        movements(userId, cardId).toList()
            .groupBy { it[MovimientosAnalizadosTable.periodo] }
            .mapNotNull { (period, rows) ->
                val month = runCatching { YearMonth.parse(period) }.getOrNull() ?: return@mapNotNull null
                val total = rows.fold(BigDecimal.ZERO) { acc, r ->
                    when (r[MovimientosAnalizadosTable.tipo]) {
                        in SPEND_TYPES -> acc + r[MovimientosAnalizadosTable.monto]
                        "DEVOLUCION" -> acc - r[MovimientosAnalizadosTable.monto]
                        else -> acc
                    }
                }.max(BigDecimal.ZERO)
                val ants = rows.filter { it[MovimientosAnalizadosTable.esHormiga] }
                    .fold(BigDecimal.ZERO) { acc, r -> acc + r[MovimientosAnalizadosTable.monto] }
                val byCard = rows.groupBy { it[MovimientosAnalizadosTable.tarjetaId].toString() }
                    .mapValues { (_, cardRows) -> spendOf(cardRows) }
                SpendingMath.MonthTotal(month, total, ants, byCard)
            }

    private fun spendOf(rows: List<ResultRow>): BigDecimal =
        rows.fold(BigDecimal.ZERO) { acc, r ->
            when (r[MovimientosAnalizadosTable.tipo]) {
                in SPEND_TYPES -> acc + r[MovimientosAnalizadosTable.monto]
                "DEVOLUCION" -> acc - r[MovimientosAnalizadosTable.monto]
                else -> acc
            }
        }.max(BigDecimal.ZERO)

    /** Nombre corto de cada tarjeta del usuario: "Nu •••• 0082". */
    fun cardNames(userId: UUID): Map<String, String> =
        TarjetasCreditoTable.selectAll().where { TarjetasCreditoTable.usuarioId eq userId }.associate {
            it[TarjetasCreditoTable.id].toString() to
                (it[TarjetasCreditoTable.banco] + (it[TarjetasCreditoTable.ultimosCuatro]?.let { l -> " •••• $l" } ?: ""))
        }

    fun categories(userId: UUID, cardId: UUID?, period: YearMonth): List<CategoryAmountDto> =
        movements(userId, cardId).toList()
            .filter { it[MovimientosAnalizadosTable.periodo] == period.toString() }
            .groupBy { it[MovimientosAnalizadosTable.categoria] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, r ->
                    when (r[MovimientosAnalizadosTable.tipo]) {
                        in SPEND_TYPES -> acc + r[MovimientosAnalizadosTable.monto]
                        "DEVOLUCION" -> acc - r[MovimientosAnalizadosTable.monto]
                        else -> acc
                    }
                }
            }
            .filterValues { it.signum() > 0 }
            .map { (category, amount) -> CategoryAmountDto(category, amount.setScale(2).toPlainString()) }
            .sortedByDescending { it.monto.toBigDecimal() }

    /** Último mes y deuda más reciente de cada tarjeta. */
    fun perCard(userId: UUID): List<CardSpendDto> {
        val cards = TarjetasCreditoTable.selectAll().where { TarjetasCreditoTable.usuarioId eq userId }.toList()
        val latest = latestAnalyses(userId)
        return cards.mapNotNull { card ->
            val id = card[TarjetasCreditoTable.id]
            val analysis = latest[id] ?: return@mapNotNull null
            CardSpendDto(
                tarjetaId = id.toString(),
                nombre = card[TarjetasCreditoTable.alias].ifBlank { card[TarjetasCreditoTable.banco] },
                ultimosCuatro = card[TarjetasCreditoTable.ultimosCuatro],
                gastoUltimoMes = analysis[AnalisisEstadosTable.totalGastos].setScale(2).toPlainString(),
                deuda = analysis[AnalisisEstadosTable.deudaTotal].setScale(2).toPlainString(),
            )
        }.sortedByDescending { it.gastoUltimoMes.toBigDecimal() }
    }

    private fun latestAnalyses(userId: UUID): Map<UUID, ResultRow> =
        AnalisisEstadosTable.selectAll().where { AnalisisEstadosTable.usuarioId eq userId }
            .groupBy { it[AnalisisEstadosTable.tarjetaId] }
            .mapValues { (_, rows) -> rows.maxBy { it[AnalisisEstadosTable.periodo] } }

    // ---------- Inicio ----------

    class HomeBase(
        val summary: HomeSummaryDto,
        val messages: List<HomeMessageDto>,
        val triggers: Set<AdviceTrigger>,
        /** Situación general sin datos personales, para pedir el consejo del día. */
        val situation: List<String>,
        val allowAi: Boolean,
    )

    fun homeBase(userId: UUID, today: LocalDate): HomeBase {
        val userName = DatosPersonalesTable.selectAll().where { DatosPersonalesTable.usuarioId eq userId }
            .singleOrNull()?.get(DatosPersonalesTable.nombre)
            ?.let { runCatching { DataCipher.decrypt(it) }.getOrNull() }.orEmpty()

        val cards = TarjetasCreditoTable.selectAll().where { TarjetasCreditoTable.usuarioId eq userId }.toList()
        val latestRows = latestAnalyses(userId)
        val latest = latestRows.mapValues { (_, row) -> runCatching { row.toAnalysisDto() }.getOrNull() }

        val messages = mutableListOf<HomeMessageDto>()
        val homeCards = cards.map { card ->
            val id = card[TarjetasCreditoTable.id]
            val banco = card[TarjetasCreditoTable.banco]
            val last4 = card[TarjetasCreditoTable.ultimosCuatro]
            val label = "$banco${last4?.let { " •••• $it" } ?: ""}"
            val analysis = latest[id]

            // Próximo pago: la fecha límite del estado si sigue vigente; si no, el día de pago registrado.
            val deadline = analysis?.fechaLimitePago?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isBefore(today) }
                ?: card[TarjetasCreditoTable.diaPago]?.let { nextDayOfMonth(it, today) }
            val daysToPay = deadline?.let { ChronoUnit.DAYS.between(today, it).toInt() }
            val amountToPay = analysis?.pagoSinIntereses?.takeIf { deadline != null && analysis.fechaLimitePago == deadline.toString() }

            if (daysToPay != null && daysToPay <= 7) {
                val whenText = when (daysToPay) { 0 -> "hoy"; 1 -> "mañana"; else -> "en $daysToPay días" }
                messages += HomeMessageDto(
                    tipo = HomeMessageType.PAGO,
                    titulo = "Pago de $label",
                    texto = "Tu pago vence $whenText (${shortDate(deadline)})." +
                        (amountToPay?.let { " Para no pagar intereses cubre ${StatementAnalysisService.mxn(it.toBigDecimal())}." } ?: ""),
                    tarjetaId = id.toString(),
                    urgente = daysToPay <= 3,
                )
            }
            card[TarjetasCreditoTable.diaCorte]?.let { cut ->
                val cutDate = nextDayOfMonth(cut, today)
                val days = ChronoUnit.DAYS.between(today, cutDate).toInt()
                if (days in 0..3) {
                    messages += HomeMessageDto(
                        tipo = HomeMessageType.CORTE,
                        titulo = "Corte de $label",
                        texto = "Tu tarjeta corta ${if (days == 0) "hoy" else if (days == 1) "mañana" else "en $days días"}. " +
                            "Lo que compres después del corte lo pagarás hasta el siguiente mes.",
                        tarjetaId = id.toString(),
                    )
                }
            }
            analysis?.alertas?.filter { it.severidad != AlertSeverity.BAJA }?.takeIf { it.isNotEmpty() }?.let { alerts ->
                val first = alerts.first()
                messages += HomeMessageDto(
                    tipo = HomeMessageType.ALERTA,
                    titulo = "Revisa ${alerts.size} cargo${if (alerts.size == 1) "" else "s"} en $label",
                    texto = "${first.descripcion} por ${StatementAnalysisService.mxn(first.monto.toBigDecimal())}: ${first.motivo}",
                    tarjetaId = id.toString(),
                    urgente = alerts.any { it.severidad == AlertSeverity.ALTA },
                )
            }
            analysis?.comprasMeses
                ?.filter { it.pagoActual != null && it.pagosTotales != null && it.pagosTotales!! - it.pagoActual!! == 1 }
                ?.forEach { plan ->
                    messages += HomeMessageDto(
                        tipo = HomeMessageType.MESES,
                        titulo = "Casi terminas una compra a meses",
                        texto = "A \"${plan.descripcion}\" en $label le queda una mensualidad" +
                            (plan.mensualidad?.let { " de ${StatementAnalysisService.mxn(it.toBigDecimal())}" } ?: "") + ".",
                        tarjetaId = id.toString(),
                    )
                }

            HomeCardDto(
                tarjetaId = id.toString(),
                banco = banco,
                alias = card[TarjetasCreditoTable.alias],
                ultimosCuatro = last4,
                deuda = analysis?.deudaTotal,
                proximoPago = deadline?.toString(),
                diasParaPago = daysToPay,
                pagoSinIntereses = amountToPay,
            )
        }

        val analyses = latest.values.filterNotNull()
        val debt = analyses.fold(BigDecimal.ZERO) { acc, a -> acc + a.deudaTotal.toBigDecimal() }
        val toPay = homeCards.mapNotNull { it.pagoSinIntereses?.toBigDecimal() }.fold(BigDecimal.ZERO, BigDecimal::add)
        val limit = analyses.mapNotNull { it.limiteCredito?.toBigDecimal() }.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)

        val (incomeMonth, incomeEstimate) = incomes(userId, today)

        val months = monthTotals(userId, null).sortedBy { it.period }
        val last = months.lastOrNull()
        val previous = months.getOrNull(months.size - 2)
        val comparison = if (last != null && previous != null) SpendingMath.compare(last, previous) else null
        val lastPeriodRows = last?.let { m ->
            movements(userId, null).toList().filter { it[MovimientosAnalizadosTable.periodo] == m.period.toString() }
        }.orEmpty()
        val interest = lastPeriodRows.filter { it[MovimientosAnalizadosTable.tipo] == "CARGO_FINANCIERO" }
            .fold(BigDecimal.ZERO) { acc, r -> acc + r[MovimientosAnalizadosTable.monto] }
        val openAlerts = analyses.sumOf { a -> a.alertas.count { it.severidad != AlertSeverity.BAJA } }

        val health = HealthMath.compute(
            HealthMath.Inputs(
                hasAnalysis = analyses.isNotEmpty(),
                debt = debt,
                monthlyIncome = incomeEstimate.takeIf { it.signum() > 0 },
                paymentsDue = toPay,
                creditLimit = limit,
                lastMonthSpend = last?.total,
                comparableLastSpend = comparison?.takeIf { it.change != null }?.last,
                previousMonthSpend = comparison?.takeIf { it.change != null }?.previous,
                lastMonthHormiga = last?.hormiga,
                lastMonthInterest = if (last != null) interest else null,
                openAlerts = openAlerts,
            ),
        )

        val triggers = buildSet {
            analyses.forEach { a ->
                if (a.deudas.any { it.tipo == "REVOLVENTE" }) add(AdviceTrigger.DEUDA_REVOLVENTE)
                if (a.comprasMeses.any { it.conIntereses }) add(AdviceTrigger.CON_INTERESES)
                if (a.comprasMeses.any { !it.conIntereses }) add(AdviceTrigger.MSI)
                if (a.gastosHormiga.compras > 0) add(AdviceTrigger.HORMIGA)
            }
            if (openAlerts > 0) add(AdviceTrigger.ALERTAS)
        }
        val situation = buildList {
            add("${cards.size} tarjeta(s) de crédito")
            add("salud financiera: ${health.nivel.name.lowercase()}")
            if (AdviceTrigger.DEUDA_REVOLVENTE in triggers) add("tiene saldo revolvente")
            if (AdviceTrigger.CON_INTERESES in triggers) add("tiene compras a meses con intereses")
            if (AdviceTrigger.MSI in triggers) add("tiene compras a meses sin intereses")
            if (AdviceTrigger.HORMIGA in triggers) add("tiene gastos hormiga")
            comparison?.change?.let { add(if (it > 0) "su gasto subió el último mes" else "su gasto bajó el último mes") }
        }

        PayrollStore.homeMessage(userId, today)?.let { messages += it }
        messages += ReminderStore.homeMessages(userId, today)

        val ordered = messages.sortedWith(
            compareByDescending<HomeMessageDto> { it.urgente }.thenBy {
                listOf(
                    HomeMessageType.PAGO, HomeMessageType.ALERTA, HomeMessageType.NOMINA, HomeMessageType.RECORDATORIO,
                    HomeMessageType.CORTE, HomeMessageType.MESES, HomeMessageType.CONSEJO,
                )
                    .indexOf(it.tipo)
            },
        )

        val summary = HomeSummaryDto(
            userName = userName,
            salud = health,
            tarjetas = homeCards.sortedWith(compareBy(nullsLast()) { it.diasParaPago }),
            deudaTotal = debt.setScale(2).toPlainString(),
            totalAPagar = toPay.setScale(2).toPlainString(),
            ingresosMes = incomeMonth.setScale(2).toPlainString(),
            ingresoMensualEstimado = incomeEstimate.setScale(2).toPlainString(),
            gastoUltimoMes = last?.total?.setScale(2)?.toPlainString(),
            gastoMesAnterior = previous?.total?.setScale(2)?.toPlainString(),
            cambioPorcentaje = comparison?.change,
            comparacionParcial = comparison?.missingCards?.isNotEmpty() == true,
            mesesGasto = months.takeLast(6).map { MonthSpendDto(it.period.toString(), it.total.setScale(2).toPlainString(), it.hormiga.setScale(2).toPlainString()) },
        )
        return HomeBase(summary, ordered, triggers, situation, acceptedAi(userId))
    }

    /**
     * Ingresos del mes en curso e ingreso mensual estimado: los recurrentes según su frecuencia
     * (último monto de cada concepto) más los extraordinarios cobrados este mes.
     */
    private fun incomes(userId: UUID, today: LocalDate): Pair<BigDecimal, BigDecimal> {
        val rows = IngresosTable.join(
            IngresosPlanificacionTable, JoinType.LEFT,
            onColumn = IngresosTable.id, otherColumn = IngresosPlanificacionTable.ingresoId,
        ).selectAll().where { IngresosTable.usuarioId eq userId }.toList()

        val month = YearMonth.from(today)
        fun registered(row: ResultRow): LocalDate? =
            row[IngresosTable.fechaRegistro]?.let { LocalDate.ofInstant(it, mexico) }
        fun charged(row: ResultRow): LocalDate? =
            row.getOrNull(IngresosPlanificacionTable.fechaCobro) ?: registered(row)

        val thisMonth = rows.filter { r -> charged(r)?.let(YearMonth::from) == month }
            .fold(BigDecimal.ZERO) { acc, r -> acc + r[IngresosTable.montoNeto] }

        val recurring = rows.filter { it.getOrNull(IngresosPlanificacionTable.tipoIngreso) == IncomeType.RECURRENTE.name }
            .groupBy { it[IngresosTable.nombre].trim().lowercase() }
            .values.map { group -> group.maxBy { charged(it) ?: LocalDate.MIN } }
            .fold(BigDecimal.ZERO) { acc, r ->
                val factor = when (r.getOrNull(IngresosPlanificacionTable.frecuencia)) {
                    IncomeFrequency.SEMANAL.name -> BigDecimal("4.33")
                    IncomeFrequency.CATORCENAL.name -> BigDecimal("2.17")
                    IncomeFrequency.QUINCENAL.name -> BigDecimal(2)
                    else -> BigDecimal.ONE
                }
                acc + r[IngresosTable.montoNeto] * factor
            }
        val extraordinaryThisMonth = rows.filter {
            it.getOrNull(IngresosPlanificacionTable.tipoIngreso) != IncomeType.RECURRENTE.name &&
                charged(it)?.let(YearMonth::from) == month
        }.fold(BigDecimal.ZERO) { acc, r -> acc + r[IngresosTable.montoNeto] }

        val estimate = if (recurring.signum() > 0) recurring + extraordinaryThisMonth else thisMonth
        return thisMonth to estimate.setScale(2, java.math.RoundingMode.HALF_UP)
    }

    fun storedTip(userId: UUID, day: LocalDate): AdviceDto? =
        ConsejosDiariosTable.selectAll().where { (ConsejosDiariosTable.usuarioId eq userId) and (ConsejosDiariosTable.fecha eq day) }
            .singleOrNull()?.let { row ->
                val source = GovernmentSource.fromId(row[ConsejosDiariosTable.fuenteId]) ?: GovernmentSource.CONDUSEF
                AdviceDto(row[ConsejosDiariosTable.titulo], row[ConsejosDiariosTable.texto], source.nombre, source.url)
            }

    fun storeTip(userId: UUID, day: LocalDate, tip: AdviceDto, sourceId: String) {
        if (storedTip(userId, day) != null) return
        ConsejosDiariosTable.insert {
            it[usuarioId] = userId
            it[fecha] = day
            it[titulo] = tip.titulo.take(120)
            it[texto] = tip.texto
            it[fuenteId] = sourceId
        }
    }

    /** Siguiente fecha (hoy incluido) con ese día del mes; si el mes es corto, el último día. */
    internal fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate {
        fun inMonth(m: YearMonth) = m.atDay(minOf(day, m.lengthOfMonth()))
        val current = inMonth(YearMonth.from(today))
        return if (current.isBefore(today)) inMonth(YearMonth.from(today).plusMonths(1)) else current
    }

    private fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth} de ${com.example.prisma.ai.monthName(YearMonth.from(date))}"
}
