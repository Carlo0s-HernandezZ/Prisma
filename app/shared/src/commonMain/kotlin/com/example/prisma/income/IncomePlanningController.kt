package com.example.prisma.income

import com.example.prisma.api.IncomeFrequency
import com.example.prisma.api.IncomePlanningDto
import com.example.prisma.api.IncomePlanningRequest
import com.example.prisma.api.IncomeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IncomePlanningEditor(
    val incomeId: String? = null,
    val paymentDate: String = "",
    val type: IncomeType? = null,
    val frequency: IncomeFrequency? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class IncomePlanningUiState(
    val records: List<IncomePlanningDto> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val editor: IncomePlanningEditor? = null,
)

class IncomePlanningController(
    private val repository: IncomeRepository,
    private val scope: CoroutineScope,
    private val onSessionExpired: () -> Unit,
    private val onIncomeMissing: () -> Unit,
) {
    private val _state = MutableStateFlow(
        IncomePlanningUiState(),
    )

    val state: StateFlow<IncomePlanningUiState> =
        _state.asStateFlow()

    private var loadJob: Job? = null
    private var revision = 0L

    fun refresh() {
        if (_state.value.editor?.isSaving == true) return

        loadJob?.cancel()
        val currentRevision = ++revision

        _state.update {
            it.copy(
                isLoading = true,
                error = null,
            )
        }

        loadJob = scope.launch {
            val result = repository.listPlanning()

            if (currentRevision != revision) return@launch

            result.fold(
                onSuccess = { summary ->
                    _state.update {
                        it.copy(
                            records = summary.ingresos,
                            hasLoaded = true,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { cause ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = messageFor(cause),
                        )
                    }

                    notifyExpiredSession(cause)
                },
            )
        }
    }

    fun openEditor() {
        val current = _state.value

        if (
            !current.hasLoaded ||
            current.isLoading ||
            current.error != null
        ) {
            return
        }

        if (current.editor?.isSaving == true) return

        _state.update {
            it.copy(
                editor = IncomePlanningEditor(),
                message = null,
            )
        }
    }

    fun closeEditor() {
        _state.update {
            if (it.editor?.isSaving == true) {
                it
            } else {
                it.copy(editor = null)
            }
        }
    }

    fun selectIncome(incomeId: String) {
        val current = _state.value

        if (
            current.editor == null ||
            current.editor.isSaving
        ) {
            return
        }

        val saved = current.records.firstOrNull {
            it.ingresoId == incomeId
        }

        _state.update {
            it.copy(
                editor = IncomePlanningEditor(
                    incomeId = incomeId,
                    paymentDate = saved?.fechaCobro.orEmpty(),
                    type = saved?.tipoIngreso,
                    frequency = saved?.frecuencia,
                ),
            )
        }
    }

    fun onDateChange(value: String) = edit {
        it.copy(
            paymentDate = value.take(10),
            error = null,
        )
    }

    fun onTypeChange(value: IncomeType) = edit {
        it.copy(
            type = value,
            frequency = if (
                value == IncomeType.EXTRAORDINARIO
            ) {
                null
            } else {
                it.frequency
            },
            error = null,
        )
    }

    fun onFrequencyChange(value: IncomeFrequency) = edit {
        it.copy(
            frequency = value,
            error = null,
        )
    }

    fun save(availableIncomeIds: Set<String>) {
        val current = _state.value
        val editor = current.editor ?: return

        if (editor.isSaving || current.isLoading) return

        if (!current.hasLoaded) {
            return setEditorError(
                "Primero carga la configuración.",
            )
        }

        if (current.error != null) {
            return setEditorError(
                "Reintenta la carga antes de guardar.",
            )
        }

        val incomeId = editor.incomeId
            ?: return setEditorError(
                "Selecciona un ingreso.",
            )

        if (incomeId !in availableIncomeIds) {
            return setEditorError(
                "Ese ingreso ya no está disponible.",
            )
        }

        val date = editor.paymentDate.trim()

        if (
            !Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                .matches(date)
        ) {
            return setEditorError(
                "Escribe la fecha como AAAA-MM-DD.",
            )
        }

        val type = editor.type
            ?: return setEditorError(
                "Selecciona el tipo de ingreso.",
            )

        if (
            type == IncomeType.RECURRENTE &&
            editor.frequency == null
        ) {
            return setEditorError(
                "Selecciona la frecuencia del ingreso.",
            )
        }

        val request = IncomePlanningRequest(
            fechaCobro = date,
            tipoIngreso = type,
            frecuencia = if (
                type == IncomeType.RECURRENTE
            ) {
                editor.frequency
            } else {
                null
            },
        )

        // Una lectura anterior no debe sobrescribir este guardado.
        loadJob?.cancel()
        val currentRevision = ++revision

        _state.update {
            it.copy(
                isLoading = false,
                error = null,
                message = null,
                editor = editor.copy(
                    isSaving = true,
                    error = null,
                ),
            )
        }

        scope.launch {
            val result = repository.savePlanning(
                incomeId,
                request,
            )

            if (currentRevision != revision) return@launch

            result.fold(
                onSuccess = { saved ->
                    _state.update {
                        it.copy(
                            records = it.records.filterNot { record ->
                                record.ingresoId == saved.ingresoId
                            } + saved,
                            editor = null,
                            message = "Datos de cobro guardados.",
                        )
                    }

                    refresh()
                },
                onFailure = { cause ->
                    _state.update {
                        it.copy(
                            editor = it.editor?.copy(
                                isSaving = false,
                                error = messageFor(cause),
                            ),
                        )
                    }

                    notifyExpiredSession(cause)

                    if (
                        (cause as? IncomeException)?.error ==
                        IncomeError.NotFound
                    ) {
                        onIncomeMissing()
                    }
                },
            )
        }
    }

    private fun edit(
        transform: (IncomePlanningEditor) -> IncomePlanningEditor,
    ) {
        _state.update { current ->
            val editor = current.editor

            if (editor == null || editor.isSaving) {
                current
            } else {
                current.copy(
                    editor = transform(editor),
                )
            }
        }
    }

    private fun setEditorError(message: String) = edit {
        it.copy(error = message)
    }

    private fun notifyExpiredSession(cause: Throwable) {
        if (
            (cause as? IncomeException)?.error ==
            IncomeError.Unauthorized
        ) {
            onSessionExpired()
        }
    }

    private fun messageFor(cause: Throwable): String {
        val exception = cause as? IncomeException

        exception?.serverMessage
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return it
            }

        return when (exception?.error) {
            IncomeError.Unauthorized ->
                "Tu sesión expiró."

            IncomeError.Network ->
                "Sin conexión con el servidor. Puedes reintentar."

            IncomeError.NotFound ->
                "Ingreso o servicio no disponible. Actualiza los " +
                        "ingresos y verifica que reiniciaste el servidor."

            IncomeError.Invalid ->
                "Revisa la fecha, el tipo y la frecuencia."

            else ->
                "No se pudo completar la solicitud. Inténtalo de nuevo."
        }
    }
}