@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.*
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.apiErrorMessage
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

data class StatementUploadFile(
    val nombre: String,
    val mimeType: String,
    val bytes: ByteArray,
)

class AutomaticStatementException(
    val status: Int?,
    message: String,
) : Exception(message)

interface AutomaticStatementRepository {
    suspend fun list(cardId: String): Result<List<AutomaticStatementDto>>

    suspend fun importFiles(
        cardId: String,
        files: List<StatementUploadFile>,
    ): Result<AutomaticStatementDto>

    suspend fun resolve(
        cardId: String,
        id: String,
        request: StatementResolutionRequest,
    ): Result<AutomaticStatementDto>
}

class KtorAutomaticStatementRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : AutomaticStatementRepository {
    private fun url(cardId: String) =
        "$baseUrl/api/v1/cards/$cardId/automatic-statements"

    override suspend fun list(
        cardId: String,
    ): Result<List<AutomaticStatementDto>> = send {
        client.get(url(cardId)) {
            withToken(token)
        }
    }

    override suspend fun importFiles(
        cardId: String,
        files: List<StatementUploadFile>,
    ): Result<AutomaticStatementDto> = send {
        client.post(url(cardId)) {
            withToken(token)

            timeout {
                requestTimeoutMillis = 420_000
                socketTimeoutMillis = 420_000
            }

            setBody(
                MultiPartFormDataContent(
                    formData {
                        files.forEachIndexed { index, file ->
                            append(
                                "files",
                                file.bytes,
                                Headers.build {
                                    append(
                                        HttpHeaders.ContentType,
                                        "application/octet-stream",
                                    )
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "filename=\"estado-${index + 1}.bin\"",
                                    )
                                },
                            )
                        }
                    },
                ),
            )
        }
    }

    override suspend fun resolve(
        cardId: String,
        id: String,
        request: StatementResolutionRequest,
    ): Result<AutomaticStatementDto> = send {
        client.post(url(cardId) + "/$id/resolve") {
            withToken(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private suspend inline fun <reified T> send(
        crossinline operation: suspend () -> HttpResponse,
    ): Result<T> {
        val response = try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return Result.failure(
                AutomaticStatementException(
                    null,
                    "Sin conexión. Puedes reintentar la lectura.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                AutomaticStatementException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo incorporar el estado.",
                ),
            )
        }

        return try {
            Result.success(response.body<T>())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(
                AutomaticStatementException(
                    null,
                    "No se pudo interpretar la respuesta. Actualiza los estados guardados.",
                ),
            )
        }
    }
}

data class AutomaticStatementUiState(
    val records: List<AutomaticStatementDto> = emptyList(),
    val current: AutomaticStatementDto? = null,
    val isLoading: Boolean = false,
    val isReading: Boolean = false,
    val isResolving: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class AutomaticStatementViewModel(
    private val cardId: String,
    private val repository: AutomaticStatementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AutomaticStatementUiState())
    val state = _state.asStateFlow()
    private var revision = 0L

    init {
        reload()
    }

    private fun busy() = _state.value.let {
        it.isLoading || it.isReading || it.isResolving || it.sessionExpired
    }

    fun reload() {
        if (busy()) return

        val requestRevision = ++revision
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            repository.list(cardId).fold(
                onSuccess = { records ->
                    if (requestRevision != revision) return@fold

                    if (
                        records.any { !valid(it) } ||
                        records.map { it.id }.distinct().size != records.size
                    ) {
                        fail(
                            AutomaticStatementException(
                                null,
                                "La lista recibida no es válida.",
                            ),
                        )
                    } else {
                        val sorted = records.sortedWith(
                            compareByDescending<AutomaticStatementDto> {
                                it.deteccion.campos.fechaCorte.orEmpty()
                            }.thenByDescending {
                                it.fechaActualizacion
                            },
                        )

                        _state.update { currentState ->
                            currentState.copy(
                                records = sorted,
                                current = sorted.firstOrNull {
                                    it.id == currentState.current?.id
                                } ?: sorted.firstOrNull(),
                                isLoading = false,
                            )
                        }
                    }
                },
                onFailure = {
                    if (requestRevision == revision) fail(it)
                },
            )
        }
    }

    fun importFiles(files: List<StatementUploadFile>) {
        if (busy()) return

        val error = when {
            files.isEmpty() ->
                "Selecciona un PDF o las imágenes del mismo estado."

            files.size > 12 ->
                "Selecciona hasta 12 archivos."

            files.any {
                it.bytes.isEmpty() || it.bytes.size > 10 * 1024 * 1024
            } -> "Cada archivo debe contener datos y pesar hasta 10 MB."

            files.sumOf { it.bytes.size.toLong() } > 20L * 1024 * 1024 ->
                "El conjunto supera 20 MB."

            files.indices.any { index ->
                (0 until index).any {
                    files[it].bytes.contentEquals(files[index].bytes)
                }
            } -> "Hay archivos o páginas repetidos en la selección."

            else -> null
        }

        if (error != null) {
            fileError(error)
            return
        }

        val snapshot = files.toList()
        val requestRevision = ++revision
        _state.update { it.copy(isReading = true, error = null) }

        viewModelScope.launch {
            repository.importFiles(cardId, snapshot).fold(
                onSuccess = {
                    if (requestRevision == revision) accept(it)
                },
                onFailure = {
                    if (requestRevision == revision) fail(it)
                },
            )
        }
    }

    fun resolve(value: String) {
        if (busy()) return

        val current = _state.value.current ?: return

        if (
            value !in current.deteccion.opcionesPago ||
            current.version == Int.MAX_VALUE
        ) return

        val requestRevision = ++revision
        _state.update { it.copy(isResolving = true, error = null) }

        viewModelScope.launch {
            repository.resolve(
                cardId,
                current.id,
                StatementResolutionRequest(current.version, value),
            ).fold(
                onSuccess = { dto ->
                    if (requestRevision != revision) return@fold

                    if (
                        dto.id != current.id ||
                        dto.version != current.version + 1
                    ) {
                        fail(
                            AutomaticStatementException(
                                null,
                                "La respuesta cambió. Actualiza el estado antes de continuar.",
                            ),
                        )
                    } else accept(dto)
                },
                onFailure = {
                    if (requestRevision == revision) fail(it)
                },
            )
        }
    }

    fun fileError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun selectStatement(id: String) {
        if (!busy()) {
            _state.value.records.firstOrNull { it.id == id }?.let { dto ->
                _state.update { it.copy(current = dto, error = null) }
            }
        }
    }

    private fun accept(dto: AutomaticStatementDto) {
        if (!valid(dto)) {
            fail(
                AutomaticStatementException(
                    null,
                    "No se pudo comprobar el estado recibido. Actualiza la lista.",
                ),
            )
            return
        }

        _state.update {
            it.copy(
                records = it.records.filterNot { record ->
                    record.id == dto.id
                } + dto,
                current = dto,
                isLoading = false,
                isReading = false,
                isResolving = false,
                error = null,
            )
        }
    }

    private fun fail(error: Throwable) {
        _state.update {
            it.copy(
                isLoading = false,
                isReading = false,
                isResolving = false,
                error = error.message ?: "No se pudo leer el estado.",
                sessionExpired = it.sessionExpired ||
                        (error as? AutomaticStatementException)?.status == 401,
            )
        }
    }

    private fun valid(dto: AutomaticStatementDto): Boolean =
        dto.tarjetaId == cardId &&
                dto.id.isNotBlank() &&
                dto.version > 0 &&
                dto.fechaActualizacion.isNotBlank() &&
                (
                        dto.deteccion.estado != StatementDetectionStatus.LISTO ||
                                (
                                        dto.versionFinanciera != null &&
                                                dto.versionFinanciera!! > 0 &&
                                                !dto.deteccion.campos.periodo.isNullOrBlank() &&
                                                !dto.deteccion.campos.fechaCorte.isNullOrBlank() &&
                                                !dto.deteccion.campos.fechaLimitePago.isNullOrBlank() &&
                                                !dto.deteccion.campos.pagoMinimo.isNullOrBlank() &&
                                                !dto.deteccion.campos.pagoParaNoGenerarIntereses.isNullOrBlank()
                                        )
                        )
}

