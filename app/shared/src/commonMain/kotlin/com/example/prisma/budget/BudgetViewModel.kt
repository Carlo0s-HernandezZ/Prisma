@file:OptIn(
    kotlin.time.ExperimentalTime::class,
    kotlin.uuid.ExperimentalUuidApi::class,
)

package com.example.prisma.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun budgetToday(): String =
    Clock.System.now()
        .toLocalDateTime(
            TimeZone.of("America/Mexico_City"),
        )
        .date
        .toString()

data class BudgetMovementEditor(
    val id: String? = null,
    val tipo: BudgetMovementType,
    val concepto: String = "",
    val fecha: String = "",
    val monto: String = "",
    val error: String? = null,
)

data class BudgetUiState(
    val periodo: String = budgetToday().take(7),
    val fechaBase: String = budgetToday(),
    val saldo: String = "",
    val reserva: String = "0.00",
    val movimientos: List<BudgetMovement> = emptyList(),
    val version: Int = 0,
    val resumen: BudgetSummary? = null,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val dirty: Boolean = false,
    val conflict: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val pendingPeriod: String? = null,
    val editor: BudgetMovementEditor? = null,
)

class BudgetViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetUiState())
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var revision = 0L

    init {
        load(_state.value.periodo)
    }

    fun selectPeriod(value: String) {
        if (value == _state.value.periodo) return

        if (
            date(value + "-01") == null ||
            value > budgetToday().take(7)
        ) {
            _state.update {
                it.copy(
                    error = "Selecciona el mes actual o uno anterior.",
                )
            }
            return
        }

        requestLoad(value)
    }

    fun reload() = requestLoad(_state.value.periodo)

    private fun requestLoad(period: String) {
        val current = _state.value

        if (current.isLoading || current.isSaving) return

        if (current.dirty || current.editor != null) {
            _state.update {
                it.copy(pendingPeriod = period)
            }
        } else {
            load(period)
        }
    }

    fun cancelDiscard() {
        _state.update {
            it.copy(pendingPeriod = null)
        }
    }

    fun confirmDiscard() {
        _state.value.pendingPeriod?.let(::load)
    }

    private fun load(period: String) {
        loadJob?.cancel()
        val requestRevision = ++revision
        val today = budgetToday()

        _state.value = BudgetUiState(
            periodo = period,
            fechaBase = if (period == today.take(7)) {
                today
            } else {
                ""
            },
            isLoading = true,
        )

        loadJob = viewModelScope.launch {
            repository.get(period).fold(
                onSuccess = { dto ->
                    if (requestRevision != revision) {
                        return@fold
                    }

                    if (
                        dto.periodo != period ||
                        (
                                dto.configurado &&
                                        (
                                                dto.presupuesto == null ||
                                                        dto.resumen == null
                                                )
                                )
                    ) {
                        fail(
                            BudgetException(
                                null,
                                "El presupuesto recibido no es válido.",
                            ),
                        )
                    } else if (dto.configurado) {
                        apply(dto)
                    } else {
                        _state.update {
                            it.copy(
                                hasLoaded = true,
                                isLoading = false,
                            )
                        }
                    }
                },
                onFailure = {
                    if (requestRevision == revision) {
                        fail(it)
                    }
                },
            )
        }
    }

    private fun apply(dto: BudgetDto) {
        val budget = dto.presupuesto ?: return

        _state.value = BudgetUiState(
            periodo = dto.periodo,
            fechaBase = budget.fechaBase,
            saldo = budget.saldoDisponible,
            reserva = budget.reserva,
            movimientos = budget.movimientos,
            version = budget.version,
            resumen = dto.resumen,
            hasLoaded = true,
            message = "Presupuesto cargado y calculado.",
        )
    }

    private fun canEdit(value: BudgetUiState): Boolean =
        value.hasLoaded &&
                !value.isLoading &&
                !value.isSaving &&
                !value.conflict

    private fun edit(
        transform: (BudgetUiState) -> BudgetUiState,
    ) {
        _state.update {
            if (!canEdit(it)) {
                it
            } else {
                transform(it).copy(
                    dirty = true,
                    resumen = null,
                    error = null,
                    message = null,
                )
            }
        }
    }

    fun onDateChange(value: String) = edit {
        it.copy(fechaBase = value)
    }

    fun onBalanceChange(value: String) = edit {
        it.copy(saldo = value.replace(',', '.'))
    }

    fun onReserveChange(value: String) = edit {
        it.copy(reserva = value.replace(',', '.'))
    }

    fun openMovement(tipo: BudgetMovementType) {
        val current = _state.value
        if (!canEdit(current)) return

        if (current.movimientos.size >= 100) {
            _state.update {
                it.copy(
                    error = "Puedes agregar hasta 100 movimientos.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                editor = BudgetMovementEditor(tipo = tipo),
            )
        }
    }

    fun editMovement(id: String) {
        if (!canEdit(_state.value)) return

        val movement = _state.value.movimientos.firstOrNull {
            it.id == id
        } ?: return

        _state.update {
            it.copy(
                editor = BudgetMovementEditor(
                    id = movement.id,
                    tipo = movement.tipo,
                    concepto = movement.concepto,
                    fecha = movement.fecha,
                    monto = movement.monto,
                ),
            )
        }
    }

    fun removeMovement(id: String) = edit {
        it.copy(
            movimientos = it.movimientos.filterNot { movement ->
                movement.id == id
            },
        )
    }

    fun closeMovement() {
        if (!_state.value.isSaving) {
            _state.update {
                it.copy(editor = null)
            }
        }
    }

    private fun editMovementForm(
        transform: (BudgetMovementEditor) -> BudgetMovementEditor,
    ) {
        _state.update { current ->
            val editor = current.editor

            if (!canEdit(current) || editor == null) {
                current
            } else {
                current.copy(
                    editor = transform(editor).copy(error = null),
                )
            }
        }
    }

    fun onMovementName(value: String) = editMovementForm {
        it.copy(concepto = value.take(100))
    }

    fun onMovementDate(value: String) = editMovementForm {
        it.copy(fecha = value)
    }

    fun onMovementAmount(value: String) = editMovementForm {
        it.copy(monto = value.replace(',', '.'))
    }

    fun saveMovement() {
        val current = _state.value
        val editor = current.editor ?: return

        if (!canEdit(current)) return

        val name = editor.concepto.trim().replace(
            Regex("\\s+"),
            " ",
        )

        val day = date(editor.fecha)
        val amount = money(editor.monto, allowZero = false)

        val error = when {
            date(current.fechaBase) == null ->
                "Selecciona primero la fecha de referencia."

            name.isBlank() ->
                "Escribe el concepto."

            day == null ||
                    editor.fecha.take(7) != current.periodo ||
                    editor.fecha < current.fechaBase ->
                "Selecciona una fecha entre la fecha base y el fin de este mes."

            amount == null ->
                "Escribe un monto mayor a cero, con hasta dos decimales."

            else -> null
        }

        if (error != null) {
            _state.update {
                it.copy(
                    editor = editor.copy(error = error),
                )
            }
            return
        }

        val movement = BudgetMovement(
            id = editor.id ?: Uuid.random().toString(),
            tipo = editor.tipo,
            concepto = name,
            fecha = editor.fecha,
            monto = amount!!,
        )

        edit {
            it.copy(
                movimientos = it.movimientos.filterNot { existing ->
                    existing.id == movement.id
                } + movement,
                editor = null,
            )
        }
    }

    fun save() {
        val current = _state.value

        if (!canEdit(current) || current.editor != null) return

        val base = date(current.fechaBase)
        val balance = money(current.saldo, allowZero = true)
        val reserve = money(current.reserva, allowZero = true)

        val error = when {
            base == null ||
                    current.fechaBase.take(7) != current.periodo ||
                    current.fechaBase > budgetToday() ->
                "Selecciona una fecha base de este periodo, hasta hoy."

            balance == null || reserve == null ->
                "Saldo y reserva deben ser montos desde cero, con hasta dos decimales."

            else -> null
        }

        if (error != null) {
            _state.update {
                it.copy(error = error)
            }
            return
        }

        val payload = BudgetRequest(
            fechaBase = current.fechaBase,
            saldoDisponible = balance!!,
            reserva = reserve!!,
            movimientos = current.movimientos,
            version = current.version,
        )

        loadJob?.cancel()
        val requestRevision = ++revision

        _state.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            repository.save(
                current.periodo,
                payload,
            ).fold(
                onSuccess = { dto ->
                    if (requestRevision != revision) {
                        return@fold
                    }

                    if (
                        dto.periodo != current.periodo ||
                        !dto.configurado ||
                        dto.presupuesto == null ||
                        dto.resumen == null
                    ) {
                        fail(
                            BudgetException(
                                null,
                                "La respuesta de guardado no es válida. " +
                                        "Recarga para comprobar el presupuesto.",
                            ),
                        )
                    } else {
                        apply(dto)

                        _state.update {
                            it.copy(
                                message = "Presupuesto guardado.",
                            )
                        }
                    }
                },
                onFailure = {
                    if (requestRevision == revision) {
                        fail(it)
                    }
                },
            )
        }
    }

    private fun fail(cause: Throwable) {
        val status = (cause as? BudgetException)?.status

        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                conflict = it.conflict || status == 409,
                sessionExpired = status == 401,
                error = cause.message
                    ?: "No se pudo completar la solicitud.",
            )
        }
    }

    private fun date(value: String): LocalDate? =
        runCatching {
            LocalDate.parse(value).takeIf {
                it.year in 1..9999 &&
                        it.toString() == value
            }
        }.getOrNull()

    private fun money(
        raw: String,
        allowZero: Boolean,
    ): String? {
        val value = raw.trim()

        if (
            !Regex("[0-9]{1,10}(?:\\.[0-9]{1,2})?")
                .matches(value)
        ) {
            return null
        }

        val parts = value.split('.')

        val cents = parts[0].toLong() * 100 +
                parts.getOrElse(1) { "" }
                    .padEnd(2, '0')
                    .toLong()

        if (!allowZero && cents == 0L) return null

        return (cents / 100).toString() +
                "." +
                (cents % 100).toString().padStart(2, '0')
    }
}