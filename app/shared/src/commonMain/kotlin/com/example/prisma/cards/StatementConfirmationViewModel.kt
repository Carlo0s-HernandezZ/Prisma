package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.ConfirmedStatementDto
import com.example.prisma.api.StatementConfirmationRequest
import com.example.prisma.api.StatementExtractionResponse
import com.example.prisma.api.StatementFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class StatementConfirmationUiState(
    val records: List<ConfirmedStatementDto> = emptyList(),
    val selectedPeriod: String? = null,
    val draft: StatementFields? = null,
    val acknowledged: Boolean = false,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val conflict: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class StatementConfirmationViewModel(
    private val cardId: String,
    private val repository: StatementConfirmationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        StatementConfirmationUiState(),
    )

    val state = _state.asStateFlow()

    init {
        reload()
    }

    private fun busy(
        state: StatementConfirmationUiState,
    ): Boolean =
        state.isLoading || state.isSaving

    private fun canBegin(
        state: StatementConfirmationUiState,
    ): Boolean =
        state.hasLoaded &&
                !busy(state) &&
                state.draft == null &&
                !state.conflict &&
                !state.sessionExpired

    fun reload() {
        val current = _state.value

        if (busy(current)) {
            return
        }

        if (current.draft != null) {
            _state.update {
                it.copy(
                    error = "Cancela la edición antes de recargar los estados guardados.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            repository.list(cardId).fold(
                onSuccess = { records ->
                    if (
                        records.any { !validRecord(it) } ||
                        records.map {
                            it.campos.periodo
                        }.distinct().size != records.size
                    ) {
                        fail(
                            StatementConfirmationException(
                                null,
                                "La lista recibida no es válida. Recarga los estados.",
                                requiresReload = true,
                            ),
                        )
                    } else {
                        val sorted = records.sortedByDescending {
                            it.campos.periodo
                        }

                        _state.update { currentState ->
                            currentState.copy(
                                records = sorted,
                                selectedPeriod =
                                    currentState.selectedPeriod?.takeIf { period ->
                                        sorted.any {
                                            it.campos.periodo == period
                                        }
                                    } ?: sorted.firstOrNull()?.campos?.periodo,
                                hasLoaded = true,
                                isLoading = false,
                                conflict = false,
                                error = null,
                            )
                        }
                    }
                },
                onFailure = ::fail,
            )
        }
    }

    fun selectPeriod(value: String) {
        if (!canBegin(_state.value)) {
            return
        }

        if (!validPeriod(value)) {
            _state.update {
                it.copy(
                    error = "Selecciona un periodo válido.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                selectedPeriod = value,
                error = null,
                message = null,
            )
        }
    }

    fun startReview(
        extraction: StatementExtractionResponse,
    ) {
        if (!canBegin(_state.value)) {
            return
        }

        _state.update {
            it.copy(
                draft = extraction.campos,
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun editSaved() {
        val current = _state.value

        if (!canBegin(current)) {
            return
        }

        val record = current.records.singleOrNull {
            it.campos.periodo == current.selectedPeriod
        } ?: return

        _state.update {
            it.copy(
                draft = record.campos,
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun edit(
        transform: (StatementFields) -> StatementFields,
    ) {
        _state.update { current ->
            if (
                busy(current) ||
                current.conflict ||
                current.sessionExpired ||
                current.draft == null
            ) {
                current
            } else {
                current.copy(
                    draft = transform(current.draft),
                    acknowledged = false,
                    error = null,
                    message = null,
                )
            }
        }
    }

    fun setAcknowledged(value: Boolean) {
        _state.update {
            if (
                busy(it) ||
                it.conflict ||
                it.sessionExpired ||
                it.draft == null
            ) {
                it
            } else {
                it.copy(
                    acknowledged = value,
                    error = null,
                )
            }
        }
    }

    fun cancelEdit() {
        _state.update {
            if (busy(it)) {
                it
            } else {
                it.copy(
                    draft = null,
                    acknowledged = false,
                    error = if (it.conflict) it.error else null,
                    message = null,
                )
            }
        }
    }

    fun discardAndReload() {
        if (busy(_state.value)) {
            return
        }

        _state.update {
            it.copy(
                draft = null,
                acknowledged = false,
            )
        }

        reload()
    }

    fun save() {
        val current = _state.value
        val draft = current.draft ?: return

        if (
            !current.hasLoaded ||
            busy(current) ||
            current.conflict ||
            current.sessionExpired
        ) {
            return
        }

        val period = draft.periodo

        val error = when {
            !current.acknowledged ->
                "Confirma que revisaste los importes y las fechas contra el documento."

            period == null || !validPeriod(period) ->
                "Selecciona el periodo del estado de cuenta."

            draft.fechaCorte.isNullOrBlank() ||
                    draft.fechaLimitePago.isNullOrBlank() ->
                "Selecciona la fecha de corte y la fecha límite de pago."

            draft.pagoMinimo.isNullOrBlank() ||
                    draft.pagoParaNoGenerarIntereses.isNullOrBlank() ->
                "Completa el pago mínimo y el pago para no generar intereses."

            else -> null
        }

        if (error != null) {
            _state.update {
                it.copy(error = error)
            }
            return
        }

        val version = current.records.singleOrNull {
            it.campos.periodo == period
        }?.version ?: 0

        if (version == Int.MAX_VALUE) {
            _state.update {
                it.copy(
                    error = "Se alcanzó el límite de revisiones de este estado de cuenta.",
                )
            }
            return
        }

        val request = StatementConfirmationRequest(
            campos = draft,
            version = version,
            confirmadoPorUsuario = true,
        )

        _state.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            repository.save(
                cardId,
                period!!,
                request,
            ).fold(
                onSuccess = { dto ->
                    if (
                        !validRecord(dto) ||
                        dto.campos.periodo != period ||
                        dto.version != version + 1
                    ) {
                        fail(
                            StatementConfirmationException(
                                null,
                                "No se pudo comprobar la respuesta de guardado. " +
                                        "Recarga antes de continuar.",
                                requiresReload = true,
                            ),
                        )
                    } else {
                        _state.update {
                            it.copy(
                                records = (
                                        it.records.filterNot { record ->
                                            record.campos.periodo == period
                                        } + dto
                                        ).sortedByDescending { record ->
                                        record.campos.periodo
                                    },
                                selectedPeriod = period,
                                draft = null,
                                acknowledged = false,
                                isSaving = false,
                                conflict = false,
                                error = null,
                                message = "Estado de cuenta confirmado y guardado.",
                            )
                        }
                    }
                },
                onFailure = ::fail,
            )
        }
    }

    private fun fail(error: Throwable) {
        val failure = error as? StatementConfirmationException

        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                error = error.message ?: "No se pudo completar la solicitud.",
                conflict = it.conflict ||
                        failure?.status == 409 ||
                        failure?.requiresReload == true,
                sessionExpired = it.sessionExpired ||
                        failure?.status == 401,
            )
        }
    }

    private fun validPeriod(value: String): Boolean =
        Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(value) &&
                runCatching {
                    LocalDate.parse(value + "-01").year in 1..9999
                }.getOrDefault(false)

    private fun validRecord(
        dto: ConfirmedStatementDto,
    ): Boolean {
        val fields = dto.campos
        val period = fields.periodo ?: return false

        return dto.tarjetaId == cardId &&
                dto.version > 0 &&
                dto.fechaConfirmacion.isNotBlank() &&
                validPeriod(period) &&
                fields.fechaCorte?.take(7) == period &&
                !fields.fechaLimitePago.isNullOrBlank() &&
                !fields.pagoMinimo.isNullOrBlank() &&
                !fields.pagoParaNoGenerarIntereses.isNullOrBlank()
    }
}