object IncomeForecasts {
    private data class Anchor(
        val income: IncomeDto,
        val date: LocalDate,
        val frequency: String,
        val history: List<LocalDate> = emptyList(),
    )

    fun build(
        period: String,
        today: LocalDate,
        incomes: List<IncomeDto>,
        planning: JsonObject,
    ): List<ForecastIncome> {
        require(Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(period))

        val first = LocalDate.parse("$period-01")
        require(first.year in 1..9999)

        val end = first + DatePeriod(months = 1) - DatePeriod(days = 1)
        if (end <= today) return emptyList()

        val byId = incomes.associateBy { it.id }

        val entries = planning["ingresos"] as? JsonArray
            ?: error("Falta la lista de ingresos configurados.")

        fun JsonObject.string(key: String) =
            (this[key] as? JsonPrimitive)?.content

        val anchors = entries.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null

            if (row.string("tipoIngreso") != "RECURRENTE") {
                return@mapNotNull null
            }

            val income = byId[row.string("ingresoId")]
                ?: return@mapNotNull null

            val date = runCatching {
                LocalDate.parse(row.string("fechaCobro").orEmpty())
            }.getOrNull()?.takeIf {
                it.year in 1..9999 && it <= today
            } ?: return@mapNotNull null

            val frequency = row.string("frecuencia")
                ?: return@mapNotNull null

            if (
                income.nombre.isBlank() ||
                !Regex("[0-9]{1,10}(?:\\.[0-9]{1,2})?")
                    .matches(income.montoNeto) ||
                income.montoNeto.replace(".", "").all { it == '0' }
            ) return@mapNotNull null

            Anchor(income, date, frequency)
        }.groupBy {
            it.income.nombre.trim()
                .lowercase()
                .replace(Regex("\\s+"), " ") to it.frequency
        }.values.map { group ->
            group.sortedWith(
                compareByDescending<Anchor> {
                    it.date
                }.thenBy {
                    cents(it.income.montoNeto)
                },
            ).first().copy(
                history = group.map { it.date },
            )
        }

        val lower = maxOf(today, first - DatePeriod(days = 1))

        return anchors.flatMap { anchor ->
            val dates = when (anchor.frequency) {
                "SEMANAL", "CATORCENAL" -> {
                    val days =
                        if (anchor.frequency == "SEMANAL") 7 else 14

                    var next = anchor.date + DatePeriod(
                        days = (
                                anchor.date.daysUntil(lower) / days + 1
                                ) * days,
                    )

                    buildList {
                        while (next <= end) {
                            if (next >= first) add(next)
                            next += DatePeriod(days = days)
                        }
                    }
                }

                "MENSUAL" -> {
                    val anchorEnd = LocalDate.parse(
                        anchor.date.toString().take(7) + "-01",
                    ) + DatePeriod(months = 1) - DatePeriod(days = 1)

                    val day = if (anchor.date == anchorEnd) {
                        end.dayOfMonth
                    } else {
                        minOf(anchor.date.dayOfMonth, end.dayOfMonth)
                    }

                    listOf(
                        LocalDate.parse(
                            period + "-" + day.toString().padStart(2, '0'),
                        ),
                    )
                }

                "QUINCENAL" -> {
                    val observed = anchor.history.sortedDescending()
                        .map { paid ->
                            val paidEnd = LocalDate.parse(
                                paid.toString().take(7) + "-01",
                            ) + DatePeriod(months = 1) - DatePeriod(days = 1)

                            if (paid == paidEnd) 32 else paid.dayOfMonth
                        }
                        .distinct()
                        .take(2)

                    if (observed.size == 2) {
                        observed.map { day ->
                            if (day == 32) {
                                end
                            } else {
                                LocalDate.parse(
                                    period + "-" +
                                            minOf(day, end.dayOfMonth)
                                                .toString().padStart(2, '0'),
                                )
                            }
                        }
                    } else emptyList()
                }

                else -> emptyList()
            }

            dates.filter {
                it > today && it >= first && it <= end
            }.map { date ->
                ForecastIncome(
                    id = "${anchor.income.id}-$date",
                    fecha = date.toString(),
                    monto = anchor.income.montoNeto,
                )
            }
        }.distinctBy {
            it.id
        }.sortedWith(
            compareBy({ it.fecha }, { it.id }),
        ).take(100)
    }

    private fun cents(value: String): Long {
        val parts = value.split('.')

        return parts[0].toLong() * 100 +
                parts.getOrElse(1) { "" }.padEnd(2, '0').toLong()
    }
}

class CardPlanException(
    val status: Int?,
    message: String,
) : Exception(message)

interface CardPlanRepository {
    suspend fun load(periodo: String): Result<CardPlansDto>

    suspend fun acknowledgeNoPayments(
        cardId: String,
        periodo: String,
        financialVersion: Int,
    ): Result<Unit>

    suspend fun resolveStatement(
        cardId: String,
        id: String,
        request: StatementResolutionRequest,
    ): Result<AutomaticStatementDto>
}

class KtorCardPlanRepository(
    private val token: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : CardPlanRepository {
    override suspend fun load(
        periodo: String,
    ): Result<CardPlansDto> {
        var hints = emptyList<ForecastIncome>()
        var notice: String? = null

        try {
            coroutineScope {
                val incomeJob = async {
                    client.get("$baseUrl/api/v1/incomes") {
                        withToken(token)
                    }
                }

                val planningJob = async {
                    client.get("$baseUrl/api/v1/income-planning") {
                        withToken(token)
                    }
                }

                val incomes = incomeJob.await()
                val planning = planningJob.await()

                if (
                    incomes.status.value == 401 ||
                    planning.status.value == 401
                ) {
                    throw CardPlanException(401, "Tu sesión expiró.")
                }

                if (
                    !incomes.status.isSuccess() ||
                    !planning.status.isSuccess()
                ) error("Ingresos sin actualizar")

                hints = IncomeForecasts.build(
                    period = periodo,
                    today = Clock.System.now().toLocalDateTime(
                        TimeZone.of("America/Mexico_City"),
                    ).date,
                    incomes = incomes.body<IncomesSummary>().ingresos,
                    planning = planning.body<JsonObject>(),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: CardPlanException) {
            return Result.failure(error)
        } catch (_: Exception) {
            notice = "No fue posible actualizar los ingresos previstos. Se usa el presupuesto guardado."
        }

        return send<CardPlansDto> {
            client.post("$baseUrl/api/v1/card-plans/$periodo") {
                withToken(token)
                contentType(ContentType.Application.Json)
                setBody(CardPlansRequest(hints))
            }
        }.map { dto ->
            if (notice == null) dto else {
                dto.copy(avisos = (dto.avisos + notice!!).distinct())
            }
        }
    }

    override suspend fun acknowledgeNoPayments(
        cardId: String,
        periodo: String,
        financialVersion: Int,
    ): Result<Unit> =
        send<StatementPaymentsDto> {
            client.put(
                "$baseUrl/api/v1/cards/$cardId/statements/$periodo/payments",
            ) {
                withToken(token)
                contentType(ContentType.Application.Json)

                setBody(
                    StatementPaymentsRequest(
                        emptyList(),
                        0,
                        financialVersion,
                        true,
                    ),
                )
            }
        }.mapCatching { dto ->
            check(
                dto.estado.tarjetaId == cardId &&
                        dto.estado.campos.periodo == periodo &&
                        dto.estado.version == financialVersion &&
                        dto.version == 1 &&
                        dto.versionEstado == financialVersion &&
                        dto.pagos.isEmpty() &&
                        !dto.requiereRevision &&
                        dto.resumen != null,
            ) {
                "Actualiza el plan para comprobar los pagos guardados."
            }
        }

    override suspend fun resolveStatement(
        cardId: String,
        id: String,
        request: StatementResolutionRequest,
    ): Result<AutomaticStatementDto> = send {
        client.post(
            "$baseUrl/api/v1/cards/$cardId/automatic-statements/$id/resolve",
        ) {
            withToken(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private suspend inline fun <reified T> send(
        crossinline operation: suspend () -> HttpResponse,
    ): Result<T> {
        val response = try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return Result.failure(
                CardPlanException(
                    null,
                    "Sin conexión. Puedes actualizar el plan.",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            return Result.failure(
                CardPlanException(
                    response.status.value,
                    response.apiErrorMessage()
                        ?: "No se pudo actualizar el plan.",
                ),
            )
        }

        return try {
            Result.success(response.body<T>())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(
                CardPlanException(
                    null,
                    "La respuesta del plan no se pudo interpretar.",
                ),
            )
        }
    }
}

data class CardPlansUiState(
    val data: CardPlansDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class CardPlansViewModel(
    private val repository: CardPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CardPlansUiState())
    val state = _state.asStateFlow()

    private var job: Job? = null
    private var revision = 0L
    private var period: String? = null

    fun load(periodo: String) {
        if (_state.value.sessionExpired) return

        job?.cancel()

        val requestRevision = ++revision
        period = periodo

        _state.update {
            it.copy(
                data = if (it.data?.periodo == periodo) it.data else null,
                isLoading = true,
                error = null,
            )
        }

        job = viewModelScope.launch {
            repository.load(periodo).fold(
                onSuccess = { dto ->
                    if (requestRevision != revision) return@fold

                    if (
                        dto.periodo != periodo ||
                        dto.fechaReferencia.isBlank() ||
                        dto.tarjetas.map { it.tarjetaId }.distinct().size !=
                        dto.tarjetas.size
                    ) {
                        fail(
                            CardPlanException(
                                null,
                                "El plan recibido no es válido. Actualízalo.",
                            ),
                        )
                    } else {
                        _state.update {
                            it.copy(
                                data = dto,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
                },
                onFailure = {
                    if (requestRevision == revision) fail(it)
                },
            )
        }
    }

    fun confirmUnpaid(plan: CardPaymentPlan) {
        if (
            _state.value.isLoading ||
            _state.value.sessionExpired ||
            plan.accion != "CONFIRMAR_PAGOS" ||
            !currentPlan(plan)
        ) return

        val statement = plan.estado ?: return
        val statementPeriod = statement.campos.periodo ?: return
        val selectedPeriod = period ?: return
        val requestRevision = ++revision

        _state.update { it.copy(isLoading = true, error = null) }

        job = viewModelScope.launch {
            repository.acknowledgeNoPayments(
                plan.tarjetaId,
                statementPeriod,
                statement.version,
            ).fold(
                onSuccess = {
                    if (requestRevision == revision) load(selectedPeriod)
                },
                onFailure = { error ->
                    if (requestRevision != revision) return@fold

                    if ((error as? CardPlanException)?.status == 409) {
                        load(selectedPeriod)
                    } else fail(error)
                },
            )
        }
    }

    fun resolvePayment(plan: CardPaymentPlan, value: String) {
        if (
            _state.value.isLoading ||
            _state.value.sessionExpired ||
            !currentPlan(plan)
        ) return

        val automatic = plan.automatico ?: return
        val selectedPeriod = period ?: return

        if (
            value !in automatic.deteccion.opcionesPago ||
            automatic.version == Int.MAX_VALUE
        ) return

        val requestRevision = ++revision
        _state.update { it.copy(isLoading = true, error = null) }

        job = viewModelScope.launch {
            repository.resolveStatement(
                plan.tarjetaId,
                automatic.id,
                StatementResolutionRequest(automatic.version, value),
            ).fold(
                onSuccess = { dto ->
                    if (requestRevision != revision) return@fold

                    if (
                        dto.id != automatic.id ||
                        dto.tarjetaId != plan.tarjetaId ||
                        dto.version != automatic.version + 1
                    ) {
                        fail(
                            CardPlanException(
                                null,
                                "Actualiza el plan para comprobar el importe guardado.",
                            ),
                        )
                    } else load(selectedPeriod)
                },
                onFailure = { error ->
                    if (requestRevision != revision) return@fold

                    if ((error as? CardPlanException)?.status == 409) {
                        load(selectedPeriod)
                    } else fail(error)
                },
            )
        }
    }

    private fun currentPlan(plan: CardPaymentPlan) =
        _state.value.data?.tarjetas?.any { it == plan } == true

    private fun fail(error: Throwable) {
        val status = (error as? CardPlanException)?.status

        _state.update {
            it.copy(
                isLoading = false,
                error = error.message ?: "No se pudo actualizar el plan.",
                sessionExpired = it.sessionExpired || status == 401,
            )
        }
    }
}